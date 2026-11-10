package com.gathin.tvhome;

import android.content.Context;

/**
 * The pictures the Mountain theme can sit on. Each is a drawable (a 1600x900 photograph) and the skyline of its
 * mountains (an integer array), looked up by name so that a picture which is not in the build is simply not offered:
 * the repository ships only the first one, whose licence allows it.
 */
final class MountainScenes {
    private MountainScenes() { }

    static final class Scene {
        final String id;
        /** Resource names of the photograph (drawable) and of its skyline (array). */
        final String drawable, skyline;
        /** Where the clock's top sits, as a fraction of the screen's height, so the mountains cover its lower part. */
        final float clockTop;
        /** A pale sky: the date, the clock's glass and the glass panels turn dark instead of white. */
        final boolean lightSky;
        final int nameRes;

        Scene(String id, String drawable, String skyline, float clockTop, boolean lightSky, int nameRes) {
            this.id = id;
            this.drawable = drawable;
            this.skyline = skyline;
            this.clockTop = clockTop;
            this.lightSky = lightSky;
            this.nameRes = nameRes;
        }
    }

    static final Scene MOUNTAIN = new Scene("mountain", "scenic", "scenic_skyline", 0.215f, false, R.string.theme_mountain);
    static final Scene GLACIER = new Scene("glacier", "scenic_glacier", "scenic_skyline_glacier", 0.225f, true, R.string.theme_glacier);
    static final Scene ALPENGLOW = new Scene("alpenglow", "scenic_alpenglow", "scenic_skyline_alpenglow", 0.215f, false, R.string.theme_alpenglow);

    static final Scene[] ALL = {MOUNTAIN, GLACIER, ALPENGLOW};

    /** The scene with this id, or Mountain for an unknown one. */
    static Scene find(String id) {
        for (Scene scene : ALL) if (scene.id.equals(id)) return scene;
        return MOUNTAIN;
    }

    /** Whether this build has the scene's picture. */
    static boolean available(Context context, Scene scene) {
        return context.getResources().getIdentifier(scene.drawable, "drawable", context.getPackageName()) != 0;
    }
}
