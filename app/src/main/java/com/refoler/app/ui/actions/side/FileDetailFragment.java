package com.refoler.app.ui.actions.side;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.process.actions.FileActionRequester;
import com.refoler.app.process.actions.impl.misc.DownloadAction;
import com.refoler.app.process.actions.impl.misc.HashAction;
import com.refoler.app.process.db.RemoteFile;
import com.refoler.app.ui.holder.SideFragment;
import com.refoler.app.ui.utils.PrefsCard;
import com.refoler.app.ui.utils.ToastHelper;
import com.refoler.app.utils.BillingHelper;

import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class FileDetailFragment extends SideFragment {

    RemoteFile remoteFile;
    Refoler.Device device;

    int fileIconResId;
    boolean isHashReceived = false;

    public FileDetailFragment() {
        // Default constructor for fragment manager
    }

    public FileDetailFragment(Refoler.Device device, RemoteFile remoteFile, int fileIconResId) {
        this.remoteFile = remoteFile;
        this.device = device;
        this.fileIconResId = fileIconResId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_refile_detail, container, false);
    }

    @Override
    public OnBackPressedCallback getOnBackDispatcher() {
        return new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishScreen();
            }
        };
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("device", device);
        outState.putSerializable("remoteFile", remoteFile.getSerializeOptimized());
        outState.putInt("fileIconResId", fileIconResId);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MaterialToolbar toolbar = getToolbar(false);
        setToolbar(toolbar, getString(R.string.refoler_detail_title));
        setToolbarBackground(view);

        if (savedInstanceState != null) {
            device = (Refoler.Device) savedInstanceState.getSerializable("device");
            remoteFile = (RemoteFile) savedInstanceState.getSerializable("remoteFile");
            fileIconResId = savedInstanceState.getInt("fileIconResId");
        }

        PrefsCard fileNameItem = view.findViewById(R.id.fileNameItem);
        PrefsCard filePathItem = view.findViewById(R.id.filePathItem);
        PrefsCard fileDateItem = view.findViewById(R.id.fileDateItem);
        PrefsCard fileSizeItem = view.findViewById(R.id.fileSizeItem);
        PrefsCard fileHashItem = view.findViewById(R.id.fileHashItem);
        PrefsCard filePermissionItem = view.findViewById(R.id.filePermissionItem);

        LinearLayout waringLayout = view.findViewById(R.id.waringLayout);
        TextView waringTextView = view.findViewById(R.id.waringText);
        ExtendedFloatingActionButton downloadButton = view.findViewById(R.id.downloadButton);

        String bigFileWarning = "";
        boolean isSubscribed = BillingHelper.getInstance().isSubscribedOrDebugBuild();
        if (remoteFile.getSize() > (isSubscribed ? 2147483648L : 104857600)) {
            downloadButton.setEnabled(false);
            bigFileWarning = isSubscribed ?
                    getString(R.string.refoler_detail_size_limit_sub) :
                    getString(R.string.refoler_detail_size_limit_unsub);
        }

        fileNameItem.setIconDrawable(fileIconResId);
        fileNameItem.setDescription(remoteFile.getName());
        filePathItem.setDescription(remoteFile.getPath().replace(remoteFile.getName(), ""));
        fileDateItem.setDescription(new SimpleDateFormat(getString(R.string.default_date_format), Locale.getDefault()).format(remoteFile.getLastModified()));
        fileSizeItem.setDescription(remoteFile.getSize() + " Bytes");

        if (remoteFile.hasPermissionInfo()) {
            ArrayList<String> permissionInfo = new ArrayList<>();
            if (remoteFile.canRead())
                permissionInfo.add(getString(R.string.refoler_detail_permission_readable));
            if (remoteFile.canWrite())
                permissionInfo.add(getString(R.string.refoler_detail_permission_writable));
            if (remoteFile.canExecute())
                permissionInfo.add(getString(R.string.refoler_detail_permission_executable));

            if (!permissionInfo.isEmpty()) {
                filePermissionItem.setDescription(String.join(", ", permissionInfo));
            } else {
                filePermissionItem.setDescription(getString(R.string.refoler_detail_permission_none));
            }
        } else {
            filePermissionItem.setDescription(getString(R.string.refoler_detail_permission_unknown));
        }

        if (bigFileWarning.isEmpty()) {
            waringLayout.setVisibility(View.GONE);
        } else {
            waringTextView.setText(bigFileWarning);
        }

        downloadButton.setOnClickListener(v -> {
            try {
                downloadButton.setEnabled(false);
                FileAction.ActionRequest.Builder actionRequest = FileAction.ActionRequest.newBuilder();
                actionRequest.setActionType(FileAction.ActionType.OP_UPLOAD);
                actionRequest.addTargetFiles(remoteFile.getPath());
                FileActionRequester.setChallengeCode(device, actionRequest);

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext, getString(R.string.action_notification_upload_channel))
                        .setSmallIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_arrow_download_24_regular)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOnlyAlertOnce(true)
                        .setGroupSummary(false)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setContentTitle(mContext.getString(R.string.action_notification_upload_title))
                        .setProgress(0, 0, true);

                FileActionRequester.getInstance().requestAction(mContext, device, actionRequest, (device, uploadResponse) -> {
                    Applications.cancelNotification(mContext, actionRequest.getChallengeCode().hashCode());
                    FileAction.ActionRequest.Builder downloadRequest = FileAction.ActionRequest.newBuilder();
                    downloadRequest.setActionType(FileAction.ActionType.OP_DOWNLOAD);
                    downloadRequest.addTargetFiles(remoteFile.getPath());

                    Applications.publishNotification(mContext,
                            mContext.getString(R.string.action_notification_upload_channel),
                            mContext.getString(R.string.action_notification_upload_desc),
                            downloadRequest.hashCode(), mBuilder);

                    DownloadAction downloadAction = new DownloadAction();
                    downloadAction.performActionOp(mContext, DeviceWrapper.getSelfDeviceInfo(mContext), downloadRequest.build(), downloadResponse -> {
                        if(downloadResponse.getResult(0).getResultSuccess()) {
                            mContext.runOnUiThread(() -> downloadButton.setEnabled(true));
                        }
                    });
                });
            } catch (JSONException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        fileHashItem.setOnClickListener(v -> {
            if (isHashReceived) {
                copyInfoClip(fileHashItem.getDescriptionString());
            } else {
                fileHashItem.setDescription(getString(R.string.refoler_detail_process_hash));

                try {
                    FileAction.ActionRequest.Builder actionRequest = FileAction.ActionRequest.newBuilder();
                    actionRequest.setActionType(FileAction.ActionType.OP_HASH);
                    actionRequest.addTargetFiles(remoteFile.getPath());
                    actionRequest.setDestDir(HashAction.DEFAULT_MD5);

                    FileActionRequester.getInstance().requestAction(mContext, device, actionRequest, (device, response) -> mContext.runOnUiThread(() -> {
                        if (response.getOverallStatus().equals(DirectActionConst.RESULT_OK)) {
                            FileAction.ActionResult actionResult = response.getResult(0);
                            if (actionResult.getResultSuccess()) {
                                fileHashItem.setDescription(actionResult.getExtraData(0));
                                isHashReceived = true;
                            } else {
                                fileHashItem.setDescription(getString(R.string.refoler_detail_error_hash));
                            }
                        } else {
                            fileHashItem.setDescription(getString(R.string.refoler_detail_error_hash));
                        }
                    }));
                } catch (Exception e) {
                    fileHashItem.setDescription(getString(R.string.refoler_detail_error_hash));
                }
            }
        });

        fileNameItem.setOnClickListener(v -> copyInfoClip(fileNameItem.getDescriptionString()));
        filePathItem.setOnClickListener(v -> copyInfoClip(filePathItem.getDescriptionString()));
        fileDateItem.setOnClickListener(v -> copyInfoClip(fileDateItem.getDescriptionString()));
        fileSizeItem.setOnClickListener(v -> copyInfoClip(String.valueOf(remoteFile.getSize())));
    }

    void copyInfoClip(String data) {
        ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.refoler_detail_title), data);
        clipboard.setPrimaryClip(clip);
        ToastHelper.show(mContext, getString(R.string.refoler_detail_copied), getString(R.string.default_string_okay), ToastHelper.LENGTH_SHORT);
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return String.format(Locale.getDefault(), "%s_%d:%s_%d",
                FileDetailFragment.class.getName(), this.hashCode(),
                device.getDeviceId(), remoteFile.getPath().hashCode());
    }
}
