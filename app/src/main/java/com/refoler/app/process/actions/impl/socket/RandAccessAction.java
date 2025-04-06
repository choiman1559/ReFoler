package com.refoler.app.process.actions.impl.socket;

import android.content.Context;
import android.util.Log;

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

    private final static String LogTAG = "RandAccessAction";
    private volatile RandomAccessFile randomAccessFile;
    private WebSocketWrapper webSocketWrapper;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    private String targetFile;
    private boolean isRead = false;
    private boolean isWrite = false;
    private Long length;

    @Override
    public void performActionOp(Context context, Refoler.Device requester, FileAction.ActionRequest actionRequest, FileActionWorker.ActionCallback callback) throws Exception {
        targetFile = actionRequest.getDestDir();
        isRead = Boolean.parseBoolean(actionRequest.getTargetFiles(0));
        isWrite = Boolean.parseBoolean(actionRequest.getTargetFiles(1));
        super.performActionOp(context, requester, actionRequest, callback);
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
            length = randomAccessFile.length();
            isInitialized.set(true);

            Log.d(LogTAG,"Sending Ack to peer, File Length: " + length);
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
                final long fileLength = length;

                int length = ByteTypeUtils.toInt(fileSocket.getParameterData(2).toByteArray());
                boolean isContinuous = ByteTypeUtils.toBoolean(fileSocket.getParameterData(3).toByteArray());

                if (length < 0) {
                    length = (int) fileLength;
                }

                try {
                    byte[] buffer = new byte[bufferSize];
                    if (!isContinuous) {
                        Log.d(LogTAG, String.format("readBytes= buffer: %d; start: %d; length: %d", bufferSize, startOffset, length));
                        readData.clearReturnData();
                        readData.addReturnData(ByteString.copyFrom(ByteTypeUtils.toBytes(randomAccessFile.read(buffer, startOffset, length))));
                        readData.addReturnData(ByteString.copyFrom(buffer));
                        webSocketWrapper.postRequest(readData.build().toByteArray());
                        return;
                    }

                    Log.d(LogTAG, "===== New Read Action =====");
                    long remainingBytes = length;
                    while (remainingBytes > 0) {
                        final int lengthToRead = (int) Math.min(remainingBytes, buffer.length);
                        if(lengthToRead != buffer.length) {
                            Log.d(LogTAG, "Buffer target size not match, resizing byte array to: " + lengthToRead);
                            buffer = new byte[lengthToRead];
                        }
                        final int readBytes = randomAccessFile.read(buffer, startOffset, lengthToRead);

                        readData.clearReturnData();
                        readData.addReturnData(ByteString.copyFrom(ByteTypeUtils.toBytes(readBytes)));
                        readData.addReturnData(ByteString.copyFrom(buffer));
                        webSocketWrapper.postRequest(readData.build().toByteArray());

                        Log.d(LogTAG, "Read: " + readBytes + ", Remaining: " + remainingBytes);
                        if(readBytes > -1) {
                            remainingBytes -= readBytes;
                        } else break;
                    }

                    webSocketWrapper.postRequest(readData
                            .clearReturnData()
                            .addReturnData(ByteString.copyFrom(ByteTypeUtils.toBytes(SocketRandAccess.RAW_DATA_READ_FINISH)))
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
                if(length == null) {
                    length = randomAccessFile.length();
                }

                byte[] lengthBytes = ByteTypeUtils.toBytes(length);
                webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                        .setType(FileSocket.ProcedureType.FUNC_GET_FILE_LENGTH)
                        .addReturnData(ByteString.copyFrom(lengthBytes))
                        .build().toByteArray()
                );
            }

            case FUNC_SET_FILE_LENGTH -> {
                final long newLength = ByteTypeUtils.toLong(fileSocket.getParameterData(0).toByteArray());
                randomAccessFile.setLength(newLength);
                length = newLength;

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

            case FUNC_SEEK -> {
                randomAccessFile.seek(ByteTypeUtils.toLong(fileSocket.getParameterData(0).toByteArray()));
                webSocketWrapper.postRequest(FileSocket.Procedure.newBuilder()
                        .setType(FileSocket.ProcedureType.FUNC_SEEK)
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
