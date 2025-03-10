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
        replaceFragment(context, true, fragment);
    }

    public void replaceFragment(Context context, boolean overrideToolbar, SideFragment fragment) {
        fragmentStack.clear();
        lastCommitedFragment = null;
        pushFragment(context, overrideToolbar, fragment);
    }

    public void pushFragment(Context context, boolean overrideToolbar, SideFragment fragment) {
        if (Applications.isLayoutTablet(context)) {
            if (lastCommitedFragment != null) {
                fragmentStack.push(lastCommitedFragment);
            }
            commitFinalize(castActivity(context), fragment);
        } else {
            SideHolderActivity.onSideHolderActivityCreateListener = new SideHolderActivity.OnSideHolderActivityCreateListener() {
                @Override
                public void onCreate(AppCompatActivity activity) {
                    commitFinalize(activity, fragment);
                }

                @Override
                public void onDestroy(AppCompatActivity activity) {
                    lastCommitedFragment = null;
                }
            };
            context.startActivity(new Intent(context, SideHolderActivity.class)
                    .putExtra(SideHolderActivity.SIDE_HOLDER_OVERRIDE_TOOLBAR, overrideToolbar));
        }
    }

    public void clearFragment(Context context) {
        fragmentStack.clear();
        initInstance(context);
    }

    public void popFragment(AppCompatActivity activity) {
        if (Applications.isLayoutTablet(activity)) {
            if (fragmentStack.isEmpty()) {
                initInstance(activity);
            } else {
                SideFragment fragment = fragmentStack.pop();
                commitFinalize(activity, fragment);
            }
        } else {
            activity.finish();
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
                .addToBackStack(null)
                .commit();
    }

    private AppCompatActivity castActivity(Context context) {
        if (context instanceof AppCompatActivity) {
            return (AppCompatActivity) context;
        }
        throw new IllegalStateException("Context is not an instance of AppCompatActivity");
    }
}
