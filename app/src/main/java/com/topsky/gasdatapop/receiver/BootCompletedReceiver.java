package com.topsky.gasdatapop.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.blankj.utilcode.util.LogUtils;
import com.topsky.gasdatapop.ui.MainActivity;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            LogUtils.i(TAG, "Device boot completed. Launching MainActivity in background.");

            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Mark that this launch is from boot so Activity can move to background
            launch.putExtra("launched_from_boot", true);
            try {
                context.startActivity(launch);
            } catch (Exception e) {
                LogUtils.e(TAG, "Failed to start MainActivity after boot: " + e.getMessage(), e);
            }
        }
    }
}


