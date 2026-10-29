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

    /** True when every extra (banner, exact alarms, episode reader) is already on. */
    static boolean extrasOn(Context c) {
        try {
            String p = c.getPackageName();
            String a11y = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
            boolean overlay = android.provider.Settings.canDrawOverlays(c);
            return overlay && a11y != null && a11y.contains(p + ".WatchService");
        } catch (Exception e) {
            return false;
        }
    }

    private static long lastRevive;

    /** After an update or force-stop Android may leave the service "enabled" but unbound: toggle it off and on. */
    static void reviveWatchService(Context c) {
        long now = System.currentTimeMillis();
        if (now - lastRevive < 120_000L || WatchService.running || !ready(c)) return;
        lastRevive = now;
        String p = c.getPackageName(), svc = p + "/" + p + ".WatchService";
        String cur = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services");
        StringBuilder others = new StringBuilder();
        if (cur != null) for (String x : cur.split(":")) {
            if (x.isEmpty() || x.equals(svc) || x.equals(p + "/.WatchService")) continue;
            if (others.length() > 0) others.append(':');
            others.append(x);
        }
        String without = others.length() == 0 ? "settings delete secure enabled_accessibility_services"
                : "settings put secure enabled_accessibility_services '" + others + "'";
        String with = "settings put secure enabled_accessibility_services '" + (others.length() == 0 ? svc : others + ":" + svc) + "'";
        shell(c, without + "; sleep 1; " + with + "; settings put secure accessibility_enabled 1; echo ok");
        android.util.Log.i("TVHW", "revived WatchService");
    }

    /** One-time grants the banner and exact alarms need; harmless to repeat. */
    static void grantExtras(Context c) {
        String p = c.getPackageName();
        String svc = p + "/" + p + ".WatchService";
        shell(c, "appops set " + p + " SYSTEM_ALERT_WINDOW allow; appops set " + p + " SCHEDULE_EXACT_ALARM allow;"
                + " s=$(settings get secure enabled_accessibility_services);"
                + " case \"$s\" in *" + svc + "*) ;; null|'') settings put secure enabled_accessibility_services " + svc + " ;;"
                + " *) settings put secure enabled_accessibility_services \"$s:" + svc + "\" ;; esac;"
                + " settings put secure accessibility_enabled 1; echo ok");
    }
}
