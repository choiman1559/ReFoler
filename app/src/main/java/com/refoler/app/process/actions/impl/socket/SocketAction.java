package com.refoler.app.process.actions.impl.socket;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.WebSocketWrapper;
import com.refoler.app.backend.consts.EndPointConst;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.ActionOp;
import com.refoler.app.process.actions.FileActionWorker;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class SocketAction implements ActionOp {

    private static final String LogTAG = "SocketAction";
    protected final AtomicBoolean isConnected = new AtomicBoolean(false);
    protected String socketActionCode;
    private WebSocketWrapper webSocketWrapper;

    public SocketAction() {
        socketActionCode = generateSocketCode();
    }

    public void setSocketActionCode(String socketActionCode) {
        this.socketActionCode = socketActionCode;
    }

    @Override
    public void performActionOp(Context context, Refoler.Device requester, FileAction.ActionRequest actionRequest, FileActionWorker.ActionCallback callback) throws Exception {
        FileAction.ActionResponse.Builder actionResponse = FileAction.ActionResponse.newBuilder();
        actionResponse.setChallengeCode(actionRequest.getChallengeCode());
        actionResponse.addResult(FileAction.ActionResult.newBuilder().addExtraData(socketActionCode).build());
        actionResponse.setOverallStatus(PacketConst.STATUS_OK);

        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setUid(Applications.getUid(context));
        requestPacket.addDevice(DeviceWrapper.getSelfDeviceInfo(context));
        requestPacket.addDevice(requester);
        requestPacket.setExtraData(socketActionCode);

        webSocketWrapper = new WebSocketWrapper(context);
        webSocketWrapper.setOnDataReceiveListener(new WebSocketWrapper.OnDataReceiveListener() {
            @Override
            public void onConnect() {
                try {
                    String data = String.format(Locale.getDefault(), "%s%s%s%s",
                            EndPointConst.FILE_PART_CONTROL_PREFIX, EndPointConst.FILE_PART_CONTROL_HEADER_ACK, EndPointConst.FILE_PART_CONTROL_SEPARATOR,
                            com.google.protobuf.util.JsonFormat.printer().print(requestPacket.build()));
                    webSocketWrapper.postRequest(data);
                    callback.onFinish(actionResponse);
                } catch (Exception e) {
                    actionResponse.setOverallStatus(PacketConst.STATUS_ERROR);
                    callback.onFinish(actionResponse);
                    onDisconnected();
                    e.printStackTrace();
                }
            }

            @Override
            public void onReceiveByteArray(@NonNull byte[] data) {
                try {
                    processPacket(data);
                } catch (Exception e) {
                    onDisconnected();
                    e.printStackTrace();
                }
            }
        });
        webSocketWrapper.connect(EndPointConst.SERVICE_TYPE_TRANSFER_FILE_PART);
    }

    private void processPacket(byte[] data) throws Exception {
        if(isControlPacket(data)) {
            String controlString = new String(data);
            if(controlString.startsWith(parseControlCode(EndPointConst.FILE_PART_CONTROL_PEER_ACK))) {
                Log.d(LogTAG, "Socket Peer established! Start communicating...");
                isConnected.set(true);
                onConnected(webSocketWrapper);
                return;
            } else if(controlString.startsWith(parseControlCode(EndPointConst.FILE_PART_CONTROL_PEER_DISCONNECTED))) {
                Log.d(LogTAG, "Socket Disconnected by peer. Closing socket...");
                isConnected.set(false);
                onDisconnected();
                return;
            } else if(controlString.startsWith(parseControlCode(EndPointConst.FILE_PART_CONTROL_HEADER_OK))) {
                Log.d(LogTAG, "Socket Handshake OK Packet Received, Waiting for peer!");
                return;
            }
        }

        if(isConnected.get()) {
            onDataIncoming(data);
        }
    }

    public String parseControlCode(String toParse) {
        return String.format(Locale.getDefault(), "%s%s", EndPointConst.FILE_PART_CONTROL_PREFIX, toParse);
    }

    public boolean isControlPacket(byte[] data) {
        byte[] controlPrefix = EndPointConst.FILE_PART_CONTROL_PREFIX.getBytes();
        for (int i = 0; i < controlPrefix.length; i++) {
            if (controlPrefix[i] != data[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final boolean mergeQueryScopeIfAvailable() {
        return false;
    }

    private String generateSocketCode() {
        return UUID.randomUUID().toString();
    }

    public void onConnected(WebSocketWrapper webSocketWrapper) {

    }

    public void onDisconnected() {
        webSocketWrapper.disconnect();
    }

    public void onDataIncoming(byte[] data) throws Exception {

    }
}
