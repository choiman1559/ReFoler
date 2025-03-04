package com.refoler.app.process.actions;

import android.content.Context;

import com.refoler.FileAction;
import com.refoler.Refoler;

public interface ActionOp {
    void performActionOp(Context context,
                         Refoler.Device requester,
                         FileAction.ActionRequest actionRequest,
                         FileActionWorker.ActionCallback callback) throws Exception;

    FileAction.ActionType getActionOpcode();

    boolean mergeQueryScopeIfAvailable();
}
