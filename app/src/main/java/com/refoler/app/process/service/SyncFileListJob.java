package com.refoler.app.process.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.backend.ResponseWrapper;
import com.refoler.app.process.SyncFileListProcess;
import com.refoler.app.ui.PrefsKeyConst;

public class SyncFileListJob extends JobService {

    private Context context;
    private NotificationManager mNotifyManager;

    private final SyncFileListProcess.OnSyncFileListProcessListener syncFileListener = new SyncFileListProcess.OnSyncFileListProcessListener() {
        @Override
        public void onSyncFileListProcessFinished(ResponseWrapper responseWrapper) {
            stopForeground(true);
        }

        @Override
        public void onSyncFileListProcessFailed(Throwable throwable) {
            stopForeground(true);
        }
    };

    @Override
    public boolean onStartJob(JobParameters params) {
        if(Applications.getUid(context).isEmpty()) {
            return false;
        }

        NotificationChannel channel = new NotificationChannel(PrefsKeyConst.NOTIFICATION_SYNC_TASK_CHANNEL, PrefsKeyConst.NOTIFICATION_SYNC_TASK_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        mNotifyManager.createNotificationChannel(channel);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, PrefsKeyConst.NOTIFICATION_SYNC_TASK_CHANNEL)
                .setSmallIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_folder_sync_24_regular)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setGroupSummary(false)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentTitle(getString(R.string.sync_notification_title))
                .setContentText(getString(R.string.sync_notification_content));

        SyncFileListProcess syncFileListProcess = SyncFileListProcess.getInstance();
        if (!syncFileListProcess.isProcessRunning() && syncFileListProcess.isNeedUpdate(context)) {
            mNotifyManager.notify(PrefsKeyConst.NOTIFICATION_KEY_SYNC_PROCESS, mBuilder.build());
            syncFileListProcess.addListener(syncFileListener);
            syncFileListProcess.startSyncProcess(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        //mNotifyManager.cancel(PrefsKeyConst.NOTIFICATION_KEY_SYNC_PROCESS);
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mNotifyManager.cancelAll();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.context = this;
        this.mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
