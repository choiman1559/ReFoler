package com.refoler.app.process.actions;

import com.google.firebase.storage.OnProgressListener;

public abstract class ProgressHandler<TaskType> implements ActionOp {
    private OnProgressListener<TaskType> onProgressListener;

    public void setOnProgressListener(OnProgressListener<TaskType> onProgressListener) {
        this.onProgressListener = onProgressListener;
    }

    public OnProgressListener<TaskType> getOnProgressListener() {
        return onProgressListener;
    }

    public boolean hasProgressListener() {
        return onProgressListener != null;
    }
}
