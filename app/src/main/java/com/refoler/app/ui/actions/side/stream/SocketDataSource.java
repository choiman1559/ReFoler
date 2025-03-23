package com.refoler.app.ui.actions.side.stream;

import static androidx.media3.common.util.Util.castNonNull;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.TransferListener;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.refoler.Refoler;
import com.refoler.app.backend.consts.EndPointConst;
import com.refoler.app.process.actions.DeAsyncJob;
import com.refoler.app.process.actions.impl.socket.SocketRandAccess;
import com.refoler.app.process.db.RemoteFile;

import java.util.concurrent.ConcurrentHashMap;

@UnstableApi
public final class SocketDataSource extends BaseDataSource {

    private static final ConcurrentHashMap<Uri, Factory> factoryMap = new ConcurrentHashMap<>();

    public static MediaItem createMediaItem(Context context, Refoler.Device device, RemoteFile remoteFile) {
        Factory factory = new Factory()
                .setContext(context)
                .setSource(device, remoteFile);
        factoryMap.put(factory.createUri(), factory);

        return new MediaItem.Builder()
                .setUri(factory.createUri())
                .build();
    }

    public static final class Factory implements DataSource.Factory {

        @Nullable
        private TransferListener listener;
        private Context context;
        private Refoler.Device device;
        private RemoteFile remoteFile;

        @SuppressWarnings("unused")
        @CanIgnoreReturnValue
        public Factory setListener(@Nullable TransferListener listener) {
            this.listener = listener;
            return this;
        }

        @CanIgnoreReturnValue
        public Factory setContext(Context context) {
            this.context = context;
            return this;
        }

        @CanIgnoreReturnValue
        public Factory setSource(Refoler.Device device, RemoteFile remoteFile) {
            this.device = device;
            this.remoteFile = remoteFile;
            return this;
        }

        @NonNull
        @Override
        public SocketDataSource createDataSource() {
            SocketDataSource dataSource = new SocketDataSource(context);
            if (listener != null) {
                dataSource.addTransferListener(listener);
            }
            return dataSource;
        }

        @NonNull
        public Uri createUri() {
            return Uri.parse(Uri.encode(String.format("file://%s%s%s",
                    device.getDeviceId(),
                    EndPointConst.FILE_PART_CONTROL_SEPARATOR,
                    remoteFile.getPath())));
        }
    }

    @Nullable
    private SocketRandAccess file;
    @Nullable
    private Uri uri;

    private long bytesRemaining;
    private boolean opened;
    private final Context context;

    private long fileLength;
    private long cacheRemaining;
    private long cachePointer = -1;
    private final int CACHE_SIZE = 5 * 1024 * 1024; // 5MB
    private final byte[] cacheData = new byte[CACHE_SIZE];

    public SocketDataSource(Context context) {
        super(/* isNetwork= */ true);
        this.context = context;
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws FileDataSource.FileDataSourceException {
        if (this.file == null || !dataSpec.uri.equals(this.uri)) {
            Factory factory = factoryMap.get(dataSpec.uri);
            if (!factoryMap.containsKey(dataSpec.uri) || factory == null) {
                throw new FileDataSource.FileDataSourceException("Not registered URI: " + dataSpec.uri.getPath() + "; is MediaItem not created with SocketDataSource.createMediaItem?",
                        null, PlaybackException.ERROR_CODE_BAD_VALUE);
            }

            Refoler.Device device = factory.device;
            RemoteFile remoteFile = factory.remoteFile;
            this.uri = dataSpec.uri;

            this.file = new SocketRandAccess(device, remoteFile.getPath(), true, false);
            this.file.setSynchronized(true);
            boolean channel = this.file.requestChannel(context);
            Log.d("ddd", "req_channel: " + channel + " length: " + file.getFileLength());
            if (!channel) {
                throw new FileDataSource.FileDataSourceException(null, null, PlaybackException.ERROR_CODE_INVALID_STATE);
            }
        }

        transferInitializing(dataSpec);
        file.seek(dataSpec.position);
        fileLength = file.getFileLength();

        bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? fileLength - dataSpec.position : dataSpec.length;
        cacheRemaining = bytesRemaining;

        if (bytesRemaining < 0) {
            throw new FileDataSource.FileDataSourceException(
                    /* message= */ null,
                    /* cause= */ null,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }

        opened = true;
        transferStarted(dataSpec);
        return bytesRemaining;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) {
        if (length == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            final int lengthToRead = (int) Math.min(bytesRemaining, length);
            final int bytesRead = readCache(buffer, offset, lengthToRead);
            Log.d("ddd", "fetch buffer: " + buffer.length + " offset: " + offset + " read: " + bytesRemaining + " length: " + length + " result: " + bytesRead);

            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
                bytesTransferred(bytesRead);
            } else if (bytesRead == -1) {
                return C.RESULT_END_OF_INPUT;
            }
            return bytesRead;
        }
    }

    private void renewCache() {
        Log.d("ddd", "renew cache:" + cacheRemaining);
        final int readBytes = new DeAsyncJob<Integer>((job) -> {
            castNonNull(file).setBufferReceivedListener((result, receiveBuffer) -> {
                System.arraycopy(receiveBuffer, 0, cacheData, 0, result);
                job.setResult(result);
            });
            castNonNull(file).readBytes(CACHE_SIZE, 0, (int) Math.min(CACHE_SIZE, cacheRemaining), false);
        }).runAndWait();

        if (readBytes > 0) {
            cacheRemaining -= readBytes;
        }
    }

    private int readCache(byte[] buffer, int offset, int length) {
        if (cachePointer < 0) {
            Log.d("ddd", "init cache");
            cachePointer = 0;
            renewCache();
            System.arraycopy(cacheData, 0, buffer, offset, length);
            return length;
        } else if (cachePointer <= fileLength - bytesRemaining
                && fileLength - bytesRemaining + length < cachePointer + CACHE_SIZE) {
            Log.d("ddd", "within cache size, cachePointer: " + cachePointer);
            System.arraycopy(cacheData, (int) ((fileLength - bytesRemaining) - cachePointer), buffer, offset, length);
            return length;
        } else if (cachePointer <= fileLength - bytesRemaining
                && fileLength - bytesRemaining + length >= cachePointer + CACHE_SIZE) {
            Log.d("ddd", "out of cache size, cachePointer: " + cachePointer);
            System.arraycopy(cacheData,
                    (int) ((fileLength - bytesRemaining) - cachePointer), buffer, offset,
                    (int) ((cachePointer + CACHE_SIZE) - (fileLength - bytesRemaining)));
            cachePointer += CACHE_SIZE;
            renewCache();
            System.arraycopy(cacheData, 0, buffer,
                    (int) (offset + (cachePointer - (fileLength - bytesRemaining))),
                    (int) ((fileLength - bytesRemaining + length) - cachePointer));
            return length;
        } else {
            return C.RESULT_END_OF_INPUT;
        }
    }

    @Override
    @Nullable
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        if (uri != null) {
            uri = null;
        }
        try {
            if (file != null) {
                file.close();
            }
        } finally {
            file = null;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }
}