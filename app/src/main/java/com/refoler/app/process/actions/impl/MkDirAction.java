package com.refoler.app.process.actions.impl;

import android.content.Context;

import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.ActionOp;
import com.refoler.app.process.actions.FSEditSyncJob;
import com.refoler.app.process.actions.FileActionWorker;

import java.io.File;

public class MkDirAction implements ActionOp {
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

        for(String path : actionRequest.getTargetFilesList()) {
            FileAction.ActionResult.Builder result = FileAction.ActionResult.newBuilder();
            result.setOpPaths(path);

            try {
                File source = new File(path);
                result.setResultSuccess(source.exists() || source.mkdirs());
            } catch (Exception e) {
                result.setResultSuccess(false);
                actionResponse.addResult(result);
            }

            actionResponse.addResult(result);
            editTask.addQuery(path);
        }
        editTask.execute(context);
    }

    @Override
    public FileAction.ActionType getActionOpcode() {
        return FileAction.ActionType.OP_MAKE_DIR;
    }

    @Override
    public boolean mergeQueryScopeIfAvailable() {
        return false;
    }
}