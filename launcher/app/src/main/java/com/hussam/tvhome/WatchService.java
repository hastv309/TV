package com.hussam.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the show and episode from Shahid's player screen ("الحلقة 6 - منطقة آمنة"). Reads nothing else. */
public class WatchService extends AccessibilityService {
    private static final Pattern EP = Pattern.compile("^\\s*(?:الحلقة|Episode)\\s*(\\d+)\\s*[-–—]\\s*(.+?)\\s*$");
    private long last;

    private final java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();

    /** Probe for IPTV apps: logs the on-screen text (tag TVHW) so we can learn where the title shows. */
    private void probe(String pkg, AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 30) return;
        CharSequence t = n.getText(), d = n.getContentDescription();
        for (CharSequence x : new CharSequence[]{t, d}) {
            if (x == null || x.length() == 0) continue;
            String line = pkg + " | " + (n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName()) + " | " + x;
            if (seen.add(line)) {
                android.util.Log.i("TVHW", line);
                if (seen.size() > 400) seen.remove(seen.iterator().next());
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) probe(pkg, n.getChild(i), depth + 1);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.startsWith("com.mbm_soft")) {
            long now = System.currentTimeMillis();
            if (now - last < 1500) return;
            last = now;
            probe(pkg, getRootInActiveWindow(), 0);
            return;
        }
        if (!"net.mbc.shahidTV".equals(pkg)) return;
        long now = System.currentTimeMillis();
        if (now - last < 1500) return;
        last = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        scan(root, 0);
    }

    private boolean scan(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 30) return false;
        CharSequence t = n.getText();
        if (t != null) {
            Matcher m = EP.matcher(t.toString());
            if (m.matches()) {
                WatchStore.add(this, "net.mbc.shahidTV", m.group(2), "الحلقة " + m.group(1), "");
                return true;
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            if (scan(n.getChild(i), depth + 1)) return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() { }
}
