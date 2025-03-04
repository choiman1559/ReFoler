package com.refoler.app.process.actions.impl.misc;

import android.accounts.NetworkErrorException;
import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;
import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.DeAsyncJob;
import com.refoler.app.process.actions.FileActionWorker;
import com.refoler.app.process.actions.ProgressHandler;

import java.io.FileInputStream;
import java.util.Locale;

public class UploadAction extends ProgressHandler<UploadTask.TaskSnapshot> {

    @Override
    public void performActionOp(Context context, Refoler.Device requester, FileAction.ActionRequest actionRequest, FileActionWorker.ActionCallback callback) {
        FileAction.ActionResponse.Builder actionResponse = FileAction.ActionResponse.newBuilder();
        actionResponse.setOverallStatus(PacketConst.STATUS_OK);

        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        StorageReference userDirectoryRef = storageRef.child(Applications.getUid(context));

        for (String filePath : actionRequest.getTargetFilesList()) {
            String refName = getFileRefName(filePath);
            FileAction.ActionResult.Builder result = FileAction.ActionResult.newBuilder();
            result.setOpPaths(filePath);

            try (FileInputStream stream = new FileInputStream(filePath)) {
                StorageReference fileRef = userDirectoryRef.child(refName);
                DeAsyncJob<Task<UploadTask.TaskSnapshot>> taskDeAsyncJob = new DeAsyncJob<>((job) -> {
                    StorageTask<UploadTask.TaskSnapshot> storageTask = fileRef.putStream(stream).addOnCompleteListener(job::setResult);
                    if (hasProgressListener()) {
                        storageTask.addOnProgressListener(getOnProgressListener());
                    }
                });

                Task<UploadTask.TaskSnapshot> taskResult = taskDeAsyncJob.runAndWait();
                if (taskResult.isSuccessful()) {
                    result.setResultSuccess(true);
                    result.addExtraData(refName);
                } else {
                    throw new NetworkErrorException(taskResult.getException());
                }
            } catch (Exception e) {
                result.setResultSuccess(false);
                result.setErrorCause(e.toString());
            }
            actionResponse.addResult(result);
        }
        callback.onFinish(actionResponse);
    }

    public static String getNameFromPath(String pathOrName) {
        String[] names = pathOrName.split("/");
        return names[names.length - 1];
    }

    public static String getFileRefName(String pathOrName) {
        return String.format(Locale.getDefault(), "%d.dat", getNameFromPath(pathOrName).hashCode());
    }

    @Override
    public FileAction.ActionType getActionOpcode() {
        return FileAction.ActionType.OP_UPLOAD;
    }

    @Override
    public boolean mergeQueryScopeIfAvailable() {
        return true;
    }
}
