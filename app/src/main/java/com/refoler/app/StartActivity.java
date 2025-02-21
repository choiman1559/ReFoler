package com.refoler.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.refoler.app.ui.MainActivity;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.ui.utils.ToastHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;

public class StartActivity extends AppCompatActivity {
    public static class RequestAct {
        public interface PerformRunnable {
            void perform(RequestAct requestAct);
        }

        private final AppCompatActivity activity;
        private final PerformRunnable parentPerformRunnable;
        private PerformRunnable requestPerformRunnable;
        private PerformRunnable checkPerformRunnable;
        private MaterialButton materialButton;
        private boolean isCompleted = false;
        private int buttonResId = -1;

        public RequestAct(AppCompatActivity activity, PerformRunnable runnable) {
            this.activity = activity;
            this.parentPerformRunnable = runnable;
        }

        public RequestAct setCheckPerformRunnable(PerformRunnable checkPerformRunnable) {
            this.checkPerformRunnable = checkPerformRunnable;
            return this;
        }

        public RequestAct setRequestPerformRunnable(PerformRunnable requestPerformRunnable) {
            this.requestPerformRunnable = requestPerformRunnable;
            return this;
        }

        public void setCompleted(boolean isInitial, boolean isCompleted) {
            this.isCompleted = isCompleted;
            if (materialButton != null) {
                if (!isInitial) {
                    parentPerformRunnable.perform(this);
                }
                activity.runOnUiThread(() -> {
                    materialButton.setEnabled(!isCompleted);
                    if (isCompleted()) {
                        materialButton.setIcon(AppCompatResources.getDrawable(activity, com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_checkmark_24_regular));
                    }
                });
            }
        }

        public void setCompleted(boolean isCompleted) {
            setCompleted(false, isCompleted);
        }

        public boolean isCompleted() {
            return isCompleted;
        }

        public void performCheck() {
            if (this.checkPerformRunnable != null) {
                this.checkPerformRunnable.perform(this);
            }
        }

        public RequestAct setButton(int viewId) {
            this.buttonResId = viewId;
            MaterialButton materialButton = activity.findViewById(viewId);
            materialButton.setOnClickListener((v) -> {
                if (requestPerformRunnable != null) {
                    requestPerformRunnable.perform(this);
                }
            });
            this.materialButton = materialButton;
            return this;
        }

        @Override
        public int hashCode() {
            return buttonResId;
        }
    }

    private final HashMap<Integer, RequestAct> requestActs = new HashMap<>();
    private final RequestAct.PerformRunnable onRequestActPerformRunnable = (requestAct) -> responsePerformResult();
    private SharedPreferences prefs;
    private ExtendedFloatingActionButton startMainActionButton;
    private FirebaseAuth mAuth;

    ActivityResultLauncher<Intent> startBatteryOptimizations = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> getRequestAct(R.id.Permit_Battery).performCheck());
    ActivityResultLauncher<String[]> startFilePermit = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> getRequestAct(R.id.Permit_File).performCheck());
    ActivityResultLauncher<String[]> notificationLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> getRequestAct(R.id.Permit_Notification).performCheck());
    ActivityResultLauncher<Intent> startAllFilesPermit = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            getRequestAct(R.id.Permit_File).setCompleted(true);
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);

        mAuth = FirebaseAuth.getInstance();
        startMainActionButton = findViewById(R.id.Start_App);
        prefs = Applications.getPrefs(this);

        if (!prefs.contains(PrefsKeyConst.PREFS_KEY_DEVICE_ID)) {
            prefs.edit().putString(PrefsKeyConst.PREFS_KEY_DEVICE_ID, UUID.randomUUID().toString()).apply();
        }

        String UID = prefs.getString(PrefsKeyConst.PREFS_KEY_UID, "");
        if (!prefs.getBoolean(PrefsKeyConst.PREFS_KEY_FCM_SUB, false) && !UID.isEmpty()) {
            FirebaseMessaging.getInstance().subscribeToTopic(Objects.requireNonNull(UID)).addOnCompleteListener(task1 -> {
                if (task1.isSuccessful()) {
                    prefs.edit().putBoolean(PrefsKeyConst.PREFS_KEY_FCM_SUB, true).apply();
                }
            });
        }

        RequestAct accountAct = new RequestAct(this, onRequestActPerformRunnable)
                .setButton(R.id.Permit_Account)
                .setCheckPerformRunnable((r) -> r.setCompleted(true, !prefs.getString(PrefsKeyConst.PREFS_KEY_UID, "").isEmpty()))
                .setRequestPerformRunnable(this::runLoginTask);
        addRequestAct(accountAct);

        RequestAct notificationAct = new RequestAct(this, onRequestActPerformRunnable)
                .setButton(R.id.Permit_Notification)
                .setCheckPerformRunnable((r) -> r.setCompleted(Build.VERSION.SDK_INT < 31 || ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled()))
                .setRequestPerformRunnable(this::runNotificationTask);
        addRequestAct(notificationAct);

        RequestAct batteryAct = new RequestAct(this, onRequestActPerformRunnable)
                .setButton(R.id.Permit_Battery)
                .setCheckPerformRunnable((r) -> {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    r.setCompleted(true, pm.isIgnoringBatteryOptimizations(getPackageName()));
                })
                .setRequestPerformRunnable(this::runBatteryTask);
        addRequestAct(batteryAct);

        RequestAct fileAct = new RequestAct(this, onRequestActPerformRunnable)
                .setButton(R.id.Permit_File)
                .setCheckPerformRunnable((r) -> r.setCompleted(true, checkFilePermission()))
                .setRequestPerformRunnable(this::runFileTask);
        addRequestAct(fileAct);

        RequestAct privacyPolicyAct = new RequestAct(this, onRequestActPerformRunnable)
                .setButton(R.id.Permit_Privacy)
                .setCheckPerformRunnable((r) -> r.setCompleted(true, prefs.getBoolean(PrefsKeyConst.PREFS_KEY_AGREE_POLICY_TERMS, false)))
                .setRequestPerformRunnable(this::runPrivacyPolicyTask);
        addRequestAct(privacyPolicyAct);

        RequestAct collectDataAct = new RequestAct(this, onRequestActPerformRunnable)
                .setButton(R.id.Permit_Collect_Data)
                .setCheckPerformRunnable((r) -> r.setCompleted(true, prefs.getBoolean(PrefsKeyConst.PREFS_KEY_AGREE_DATA_COLLECTION, false)))
                .setRequestPerformRunnable(this::runDataCollectionTask);
        addRequestAct(collectDataAct);

        startMainActionButton.setOnClickListener((v) -> startMainActivity());
        checkInitialPermission();
    }

    private void addRequestAct(RequestAct requestAct) {
        requestActs.put(requestAct.hashCode(), requestAct);
    }

    private RequestAct getRequestAct(int hashCode) {
        return requestActs.get(hashCode);
    }

    private void runDataCollectionTask(RequestAct r) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
        dialog.setTitle(getString(R.string.options_info_title_data_collections));
        dialog.setMessage(getString(R.string.refoler_start_dialog_data_desc));
        dialog.setIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_database_search_24_regular);
        dialog.setPositiveButton(getString(R.string.default_string_aggree), (dialog1, which) -> {
            prefs.edit().putBoolean(PrefsKeyConst.PREFS_KEY_AGREE_DATA_COLLECTION, true).apply();
            r.setCompleted(true);
        });
        dialog.setNegativeButton("Deny", (dialog1, which) -> {
        });
        dialog.show();
    }

    private void runPrivacyPolicyTask(RequestAct r) {
        RelativeLayout layout = (RelativeLayout) LayoutInflater.from(this).inflate(R.layout.dialog_privacy, null, false);
        WebView webView = layout.findViewById(R.id.webView);
        webView.loadUrl("file:///android_asset/privacy_policy.html");
        Button acceptButton = layout.findViewById(R.id.acceptButton);
        Button denyButton = layout.findViewById(R.id.denyButton);

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
        dialog.setTitle(getString(R.string.options_info_title_privacy_policy));
        dialog.setMessage(Html.fromHtml(getString(R.string.refoler_start_dialog_privacy_desc), Html.FROM_HTML_MODE_COMPACT));
        dialog.setView(layout);
        dialog.setIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_inprivate_account_24_regular);

        AlertDialog alertDialog = dialog.show();
        ((TextView) Objects.requireNonNull(alertDialog.findViewById(android.R.id.message))).setMovementMethod(LinkMovementMethod.getInstance());

        acceptButton.setOnClickListener((view) -> {
            prefs.edit().putBoolean(PrefsKeyConst.PREFS_KEY_AGREE_POLICY_TERMS, true).apply();
            r.setCompleted(true);
            alertDialog.dismiss();
        });
        denyButton.setOnClickListener((view) -> alertDialog.dismiss());
    }

    @SuppressLint("BatteryLife")
    private void runBatteryTask(RequestAct r) {
        startBatteryOptimizations.launch(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + getPackageName())));
    }

    private boolean checkFilePermission() {
        boolean isPermissionGranted = false;
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            isPermissionGranted = true;
        } else if (Build.VERSION.SDK_INT > 28 &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            isPermissionGranted = true;
        } else if (Build.VERSION.SDK_INT <= 28) {
            isPermissionGranted = true;
        }
        return isPermissionGranted;
    }

    private void runFileTask(RequestAct r) {
        if (Build.VERSION.SDK_INT >= 30) {
            Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
            startAllFilesPermit.launch(intent);
        } else if (Build.VERSION.SDK_INT > 28) {
            startFilePermit.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE});
        }
    }

    private void runNotificationTask(RequestAct r) {
        notificationLauncher.launch(new String[]{"android.permission.POST_NOTIFICATIONS"});
    }

    private void runLoginTask(RequestAct r) {
        GetSignInWithGoogleOption signInWithGoogleOption = new GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build();
        GetCredentialRequest credentialRequest = new GetCredentialRequest(List.of(signInWithGoogleOption));
        CredentialManager credentialManager = CredentialManager.create(this);
        credentialManager.getCredentialAsync(this, credentialRequest, new CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.getCredential().getData());
                        AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.getIdToken(), null);
                        mAuth.signInWithCredential(firebaseCredential)
                                .addOnCompleteListener(task -> {
                                    if (!task.isSuccessful()) {
                                        ToastHelper.show(StartActivity.this, getString(R.string.refoler_start_login_failed), getString(R.string.default_string_okay), ToastHelper.LENGTH_SHORT);
                                        r.setCompleted(false);
                                    } else if (mAuth.getCurrentUser() != null) {
                                        prefs.edit().putString(PrefsKeyConst.PREFS_KEY_UID, mAuth.getUid()).apply();
                                        prefs.edit().putString(PrefsKeyConst.PREFS_KEY_EMAIL, mAuth.getCurrentUser().getEmail()).apply();

                                        FirebaseMessaging.getInstance().subscribeToTopic(Objects.requireNonNull(mAuth.getUid())).addOnCompleteListener(task1 -> {
                                            if (task1.isSuccessful()) {
                                                prefs.edit().putBoolean(PrefsKeyConst.PREFS_KEY_FCM_SUB, true).apply();
                                            }
                                        });
                                        r.setCompleted(true);
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        r.setCompleted(false);
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    protected void startMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    protected void checkInitialPermission() {
        boolean isAllPermit = true;
        for (Integer key : requestActs.keySet()) {
            RequestAct requestAct = requestActs.get(key);
            if (requestAct == null) {
                continue;
            }
            requestAct.performCheck();
            if (!requestAct.isCompleted()) {
                isAllPermit = false;
                startMainActionButton.setEnabled(false);
            }
        }
        if (isAllPermit) {
            startMainActivity();
        }
    }

    protected void responsePerformResult() {
        for (Integer key : requestActs.keySet()) {
            RequestAct requestAct = requestActs.get(key);
            if (requestAct != null && !requestAct.isCompleted()) {
                return;
            }
        }
        startMainActionButton.setEnabled(true);
    }
}
