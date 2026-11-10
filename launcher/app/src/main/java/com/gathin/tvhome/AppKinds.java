package com.gathin.tvhome;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Which of three groups an app belongs to, for the Mountain theme's separate docks: streaming, games, and everything
 * else. It uses what the app itself declares (its category, its game flag) plus a list of well-known streaming apps,
 * because many TV apps declare no category at all.
 */
final class AppKinds {
    private AppKinds() { }

    static final int STREAMING = 0, GAMES = 1, OTHER = 2;

    /** Streaming apps that do not reliably declare themselves as video apps. */
    private static final Set<String> STREAMING_PACKAGES = new HashSet<>(Arrays.asList(
            "com.netflix.ninja", "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.youtube.tv", "com.google.android.youtube.tvunplugged",
            "com.apple.atve.androidtv.appletv",
            "com.wbd.stream", "com.hbo.hbonow",
            "com.dazn",
            "it.rainet.androidtv",
            "com.hulu.livingroomplus",
            "com.cbs.app", "com.cbs.ott",
            "tv.twitch.android.app",
            "com.plexapp.android",
            "org.xbmc.kodi"));

    static int kindOf(ApplicationInfo app) {
        if (app == null) return OTHER;
        if (STREAMING_PACKAGES.contains(app.packageName)) return STREAMING;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (app.category == ApplicationInfo.CATEGORY_GAME) return GAMES;
            if (app.category == ApplicationInfo.CATEGORY_VIDEO) return STREAMING;
        }
        if ((app.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return GAMES;
        return OTHER;
    }
}
