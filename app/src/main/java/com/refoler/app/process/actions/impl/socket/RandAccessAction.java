package com.refoler.app.process.actions.impl.socket;

import android.content.Context;

import com.google.protobuf.ByteString;
import com.refoler.FileAction;
import com.refoler.FileSocket;
import com.refoler.Refoler;
import com.refoler.app.backend.WebSocketWrapper;
import com.refoler.app.process.actions.FileActionWorker;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class RandAccessAction extends SocketAction {

    private volatile RandomAccessFile randomAccessFile;
    private WebSocketWrapper webSocketWrapper;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private String targetFile;
    private boolean isRead = false;
    private boolean isWrite = false;

    @Override
    public void performActionOp(Context context, Refoler.Device requester, FileAction.ActionRequest actionRequest, FileActionWorker.ActionCallback callback) throws Exception {
        super.performActionOp(context, requester, actionRequest, callback);
        targetFile = actionRequest.getDestDir();
        isRead = Boolean.parseBoolean(actionRequest.getTargetFiles(0));
        isWrite = Boolean.parseBoolean(actionRequest.getTargetFiles(1));
    }

    @Override
    public void onConnected(WebSocketWrapper webSocketWrapper) {
        super.onConnected(webSocketWrapper);
        this.webSocketWrapper = webSocketWrapper;

        try {
            String accessMode = "";
            if (isRead) accessMode += "r";
            if (isWrite) accessMode += "w";
            randomAccessFile = new RandomAccessFile(targetFile, accessMode);
            isInitialized.set(true);

            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_CONTROL)
                    .setControl(FileSocket.ControlProcedure.CTRL_ACK)
                    .build().toByteArray());
        } catch (Exception e) {
            throwException(e);
        }
    }

    private void throwException(Exception e) {
        if (webSocketWrapper != null) {
            webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                    .setType(FileSocket.ProcedureType.FUNC_CONTROL)
                    .setControl(FileSocket.ControlProcedure.CTRL_ERROR)
                    .addReturnData(ByteString.copyFrom(e.toString().getBytes(StandardCharsets.UTF_8)))
                    .build().toByteArray()
            );
        }
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        if (isInitialized.get() && randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDataIncoming(byte[] data) throws Exception {
        if (!isInitialized.get() || randomAccessFile == null) {
            throwException(new IllegalAccessException("RandomAccessFile not initialized!"));
            return;
        }

        FileSocket.Procedure fileSocket = FileSocket.Procedure.parseFrom(data);
        FileSocket.ProcedureType procedureType = fileSocket.getType();
        if (procedureType.equals(FileSocket.ProcedureType.FUNC_CONTROL) && fileSocket.hasControl()) {
            return;
        }

        switch (procedureType) {
            case FUNC_READ_BYTES -> {
                FileSocket.Procedure.Builder readData = FileSocket.Procedure.newBuilder();
                readData.setType(FileSocket.ProcedureType.FUNC_READ_BYTES);

                final int bufferSize = ByteTypeUtils.toInt(fileSocket.getParameterData(0).toByteArray());
                final int startOffset = ByteTypeUtils.toInt(fileSocket.getParameterData(1).toByteArray());
                final long fileLength = randomAccessFile.length();

                int length = ByteTypeUtils.toInt(fileSocket.getParameterData(2).toByteArray());
                if (length < 0) {
                    length = (int) fileLength;
                }

                try {
                    int currentPoint = startOffset;
                    byte[] buffer = new byte[bufferSize];

                    while (currentPoint < length
                            && currentPoint + bufferSize < length
                            && currentPoint + bufferSize < fileLength) {

                        randomAccessFile.seek(currentPoint);
                        int result = randomAccessFile.read(buffer);

                        readData.addReturnData(ByteString.copyFrom(ByteTypeUtils.toBytes(result)));
                        readData.addReturnData(ByteString.copyFrom(buffer));
                        webSocketWrapper.postRequest(readData.build().toByteArray());
                        currentPoint += bufferSize;
                        if (result < 0) {
                            return;
                        }
                    }

                    if (currentPoint < length) {
                        randomAccessFile.seek(currentPoint);
                        buffer = new byte[length - currentPoint];
                        int result = randomAccessFile.read(buffer);

                        readData.addReturnData(ByteString.copyFrom(ByteTypeUtils.toBytes(result)));
                        readData.addReturnData(ByteString.copyFrom(buffer));
                        webSocketWrapper.postRequest(readData.build().toByteArray());
                        if (result < 0) {
                            return;
                        }
                    }

                    webSocketWrapper.postRequest(readData
                            .addReturnData(ByteString.copyFrom(ByteTypeUtils.toBytes(-1)))
                            .addReturnData(ByteString.copyFrom(new byte[0]))
                            .build().toByteArray());
                } catch (IOException e) {
                    throwException(e);
                }
            }

            case FUNC_WRITE_BYTES -> {
                randomAccessFile.write(
                        fileSocket.getParameterData(0).toByteArray(),
                        ByteTypeUtils.toInt(fileSocket.getParameterData(1).toByteArray()),
                        ByteTypeUtils.toInt(fileSocket.getParameterData(2).toByteArray())
                );
                webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                        .setType(FileSocket.ProcedureType.FUNC_WRITE_BYTES)
                        .build().toByteArray()
                );
            }

            case FUNC_GET_FILE_LENGTH -> {
                byte[] length = ByteTypeUtils.toBytes(randomAccessFile.length());
                webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                        .setType(FileSocket.ProcedureType.FUNC_GET_FILE_LENGTH)
                        .addReturnData(ByteString.copyFrom(length))
                        .build().toByteArray()
                );
            }

            case FUNC_SET_FILE_LENGTH -> {
                randomAccessFile.setLength(ByteTypeUtils.toInt(fileSocket.getParameterData(0).toByteArray()));
                webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                        .setType(FileSocket.ProcedureType.FUNC_SET_FILE_LENGTH)
                        .build().toByteArray()
                );
            }

            case FUNC_CLOSE -> {
                randomAccessFile.close();
                isInitialized.set(false);
                webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                        .setType(FileSocket.ProcedureType.FUNC_CLOSE)
                        .setControl(FileSocket.ControlProcedure.CTRL_CLOSE)
                        .build().toByteArray()
                );
            }
        }
    }

    @Override
    public FileAction.ActionType getActionOpcode() {
        return FileAction.ActionType.OP_ACCESS_PART;
    }
}
