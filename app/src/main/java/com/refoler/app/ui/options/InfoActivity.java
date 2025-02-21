package com.refoler.app.ui.options;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.refoler.app.BuildConfig;
import com.refoler.app.R;
import com.refoler.app.ui.utils.PrefsCard;

import java.util.Objects;

public class InfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        PrefsCard appVersion = findViewById(R.id.appVersion);
        PrefsCard appGithub = findViewById(R.id.appGithub);
        PrefsCard appOpenSource = findViewById(R.id.appOpenSource);
        PrefsCard appPrivacyPolicy = findViewById(R.id.appPrivacyPolicy);
        PrefsCard appDataCollections = findViewById(R.id.appDataCollections);

        appVersion.setDescription(BuildConfig.VERSION_NAME + (BuildConfig.DEBUG ? "_DEBUG" : "RELEASE"));
        appVersion.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:com.refoler.app"))));
        appGithub.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/ReFoler"))));

        appOpenSource.setOnClickListener(v -> {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Open Source Licenses");
            dialog.setMessage(R.string.ossl);
            dialog.setIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_database_search_24_regular);
            dialog.setPositiveButton("OK", (dialog1, which) -> {
            });
            dialog.show();
        });

        appDataCollections.setOnClickListener(v -> {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Data Collect Agreement");
            dialog.setMessage("Noti Sender reads the user's File information for synchronization between devices, even when the app is closed or not in use.");
            dialog.setIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_database_search_24_regular);
            dialog.setPositiveButton("OK", (dialog1, which) -> { });
            dialog.show();
        });

        appPrivacyPolicy.setOnClickListener(v -> {
            RelativeLayout layout = (RelativeLayout) LayoutInflater.from(this).inflate(R.layout.dialog_privacy, null, false);
            WebView webView = layout.findViewById(R.id.webView);
            webView.loadUrl("file:///android_asset/privacy_policy.html");

            Button acceptButton = layout.findViewById(R.id.acceptButton);
            acceptButton.setText(getString(R.string.default_string_cancel));
            Button denyButton = layout.findViewById(R.id.denyButton);
            denyButton.setVisibility(View.INVISIBLE);

            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Privacy Policy");
            dialog.setMessage(Html.fromHtml("You have already accepted the <a href=\"https://github.com/choiman1559/ReFoler/blob/master/PrivacyPolicy\">Privacy Policy</a>.<br> However, You can review this policy any time.", Html.FROM_HTML_MODE_COMPACT));
            dialog.setView(layout);
            dialog.setIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_inprivate_account_24_regular);

            AlertDialog alertDialog = dialog.show();
            ((TextView) Objects.requireNonNull(alertDialog.findViewById(android.R.id.message))).setMovementMethod(LinkMovementMethod.getInstance());

            acceptButton.setOnClickListener((v2) -> alertDialog.dismiss());
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }
}

