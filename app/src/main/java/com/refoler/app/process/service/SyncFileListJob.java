package com.refoler.app.process.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class SyncFileListJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
