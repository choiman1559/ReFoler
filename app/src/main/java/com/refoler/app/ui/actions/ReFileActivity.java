package com.refoler.app.ui.actions;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.db.ReFileCache;
import com.refoler.app.process.db.RemoteFile;
import com.refoler.app.ui.utils.ToastHelper;

import org.json.JSONException;

import java.io.IOException;
import java.net.URLConnection;
import java.text.CharacterIterator;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ReFileActivity extends AppCompatActivity {

    SharedPreferences prefs;
    Refoler.Device device;
    RemoteFile allFileList;
    RemoteFile lastRemoteFile;

    ScrollView remoteFileScrollView;
    SwipeRefreshLayout remoteFileRefreshLayout;
    LinearLayoutCompat remoteFileLayout;
    LinearLayoutCompat remoteFileStateLayout;
    TextView remoteFileErrorEmoji;
    TextView remoteFileStateDescription;
    ProgressBar remoteFileStateProgress;

    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (lastRemoteFile != null) {
                if (allFileList.getPath().equals(lastRemoteFile.getPath())) {
                    ReFileActivity.this.finish();
                } else {
                    lastRemoteFile = lastRemoteFile.getParent();
                    loadFileListLayout();
                }
            }
        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refiler);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.ui_bg_toolbar));
        prefs = Applications.getPrefs(this);

        Intent intent = getIntent();
        device = DeviceWrapper.detachIntentDevice(intent);

        remoteFileScrollView = findViewById(R.id.remoteFileScrollView);
        remoteFileRefreshLayout = findViewById(R.id.remoteFileRefreshLayout);
        remoteFileLayout = findViewById(R.id.remoteFileLayout);
        remoteFileStateLayout = findViewById(R.id.remoteFileStateLayout);
        remoteFileErrorEmoji = findViewById(R.id.remoteFileErrorEmoji);
        remoteFileStateProgress = findViewById(R.id.remoteFileStateProgress);
        remoteFileStateDescription = findViewById(R.id.remoteFileStateDescription);

        remoteFileLayout.setVisibility(View.GONE);
        remoteFileErrorEmoji.setVisibility(View.GONE);
        remoteFileScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> remoteFileRefreshLayout.setEnabled(remoteFileLayout.getVisibility() == View.VISIBLE && remoteFileScrollView.getScrollY() == 0));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());

        remoteFileRefreshLayout.setOnRefreshListener(() -> {
            remoteFileRefreshLayout.setRefreshing(false);
            if(remoteFileStateProgress.getVisibility() == View.GONE) {
                loadQueryFromDB(true);
            } else {
                ToastHelper.show(this, "Working in progress!", ToastHelper.LENGTH_SHORT);
            }
        });

        getOnBackPressedDispatcher().addCallback(backPressedCallback);
        loadQueryFromDB(false);
    }

    void loadQueryFromDB(boolean renewCache) {
        showProgress("Retrieving file list from database...");
        ReFileCache.getInstance(this).fetchList(renewCache, device, new ReFileCache.ReFileFetchListener() {
            @Override
            public void onReceive(@Nullable RemoteFile remoteFile) {
                allFileList = remoteFile;
                if(lastRemoteFile != null) {
                    findLastMatchedFolder();
                }
                runOnUiThread(ReFileActivity.this::loadFileListLayout);
            }

            @Override
            public void onEmpty() {
                runOnUiThread(() -> {
                    try {
                        loadFreshQuery();
                    } catch (JSONException | IOException e) {
                        showErrorException(e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                showErrorException(e);
            }
        });
    }

    void showErrorException(Exception e) {
        e.printStackTrace();
        runOnUiThread(() -> showError(e.getMessage()));
    }

    void findLastMatchedFolder() {
        RemoteFile latestFile = allFileList;
        RemoteFile lastFile = lastRemoteFile;
        ArrayList<String> folderNameList = new ArrayList<>();

        while (lastFile.getParent() != null) {
            folderNameList.add(lastFile.getName());
            lastFile = lastFile.getParent();
        }

        for(int i = folderNameList.size() - 1; i >= 0; i--) {
            boolean notFoundMatch = true;
            List<RemoteFile> folderList = latestFile.getList();

            for(int j = 0; j < folderList.size(); j++) {
                String name = folderList.get(j).getName();
                if(name.equals(folderNameList.get(i))) {
                    latestFile = folderList.get(j);
                    notFoundMatch = false;
                    break;
                }
            }

            if(notFoundMatch) {
                lastRemoteFile = latestFile;
                return;
            }
        }
        lastRemoteFile = latestFile;
    }

    @SuppressLint("SetTextI18n")
    void loadFreshQuery() throws JSONException, IOException {
        showProgress("Querying file list from target device...\n\nThis task may take some time");
        ReFileCache.getInstance(this).requestDeviceFileList(device, responsePacket -> {
            if(responsePacket.getStatus().equals(PacketConst.STATUS_OK)) {
                runOnUiThread(() -> loadQueryFromDB(true));
            } else {
                runOnUiThread(() -> showError(responsePacket.getErrorCause()));
            }
        });
    }

    void loadFileListLayout() {
        showProgress("Showing file list...");
        boolean listFolderFirst = prefs.getBoolean("listFolderFirst", true);
        remoteFileLayout.removeViews(0, remoteFileLayout.getChildCount());

        if (lastRemoteFile == null) lastRemoteFile = allFileList;
        if (!lastRemoteFile.getPath().equals(allFileList.getPath())) {
            RelativeLayout goParentLayout = (RelativeLayout) View.inflate(this, R.layout.cardview_refile_item, null);
            new RemoteFileHolder(null, goParentLayout, true, false);
            goParentLayout.setOnClickListener(v -> {
                lastRemoteFile = lastRemoteFile.getParent();
                loadFileListLayout();
            });

            remoteFileLayout.addView(goParentLayout);
        }

        if(lastRemoteFile.isIndexSkipped()) {
            RelativeLayout skippedLayout = (RelativeLayout) View.inflate(this, R.layout.cardview_refile_item, null);
            new RemoteFileHolder(lastRemoteFile, skippedLayout, false, true);
            remoteFileLayout.addView(skippedLayout);
        } else {
            ArrayList<RemoteFileHolder> folders = new ArrayList<>();
            ArrayList<RemoteFileHolder> files = new ArrayList<>();

            for (RemoteFile file : lastRemoteFile.getList()) {
                RelativeLayout layout = (RelativeLayout) View.inflate(this, R.layout.cardview_refile_item, null);
                RemoteFileHolder holder = new RemoteFileHolder(file, layout, false, false);

                layout.setOnClickListener(v -> {
                    if (file.isFile()) {
                        Intent fileDetailIntent = new Intent(this, FileDetailActivity.class);
                        fileDetailIntent.putExtra("file" /*TODO: Change to const*/, file);
                        startActivity(DeviceWrapper.attachIntentDevice(fileDetailIntent, device));
                    } else {
                        lastRemoteFile = file;
                        loadFileListLayout();
                    }
                });

                if (!listFolderFirst || holder.remoteFile.isFile()) {
                    files.add(holder);
                } else {
                    folders.add(holder);
                }
            }

            if(listFolderFirst) Collections.sort(folders);
            Collections.sort(files);

            if(listFolderFirst) for (RemoteFileHolder holder : folders) {
                remoteFileLayout.addView(holder.parentView);
            }

            for (RemoteFileHolder holder : files) {
                remoteFileLayout.addView(holder.parentView);
            }
        }

        setFileListVisibility(true);
    }

    void showProgress(String message) {
        setFileListVisibility(false);
        remoteFileErrorEmoji.setVisibility(View.GONE);
        remoteFileStateProgress.setVisibility(View.VISIBLE);
        remoteFileStateDescription.setText(message);
    }

    void showError(String errorMessage) {
        setFileListVisibility(false);
        remoteFileErrorEmoji.setVisibility(View.VISIBLE);
        remoteFileStateProgress.setVisibility(View.GONE);
        remoteFileStateDescription.setText(errorMessage);
    }

    void setFileListVisibility(boolean visible) {
        remoteFileLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        remoteFileStateLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
        remoteFileStateProgress.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    static class RemoteFileHolder implements Comparable<RemoteFileHolder> {

        int remoteFileIconId;
        View parentView;
        RemoteFile remoteFile;
        ImageView remoteFileIcon;
        ImageView remoteFileDetail;
        TextView remoteFileTitle;
        TextView remoteFileDescription;

        @SuppressLint("SetTextI18n")
        public RemoteFileHolder(RemoteFile remoteFile, View view, boolean isGoingParentButton, boolean isSkipped) {
            this.parentView = view;
            this.remoteFile = remoteFile;
            this.remoteFileIcon = view.findViewById(R.id.remoteFileIcon);
            this.remoteFileTitle = view.findViewById(R.id.remoteFileTitle);
            this.remoteFileDescription = view.findViewById(R.id.remoteFileDescription);
            this.remoteFileDetail = view.findViewById(R.id.remoteFileDetail);

            if(isSkipped) {
                remoteFileTitle.setText("This folder is not indexed");
                remoteFileDescription.setText("Indexing was skipped because\nthe folder had too much content");
                remoteFileIcon.setImageResource(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_warning_24_regular);
                remoteFileDetail.setVisibility(View.GONE);
            } else if (isGoingParentButton) {
                remoteFileTitle.setText("...");
                remoteFileDescription.setText("Parent folder");
                remoteFileIcon.setImageResource(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_folder_24_filled);
            } else {
                String description = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault()).format(remoteFile.getLastModified());
                if (remoteFile.isFile()) {
                    description += " " + humanReadableByteCountBin(remoteFile.getSize());
                }

                if (remoteFile.getPath().equals("/storage/emulated/0")) {
                    remoteFileTitle.setText("Internal Storage");
                } else remoteFileTitle.setText(remoteFile.getName());

                remoteFileDescription.setText(description);

                if(remoteFile.isFile()) {
                    String mime = null;
                    try {
                        mime = URLConnection.guessContentTypeFromName(remoteFile.getPath());
                    } catch (StringIndexOutOfBoundsException e) {
                        e.printStackTrace();
                    }

                    if(mime != null) {
                        String[] mimeArr = mime.split("/");
                        remoteFileIconId = switch (mimeArr[0]) {
                            case "audio" -> com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_music_note_1_24_regular;
                            case "video" -> com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_video_clip_24_regular;
                            case "image" -> com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_image_24_regular;
                            case "text" -> com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_slide_text_24_regular;
                            case "font" -> com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_text_font_size_24_regular;
                            case "application" -> {
                                if(mimeArr[1].contains("zip") || mimeArr[1].contains("tar") || mimeArr[1].contains("rar") || mimeArr[1].contains("7z"))
                                    yield com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_folder_zip_24_regular;
                                else yield  com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_document_24_regular;
                            }
                            default -> com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_document_24_regular;
                        };
                        remoteFileIcon.setImageResource(remoteFileIconId);
                    } else {
                        remoteFileIconId = com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_document_24_regular;
                        remoteFileIcon.setImageResource(remoteFileIconId);
                    }
                } else {
                    remoteFileIconId = com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_folder_24_filled;
                    remoteFileIcon.setImageResource(remoteFileIconId);
                }
            }
        }

        @Override
        public int compareTo(RemoteFileHolder o) {
            return o.remoteFile.compareTo(this.remoteFile);
        }
    }

    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format(Locale.getDefault(), "%.1f %ciB", value / 1024.0, ci.current());
    }
}
