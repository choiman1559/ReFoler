package com.refoler.app.utils;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import com.refoler.app.Applications;
import com.refoler.app.process.service.SyncFileListJob;
import com.refoler.app.ui.PrefsKeyConst;

public class JobUtils {
    public static void enquiry(Context context, boolean isPrefsChanged) {
        SharedPreferences prefs = Applications.getPrefs(context);
        if (!prefs.getBoolean(PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_ENABLED, true)) return;

        final int runInterval = prefs.getInt(PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_INTERVAL, 8);
        final boolean runOnCharging = prefs.getBoolean(PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_CHARGING, true);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for (JobInfo jobs : jobScheduler.getAllPendingJobs()) {
            if (jobs.getId() == PrefsKeyConst.NOTIFICATION_SYNC_TASK_ID.hashCode()) {
                if (isPrefsChanged) {
                    jobScheduler.cancel(jobs.getId());
                    break;
                } else {
                    return;
                }
            }
        }

        jobScheduler.schedule(new JobInfo.Builder(PrefsKeyConst.NOTIFICATION_SYNC_TASK_ID.hashCode(),
                new ComponentName(context, SyncFileListJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(runOnCharging)
                .setPersisted(true)
                .setPeriodic((long) runInterval * 60 * 60 * 1000)
                .build());
    }
}
