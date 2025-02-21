package com.refoler.app.ui.options;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.ui.utils.ToastHelper;

public abstract class PrefsFragment extends PreferenceFragmentCompat {

    MonetCompat monet;
    AppCompatActivity mContext;
    SharedPreferences prefs;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        MonetCompat.setup(requireContext());
        monet = MonetCompat.getInstance();
        monet.updateMonetColors();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        monet = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            mContext = (AppCompatActivity) context;
            prefs = Applications.getPrefs(mContext);
        } else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    public interface DialogClickListener {
        void onPositiveClick(android.content.DialogInterface dialog, int which, String value);

        void onResetClick(android.content.DialogInterface dialog, int which);
    }

    private void setValueDialogModel(Preference preference, String defValue, DialogClickListener dialogClickListener) {
        setValueDialogModel(preference, false, defValue, dialogClickListener);
    }

    private void setValueDialogModel(Preference preference, boolean isPassword, String defValue, DialogClickListener dialogClickListener) {
        MaterialAlertDialogBuilder dialog;
        EditText editText;
        LinearLayout parentLayout;
        LinearLayout.LayoutParams layoutParams;

        dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
        dialog.setIcon(com.microsoft.fluent.mobile.icons.R.drawable.ic_fluent_edit_24_regular);
        dialog.setCancelable(false);
        dialog.setTitle(getString(R.string.default_string_input_value));
        dialog.setMessage(preference.getTitle());

        editText = new EditText(mContext);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint(getString(R.string.default_string_input_value));
        editText.setGravity(Gravity.CENTER);
        editText.setText(defValue);

        if(isPassword) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        parentLayout = new LinearLayout(mContext);
        layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(30, 16, 30, 16);
        editText.setLayoutParams(layoutParams);
        parentLayout.addView(editText);
        dialog.setView(parentLayout);

        dialog.setPositiveButton(getString(R.string.default_string_apply), (d, w) -> {
            String value = editText.getText().toString();
            if (value.isEmpty()) {
                ToastHelper.show(mContext, getString(R.string.default_string_input_empty), getString(R.string.default_string_okay), ToastHelper.LENGTH_SHORT);
            } else {
                dialogClickListener.onPositiveClick(d, w, value);
            }
        });

        dialog.setNeutralButton(getString(R.string.default_string_reset_default), dialogClickListener::onResetClick);
        dialog.setNegativeButton(getString(R.string.default_string_cancel), (d, w) -> {
        });
        dialog.show();
    }

    public void showValueSetDialog(Preference preference, String defValue, @Nullable Preference.OnPreferenceChangeListener preferenceChangeListener) {
        showValueSetDialog(preference, false, defValue, preferenceChangeListener);
    }

    public void showValueSetDialog(Preference preference, boolean isPassword, String defValue, @Nullable Preference.OnPreferenceChangeListener preferenceChangeListener) {
        setValueDialogModel(preference, isPassword, prefs.getString(preference.getKey(), defValue), new DialogClickListener() {
            @Override
            public void onPositiveClick(DialogInterface dialog, int which, String value) {
                prefs.edit().putString(preference.getKey(), value).apply();
                if (preferenceChangeListener != null) {
                    preferenceChangeListener.onPreferenceChange(preference, value);
                }
            }

            @Override
            public void onResetClick(DialogInterface dialog, int which) {
                prefs.edit().putString(preference.getKey(), defValue).apply();
                if (preferenceChangeListener != null) {
                    preferenceChangeListener.onPreferenceChange(preference, defValue);
                }
            }
        });
    }

    public void showValueSetDialog(Preference preference, int defValue, @Nullable Preference.OnPreferenceChangeListener preferenceChangeListener) {
        setValueDialogModel(preference, String.valueOf(prefs.getInt(preference.getKey(), defValue)), new DialogClickListener() {
            @Override
            public void onPositiveClick(DialogInterface dialog, int which, String value) {
                int IntValue = Integer.parseInt(value);
                if (IntValue < 0 || IntValue > 65535) {
                    ToastHelper.show(mContext, getString(R.string.default_string_input_out_of_range), getString(R.string.default_string_okay), ToastHelper.LENGTH_SHORT);
                } else {
                    prefs.edit().putInt(preference.getKey(), IntValue).apply();
                    if (preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChange(preference, IntValue);
                    }
                }
            }

            @Override
            public void onResetClick(DialogInterface dialog, int which) {
                prefs.edit().putInt(preference.getKey(), defValue).apply();
                if (preferenceChangeListener != null) {
                    preferenceChangeListener.onPreferenceChange(preference, defValue);
                }
            }
        });
    }
}
