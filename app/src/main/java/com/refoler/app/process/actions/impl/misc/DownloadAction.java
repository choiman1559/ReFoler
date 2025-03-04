package com.refoler.app.process.actions.impl.misc;

import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.DeAsyncJob;
import com.refoler.app.process.actions.FSEditSyncJob;
import com.refoler.app.process.actions.FileActionWorker;
import com.refoler.app.process.actions.ProgressHandler;

import java.io.File;

public class DownloadAction extends ProgressHandler<FileDownloadTask.TaskSnapshot> {
    /**
     * @noinspection ResultOfMethodCallIgnored
     */
    @Override
    public void performActionOp(Context context, Refoler.Device requester, FileAction.ActionRequest actionRequest, FileActionWorker.ActionCallback callback) {
        Log.d("ddd", "DownloadAction");
        FileAction.ActionResponse.Builder actionResponse = FileAction.ActionResponse.newBuilder();
        actionResponse.setOverallStatus(PacketConst.STATUS_OK);

        FSEditSyncJob editTask = FSEditSyncJob.getInstance();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        StorageReference userDirectoryRef = storageRef.child(Applications.getUid(context));

        for (String filePath : actionRequest.getTargetFilesList()) {
            String refName = UploadAction.getFileRefName(filePath);
            FileAction.ActionResult.Builder result = FileAction.ActionResult.newBuilder();
            result.setOpPaths(filePath);

            try {
                StorageReference fileRef = userDirectoryRef.child(refName);
                File downloadFile = new File(getFileDownloadDir(), UploadAction.getNameFromPath(filePath));

                if (downloadFile.exists()) {
                    if (actionRequest.hasOverrideExists() && actionRequest.getOverrideExists()) {
                        downloadFile.delete();
                        downloadFile.createNewFile();
                    } else {
                        result.setResultSuccess(false);
                        result.setErrorCause("File already exists");
                        actionResponse.addResult(result);
                        continue;
                    }
                }

                DeAsyncJob<Task<FileDownloadTask.TaskSnapshot>> taskDeAsyncJob = new DeAsyncJob<>((job) -> {
                    StorageTask<FileDownloadTask.TaskSnapshot> storageTask = fileRef.getFile(downloadFile).addOnCompleteListener(job::setResult);
                    if(hasProgressListener()) {
                        storageTask.addOnProgressListener(getOnProgressListener());
                    }
                });

                Task<FileDownloadTask.TaskSnapshot> taskResult = taskDeAsyncJob.runAndWait();
                if (taskResult.isSuccessful()) {
                    result.setResultSuccess(true);
                    result.addExtraData(refName);
                    editTask.addQuery(downloadFile.getPath());
                } else {
                    throw new NetworkErrorException(taskResult.getException());
                }
            } catch (Exception e) {
                result.setResultSuccess(false);
                result.setErrorCause(e.toString());
            }
            actionResponse.addResult(result);
        }

        editTask.addCallBack(new FSEditSyncJob.TaskResultCallBack() {
            @Override
            public void onEditSuccess(boolean needsQueryEntireList) {
                if(needsQueryEntireList) {
                    callback.onFinish(actionResponse);
                }
            }

            @Override
            public void onUploadSuccess() {
                callback.onFinish(actionResponse);
            }

            @Override
            public void onUploadFailed(Throwable throwable) {
                actionResponse.setOverallStatus(DirectActionConst.getErrorCode(DirectActionConst.RESULT_ERROR_EXCEPTION, throwable.toString()));
                callback.onFinish(actionResponse);
            }
        });
        editTask.execute(context);
    }

    /**
     * @noinspection ResultOfMethodCallIgnored
     */
    public File getFileDownloadDir() {
        File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ReFoler");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        return downloadDir;
    }

    @Override
    public FileAction.ActionType getActionOpcode() {
        return FileAction.ActionType.OP_DOWNLOAD;
    }

    @Override
    public boolean mergeQueryScopeIfAvailable() {
        return false;
    }
}
