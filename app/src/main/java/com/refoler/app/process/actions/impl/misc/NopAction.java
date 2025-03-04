package com.refoler.app.process.actions.impl.misc;

import android.content.Context;

import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.ActionOp;
import com.refoler.app.process.actions.FSEditSyncJob;
import com.refoler.app.process.actions.FileActionWorker;

public class NopAction implements ActionOp {
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
        editTask.execute(context);
    }

    @Override
    public FileAction.ActionType getActionOpcode() {
        return FileAction.ActionType.OP_NONE;
    }

    @Override
    public boolean mergeQueryScopeIfAvailable() {
        return false;
    }
}
