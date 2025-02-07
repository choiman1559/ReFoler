package com.refoler.app.ui.actions.side;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.db.ReFileCache;
import com.refoler.app.process.db.RemoteFile;
import com.refoler.app.ui.holder.SideFragment;
import com.refoler.app.ui.holder.SideFragmentHolder;
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

public class ReFileFragment extends SideFragment {

    AppCompatActivity mContext;
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

    public ReFileFragment(Refoler.Device device) {
        this.device = device;
    }

    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (lastRemoteFile != null) {
                if (allFileList.getPath().equals(lastRemoteFile.getPath())) {
                    finishScreen();
                } else {
                    lastRemoteFile = lastRemoteFile.getParent();
                    loadFileListLayout();
                }
            }
        }
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) mContext = (AppCompatActivity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_refiler, container, false);
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return String.format(Locale.getDefault(),"%s:%s",
                ReFileFragment.class.getName(),
                device.getDeviceId());
    }

    @Override
    public OnBackPressedCallback getOnBackDispatcher() {
        return backPressedCallback;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(!Applications.isLayoutTablet(mContext)) {
            mContext.getWindow().setStatusBarColor(ContextCompat.getColor(mContext, R.color.ui_bg_toolbar));
        }
        prefs = Applications.getPrefs(mContext);

        remoteFileScrollView = view.findViewById(R.id.remoteFileScrollView);
        remoteFileRefreshLayout = view.findViewById(R.id.remoteFileRefreshLayout);
        remoteFileLayout = view.findViewById(R.id.remoteFileLayout);
        remoteFileStateLayout = view.findViewById(R.id.remoteFileStateLayout);
        remoteFileErrorEmoji = view.findViewById(R.id.remoteFileErrorEmoji);
        remoteFileStateProgress = view.findViewById(R.id.remoteFileStateProgress);
        remoteFileStateDescription = view.findViewById(R.id.remoteFileStateDescription);

        remoteFileLayout.setVisibility(View.GONE);
        remoteFileErrorEmoji.setVisibility(View.GONE);
        remoteFileScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> remoteFileRefreshLayout.setEnabled(remoteFileLayout.getVisibility() == View.VISIBLE && remoteFileScrollView.getScrollY() == 0));

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(device.getDeviceName());
        setToolbar(toolbar);

        remoteFileRefreshLayout.setOnRefreshListener(() -> {
            remoteFileRefreshLayout.setRefreshing(false);
            if(remoteFileStateProgress.getVisibility() == View.GONE) {
                loadQueryFromDB(true);
            } else {
                ToastHelper.show(mContext, getString(R.string.refoler_list_warn_progress), ToastHelper.LENGTH_SHORT);
            }
        });

        loadQueryFromDB(false);
    }

    void loadQueryFromDB(boolean renewCache) {
        showProgress(getString(R.string.refoler_list_info_progress));
        ReFileCache.getInstance(mContext).fetchList(renewCache, device, new ReFileCache.ReFileFetchListener() {
            @Override
            public void onReceive(@Nullable RemoteFile remoteFile) {
                allFileList = remoteFile;
                if(lastRemoteFile != null) {
                    findLastMatchedFolder();
                }
                mContext.runOnUiThread(ReFileFragment.this::loadFileListLayout);
            }

            @Override
            public void onEmpty() {
                mContext.runOnUiThread(() -> {
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
        mContext.runOnUiThread(() -> showError(e.getMessage()));
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

    void loadFreshQuery() throws JSONException, IOException {
        showProgress(getString(R.string.refoler_list_info_query));
        ReFileCache.getInstance(mContext).requestDeviceFileList(device, responsePacket -> {
            if(responsePacket.getStatus().equals(PacketConst.STATUS_OK)) {
                mContext.runOnUiThread(() -> loadQueryFromDB(true));
            } else {
                mContext.runOnUiThread(() -> showError(responsePacket.getErrorCause()));
            }
        });
    }

    void loadFileListLayout() {
        showProgress(getString(R.string.refoler_list_info_rendering));
        boolean listFolderFirst = prefs.getBoolean("listFolderFirst", true);
        remoteFileLayout.removeViews(0, remoteFileLayout.getChildCount());

        if (lastRemoteFile == null) lastRemoteFile = allFileList;
        if (!lastRemoteFile.getPath().equals(allFileList.getPath())) {
            RelativeLayout goParentLayout = (RelativeLayout) View.inflate(mContext, R.layout.cardview_refile_item, null);
            new RemoteFileHolder(mContext, null, goParentLayout, true, false);
            goParentLayout.setOnClickListener(v -> {
                lastRemoteFile = lastRemoteFile.getParent();
                loadFileListLayout();
            });

            remoteFileLayout.addView(goParentLayout);
        }

        if(lastRemoteFile.isIndexSkipped()) {
            RelativeLayout skippedLayout = (RelativeLayout) View.inflate(mContext, R.layout.cardview_refile_item, null);
            new RemoteFileHolder(mContext, lastRemoteFile, skippedLayout, false, true);
            remoteFileLayout.addView(skippedLayout);
        } else {
            ArrayList<RemoteFileHolder> folders = new ArrayList<>();
            ArrayList<RemoteFileHolder> files = new ArrayList<>();

            for (RemoteFile file : lastRemoteFile.getList()) {
                RelativeLayout layout = (RelativeLayout) View.inflate(mContext, R.layout.cardview_refile_item, null);
                RemoteFileHolder holder = new RemoteFileHolder(mContext, file, layout, false, false);

                layout.setOnClickListener(v -> {
                    if (file.isFile()) {
                        SideFragmentHolder.getInstance().pushFragment(mContext, new FileDetailFragment(device, file, holder.remoteFileIconId));
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

        public RemoteFileHolder(Context context, RemoteFile remoteFile, View view, boolean isGoingParentButton, boolean isSkipped) {
            this.parentView = view;
            this.remoteFile = remoteFile;
            this.remoteFileIcon = view.findViewById(R.id.remoteFileIcon);
            this.remoteFileTitle = view.findViewById(R.id.remoteFileTitle);
            this.remoteFileDescription = view.findViewById(R.id.remoteFileDescription);
            this.remoteFileDetail = view.findViewById(R.id.remoteFileDetail);

            if(isSkipped) {
                remoteFileTitle.setText(context.getString(R.string.refoler_list_info_not_indexed));
                remoteFileDescription.setText(context.getString(R.string.refoler_list_info_not_indexed_desc));
                remoteFileIcon.setImageResource(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_warning_24_regular);
                remoteFileDetail.setVisibility(View.GONE);
            } else if (isGoingParentButton) {
                remoteFileTitle.setText("...");
                remoteFileDescription.setText(context.getString(R.string.refoler_list_item_parent));
                remoteFileIcon.setImageResource(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_folder_24_filled);
            } else {
                String description = new SimpleDateFormat(context.getString(R.string.default_date_format), Locale.getDefault()).format(remoteFile.getLastModified());
                if (remoteFile.isFile()) {
                    description += " " + humanReadableByteCountBin(remoteFile.getSize());
                }

                if (remoteFile.getPath().equals("/storage/emulated/0")) {
                    remoteFileTitle.setText(context.getString(R.string.refoler_list_item_internal_storage));
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
