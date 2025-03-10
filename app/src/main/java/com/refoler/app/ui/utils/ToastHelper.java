package com.refoler.app.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.refoler.app.Applications;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ToastHelper {

    @IntDef({LENGTH_SHORT, LENGTH_LONG})
    @IntRange(from = 1)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Duration {}
    public static final int LENGTH_SHORT = -1;
    public static final int LENGTH_LONG = 0;

    public static void show(Activity context, String message, String actionMessage, @Duration int duration, @Nullable View anchorView) {
        if(!Applications.getPrefs(context).getBoolean("UseToastInstead", false)) {
            Snackbar snackbar = Snackbar.make(context, ((ViewGroup) context.findViewById(android.R.id.content)).getChildAt(0), message, (duration == LENGTH_SHORT ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG));
            if(!actionMessage.isEmpty()) {
                snackbar.setAction(actionMessage, v -> { });
            }
            if(anchorView != null) snackbar.setAnchorView(anchorView);
            snackbar.show();
        } else {
            Toast.makeText(context, message, (duration == LENGTH_SHORT ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG)).show();
        }
    }

    public static void show(Activity context, String message, String actionMessage, @Duration int duration) {
        show(context, message, actionMessage, duration, null);
    }

    public static void show(Activity context, String message, @Duration int duration) {
      show(context, message, "", duration);
    }

    public static void show(Context context, String message, @Duration int duration) {
        if(context instanceof Activity) show((Activity) context, message, "", duration);
        else throw new ClassCastException("Context cannot be cast to android.app.Activity");
    }
}
