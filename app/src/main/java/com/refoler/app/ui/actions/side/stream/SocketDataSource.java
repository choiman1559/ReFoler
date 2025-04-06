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

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @noinspection FieldCanBeLocal
 */
@UnstableApi
public final class SocketDataSource extends BaseDataSource {

    private static final String LogTAG = "SocketDataSource";
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

    private class MovingCacheItem {
        private final byte[] buffer = new byte[CACHE_SIZE];
        private int pointer = 0;

        public int getPointer() {
            return pointer;
        }

        public void setPointer(int pointer) {
            this.pointer = pointer;
        }

        public void setCache(byte[] buffer) {
            System.arraycopy(buffer, 0, this.buffer, 0, buffer.length);
        }

        public void writeCacheTo(int srcOffset, byte[] buffer, int destOffset, int length) {
            System.arraycopy(this.buffer, srcOffset, buffer, destOffset, length);
        }
    }

    private interface OnCacheFetchListener {
        void onFetch();
    }

    @Nullable
    private SocketRandAccess file;
    @Nullable
    private DataSpec dataSpec;

    private long bytesRemaining;
    private boolean opened;
    private final Context context;

    private long fileLength;
    private long cacheRemaining;
    private long cachePointer = -1;
    private final int CACHE_SIZE = 3 * 1024 * 1024; // 3MB
    private final int CACHE_MAX_QUERY = 16; // Total 48MB cache allocation
    private final float CACHE_REALLOC_RATIO = 0.5f; // Re-Read to cache when 50% of memory is empty

    private Thread poolFetchingThread;
    private OnCacheFetchListener onCacheFetchListener;
    private final Queue<MovingCacheItem> movingCacheItems = new LinkedList<>();

    public SocketDataSource(Context context) {
        super(/* isNetwork= */ false);
        this.context = context;
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws FileDataSource.FileDataSourceException {
        if (this.file == null || (this.dataSpec != null && !dataSpec.uri.equals(this.dataSpec.uri))) {
            Factory factory = factoryMap.get(dataSpec.uri);
            if (!factoryMap.containsKey(dataSpec.uri) || factory == null) {
                throw new FileDataSource.FileDataSourceException("Not registered URI: " + dataSpec.uri.getPath() + "; is MediaItem not created with SocketDataSource.createMediaItem?",
                        null, PlaybackException.ERROR_CODE_BAD_VALUE);
            }

            Refoler.Device device = factory.device;
            RemoteFile remoteFile = factory.remoteFile;
            this.dataSpec = dataSpec;

            this.file = new SocketRandAccess(device, remoteFile.getPath(), true, false);
            this.file.setSynchronized(true);
            boolean channel = this.file.requestChannel(context);
            Log.d(LogTAG, "req_channel: " + channel + " length: " + file.getFileLength());
            if (!channel) {
                throw new FileDataSource.FileDataSourceException(null, null, PlaybackException.ERROR_CODE_INVALID_STATE);
            }
        }

        transferInitializing(dataSpec);
        file.seek(dataSpec.position);
        fileLength = file.getFileLength();

        bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? fileLength - dataSpec.position : dataSpec.length;
        cacheRemaining = bytesRemaining;

        if (cachePointer >= 0) {
            cachePointer = -1;
            movingCacheItems.clear();
        }

        if (bytesRemaining < 0) {
            throw new FileDataSource.FileDataSourceException(
                    /* message= */ null,
                    /* cause= */ null,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }

        opened = true;
        poolFetchingThread = new Thread(this::runMemoryScheduler);
        poolFetchingThread.start();

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
            Log.d(LogTAG, "fetch buffer: " + buffer.length + " offset: " + offset + " read: " + bytesRemaining + " length: " + length + " result: " + bytesRead);

            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
                bytesTransferred(bytesRead);
            } else if (bytesRead == -1) {
                return C.RESULT_END_OF_INPUT;
            }
            return bytesRead;
        }
    }

    private void runMemoryScheduler() {
        while (true) {
            if (CACHE_MAX_QUERY * CACHE_REALLOC_RATIO > movingCacheItems.size()) {
                Log.d("ddd", "Cache refilled: " + movingCacheItems.size());
                executeAddMemoryJob().runAndWait();
            }

            if (cacheRemaining <= 0) {
                return;
            }

            try {
                //noinspection BusyWait
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e("ddd", "runMemoryScheduler thread interrupted!!!");
            }
        }
    }

    private DeAsyncJob<Integer> executeAddMemoryJob() {
        return new DeAsyncJob<>((job -> {
            final AtomicInteger requireCacheQuantity = new AtomicInteger(CACHE_MAX_QUERY - movingCacheItems.size());
            final AtomicInteger actualReadSize = new AtomicInteger();
            final int requireReadSize = CACHE_SIZE * requireCacheQuantity.get();

            castNonNull(file).setBufferReceivedListener((result, receiveBuffer) -> {
                if (result == SocketRandAccess.RAW_DATA_READ_FINISH) {
                    if (onCacheFetchListener != null) {
                        Log.d("ddd", "call onCacheFetchListener");
                        onCacheFetchListener.onFetch();
                    }
                    job.setResult(actualReadSize.get());
                    return;
                }

                MovingCacheItem movingCacheItem = new MovingCacheItem();
                movingCacheItem.setCache(receiveBuffer);

                if (cachePointer < 0) {
                    cachePointer = fileLength - cacheRemaining;
                } else {
                    cachePointer += result;
                }

                if (result > 0) {
                    Log.d("ddd", "read decrease: " + result + ", cacheRemaining: " + cacheRemaining + ", cachePointer: " + cachePointer);
                    cacheRemaining -= result;
                }

                movingCacheItem.setPointer((int) cachePointer);
                movingCacheItems.add(movingCacheItem);
                if (onCacheFetchListener != null) {
                    Log.d("ddd", "call onCacheFetchListener");
                    onCacheFetchListener.onFetch();
                }

                if (requireCacheQuantity.decrementAndGet() > 0) {
                    actualReadSize.addAndGet(result);
                } else {
                    job.setResult(actualReadSize.get());
                }
            });

            castNonNull(file).readBytes(CACHE_SIZE, 0, (int) Math.min(requireReadSize, cacheRemaining), true);
        }));
    }

    private MovingCacheItem requireCache(boolean popFirst) {
        Log.d(LogTAG, "Renew cache: " + cacheRemaining + ", Current query: " + movingCacheItems.size());
        if (!movingCacheItems.isEmpty() && popFirst) {
            Log.d(LogTAG, "Poll obsoleted queue");
            movingCacheItems.poll();
        }

        MovingCacheItem movingCacheItem = null;
        if (!movingCacheItems.isEmpty()) {
            movingCacheItem = movingCacheItems.peek();
        }

        try {
            if (cacheRemaining > 0) {
                if (movingCacheItem == null) {
                    new DeAsyncJob<>((job) -> onCacheFetchListener = () -> {
                        job.setResult(new Object());
                        Log.d("ddd", "Eval onCacheFetchListener!!!");
                        onCacheFetchListener = null;
                    }).runAndWait();

                    Log.d(LogTAG, "Queue empty, Adding one...");
                    movingCacheItem = movingCacheItems.peek();
                }
            } else {
                movingCacheItem = new MovingCacheItem();
                movingCacheItem.setPointer(C.RESULT_END_OF_INPUT);
                return movingCacheItem;
            }
        } catch (Exception e) {
            movingCacheItem = new MovingCacheItem();
            movingCacheItem.setPointer(C.LENGTH_UNSET);
        }
        return movingCacheItem;
    }

    private int readCache(byte[] buffer, int offset, int length) {
        MovingCacheItem movingCacheItem = requireCache(false);
        final long cachePointer = movingCacheItem.getPointer();

        if (cachePointer < 0) {
            Log.d(LogTAG, "Cache encountered end of file: " + cachePointer);
            return (int) cachePointer;
        } else if (cachePointer <= fileLength - bytesRemaining
                && fileLength - bytesRemaining + length < cachePointer + CACHE_SIZE) {
            Log.d(LogTAG, "within cache size, cachePointer: " + cachePointer);
            movingCacheItem.writeCacheTo((int) ((fileLength - bytesRemaining) - cachePointer), buffer, offset, length);
            return length;
        } else if (cachePointer <= fileLength - bytesRemaining
                && fileLength - bytesRemaining + length >= cachePointer + CACHE_SIZE) {
            Log.d(LogTAG, "Out of cache size, cachePointer: " + cachePointer);
            movingCacheItem.writeCacheTo(
                    (int) ((fileLength - bytesRemaining) - cachePointer), buffer, offset,
                    (int) ((cachePointer + CACHE_SIZE) - (fileLength - bytesRemaining)));
            movingCacheItem = requireCache(true);
            Log.d(LogTAG, "Copying from new cache: " + movingCacheItem.getPointer() + ", req length: " + length + " offset: " + (fileLength - bytesRemaining));
            movingCacheItem.writeCacheTo(0, buffer,
                    (int) (offset + (movingCacheItem.getPointer() - (fileLength - bytesRemaining))),
                    (int) ((fileLength - bytesRemaining + length) - movingCacheItem.getPointer()));
            return length;
        } else {
            return C.RESULT_END_OF_INPUT;
        }
    }

    @NonNull
    @Override
    public Uri getUri() {
        return Objects.requireNonNull(dataSpec).uri;
    }

    @Override
    public void close() {
        try {
            if (poolFetchingThread != null && poolFetchingThread.isAlive()) {
                poolFetchingThread.interrupt();
            }
            if (file != null) {
                file.close();
            }
        } finally {
            file = null;
            if (opened) {
                opened = false;
                transferEnded();
            }
            if (dataSpec != null) {
                dataSpec = null;
            }
        }
    }
}