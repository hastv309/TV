package com.hussam.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Recently watched items: {pkg, title, sub, vid, t}, newest first. One entry per show; the page shows up to 3 per app. */
final class WatchStore {
    private WatchStore() { }

    static synchronized void add(Context c, String pkg, String title, String sub, String vid) {
        if (title == null || title.trim().isEmpty()) return;
        title = title.trim();
        try {
            SharedPreferences p = c.getSharedPreferences("home", Context.MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString("watch", "[]"));
            JSONArray out = new JSONArray();
            JSONObject cur = new JSONObject().put("pkg", pkg).put("title", title)
                    .put("sub", sub == null ? "" : sub).put("vid", vid == null ? "" : vid).put("t", System.currentTimeMillis());
            out.put(cur);
            for (int i = 0; i < old.length() && out.length() < 20; i++) {
                JSONObject o = old.getJSONObject(i);
                if (o.optString("pkg").equals(pkg) && o.optString("title").equals(title)) continue; // same show: update in place
                out.put(o);
            }
            p.edit().putString("watch", out.toString()).apply();
        } catch (Exception ignored) { }
    }

    static void clear(Context c) {
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().remove("watch").apply();
    }

    static String list(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE).getString("watch", "[]");
    }
}
