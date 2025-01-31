package com.refoler.app.process.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.refoler.app.R;
import com.refoler.app.backend.consts.ResponseWrapper;
import com.refoler.app.process.SyncFileListProcess;
import com.refoler.app.ui.PrefsKeyConst;

public class SyncFileListService extends Service {

    private NotificationManager mNotifyManager;

    private final SyncFileListProcess.OnSyncFileListProcessListener syncFileListener = new SyncFileListProcess.OnSyncFileListProcessListener() {
        @Override
        public void onSyncFileListProcessFinished(ResponseWrapper responseWrapper) {
            Log.d("ddd", "Finished");
            stopSelf();
        }

        @Override
        public void onSyncFileListProcessFailed(Throwable throwable) {
            Log.d("ddd", "Failed!");
            throwable.printStackTrace();
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        this.mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(PrefsKeyConst.NOTIFICATION_SYNC_TASK_CHANNEL, PrefsKeyConst.NOTIFICATION_SYNC_TASK_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        mNotifyManager.createNotificationChannel(channel);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, PrefsKeyConst.NOTIFICATION_SYNC_TASK_CHANNEL)
                .setSmallIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_arrow_download_24_regular)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setGroupSummary(false)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentTitle(getString(R.string.sync_notification_title))
                .setContentText(getString(R.string.sync_notification_content));

        startForeground(SyncFileListProcess.getInstance().hashCode(), mBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SyncFileListProcess syncFileListProcess = SyncFileListProcess.getInstance();
        syncFileListProcess.addListener(syncFileListener);

        if (!syncFileListProcess.isProcessRunning()) {
            syncFileListProcess.startSyncProcess(this);
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mNotifyManager.cancel(SyncFileListProcess.getInstance().hashCode());
    }
}
