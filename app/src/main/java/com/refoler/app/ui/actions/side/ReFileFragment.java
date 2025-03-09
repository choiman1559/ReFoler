package com.refoler.app.ui.actions.side;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.R;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.FileActionRequester;
import com.refoler.app.process.db.ReFileCache;
import com.refoler.app.process.db.RemoteFile;
import com.refoler.app.ui.PrefsKeyConst;
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
import java.util.Objects;

public class ReFileFragment extends SideFragment {

    Refoler.Device device;
    RemoteFile allFileList;
    RemoteFile lastRemoteFile;
    String referencePath;

    boolean finishOnBack = false;
    boolean fetchDbOnFirstLoad = false;
    boolean isFindingFile = false;
    RemoteFile findingTargetFile;

    boolean isSelectingMode = false;
    boolean isPathSpecifyMode = false;
    volatile RemoteFileHolder lastSelectedHolder = null;
    volatile RemoteFileHolder selectedHolder = null;
    ArrayList<RemoteFileHolder> selectedLists = new ArrayList<>();
    ArrayList<RemoteFileHolder> remoteFileLists = new ArrayList<>();

    MaterialToolbar toolbar;
    ScrollView remoteFileScrollView;
    SwipeRefreshLayout remoteFileRefreshLayout;
    LinearLayoutCompat remoteFileLayout;
    LinearLayoutCompat remoteFileStateLayout;
    TextView remoteFileErrorEmoji;
    TextView remoteFileStateDescription;
    ProgressBar remoteFileStateProgress;
    ImageButton refolerListRemoveAll;
    ImageButton refolerListContext;
    ImageButton refolerPathSpecifyApply;

    public ReFileFragment() {
        // Required for default Fragment constructor
    }

    public ReFileFragment(Refoler.Device device, @Nullable String referencePath) {
        this.device = device;
        this.referencePath = referencePath;
    }

    public ReFileFragment isFetchDbOnFirstLoad(boolean fetchDbOnFirstLoad) {
        this.fetchDbOnFirstLoad = fetchDbOnFirstLoad;
        return this;
    }

    public ReFileFragment isFindingFile(boolean isFindingFile) {
        this.isFindingFile = isFindingFile;
        return this;
    }

    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (isSelectingMode) {
                setSelectingMode(false, false);
                return;
            } else if (finishOnBack && isPathSpecifyMode) {
                setPathSpecifyMode(false, null, null);
                return;
            }

            if (finishOnBack) {
                finishScreen();
            } else if (lastRemoteFile != null) {
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(PrefsKeyConst.PREFS_KEY_DEVICE_LIST_CACHE, device);
        outState.putString("lastRemotePath", lastRemoteFile.getPath());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_refiler, container, false);
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return String.format(Locale.getDefault(), "%s:%s",
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

        toolbar = getToolbar(false);
        setToolbarBackground(view);
        onItemSelectionChanged(-1);
        toolbar.setNavigationOnClickListener((v) -> {
            if (isSelectingMode) {
                setSelectingMode(false, false);
            } else if (isPathSpecifyMode) {
                setPathSpecifyMode(false, null, null);
            } else {
                finishScreen();
            }
        });

        if (savedInstanceState != null) {
            device = (Refoler.Device) savedInstanceState.getSerializable(PrefsKeyConst.PREFS_KEY_DEVICE_LIST_CACHE);
            referencePath = savedInstanceState.getString("lastRemotePath");
        }
        finishOnBack = prefs.getBoolean(PrefsKeyConst.PREFS_KEY_FILE_LIST_FINISH_ON_BACK, false);

        remoteFileScrollView = view.findViewById(R.id.remoteFileScrollView);
        remoteFileRefreshLayout = view.findViewById(R.id.remoteFileRefreshLayout);
        remoteFileLayout = view.findViewById(R.id.remoteFileLayout);
        remoteFileStateLayout = view.findViewById(R.id.remoteFileStateLayout);
        remoteFileErrorEmoji = view.findViewById(R.id.remoteFileErrorEmoji);
        remoteFileStateProgress = view.findViewById(R.id.remoteFileStateProgress);
        remoteFileStateDescription = view.findViewById(R.id.remoteFileStateDescription);
        refolerListRemoveAll = view.findViewById(R.id.refolerListRemoveAll);
        refolerListContext = view.findViewById(R.id.refolerListContext);
        refolerPathSpecifyApply = view.findViewById(R.id.refolerPathSpecifyApply);

        remoteFileLayout.setVisibility(View.GONE);
        remoteFileErrorEmoji.setVisibility(View.GONE);
        remoteFileScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> remoteFileRefreshLayout.setEnabled(remoteFileLayout.getVisibility() == View.VISIBLE && remoteFileScrollView.getScrollY() == 0));

        remoteFileRefreshLayout.setOnRefreshListener(() -> {
            remoteFileRefreshLayout.setRefreshing(false);
            if (remoteFileStateProgress.getVisibility() == View.GONE) {
                loadQueryFromDB(true);
            } else {
                ToastHelper.show(mContext, getString(R.string.refoler_list_warn_progress), ToastHelper.LENGTH_SHORT);
            }
        });

        registerForContextMenu(refolerListContext);
        refolerListRemoveAll.setVisibility(View.GONE);
        refolerListRemoveAll.setOnClickListener((v) -> deleteSelectedFile());
        refolerListContext.setVisibility(View.GONE);
        refolerListContext.setOnClickListener((v) -> mContext.openContextMenu(refolerListContext));
        loadQueryFromDB(fetchDbOnFirstLoad);
    }

    @Override
    public synchronized void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, @Nullable ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (isSelectingMode) {
            mContext.getMenuInflater().inflate(R.menu.menu_refoler_title_selected, menu);
        } else {
            if (lastSelectedHolder == null) {
                mContext.getMenuInflater().inflate(R.menu.menu_refoler_title_normal, menu);
            } else {
                mContext.getMenuInflater().inflate(R.menu.menu_refoler_list_item, menu);
                selectedHolder = lastSelectedHolder;
                lastSelectedHolder = null;
            }
        }
    }

    @Override
    public synchronized boolean onContextItemSelected(@NonNull MenuItem item) {
        int actionId = item.getItemId();
        selectedLists.clear();

        if (isSelectingMode) {
            if (actionId == R.id.menu_selectAll) {
                for (RemoteFileHolder remoteFileHolder : remoteFileLists) {
                    remoteFileHolder.setItemSelected(true);
                }
                onItemSelectionChanged(remoteFileLists.size());
            } else if (actionId == R.id.menu_moveTo) {
                selectedLists.addAll(getSelectedHolders());
                setPathSpecifyMode(true, getString(R.string.refoler_list_menu_move_to), FileAction.ActionType.OP_CUT);
            } else if (actionId == R.id.menu_copyTo) {
                selectedLists.addAll(getSelectedHolders());
                setPathSpecifyMode(true, getString(R.string.refoler_list_menu_copy_to), FileAction.ActionType.OP_COPY);
            }
        } else if (selectedHolder == null) {
            if (actionId == R.id.menu_selectAll) {
                setSelectingMode(true, true);
                for (RemoteFileHolder remoteFileHolder : remoteFileLists) {
                    remoteFileHolder.setItemSelected(true);
                }
            } else if (actionId == R.id.menu_newFile) {
                showMakeNewDialog(getString(R.string.refoler_list_menu_new_file), getString(R.string.refoler_list_menu_new_dialog_hint), FileAction.ActionType.OP_NEW_FILE);
            } else if (actionId == R.id.menu_newFolder) {
                showMakeNewDialog(getString(R.string.refoler_list_menu_new_folder), getString(R.string.refoler_list_menu_new_dialog_hint), FileAction.ActionType.OP_MAKE_DIR);
            }
        } else {
            if (actionId == R.id.menu_select) {
                setSelectingMode(true, false);
                selectedHolder.setItemSelected(true);
            } else if (actionId == R.id.menu_moveTo) {
                selectedLists.add(selectedHolder);
                setPathSpecifyMode(true, getString(R.string.refoler_list_menu_move_to), FileAction.ActionType.OP_CUT);
            } else if (actionId == R.id.menu_copyTo) {
                selectedLists.add(selectedHolder);
                setPathSpecifyMode(true, getString(R.string.refoler_list_menu_copy_to), FileAction.ActionType.OP_COPY);
            } else if (actionId == R.id.menu_rename) {
                showEditNameDialog(selectedHolder);
            }
        }

        selectedHolder = null;
        return super.onContextItemSelected(item);
    }

    private void postActionRequest(FileAction.ActionRequest.Builder actionRequest) {
        try {
            showProgress(getString(R.string.refoler_list_action_processing));
            FileActionRequester.setChallengeCode(device, actionRequest);
            FileActionRequester.getInstance().requestAction(mContext, device, actionRequest, (device, uploadResponse) -> mContext.runOnUiThread(() -> {
                if (uploadResponse.getOverallStatus().equals(PacketConst.STATUS_OK)) {
                    loadQueryFromDB(true);
                } else {
                    ToastHelper.show(mContext, getString(R.string.refoler_list_action_failed), ToastHelper.LENGTH_SHORT);
                    loadQueryFromDB(false);
                }
            }));
        } catch (Exception e) {
            e.printStackTrace();
            showError(String.format(Locale.getDefault(), getString(R.string.refoler_list_action_failed_exception), e));
        }
    }

    private void deleteSelectedFile() {
        selectedLists = getSelectedHolders();
        setSelectingMode(false, true);
        FileAction.ActionRequest.Builder actionRequest = FileAction.ActionRequest.newBuilder();
        actionRequest.setActionType(FileAction.ActionType.OP_DELETE);
        for (RemoteFileHolder holder : selectedLists) {
            actionRequest.addTargetFiles(holder.remoteFile.getPath());
        }
        postActionRequest(actionRequest);
    }

    private boolean isRootPath() {
        return lastRemoteFile.getPath().equals(allFileList.getPath());
    }

    private ArrayList<RemoteFileHolder> getSelectedHolders() {
        ArrayList<RemoteFileHolder> remoteFileHolders = new ArrayList<>();
        if (isSelectingMode) {
            for (RemoteFileHolder holder : remoteFileLists) {
                if (holder.isSelected()) remoteFileHolders.add(holder);
            }
        }
        return remoteFileHolders;
    }

    void onItemSelectionChanged(int offset) {
        if (isSelectingMode) {
            int size = getSelectedHolders().size();
            if (size == 0 && offset >= 0) {
                size = offset;
            }

            toolbar.setTitle(String.format(Locale.getDefault(), mContext.getString(R.string.refoler_list_menu_item_count), size));
        } else {
            toolbar.setTitle(device.getDeviceName());
        }
    }

    boolean isDestPathValid() {
        for (RemoteFileHolder holder : selectedLists) {
            RemoteFile remoteFile = holder.remoteFile;
            if (lastRemoteFile.equals(remoteFile.getParent())) {
                return false;
            } else if (!remoteFile.isFile() && lastRemoteFile.isChildOf(remoteFile, true)) {
                return false;
            }
        }
        return true;
    }

    void showEditNameDialog(RemoteFileHolder selectedHolder) {
        EditText editText = new EditText(mContext);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setText(selectedHolder.remoteFile.getName());

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
        dialogBuilder.setView(editText);
        dialogBuilder.setTitle(getString(R.string.refoler_list_menu_rename));

        dialogBuilder.setPositiveButton(mContext.getString(R.string.default_string_okay), (dialog, which) -> {
            String newFileName = editText.getText().toString();
            FileAction.ActionRequest.Builder requestBuilder = FileAction.ActionRequest.newBuilder();
            requestBuilder.setActionType(FileAction.ActionType.OP_RENAME);
            requestBuilder.addTargetFiles(selectedHolder.remoteFile.getPath());
            requestBuilder.setDestDir(selectedHolder.remoteFile.getParent().getPath() + "/" + newFileName);
            postActionRequest(requestBuilder);
        });
        dialogBuilder.setNegativeButton(mContext.getString(R.string.default_string_cancel), (dialog, which) -> dialog.cancel());
        dialogBuilder.create().show();
    }

    void showMakeNewDialog(String title, String hint, FileAction.ActionType actionType) {
        EditText editText = new EditText(mContext);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setHint(hint);

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
        dialogBuilder.setView(editText);
        dialogBuilder.setTitle(title);

        dialogBuilder.setPositiveButton(mContext.getString(R.string.default_string_okay), (dialog, which) -> {
            String newFileName = editText.getText().toString();
            FileAction.ActionRequest.Builder requestBuilder = FileAction.ActionRequest.newBuilder();
            requestBuilder.setActionType(actionType);
            requestBuilder.addTargetFiles(lastRemoteFile.getPath() + "/" + newFileName.trim().replace("/", ""));
            postActionRequest(requestBuilder);
        });
        dialogBuilder.setNegativeButton(mContext.getString(R.string.default_string_cancel), (dialog, which) -> dialog.cancel());
        dialogBuilder.create().show();
    }

    void setPathSpecifyMode(boolean isPathSpecifyMode, @Nullable String toolbarTitle, @Nullable FileAction.ActionType actionType) {
        this.isPathSpecifyMode = isPathSpecifyMode;
        remoteFileRefreshLayout.setEnabled(!isSelectingMode);
        setSelectingMode(false, true);

        refolerListContext.setVisibility(isPathSpecifyMode ? View.GONE : View.VISIBLE);
        for (RemoteFileHolder holder : remoteFileLists) {
            holder.remoteFileDetail.setVisibility(isPathSpecifyMode ? View.GONE : View.VISIBLE);
        }

        refolerPathSpecifyApply.setVisibility(isPathSpecifyMode ? View.VISIBLE : View.GONE);
        toolbar.setTitle(Objects.requireNonNullElseGet(toolbarTitle, () -> device.getDeviceName()));
        toolbar.setNavigationIcon(ContextCompat.getDrawable(mContext,
                isPathSpecifyMode ? com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_dismiss_24_regular : R.drawable.back_btn_selector));

        if (isPathSpecifyMode && actionType != null) {
            refolerPathSpecifyApply.setOnClickListener((v) -> {
                setPathSpecifyMode(false, toolbarTitle, null);
                FileAction.ActionRequest.Builder actionRequest = FileAction.ActionRequest.newBuilder();
                actionRequest.setActionType(actionType);
                actionRequest.setDestDir(lastRemoteFile.getPath());
                for (RemoteFileHolder holder : selectedLists) {
                    actionRequest.addTargetFiles(holder.remoteFile.getPath());
                }
                postActionRequest(actionRequest);
            });
        }
    }

    void setSelectingMode(boolean isSelectingMode, boolean selectAll) {
        this.isSelectingMode = isSelectingMode;
        remoteFileRefreshLayout.setEnabled(!isSelectingMode);
        refolerListRemoveAll.setVisibility(isSelectingMode ? View.VISIBLE : View.GONE);

        if (!isSelectingMode) {
            for (RemoteFileHolder holder : remoteFileLists) {
                holder.setItemSelected(false);
                holder.setSelectMode(false);
            }
        } else {
            for (RemoteFileHolder holder : remoteFileLists) {
                holder.setSelectMode(true);
            }
        }

        onItemSelectionChanged(selectAll ? remoteFileLists.size() : 1);
        toolbar.setNavigationIcon(ContextCompat.getDrawable(mContext,
                isSelectingMode ? com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_dismiss_24_regular : R.drawable.back_btn_selector));
    }

    void loadQueryFromDB(boolean renewCache) {
        showProgress(getString(R.string.refoler_list_info_progress));
        ReFileCache.getInstance(mContext).fetchList(renewCache, device, new ReFileCache.ReFileFetchListener() {
            @Override
            public void onReceive(@Nullable RemoteFile remoteFile) {
                allFileList = remoteFile;
                if (referencePath != null) {
                    findMostMatchedFolder();
                } else if (lastRemoteFile != null) {
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

    void findMostMatchedFolder() {
        RemoteFile mostMatchedFile = findMostMatchedByName(allFileList);
        findingTargetFile = mostMatchedFile;

        if (mostMatchedFile == null) {
            mostMatchedFile = allFileList;
            ToastHelper.show(mContext, String.format(getString(R.string.refoler_list_warn_not_found), referencePath), ToastHelper.LENGTH_SHORT);
        } else if (isFindingFile || mostMatchedFile.isFile()) {
            mostMatchedFile = mostMatchedFile.getParent();
        }
        lastRemoteFile = mostMatchedFile;
    }

    RemoteFile findMostMatchedByName(RemoteFile basePath) {
        for (RemoteFile remoteFile : basePath.getList()) {
            if (referencePath.equals(remoteFile.getPath())) {
                return remoteFile;
            }

            if (!remoteFile.isFile() && referencePath.startsWith(remoteFile.getPath() + "/")) {
                return findMostMatchedByName(remoteFile);
            }
        }
        return null;
    }

    void findLastMatchedFolder() {
        RemoteFile latestFile = allFileList;
        RemoteFile lastFile = lastRemoteFile;
        ArrayList<String> folderNameList = new ArrayList<>();

        while (lastFile.getParent() != null) {
            folderNameList.add(lastFile.getName());
            lastFile = lastFile.getParent();
        }

        for (int i = folderNameList.size() - 1; i >= 0; i--) {
            boolean notFoundMatch = true;
            List<RemoteFile> folderList = latestFile.getList();

            for (int j = 0; j < folderList.size(); j++) {
                String name = folderList.get(j).getName();
                if (name.equals(folderNameList.get(i))) {
                    latestFile = folderList.get(j);
                    notFoundMatch = false;
                    break;
                }
            }

            if (notFoundMatch) {
                lastRemoteFile = latestFile;
                return;
            }
        }
        lastRemoteFile = latestFile;
    }

    void loadFreshQuery() throws JSONException, IOException {
        showProgress(getString(R.string.refoler_list_info_query));
        ReFileCache.getInstance(mContext).requestDeviceFileList(device, responsePacket -> {
            if (responsePacket.getStatus().equals(PacketConst.STATUS_OK)) {
                mContext.runOnUiThread(() -> loadQueryFromDB(true));
            } else {
                mContext.runOnUiThread(() -> showError(responsePacket.getErrorCause()));
            }
        });
    }

    void loadFileListLayout() {
        showProgress(getString(R.string.refoler_list_info_rendering));
        remoteFileLayout.removeViews(0, remoteFileLayout.getChildCount());

        boolean listFolderFirst = prefs.getBoolean(PrefsKeyConst.PREFS_KEY_FILE_LIST_VIEW_FOLDER_FIRST, true);
        String orderByValue = prefs.getString(PrefsKeyConst.PREFS_KEY_FILE_LIST_VIEW_SORT_BY, getString(R.string.option_settings_value_order_by_name_az));

        if (lastRemoteFile == null) lastRemoteFile = allFileList;
        boolean isRootPath = isRootPath();

        if (!isRootPath) {
            if (!isPathSpecifyMode) {
                refolerListContext.setVisibility(View.VISIBLE);
            } else {
                refolerPathSpecifyApply.setEnabled(isDestPathValid());
            }

            RelativeLayout goParentLayout = (RelativeLayout) View.inflate(mContext, R.layout.cardview_refile_item, null);
            new RemoteFileHolder(mContext, ReFileFragment.this, orderByValue, null, goParentLayout, true, false, false);
            goParentLayout.setOnClickListener(v -> {
                if (!isSelectingMode) {
                    lastRemoteFile = lastRemoteFile.getParent();
                    loadFileListLayout();
                }
            });
            remoteFileLayout.addView(goParentLayout);
        } else {
            refolerListContext.setVisibility(View.GONE);
            refolerPathSpecifyApply.setEnabled(false);
        }

        if (lastRemoteFile.isIndexSkipped()) {
            RelativeLayout skippedLayout = (RelativeLayout) View.inflate(mContext, R.layout.cardview_refile_item, null);
            new RemoteFileHolder(mContext, ReFileFragment.this, orderByValue, lastRemoteFile, skippedLayout, false, true, isRootPath);
            remoteFileLayout.addView(skippedLayout);
        } else {
            remoteFileLists.clear();
            ArrayList<RemoteFileHolder> folders = new ArrayList<>();
            ArrayList<RemoteFileHolder> files = new ArrayList<>();

            for (RemoteFile file : lastRemoteFile.getList()) {
                RelativeLayout layout = (RelativeLayout) View.inflate(mContext, R.layout.cardview_refile_item, null);
                RemoteFileHolder holder = new RemoteFileHolder(mContext, ReFileFragment.this, orderByValue, file, layout, false, false, isRootPath);

                layout.setOnLongClickListener(v -> {
                    if (!isRootPath && !isSelectingMode) {
                        setSelectingMode(true, false);
                        holder.setItemSelected(true);
                        return true;
                    }
                    return false;
                });

                layout.setOnClickListener(v -> {
                    if (isSelectingMode) {
                        holder.setItemSelected(!holder.isSelected());
                        onItemSelectionChanged(-1);
                    } else {
                        if (file.isFile()) {
                            SideFragmentHolder.getInstance().pushFragment(mContext, true, new FileDetailFragment(device, file, holder.remoteFileIconId));
                        } else {
                            lastRemoteFile = file;
                            loadFileListLayout();
                        }
                    }
                });

                if (isPathSpecifyMode) {
                    holder.remoteFileDetail.setVisibility(View.GONE);
                }

                holder.remoteFileDetail.setOnClickListener((v) -> {
                    lastSelectedHolder = holder;
                    mContext.openContextMenu(holder.remoteFileDetail);
                });

                if (!listFolderFirst || holder.remoteFile.isFile()) {
                    files.add(holder);
                } else {
                    folders.add(holder);
                }
            }

            if (listFolderFirst) Collections.sort(folders);
            Collections.sort(files);

            if (listFolderFirst) for (RemoteFileHolder holder : folders) {
                remoteFileLayout.addView(holder.parentView);
            }

            for (RemoteFileHolder holder : files) {
                remoteFileLayout.addView(holder.parentView);
            }

            remoteFileLists.addAll(files);
            remoteFileLists.addAll(folders);

            if (isFindingFile && findingTargetFile != null) {
                for (RemoteFileHolder holder : remoteFileLists) {
                    if (holder.remoteFile.equals(findingTargetFile)) {
                        holder.parentView.requestFocusFromTouch();
                        isFindingFile = false;
                        findingTargetFile = null;
                        break;
                    }
                }
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
        if (!visible) {
            refolerListContext.setVisibility(View.GONE);
        }
        remoteFileLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        remoteFileStateLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
        remoteFileStateProgress.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    public static class RemoteFileHolder implements Comparable<RemoteFileHolder> {

        Activity context;
        String orderByValue;

        int remoteFileIconId;
        View parentView;
        RemoteFile remoteFile;
        ImageView remoteFileIcon;
        ImageView remoteFileDetail;
        TextView remoteFileTitle;
        TextView remoteFileDescription;
        AppCompatCheckBox remoteFileSelect;

        public RemoteFileHolder(Activity context, SideFragment fragment, String orderByValue, RemoteFile remoteFile, View view,
                                boolean isGoingParentButton, boolean isSkipped, boolean isRootPath) {
            this.context = context;
            this.orderByValue = orderByValue;
            this.parentView = view;
            this.remoteFile = remoteFile;
            this.remoteFileIcon = view.findViewById(R.id.remoteFileIcon);
            this.remoteFileTitle = view.findViewById(R.id.remoteFileTitle);
            this.remoteFileDescription = view.findViewById(R.id.remoteFileDescription);
            this.remoteFileDetail = view.findViewById(R.id.remoteFileDetail);
            this.remoteFileSelect = view.findViewById(R.id.remoteFileSelect);

            if (isSkipped) {
                remoteFileTitle.setText(context.getString(R.string.refoler_list_info_not_indexed));
                remoteFileDescription.setText(context.getString(R.string.refoler_list_info_not_indexed_desc));
                remoteFileIcon.setImageResource(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_warning_24_regular);
                remoteFileDetail.setVisibility(View.GONE);
            } else if (isGoingParentButton) {
                remoteFileTitle.setText("...");
                remoteFileDetail.setVisibility(View.GONE);
                remoteFileDescription.setText(context.getString(R.string.refoler_list_item_parent));
                remoteFileIcon.setImageResource(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_folder_24_filled);
            } else {
                if (isRootPath) {
                    remoteFileDetail.setVisibility(View.GONE);
                }
                fragment.registerForContextMenu(remoteFileDetail);

                String description = new SimpleDateFormat(context.getString(R.string.default_date_format), Locale.getDefault()).format(remoteFile.getLastModified());
                if (remoteFile.isFile()) {
                    description += " " + humanReadableByteCountBin(remoteFile.getSize());
                }

                if (remoteFile.getPath().equals("/storage/emulated/0")) {
                    remoteFileTitle.setText(context.getString(R.string.refoler_list_item_internal_storage));
                } else remoteFileTitle.setText(remoteFile.getName());

                remoteFileDescription.setText(description);

                if (remoteFile.isFile()) {
                    String mime = null;
                    try {
                        mime = URLConnection.guessContentTypeFromName(remoteFile.getPath());
                    } catch (StringIndexOutOfBoundsException e) {
                        e.printStackTrace();
                    }

                    if (mime != null) {
                        String[] mimeArr = mime.split("/");
                        remoteFileIconId = switch (mimeArr[0]) {
                            case "audio" ->
                                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_music_note_1_24_regular;
                            case "video" ->
                                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_video_clip_24_regular;
                            case "image" ->
                                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_image_24_regular;
                            case "text" ->
                                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_slide_text_24_regular;
                            case "font" ->
                                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_text_font_size_24_regular;
                            case "application" -> {
                                if (mimeArr[1].contains("zip") || mimeArr[1].contains("tar") || mimeArr[1].contains("rar") || mimeArr[1].contains("7z"))
                                    yield com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_folder_zip_24_regular;
                                else
                                    yield com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_document_24_regular;
                            }
                            default ->
                                    com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_document_24_regular;
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

        public boolean isSelected() {
            return remoteFileSelect.isChecked();
        }

        public void setItemSelected(boolean isSelected) {
            if (isSelected) {
                remoteFileSelect.setChecked(true);
                parentView.setBackgroundColor(ContextCompat.getColor(context, R.color.ui_menu_accent));
            } else {
                remoteFileSelect.setChecked(false);
                parentView.setBackgroundColor(ContextCompat.getColor(context, R.color.ui_bg));
            }
        }

        public void setSelectMode(boolean isSelectingMode) {
            remoteFileSelect.setVisibility(isSelectingMode ? View.VISIBLE : View.GONE);
            remoteFileDetail.setVisibility(isSelectingMode ? View.GONE : View.VISIBLE);
        }

        @Override
        public int compareTo(RemoteFileHolder o) {
            if (orderByValue.equals(context.getString(R.string.option_settings_value_order_by_name_az))) {
                return o.remoteFile.compareTo(this.remoteFile);
            } else if (orderByValue.equals(context.getString(R.string.option_settings_value_order_by_name_za))) {
                return this.remoteFile.compareTo(o.remoteFile);
            } else if (orderByValue.equals(context.getString(R.string.option_settings_value_order_by_new_date))) {
                return Long.compare(o.remoteFile.getLastModified(), this.remoteFile.getLastModified());
            } else if (orderByValue.equals(context.getString(R.string.option_settings_value_order_by_old_date))) {
                return Long.compare(this.remoteFile.getLastModified(), o.remoteFile.getLastModified());
            } else if (orderByValue.equals(context.getString(R.string.option_settings_value_order_by_large_size))) {
                return Long.compare(o.remoteFile.getSize(), this.remoteFile.getSize());
            } else if (orderByValue.equals(context.getString(R.string.option_settings_value_order_by_small_size))) {
                return Long.compare(this.remoteFile.getSize(), o.remoteFile.getSize());
            }
            return 0;
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
