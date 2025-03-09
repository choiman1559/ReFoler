package com.refoler.app.process.actions.impl;

import android.content.Context;

import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.ActionOp;
import com.refoler.app.process.actions.FSEditSyncJob;
import com.refoler.app.process.actions.FileActionWorker;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class RenameAction implements ActionOp {
    @Override
    public void performActionOp(Context context, Refoler.Device requester, FileAction.ActionRequest actionRequest, FileActionWorker.ActionCallback callback) throws Exception {
        FSEditSyncJob editTask = FSEditSyncJob.getInstance();
        FileAction.ActionResponse.Builder actionResponse = FileAction.ActionResponse.newBuilder();
        actionResponse.setOverallStatus(PacketConst.STATUS_OK);

        editTask.addCallBack(new FSEditSyncJob.TaskResultCallBack() {
            @Override
            public void onEditSuccess(boolean needsQueryEntireList) {
                if (needsQueryEntireList) {
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

        String originPath = actionRequest.getTargetFiles(0);
        String targetPath = actionRequest.getDestDir();

        FileAction.ActionResult.Builder result = FileAction.ActionResult.newBuilder();
        result.setOpPaths(originPath);

        try {
            File source = new File(originPath);
            if (!source.exists()) {
                result.setResultSuccess(false);
                actionResponse.addResult(result);
            }

            File dest = new File(targetPath);
            if (dest.exists()) {
                if (dest.isFile()) {
                    FileUtils.delete(dest);
                } else {
                    FileUtils.deleteDirectory(dest);
                }
            }

            result.setResultSuccess(source.renameTo(dest));
        } catch (Exception e) {
            result.setResultSuccess(false);
            actionResponse.addResult(result);
        }

        actionResponse.addResult(result);
        editTask.addQuery(targetPath);
        editTask.addQuery(originPath);
        editTask.execute(context);
    }

    @Override
    public FileAction.ActionType getActionOpcode() {
        return FileAction.ActionType.OP_RENAME;
    }

    @Override
    public boolean mergeQueryScopeIfAvailable() {
        return true;
    }
}

