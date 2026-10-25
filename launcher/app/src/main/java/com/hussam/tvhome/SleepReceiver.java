package com.hussam.tvhome;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Fired by the sleep-timer alarm: turns the screen off if the app is an active device admin. */
public class SleepReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        lockNow(context);
        context.getSharedPreferences("home", Context.MODE_PRIVATE).edit().remove("sleepAt").apply();
    }

    static boolean lockNow(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, SleepAdmin.class);
            if (dpm == null || !dpm.isAdminActive(admin)) return false;
            dpm.lockNow();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
