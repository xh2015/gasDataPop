package com.topsky.gasdatapop;

import com.blankj.utilcode.util.LogUtils;

import androidx.multidex.MultiDexApplication;

public class DataPopApp extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.getConfig()
                .setLogSwitch(BuildConfig.DEBUG);
    }
}