package com.refoler.app.process.service;

import android.content.Context;

import com.refoler.Refoler;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.backend.consts.PacketConst;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileActionResponse {

    public record RequestParams(Context context,
                                 Refoler.Device device,
                                 String actionTicket,
                                 String[] filePaths) {
    }

    public static void responseActions(Context context, Refoler.RequestPacket requestPacket) {
        try {
            JSONArray jsonArray = new JSONArray(requestPacket.getExtraData());
            String fileActionType = jsonArray.getString(0);
            String[] filePaths = new String[jsonArray.length() - 1];

            for (int i = 1; i < jsonArray.length(); i++) {
                filePaths[i - 1] = jsonArray.getString(i);
            }

            RequestParams requestParams = new RequestParams(context,
                    requestPacket.getDevice(0),
                    requestPacket.getDataDecryptKey(),
                    filePaths);

            switch (fileActionType) {
                case DirectActionConst.FILE_ACTION_HASH ->
                        performHash(requestParams);
                case DirectActionConst.FILE_ACTION_COPY -> {

                }
                case DirectActionConst.FILE_ACTION_DELETE -> {

                }
                case DirectActionConst.FILE_ACTION_MOVE -> {

                }
                case DirectActionConst.FILE_ACTION_RENAME -> {

                }
                case DirectActionConst.FILE_ACTION_NEW_DIR -> {

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void performHash(RequestParams requestParams) throws JSONException, IOException {
        Refoler.ResponsePacket.Builder responseBuilder = Refoler.ResponsePacket.newBuilder();
        responseBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(requestParams.context));
        responseBuilder.addDevice(requestParams.device);
        responseBuilder.addExtraData(requestParams.actionTicket);

        try {
            responseBuilder.addExtraData(getFileMD5Hash(new File(requestParams.filePaths[0])));
            responseBuilder.setStatus(PacketConst.STATUS_OK);
        } catch (GeneralSecurityException | IOException e) {
            responseBuilder.setErrorCause(e.toString());
            responseBuilder.setStatus(PacketConst.STATUS_ERROR);
        }
        FirebaseMessageService.postResponseMessage(requestParams.context, DirectActionConst.SERVICE_TYPE_FILE_ACTION, responseBuilder.build());
    }

    private static String getFileMD5Hash(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        InputStream is = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }

        is.close();
        byte[] md5sum = digest.digest();
        BigInteger bigInt = new BigInteger(1, md5sum);
        return bigInt.toString(16);
    }
}
