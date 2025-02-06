package com.refoler.app.ui.actions.side;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.process.db.RemoteFile;
import com.refoler.app.process.service.FileAction;
import com.refoler.app.ui.holder.SideFragment;
import com.refoler.app.ui.holder.SideFragmentHolder;
import com.refoler.app.ui.utils.PrefsCard;
import com.refoler.app.ui.utils.ToastHelper;
import com.refoler.app.utils.BillingHelper;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

public class FileDetailFragment extends SideFragment {

    AppCompatActivity mContext;
    RemoteFile remoteFile;
    Refoler.Device device;

    final int fileIconResId;
    boolean isHashReceived = false;

    public FileDetailFragment(Refoler.Device device, RemoteFile remoteFile, int fileIconResId) {
        this.remoteFile = remoteFile;
        this.device = device;
        this.fileIconResId = fileIconResId;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (AppCompatActivity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_refile_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PrefsCard fileNameItem = view.findViewById(R.id.fileNameItem);
        PrefsCard filePathItem = view.findViewById(R.id.filePathItem);
        PrefsCard fileDateItem = view.findViewById(R.id.fileDateItem);
        PrefsCard fileSizeItem = view.findViewById(R.id.fileSizeItem);
        PrefsCard fileHashItem = view.findViewById(R.id.fileHashItem);

        LinearLayout waringLayout = view.findViewById(R.id.waringLayout);
        TextView waringTextView = view.findViewById(R.id.waringText);
        ExtendedFloatingActionButton downloadButton = view.findViewById(R.id.downloadButton);

        String bigFileWarning = "";
        boolean isSubscribed = BillingHelper.getInstance().isSubscribedOrDebugBuild();
        if (remoteFile.getSize() > (isSubscribed ? 2147483648L : 104857600)) {
            downloadButton.setEnabled(false);
            bigFileWarning = isSubscribed ? """
                                    The file is too large to download!
                                    Maximum download file size is 2GB.
                                    """ : """
                                    The file is too large to download!
                                    The maximum download file size is 100MB,
                                    increasing up to 2GB with subscription.
                                    """;
        }

        fileNameItem.setIconDrawable(fileIconResId);
        fileNameItem.setDescription(remoteFile.getName());
        filePathItem.setDescription(remoteFile.getPath().replace(remoteFile.getName(), ""));
        fileDateItem.setDescription(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault()).format(remoteFile.getLastModified()));
        fileSizeItem.setDescription(remoteFile.getSize() + " Bytes");

        if(bigFileWarning.isEmpty()) {
            waringLayout.setVisibility(View.GONE);
        } else {
            waringTextView.setText(bigFileWarning);
        }

        downloadButton.setOnClickListener(v -> {

        });

        fileHashItem.setOnClickListener(v -> {
            if(isHashReceived) {
                copyInfoClip(fileHashItem.getDescriptionString());
            } else {
                fileHashItem.setDescription("Getting file hash...");

                try {
                    FileAction.getInstance().requestFileHash(mContext, device, remoteFile, (responsePacket -> {
                        String hash = responsePacket.getExtraDataCount() >= 2 ? responsePacket.getExtraData(1) : null;
                        mContext.runOnUiThread(() -> fileHashItem.setDescription(Objects.requireNonNullElse(hash, "Error while getting file hash")));
                        if(hash != null) {
                            isHashReceived = true;
                        }
                    }));
                } catch (Exception e) {
                    fileHashItem.setDescription("Error while getting file hash");
                }
            }
        });

        fileNameItem.setOnClickListener(v -> copyInfoClip(fileNameItem.getDescriptionString()));
        filePathItem.setOnClickListener(v -> copyInfoClip(filePathItem.getDescriptionString()));
        fileDateItem.setOnClickListener(v -> copyInfoClip(fileDateItem.getDescriptionString()));
        fileSizeItem.setOnClickListener(v -> copyInfoClip(String.valueOf(remoteFile.getSize())));

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> {
            if (Applications.isLayoutTablet(mContext)) {
                SideFragmentHolder.getInstance().popFragment(mContext);
            } else {
                mContext.finish();
            }
        });
    }

    void copyInfoClip(String data) {
        ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("File Details", data);
        clipboard.setPrimaryClip(clip);
        ToastHelper.show(mContext, "Copied!", "Okay", ToastHelper.LENGTH_SHORT);
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return String.format("%s:%s-%s",
                FileDetailFragment.class.getName(),
                device.getDeviceId(), remoteFile.getPath().hashCode());
    }
}
