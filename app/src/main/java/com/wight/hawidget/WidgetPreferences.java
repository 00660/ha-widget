package com.wight.hawidget;

import android.content.Context;

final class WidgetPreferences {
    private static final String PREFERENCES = "widget_preferences";
    private static final String SELECTED_PRESET = "selected_preset";
    private static final String BASE_SPEED = "base_speed";
    private static final String MODE = "mode";

    private WidgetPreferences() {
    }

    static String loadSelectedPreset(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(SELECTED_PRESET, "");
    }

    static void saveSelectedPreset(Context context, String preset) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(SELECTED_PRESET, preset)
                .apply();
    }

    static int loadBaseSpeed(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getInt(BASE_SPEED, 100);
    }

    static void saveBaseSpeed(Context context, int percentage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt(BASE_SPEED, Math.max(0, Math.min(100, percentage)))
                .apply();
    }

    static String loadMode(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(MODE, "");
    }

    static void saveMode(Context context, String mode) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(MODE, mode == null ? "" : mode)
                .apply();
    }
}
