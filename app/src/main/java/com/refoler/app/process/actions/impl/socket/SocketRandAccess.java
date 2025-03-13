package com.refoler.app.process.actions.impl.socket;

import android.content.Context;

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

@SuppressWarnings("unused")
public final class SocketRandAccess extends SocketAction implements Closeable {

    private static final String LogTAG = "SocketRandAccess";
    private static final int BUFFER_SIZE = 8192;

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

    private boolean isConnected = false;
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
        return isConnected;
    }

    public boolean requestChannel(Context context) {
        FileAction.ActionRequest.Builder actionRequest = FileAction.ActionRequest.newBuilder();
        actionRequest.setActionType(FileAction.ActionType.OP_ACCESS_PART);
        actionRequest.setDestDir(filePath);
        actionRequest.addTargetFiles(String.valueOf(isRead));
        actionRequest.addTargetFiles(String.valueOf(isWrite));
        FileActionRequester.setChallengeCode(device, actionRequest);

        DeAsyncJob<Boolean> booleanDeAsyncJob = new DeAsyncJob<>((job) -> {
            try {
                FileActionRequester.getInstance().requestAction(context, device, actionRequest, (device, response) -> {
                    try {
                        setSocketActionCode(response.getResult(0).getExtraData(0));
                        if (!response.getOverallStatus().equals(PacketConst.STATUS_OK)) {
                            job.setResult(false);
                            return;
                        }

                        performActionOp(context, device, actionRequest.build(),
                                (actionResponse) -> job.setResult(response.getOverallStatus().equals(PacketConst.STATUS_OK)));
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

        return booleanDeAsyncJob.runAndWait();
    }

    public void setBufferReceivedListener(BufferReceivedListener listener) {
        this.bufferReceivedListener = listener;
    }

    public void readBytes(int buffer_size, int offset, int length) {
        if (buffer_size <= 0) buffer_size = BUFFER_SIZE;
        webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                .setType(FileSocket.ProcedureType.FUNC_READ_BYTES)
                .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(buffer_size)))
                .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(offset)))
                .addParameterData(ByteString.copyFrom(ByteTypeUtils.toBytes(length)))
                .build().toByteArray());
    }

    public void writeBytes(byte[] buffer, int offset, int length) {
        DeAsyncJob<Object> voidDeAsyncJob = new DeAsyncJob<>((job) -> {
            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_READ_BYTES)
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

    public long getFileLength() {
        DeAsyncJob<Long> voidDeAsyncJob = new DeAsyncJob<>((job) -> {
            synchronizedResult = result -> job.setResult((Long) Objects.requireNonNullElse(result, -1L));
            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_SET_FILE_LENGTH)
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
        isConnected = false;
    }

    @Override
    public void onDataIncoming(byte[] data) throws Exception {
        super.onDataIncoming(data);
        FileSocket.Procedure procedure = FileSocket.Procedure.parseFrom(data);

        if (procedure.hasControl()) {
            switch (procedure.getControl()) {
                case CTRL_ACK -> isConnected = true;
                case CTRL_ERROR -> {
                    if (errorReceivedListener != null) {
                        errorReceivedListener.onError(new IllegalStateException(new String(procedure.getReturnData(0).toByteArray())));
                    }
                }

                case CTRL_CLOSE -> {
                    if (isConnected) onDisconnected();
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
                            procedure.getReturnData(0).toByteArray());
                }
            }

            case FUNC_WRITE_BYTES, FUNC_SET_FILE_LENGTH -> {
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
}
