package com.refoler.app.process.actions.impl.misc;

import android.content.Context;

import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.process.actions.ActionOp;
import com.refoler.app.process.actions.FileActionWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class HashAction implements ActionOp {

    public static final String DEFAULT_MD5 = "MD5";
    private String algorithmType;

    @Override
    public void performActionOp(Context context, Refoler.Device requester, FileAction.ActionRequest actionRequest, FileActionWorker.ActionCallback callback) {
        FileAction.ActionResponse.Builder response = FileAction.ActionResponse.newBuilder();
        response.setOverallStatus(DirectActionConst.RESULT_OK);

        if(actionRequest.hasDestDir()) {
            algorithmType = actionRequest.getDestDir();
        } else {
            algorithmType = DEFAULT_MD5;
        }

        ArrayList<FileAction.ActionResult> results = new ArrayList<>();
        for (String filePath : actionRequest.getTargetFilesList()) {
            FileAction.ActionResult.Builder resultBuilder = FileAction.ActionResult.newBuilder();
            resultBuilder.setOpPaths(filePath);

            try {
                String hashResult = getFileHash(new File(filePath));
                resultBuilder.setResultSuccess(true);
                resultBuilder.addExtraData(hashResult);
            } catch (Exception e) {
                resultBuilder.setResultSuccess(false);
                resultBuilder.setErrorCause(e.toString());
            }
            results.add(resultBuilder.build());
        }
        callback.onFinish(response.addAllResult(results));
    }

    private String getFileHash(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance(algorithmType);
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

    @Override
    public FileAction.ActionType getActionOpcode() {
        return FileAction.ActionType.OP_HASH;
    }

    @Override
    public boolean mergeQueryScopeIfAvailable() {
        return true;
    }
}
