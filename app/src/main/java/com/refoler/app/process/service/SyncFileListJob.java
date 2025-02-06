package com.refoler.app.process.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import androidx.core.app.NotificationCompat;

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
            stopSelf();
        }

        @Override
        public void onSyncFileListProcessFailed(Throwable throwable) {
            stopSelf();
        }
    };

    @Override
    public boolean onStartJob(JobParameters params) {
        NotificationChannel channel = new NotificationChannel(PrefsKeyConst.NOTIFICATION_SYNC_TASK_CHANNEL, PrefsKeyConst.NOTIFICATION_SYNC_TASK_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        mNotifyManager.createNotificationChannel(channel);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, PrefsKeyConst.NOTIFICATION_SYNC_TASK_CHANNEL)
                .setSmallIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_arrow_download_24_regular)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setGroupSummary(false)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentTitle(getString(R.string.sync_notification_title))
                .setContentText(getString(R.string.sync_notification_content));

        mNotifyManager.notify(SyncFileListProcess.getInstance().hashCode(), mBuilder.build());
        SyncFileListProcess syncFileListProcess = SyncFileListProcess.getInstance();
        syncFileListProcess.addListener(syncFileListener);

        if (!syncFileListProcess.isProcessRunning()) {
            syncFileListProcess.startSyncProcess(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        mNotifyManager.cancel(SyncFileListProcess.getInstance().hashCode());
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        this.context = this;
        this.mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
