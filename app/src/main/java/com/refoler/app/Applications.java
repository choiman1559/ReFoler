package com.refoler.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;

import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.UploadTask;
import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.process.actions.ActionOp;
import com.refoler.app.process.actions.FileActionWorker;
import com.refoler.app.process.actions.ProgressHandler;
import com.refoler.app.process.actions.impl.misc.UploadAction;
import com.refoler.app.ui.PrefsKeyConst;

import java.util.HashSet;
import java.util.List;

public class Applications extends Application {

    public static final String PREFS_PACKAGE = "com.refoler.app";
    public static final String PREFS_MAIN_SUFFIX = "preferences";
    public static final String PREFS_DEVICE_LIST_CACHE_SUFFIX = "device_list_Cache";

    @Override
    public void onCreate() {
        super.onCreate();
        initFileActionWorker(getApplicationContext());
    }

    public static void initFileActionWorker(Context context) {
        FileActionWorker.getInstance(true).setActionRequestNotifier(new FileActionWorker.ActionRequestNotifier() {
            @Override
            public boolean onRequestRaise(ActionOp requestedOp, Refoler.Device requester, final FileAction.ActionRequest request) {
                if (hasBlockListInDevice(context, requester)) {
                    return false;
                }

                switch (requestedOp.getActionOpcode()) {
                    case FileAction.ActionType.OP_UPLOAD -> {
                        if (requestedOp instanceof ProgressHandler<?> progressHandler) {
                            progressHandler.setOnProgressListener(snapshot -> {
                                UploadTask.TaskSnapshot taskSnapshot = (UploadTask.TaskSnapshot) snapshot;
                                double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, context.getString(R.string.action_notification_upload_channel))
                                        .setSmallIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_arrow_download_24_regular)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        .setOnlyAlertOnce(true)
                                        .setGroupSummary(false)
                                        .setOngoing(true)
                                        .setAutoCancel(false)
                                        .setContentTitle(context.getString(R.string.action_notification_upload_title))
                                        .setContentText(String.format(context.getString(R.string.action_notification_upload_desc),
                                                getFilePathFromUploadName(taskSnapshot.getStorage().getName(), request.getTargetFilesList())))
                                        .setProgress(100, (int) progress, true);
                                publishNotification(context,
                                        context.getString(R.string.action_notification_upload_channel),
                                        context.getString(R.string.action_notification_upload_desc),
                                        request.getChallengeCode().hashCode(), mBuilder);
                            });
                        }
                    }

                    case FileAction.ActionType.OP_DOWNLOAD -> {
                        if (requestedOp instanceof ProgressHandler<?> progressHandler) {
                            progressHandler.setOnProgressListener(snapshot -> {
                                FileDownloadTask.TaskSnapshot taskSnapshot = (FileDownloadTask.TaskSnapshot) snapshot;
                                double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, context.getString(R.string.action_notification_upload_channel))
                                        .setSmallIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_arrow_download_24_regular)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        .setOnlyAlertOnce(true)
                                        .setGroupSummary(false)
                                        .setOngoing(true)
                                        .setAutoCancel(false)
                                        .setContentTitle(context.getString(R.string.action_notification_download_title))
                                        .setContentText(String.format(context.getString(R.string.action_notification_download_desc),
                                                getFilePathFromUploadName(taskSnapshot.getStorage().getName(), request.getTargetFilesList())))
                                        .setProgress(100, (int) progress, true);
                                publishNotification(context,
                                        context.getString(R.string.action_notification_upload_channel),
                                        context.getString(R.string.action_notification_upload_desc),
                                        request.getChallengeCode().hashCode(), mBuilder);
                            });
                        }
                    }
                }
                return true;
            }

            @Override
            public void onRequestTerminated(Refoler.Device requester, FileAction.ActionRequest request, FileAction.ActionResponse.Builder response) {
                if (hasBlockListInDevice(context, requester)) {
                    return;
                }

                switch (request.getActionType()) {
                    case FileAction.ActionType.OP_UPLOAD, FileAction.ActionType.OP_DOWNLOAD ->
                            cancelNotification(context, request.getChallengeCode().hashCode());
                }
            }
        });
    }

    public static String getFilePathFromUploadName(String rawName, List<String> names) {
        for (String name : names) {
            if (rawName.equals(UploadAction.getFileRefName(name))) {
                return name;
            }
        }
        return "";
    }

    public static boolean hasBlockListInDevice(Context context, Refoler.Device device) {
        SharedPreferences prefs = Applications.getPrefs(context, Applications.PREFS_DEVICE_LIST_CACHE_SUFFIX);
        return prefs.getStringSet(PrefsKeyConst.PREFS_KEY_FILE_ACTION_DENY, new HashSet<>()).contains(device.getDeviceId());
    }

    public static void cancelNotification(Context mContext, int notificationId) {
        NotificationManager mNotifyManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyManager.cancel(notificationId);
    }

    public static void publishNotification(Context mContext,
                                           String notificationChannel, String notificationDesc,
                                           int notificationId, NotificationCompat.Builder notification) {
        NotificationManager mNotifyManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(notificationChannel, notificationDesc, NotificationManager.IMPORTANCE_DEFAULT);
        mNotifyManager.createNotificationChannel(channel);
        mNotifyManager.notify(notificationId, notification.build());
    }

    public static boolean isLayoutTablet(Context context) {
        return context.getResources().getBoolean(R.bool.is_tablet);
    }

    public static SharedPreferences getPrefs(Context context, String SUFFIX) {
        return context.getSharedPreferences(String.format("%s_%s", PREFS_PACKAGE, SUFFIX), Context.MODE_PRIVATE);
    }

    public static SharedPreferences getPrefs(Context context) {
        return getPrefs(context, PREFS_MAIN_SUFFIX);
    }

    public static String getUid(Context context) {
        return getPrefs(context).getString(PrefsKeyConst.PREFS_KEY_UID, "");
    }
}
