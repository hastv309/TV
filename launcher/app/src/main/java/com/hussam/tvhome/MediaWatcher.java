package com.hussam.tvhome;

import android.content.ComponentName;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.NotificationListenerService;

import java.util.ArrayList;
import java.util.List;

/** Records the title of what YouTube (or any app that shares it) is playing. Ignores notifications. */
public class MediaWatcher extends NotificationListenerService {
    private final List<MediaController> watched = new ArrayList<>();
    private MediaSessionManager msm;
    private final MediaSessionManager.OnActiveSessionsChangedListener onChange = this::track;

    @Override
    public void onListenerConnected() {
        try {
            msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, MediaWatcher.class);
            msm.addOnActiveSessionsChangedListener(onChange, me);
            track(msm.getActiveSessions(me));
        } catch (Exception ignored) { }
    }

    @Override
    public void onListenerDisconnected() {
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(onChange); } catch (Exception ignored) { }
    }

    private void track(List<MediaController> list) {
        if (list == null) return;
        for (MediaController mc : list) {
            String pkg = mc.getPackageName();
            if (pkg.equals(getPackageName()) || pkg.equals("com.netflix.ninja")) continue;
            boolean known = false;
            for (MediaController w : watched) if (w.getSessionToken().equals(mc.getSessionToken())) known = true;
            if (known) continue;
            watched.add(mc);
            mc.registerCallback(new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata md) { if (playing(mc)) save(pkg, md); }

                @Override
                public void onPlaybackStateChanged(android.media.session.PlaybackState st) { if (playing(mc)) save(pkg, mc.getMetadata()); }
            });
            if (playing(mc)) save(pkg, mc.getMetadata());
        }
    }

    private static boolean playing(MediaController mc) {
        android.media.session.PlaybackState st = mc.getPlaybackState();
        return st != null && st.getState() == android.media.session.PlaybackState.STATE_PLAYING;
    }

    private void save(String pkg, MediaMetadata md) {
        if (md == null) return;
        MediaDescription d = md.getDescription();
        String title = d.getTitle() == null ? null : d.getTitle().toString();
        String sub = d.getSubtitle() == null ? "" : d.getSubtitle().toString();
        String id = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (id == null && d.getMediaId() != null) id = d.getMediaId();
        String vid = (id != null && id.matches("[A-Za-z0-9_-]{11}")) ? id : "";
        WatchStore.add(this, pkg, title, sub, vid);
    }
}
