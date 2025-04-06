package com.refoler.app.process.actions.impl.socket;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;
import com.refoler.FileAction;
import com.refoler.FileSocket;
import com.refoler.Refoler;
import com.refoler.app.backend.WebSocketWrapper;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.DeAsyncJob;
import com.refoler.app.process.actions.FileActionRequester;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused, UnusedReturnValue")
public final class SocketRandAccess extends SocketAction implements Closeable {

    private static final String LogTAG = "SocketRandAccess";
    private static final int BUFFER_SIZE = 8192;
    public static final int RAW_DATA_READ_FINISH = -2;

    @Override
    public FileAction.ActionType getActionOpcode() {
        return null; // NOT USED FOR HERE
    }

    public interface BufferReceivedListener {
        void onReceived(int result, byte[] buffer);
    }

    public interface ErrorReceivedListener {
        void onError(Exception e);
    }

    private interface SynchronizedResult {
        void onResult(@Nullable Object result);
    }

    private final Refoler.Device device;
    private final String filePath;
    private final boolean isRead;
    private final boolean isWrite;

    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicReference<String> sessionActionCode = new AtomicReference<>();
    private boolean isSynchronized = false;
    private WebSocketWrapper webSocketWrapper;
    private BufferReceivedListener bufferReceivedListener;
    private ErrorReceivedListener errorReceivedListener;
    private volatile SynchronizedResult synchronizedResult;

    public SocketRandAccess(Refoler.Device device, String filePath, boolean isRead, boolean isWrite) {
        this.device = device;
        this.filePath = filePath;
        this.isRead = isRead;
        this.isWrite = isWrite;
    }

    public void setSynchronized(boolean aSynchronized) {
        isSynchronized = aSynchronized;
    }

    public void setErrorReceivedListener(ErrorReceivedListener errorReceivedListener) {
        this.errorReceivedListener = errorReceivedListener;
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public boolean requestChannel(Context context) {
        FileAction.ActionRequest.Builder actionRequest = FileAction.ActionRequest.newBuilder();
        actionRequest.setActionType(FileAction.ActionType.OP_ACCESS_PART);
        actionRequest.setDestDir(filePath);
        actionRequest.addTargetFiles(String.valueOf(isRead));
        actionRequest.addTargetFiles(String.valueOf(isWrite));
        FileActionRequester.setChallengeCode(device, actionRequest);

        Log.d(LogTAG, "Started opening peer socket channel...");
        DeAsyncJob<Boolean> booleanDeAsyncJob = new DeAsyncJob<>((job) -> {
            try {
                Log.d(LogTAG, "Sending initial action make-up request packet...");
                FileActionRequester.getInstance().requestAction(context, device, actionRequest, (device, response) -> {
                    try {
                        Log.d(LogTAG, "Received make-up complete signal, Trying connect with relay server...");
                        sessionActionCode.set(response.getResult(0).getExtraData(0));
                        if (!response.getOverallStatus().equals(PacketConst.STATUS_OK)) {
                            job.setResult(false);
                            return;
                        }

                        performActionOp(context, this.device, actionRequest.build(), (actionResponse) -> {
                            Log.d(LogTAG, "Successfully Connected to relay server.");
                            if (response.getOverallStatus().equals(PacketConst.STATUS_OK)) {
                                job.setResult(true);
                            } else {
                                job.setResult(false);
                            }
                        });
                    } catch (Exception e) {
                        job.setResult(false);
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                job.setResult(false);
                e.printStackTrace();
            }
        });

        final boolean booleanDeAsyncJobResult = booleanDeAsyncJob.runAndWait();
        if (!isSynchronized || !booleanDeAsyncJobResult) return booleanDeAsyncJobResult;

        Log.d(LogTAG, "Sending header (peer info) packet to relay server...");
        boolean ackWaitingResult = new DeAsyncJob<Boolean>((job) -> {
            Log.d(LogTAG, "Waiting for server ACK signal, isSocketConnected: " + isConnected.get() + " isSocketNull: " + (webSocketWrapper == null));
            if (isConnected.get()) job.setResult(true);
            else synchronizedResult = result -> {
                Log.d(LogTAG, "Received ACK from server, Now waiting for peer connected; isSocketConnected: " + isConnected.get() + " isSocketNull: " + (webSocketWrapper == null));
                job.setResult(true);
            };
        }).runAndWait();

        Log.d(LogTAG, "Successfully connected to peer, File I/O Channel opened: " + ackWaitingResult + ", isSocketNull: " + (webSocketWrapper == null));
        return ackWaitingResult;
    }

    public void setBufferReceivedListener(BufferReceivedListener listener) {
        this.bufferReceivedListener = listener;
    }

    public int readBytes(byte[] buffer, int offset, int length) {
        DeAsyncJob<Integer> readByteJob = new DeAsyncJob<>((job) -> {
            setBufferReceivedListener((result, bufferData) -> {
                System.arraycopy(bufferData, 0, buffer, 0, result);
                job.setResult(result);
            });
            readBytes(buffer.length, offset, length, false);
        });
        return readByteJob.runAndWait();
    }

    public void readBytes(int buffer_size, int offset, int length, boolean isContinues) {
        if (buffer_size <= 0) buffer_size = BUFFER_SIZE;
        webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                .setType(FileSocket.ProcedureType.FUNC_READ_BYTES)
                .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(buffer_size)))
                .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(offset)))
                .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(length)))
                .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(isContinues)))
                .build().toByteArray());
    }

    public void writeBytes(byte[] buffer, int offset, int length) {
        DeAsyncJob<Object> voidDeAsyncJob = new DeAsyncJob<>((job) -> {
            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_WRITE_BYTES)
                    .addParameterData(ByteString.copyFrom(buffer))
                    .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(offset)))
                    .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(length)))
                    .build().toByteArray());

            if (isSynchronized) {
                synchronizedResult = result -> job.setResult(new Object());
            } else {
                synchronizedResult = null;
                job.setResult(new Object());
            }
        });

        voidDeAsyncJob.runAndWait();
        synchronizedResult = null;
    }

    public void seek(long position) {
        DeAsyncJob<Object> voidDeAsyncJob = new DeAsyncJob<>((job) -> {
            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_SEEK)
                    .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(position)))
                    .build().toByteArray());

            if (isSynchronized) {
                synchronizedResult = result -> job.setResult(new Object());
            } else {
                synchronizedResult = null;
                job.setResult(new Object());
            }
        });

        voidDeAsyncJob.runAndWait();
        synchronizedResult = null;
    }

    public long getFileLength() {
        DeAsyncJob<Long> voidDeAsyncJob = new DeAsyncJob<>((job) -> {
            synchronizedResult = result -> job.setResult((Long) Objects.requireNonNullElse(result, -1L));
            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_GET_FILE_LENGTH)
                    .build().toByteArray());
        });

        return voidDeAsyncJob.runAndWait();
    }

    public void setFileLength(long length) {
        DeAsyncJob<Object> voidDeAsyncJob = new DeAsyncJob<>((job) -> {
            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_SET_FILE_LENGTH)
                    .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(length)))
                    .build().toByteArray());

            if (isSynchronized) {
                synchronizedResult = result -> job.setResult(new Object());
            } else {
                synchronizedResult = null;
                job.setResult(new Object());
            }
        });

        voidDeAsyncJob.runAndWait();
        synchronizedResult = null;
    }

    @Override
    public void close() {
        if (!isConnected.get()) return;
        DeAsyncJob<Object> voidDeAsyncJob = new DeAsyncJob<>((job) -> {
            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_CLOSE)
                    .build().toByteArray());

            if (isSynchronized) {
                synchronizedResult = result -> job.setResult(new Object());
            } else {
                synchronizedResult = null;
                job.setResult(new Object());
            }
        });

        voidDeAsyncJob.runAndWait();
        synchronizedResult = null;
        onDisconnected();
    }

    @Override
    public void onConnected(WebSocketWrapper webSocketWrapper) {
        super.onConnected(webSocketWrapper);
        this.webSocketWrapper = webSocketWrapper;
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        isConnected.set(false);
        webSocketWrapper = null;
    }

    @Override
    public void onDataIncoming(byte[] data) throws Exception {
        super.onDataIncoming(data);
        FileSocket.Procedure procedure = FileSocket.Procedure.parseFrom(data);

        if (procedure.hasControl()) {
            switch (procedure.getControl()) {
                case CTRL_ACK -> {
                    isConnected.set(true);
                    if (isSynchronized && synchronizedResult != null) {
                        (synchronizedResult).onResult(null);
                    }
                }

                case CTRL_ERROR -> {
                    if (errorReceivedListener != null) {
                        errorReceivedListener.onError(new IllegalStateException(new String(procedure.getReturnData(0).toByteArray())));
                    }
                }

                case CTRL_CLOSE -> {
                    if (isConnected.get()) onDisconnected();
                    if (isSynchronized && synchronizedResult != null) {
                        (synchronizedResult).onResult(null);
                    }
                }
            }
        } else switch (procedure.getType()) {
            case FUNC_READ_BYTES -> {
                if (bufferReceivedListener != null) {
                    bufferReceivedListener.onReceived(
                            ByteTypeUtils.toInt(procedure.getReturnData(0).toByteArray()),
                            procedure.getReturnData(1).toByteArray());
                }
            }

            case FUNC_WRITE_BYTES, FUNC_SET_FILE_LENGTH, FUNC_SEEK -> {
                if (isSynchronized && synchronizedResult != null) {
                    (synchronizedResult).onResult(null);
                }
            }

            case FUNC_GET_FILE_LENGTH -> {
                if (synchronizedResult != null) {
                    (synchronizedResult).onResult(ByteTypeUtils.toLong(procedure.getReturnData(0).toByteArray()));
                }
            }
        }
    }

    @Override
    public String requireSocketSessionCode() {
        return Objects.requireNonNull(sessionActionCode.get());
    }
}
