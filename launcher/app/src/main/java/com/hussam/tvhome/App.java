package com.hussam.tvhome;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/** Cleans every app when the screen turns off, so the TV starts fresh next time. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Adb.ready(context)) return;
                final PendingResult pr = goAsync();
                new Thread(() -> {
                    try { Adb.cleanAll(context.getApplicationContext()); } finally { pr.finish(); }
                }).start();
            }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
}
