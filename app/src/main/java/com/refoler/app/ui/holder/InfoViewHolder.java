package com.refoler.app.ui.holder;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.refoler.app.R;

public class InfoViewHolder {
    private final Context context;
    private final View parent;
    private final TextView title;
    private final TextView desc;
    private final ImageView icon;

    public InfoViewHolder(Context context, View parent) {
        this.context = context;
        this.parent = parent;
        this.title = parent.findViewById(R.id.defaultTitle);
        this.desc = parent.findViewById(R.id.defaultDesc);
        this.icon = parent.findViewById(R.id.defaultIcon);
    }

    public void setTitle(String title) {
        this.title.setText(title);
    }

    public void setDescription(String desc) {
        this.desc.setText(desc);
    }

    public void setIcon(int res) {
        this.icon.setImageDrawable(ContextCompat.getDrawable(context, res));
    }

    public void setVisibility(int visibility) {
        this.parent.setVisibility(visibility);
    }

    public View getView() {
        return parent;
    }
}
