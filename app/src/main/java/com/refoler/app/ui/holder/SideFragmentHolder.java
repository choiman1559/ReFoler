package com.refoler.app.ui.holder;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.refoler.app.Applications;
import com.refoler.app.R;
import com.refoler.app.ui.actions.side.DefaultFragment;

import java.util.Stack;

public class SideFragmentHolder {

    protected static SideFragmentHolder sideFragmentHolder;
    protected final Stack<SideFragment> fragmentStack;
    protected SideFragment lastCommitedFragment;

    private SideFragmentHolder() {
        fragmentStack = new Stack<>();
    }

    public static void initInstance(Context context) {
        SideFragmentHolder sideFragmentHolder = getInstance();
        if (Applications.isLayoutTablet(context)) {
            sideFragmentHolder.commitFinalize(sideFragmentHolder.castActivity(context), new DefaultFragment());
        }
    }

    public static SideFragmentHolder getInstance() {
        if (sideFragmentHolder == null) {
            sideFragmentHolder = new SideFragmentHolder();
        }
        return sideFragmentHolder;
    }

    public void replaceFragment(Context context, SideFragment fragment) {
        fragmentStack.clear();
        lastCommitedFragment = null;
        pushFragment(context, fragment);
    }

    public void pushFragment(Context context, SideFragment fragment) {
        fragmentStack.push(fragment);
        if (Applications.isLayoutTablet(context)) {
            commitFinalize(castActivity(context), fragment);
        } else {
            SideHolderActivity.onSideHolderActivityCreateListener = activity -> commitFinalize(activity, fragment);
            context.startActivity(new Intent(context, SideHolderActivity.class));
        }
    }

    public void clearFragment(Context context) {
        fragmentStack.clear();
        initInstance(context);
    }

    public void popFragment(AppCompatActivity activity) {
        SideFragment fragment = fragmentStack.pop();
        lastCommitedFragment = null;

        if (fragmentStack.size() <= 1) {
            if (Applications.isLayoutTablet(activity)) {
                initInstance(activity);
            } else {
                activity.finish();
            }
        } else {
            commitFinalize(activity, fragment);
        }
    }

    private void commitFinalize(AppCompatActivity activity, SideFragment fragment) {
        if (lastCommitedFragment != null && lastCommitedFragment.getFragmentId().equals(fragment.getFragmentId())) {
            return;
        }

        lastCommitedFragment = fragment;
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.contentFrameSub, fragment)
                .commit();
    }

    private AppCompatActivity castActivity(Context context) {
        if (context instanceof AppCompatActivity) {
            return (AppCompatActivity) context;
        }
        throw new IllegalStateException("Context is not an instance of AppCompatActivity");
    }
}
