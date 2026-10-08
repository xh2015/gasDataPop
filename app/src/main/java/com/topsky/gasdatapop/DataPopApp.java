package com.topsky.gasdatapop;

import android.annotation.SuppressLint;
import android.content.Context;

import com.blankj.utilcode.util.LogUtils;

import androidx.multidex.MultiDexApplication;

public class DataPopApp extends MultiDexApplication {
    @SuppressLint("StaticFieldLeak")
    public static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        LogUtils.getConfig()
                .setLogSwitch(BuildConfig.DEBUG);
    }
}