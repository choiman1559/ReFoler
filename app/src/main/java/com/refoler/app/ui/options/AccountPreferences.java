package com.refoler.app.ui.options;

import android.os.Bundle;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.refoler.Refoler;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.process.db.ReFileCache;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.ui.utils.SwitchedPreference;
import com.refoler.app.ui.utils.ToastHelper;
import com.refoler.app.utils.JsonRequest;

import java.util.concurrent.Executors;

public class AccountPreferences extends PrefsFragment {

    Preference UID;
    SwitchedPreference passwordEnabled;
    Preference passwordValue;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.account_preference, rootKey);

        UID = findPreference(PrefsKeyConst.PREFS_KEY_UID);
        passwordEnabled = findPreference(PrefsKeyConst.PREFS_KEY_PASSWORD_ENABLED);
        passwordValue = findPreference(PrefsKeyConst.PREFS_KEY_PASSWORD_VALUE);

        UID.setSummary(String.format(getString(R.string.option_account_item_account_desc), prefs.getString(PrefsKeyConst.PREFS_KEY_EMAIL, "")));
        passwordValue.setEnabled(prefs.getBoolean(PrefsKeyConst.PREFS_KEY_PASSWORD_ENABLED, false));
        passwordEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
            passwordValue.setEnabled((boolean) newValue);
            return true;
        });
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        switch (preference.getKey()) {
            case PrefsKeyConst.PREFS_KEY_UID:
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                builder.setTitle(getString(R.string.option_account_item_account_dialog_title)).setMessage(getString(R.string.option_account_item_account_dialog_desc));
                builder.setPositiveButton(getString(R.string.default_string_okay), (dialog, which) -> {
                    Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
                    requestBuilder.setActionName(RecordConst.SERVICE_ACTION_TYPE_REMOVE);
                    requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(mContext));
                    JsonRequest.postRequestPacket(mContext, RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION, requestBuilder, (response) -> {
                        if (!response.gotOk()) {
                            ToastHelper.show(mContext, getString(R.string.option_account_item_register_fail), ToastHelper.LENGTH_SHORT);
                            return;
                        }

                        FirebaseAuth.getInstance().signOut();
                        ReFileCache.getInstance(mContext).clearAllCaches();
                        Applications.getPrefs(mContext, Applications.PREFS_DEVICE_LIST_CACHE_SUFFIX).edit().clear().apply();
                        prefs.edit()
                                .remove(PrefsKeyConst.PREFS_KEY_UID)
                                .remove(PrefsKeyConst.PREFS_KEY_EMAIL)
                                .apply();

                        ClearCredentialStateRequest credentialRequest = new ClearCredentialStateRequest();
                        CredentialManager credentialManager = CredentialManager.create(mContext);
                        credentialManager.clearCredentialStateAsync(credentialRequest, new CancellationSignal(), Executors.newSingleThreadExecutor(), new CredentialManagerCallback<>() {
                            @Override
                            public void onResult(Void unused) {
                                mContext.finishAffinity();
                            }

                            @Override
                            public void onError(@NonNull ClearCredentialException e) {
                                mContext.finishAffinity();
                            }
                        });
                    });
                });

                builder.setNegativeButton(getString(R.string.default_string_cancel), ((dialog, which) -> { }));
                builder.show();
                break;

            case PrefsKeyConst.PREFS_KEY_PASSWORD_VALUE:
                showValueSetDialog(preference, true, "", (preference1, newValue) -> true);
                break;

            case PrefsKeyConst.PREFS_KEY_REGISTER_MANUAL:
                DeviceWrapper.registerSelf(mContext, (response) -> {
                    if (!response.gotOk()) {
                        ToastHelper.show(mContext, getString(R.string.option_account_item_register_fail), ToastHelper.LENGTH_SHORT);
                        return;
                    }
                    FirebaseMessaging.getInstance().subscribeToTopic(prefs.getString(PrefsKeyConst.PREFS_KEY_UID, ""));
                    ToastHelper.show(mContext, getString(R.string.option_account_item_register_info), ToastHelper.LENGTH_SHORT);
                });
                break;

            default:
                return false;
        }
        return true;
    }
}

