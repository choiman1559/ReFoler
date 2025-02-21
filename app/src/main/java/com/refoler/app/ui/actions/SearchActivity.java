package com.refoler.app.ui.actions;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.refoler.FileSearch;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.ResponseWrapper;
import com.refoler.app.backend.consts.QueryConditions;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.process.db.RemoteFile;
import com.refoler.app.ui.actions.side.ReFileFragment;
import com.refoler.app.ui.holder.InfoViewHolder;
import com.refoler.app.ui.holder.SideFragmentHolder;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

public class SearchActivity extends AppCompatActivity {

    ArrayList<Refoler.Device> devices = new ArrayList<>();
    ImageButton sideNavButton;
    ImageButton keywordAction;
    AppCompatEditText searchKeyword;

    ProgressBar reloadProgressBar;
    InfoViewHolder infoViewHolder;
    LinearLayoutCompat resultLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        if (!Applications.isLayoutTablet(this)) {
            findViewById(R.id.view_separator).setVisibility(View.GONE);
        }

        sideNavButton = findViewById(R.id.sideNavButton);
        keywordAction = findViewById(R.id.actionKeyword);
        searchKeyword = findViewById(R.id.searchKeyword);
        reloadProgressBar = findViewById(R.id.reloadProgressBar);
        resultLayout = findViewById(R.id.resultLayout);

        infoViewHolder = new InfoViewHolder(this, findViewById(R.id.infoLayout));
        infoViewHolder.setVisibility(View.GONE);

        sideNavButton.setOnClickListener(v -> finish());
        keywordAction.setOnClickListener(v -> {
            searchKeyword.setText("");
            requestKeyboard();
        });

        devices.addAll(DeviceWrapper.getAllRegisteredDeviceList(this));
        requestKeyboard();

        searchKeyword.setOnEditorActionListener((v, actionId, event) -> {
            String keyword = Objects.requireNonNullElse(searchKeyword.getText(), "").toString();
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !keyword.isEmpty()) {
                try {
                    requestSearch(keyword);
                    searchKeyword.clearFocus();
                } catch (JSONException | IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }
            return true;
        });
    }

    private void requestKeyboard() {
        searchKeyword.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchKeyword, InputMethodManager.SHOW_IMPLICIT);
    }

    private void requestSearch(String keyword) throws JSONException, IOException {
        //infoViewHolder.setVisibility(View.GONE);
        reloadProgressBar.setVisibility(View.VISIBLE);
        resultLayout.removeAllViews();

        Refoler.RequestPacket.Builder request = Refoler.RequestPacket.newBuilder();
        FileSearch.Query.Builder query = FileSearch.Query.newBuilder();
        query.setKeywordQuery(
                FileSearch.KeywordQuery.newBuilder()
                        .setKeyword(keyword)
                        .setIgnoreCase(true)
                        .setKeywordCondition(QueryConditions.CASE_KEYWORD_CONTAINS)
                        .build()
        );

        request.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
        request.setFileQuery(query.build());
        request.addAllDevice(devices);
        JsonRequest.postRequestPacket(this, RecordConst.SERVICE_TYPE_FILE_SEARCH, request, this::renderResult);
    }

    private void showError(String message) {
        infoViewHolder.setVisibility(View.VISIBLE);
        infoViewHolder.setIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_globe_error_24_regular);
        infoViewHolder.setTitle(getString(R.string.search_error_title));
        infoViewHolder.setDescription(message);
    }

    @Nullable
    private Refoler.Device getDeviceById(String deviceId) {
        for (Refoler.Device device : devices) {
            if (device.getDeviceId().equals(deviceId)) {
                return device;
            }
        }
        return null;
    }

    private void renderResult(ResponseWrapper response) {
        runOnUiThread(() -> {
            reloadProgressBar.setVisibility(View.GONE);
            String extra_data = response.getRefolerPacket().getExtraData(0);

            if (!response.gotOk()) {
                showError(response.getRefolerPacket().getErrorCause());
            } else if (extra_data.isEmpty() || extra_data.equals("{}")) {
                infoViewHolder.setVisibility(View.VISIBLE);
                infoViewHolder.setIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_circle_hint_24_regular);
                infoViewHolder.setTitle(getString(R.string.search_no_result_title));
                infoViewHolder.setDescription(getString(R.string.search_no_result_desc));
            } else {
                infoViewHolder.setVisibility(View.GONE);
                resultLayout.addView(MainFragment.getMarginView(this, 24));

                try {
                    JSONObject jsonObject = new JSONObject(extra_data);
                    for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                        String key = it.next();
                        Refoler.Device device = getDeviceById(key);
                        JSONArray jsonArray = jsonObject.getJSONArray(key);
                        if (device == null || jsonArray.length() <= 0) {
                            continue;
                        }

                        DeviceResultHolder holder = new DeviceResultHolder(this, device, jsonArray);
                        holder.createView();
                        holder.addAll(resultLayout);
                        resultLayout.addView(MainFragment.getMarginView(this, 24));
                    }
                } catch (Exception e) {
                    showError(e.toString());
                }
            }
        });
    }

    protected static class DeviceResultHolder {

        private final Activity context;
        private final Refoler.Device device;
        private final ArrayList<RemoteFile> files;

        private ConstraintLayout parentView;
        private final ArrayList<View> itemViews = new ArrayList<>();

        public DeviceResultHolder(Activity context, Refoler.Device device, JSONArray jsonArray) throws JSONException {
            this.context = context;
            this.device = device;
            files = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                files.add(RemoteFile.getSingleRemoteFile(jsonArray.getJSONObject(i)));
            }
        }

        public void createView() {
            parentView = (ConstraintLayout) ConstraintLayout.inflate(context, R.layout.cardview_search_result, null);
            ImageView deviceIcon = parentView.findViewById(R.id.deviceIcon);
            TextView deviceName = parentView.findViewById(R.id.deviceName);

            if (!DeviceWrapper.isSelfDevice(context, device)) {
                parentView.findViewById(R.id.deviceThis).setVisibility(View.GONE);
            }

            deviceIcon.setImageResource(DeviceWrapper.getDeviceFormBitmap(device.getDeviceFormfactor()));
            deviceName.setText(device.getDeviceName());

            for (RemoteFile file : files) {
                View fileItemView = ReFileResultHolder.createHolder(context, file).getParentView();
                fileItemView.setOnClickListener((v) -> {
                    SideFragmentHolder.getInstance().replaceFragment(context,
                            new ReFileFragment(device, file.getPath())
                                    .isFetchDbOnFirstLoad(true)
                                    .isFindingFile(true));
                    if (Applications.isLayoutTablet(context)) {
                        context.finish();
                    }
                });
                itemViews.add(fileItemView);
            }
        }

        public void addAll(LinearLayoutCompat view) {
            view.addView(parentView);
            for (View view1 : itemViews) {
                view.addView(view1);
            }
            itemViews.clear();
        }
    }

    protected static class ReFileResultHolder {
        Activity context;

        int remoteFileIconId;
        View parentView;
        RemoteFile remoteFile;
        ImageView remoteFileIcon;
        ImageView remoteFileDetail;
        TextView remoteFileTitle;
        TextView remoteFileDescription;

        public static ReFileResultHolder createHolder(Activity context, RemoteFile remoteFile) {
            return new ReFileResultHolder(context, remoteFile, View.inflate(context, R.layout.cardview_refile_item, null));
        }

        public ReFileResultHolder(Activity context, RemoteFile remoteFile, View view) {
            this.context = context;
            this.remoteFile = remoteFile;
            this.parentView = view;

            this.remoteFileIcon = view.findViewById(R.id.remoteFileIcon);
            this.remoteFileTitle = view.findViewById(R.id.remoteFileTitle);
            this.remoteFileDescription = view.findViewById(R.id.remoteFileDescription);
            this.remoteFileDetail = view.findViewById(R.id.remoteFileDetail);

            String description = new SimpleDateFormat(context.getString(R.string.default_date_format), Locale.getDefault()).format(remoteFile.getLastModified());
            if (remoteFile.isFile()) {
                description += " " + ReFileFragment.humanReadableByteCountBin(remoteFile.getSize());
            }

            if (remoteFile.getPath().equals("/storage/emulated/0")) {
                remoteFileTitle.setText(context.getString(R.string.refoler_list_item_internal_storage));
            } else remoteFileTitle.setText(remoteFile.getName());

            remoteFileDetail.setVisibility(View.GONE);
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

        public View getParentView() {
            return parentView;
        }
    }
}
