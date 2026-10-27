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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null || !"net.mbc.shahidTV".contentEquals(e.getPackageName())) return;
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
