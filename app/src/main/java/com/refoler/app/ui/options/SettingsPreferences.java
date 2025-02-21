package com.refoler.app.ui.options;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.refoler.app.R;
import com.refoler.app.ui.PrefsKeyConst;
import com.refoler.app.ui.utils.SwitchedPreference;
import com.refoler.app.utils.JobUtils;

public class SettingsPreferences extends PrefsFragment {

    SwitchedPreference viewFolderFirst;
    Preference sortBy;
    Preference indexMaximumSize;
    SwitchedPreference indexHiddenFiles;
    SwitchedPreference autoBackupEnabled;
    Preference autoBackupInterval;
    SwitchedPreference autoBackupCharging;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_preference, rootKey);

        viewFolderFirst = findPreference(PrefsKeyConst.PREFS_KEY_FILE_LIST_VIEW_FOLDER_FIRST);
        sortBy = findPreference(PrefsKeyConst.PREFS_KEY_FILE_LIST_VIEW_SORT_BY);
        indexMaximumSize = findPreference(PrefsKeyConst.PREFS_KEY_INDEX_MAX_SIZE);
        indexHiddenFiles = findPreference(PrefsKeyConst.PREFS_KEY_INDEX_HIDDEN_FILES);
        autoBackupEnabled = findPreference(PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_ENABLED);
        autoBackupInterval = findPreference(PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_INTERVAL);
        autoBackupCharging = findPreference(PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_CHARGING);

        sortBy.setSummary(prefs.getString(PrefsKeyConst.PREFS_KEY_FILE_LIST_VIEW_SORT_BY, getString(R.string.option_settings_value_order_by_name_az)));
        sortBy.setOnPreferenceChangeListener(((preference, newValue) -> {
            sortBy.setSummary(newValue.toString());
            return true;
        }));

        autoBackupInterval.setEnabled(prefs.getBoolean(PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_ENABLED, true));
        autoBackupInterval.setSummary(String.format(getString(R.string.option_settings_item_backup_interval_desc_format),
                prefs.getInt(PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_INTERVAL, 8)));
        autoBackupCharging.setOnPreferenceChangeListener(((preference, newValue) -> {
            JobUtils.enquiry(mContext, true);
            return true;
        }));
        autoBackupEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
            autoBackupInterval.setEnabled((boolean) newValue);
            JobUtils.enquiry(mContext, true);
            return true;
        });
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        switch (preference.getKey()) {
            case PrefsKeyConst.PREFS_KEY_INDEX_MAX_SIZE -> showValueSetDialog(indexMaximumSize, 150, null);
            case PrefsKeyConst.PREFS_KEY_AUTO_BACKUP_INTERVAL ->
                    showValueSetDialog(autoBackupInterval, 8, ((preference1, newValue) -> {
                        //noinspection RedundantCast
                        autoBackupInterval.setSummary(String.format(getString(R.string.option_settings_item_backup_interval_desc_format), (Integer) newValue));
                        JobUtils.enquiry(mContext, true);
                        return true;
                    }));
            default -> {
                return false;
            }
        }
        return true;
    }
}
