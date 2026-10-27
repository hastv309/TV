package com.hussam.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Runs shell commands through the TV's own network ADB (127.0.0.1:5555), allowed once on the TV. */
final class Adb {
    private Adb() { }

    static synchronized String shell(Context c, String cmd) {
        try {
            File priv = new File(c.getFilesDir(), "adbkey");
            File pub = new File(c.getFilesDir(), "adbkey.pub");
            if (!priv.exists()) dadb.AdbKeyPair.generate(priv, pub);
            dadb.AdbKeyPair kp = dadb.AdbKeyPair.read(priv, pub);
            try (dadb.Dadb d = dadb.Dadb.create("127.0.0.1", 5555, kp, 3000, 60000)) {
                String out = d.shell(cmd).getAllOutput();
                c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putBoolean("adbOk", true).apply();
                return out;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean ready(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE).getBoolean("adbOk", false);
    }

    static Set<String> launchable(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> pkgs = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0))
            pkgs.add(ri.activityInfo.packageName);
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            pkgs.add(ri.activityInfo.packageName);
        pkgs.remove(c.getPackageName());
        return pkgs;
    }

    /** Force-stops every launchable app; falls back to closing background processes. Returns true if force-stop ran. */
    static boolean cleanAll(Context c) {
        Set<String> pkgs = launchable(c);
        StringBuilder cmd = new StringBuilder();
        for (String p : pkgs) if (p.matches("[A-Za-z0-9._]+")) cmd.append("am force-stop ").append(p).append(';');
        cmd.append("echo ok");
        String out = shell(c, cmd.toString());
        if (out != null && out.contains("ok")) return true;
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        for (String p : pkgs) {
            try { am.killBackgroundProcesses(p); } catch (Exception ignored) { }
        }
        return false;
    }

    /** One-time grants the banner and exact alarms need; harmless to repeat. */
    static void grantExtras(Context c) {
        String p = c.getPackageName();
        shell(c, "appops set " + p + " SYSTEM_ALERT_WINDOW allow; appops set " + p + " SCHEDULE_EXACT_ALARM allow; echo ok");
    }
}
