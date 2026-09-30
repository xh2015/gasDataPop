package com.topsky.gasdatapop.base;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

public abstract class BaseFragment<VB extends ViewBinding> extends Fragment implements HandlerAction {
    protected VB binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = initViewBinding(inflater, container);
        initView();
        return binding.getRoot();
    }

    protected abstract VB initViewBinding(LayoutInflater inflater, ViewGroup container);

    protected abstract void initView();

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeCallbacks();
    }

    public void start(Class clazz) {
        FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        Intent intent = new Intent(activity, clazz);
        activity.startActivity(intent);
    }
}
