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
            // IPTV apps (Aroma, LION MAX): the episode you click is "Show - مدبلج-S1.E7"
            if (e.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) iptvClick(pkg, e);
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

    private static final Pattern SE = Pattern.compile("^(.+?)[\\s\\-–_|:]*S(\\d{1,2})\\s*[.\\-_ ]?\\s*E(\\d{1,4})\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOISE = Pattern.compile("[\\s\\-–_|:]*(مدبلج|مترجم|dubbed|subbed|HD|FHD|4K)[\\s\\-–_|:]*$", Pattern.CASE_INSENSITIVE);

    private void iptvClick(String pkg, AccessibilityEvent e) {
        java.util.List<CharSequence> texts = new java.util.ArrayList<>(e.getText());
        AccessibilityNodeInfo src = e.getSource();
        if (src != null) collect(src, texts, 0);
        for (CharSequence t : texts) {
            if (t == null) continue;
            Matcher m = SE.matcher(t.toString().trim());
            if (!m.matches()) continue;
            String show = m.group(1).trim();
            for (int i = 0; i < 3; i++) show = NOISE.matcher(show).replaceAll("").trim();
            if (show.isEmpty()) continue;
            int season = Integer.parseInt(m.group(2)), ep = Integer.parseInt(m.group(3));
            WatchStore.add(this, pkg, show, "الموسم " + season + " · الحلقة " + ep, "");
            android.util.Log.i("TVHW", "saved " + pkg + " | " + show + " | S" + season + "E" + ep);
            return;
        }
        android.util.Log.i("TVHW", "click " + pkg + " | " + texts);
    }

    private void collect(AccessibilityNodeInfo n, java.util.List<CharSequence> out, int depth) {
        if (n == null || depth > 6) return;
        if (n.getText() != null) out.add(n.getText());
        if (n.getContentDescription() != null) out.add(n.getContentDescription());
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
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
