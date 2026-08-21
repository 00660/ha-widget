package com.wight.hawidget;

import android.content.Context;

final class WidgetPreferences {
    private static final String PREFERENCES = "widget_preferences";
    private static final String SELECTED_PRESET = "selected_preset";
    private static final String BASE_SPEED = "base_speed";
    private static final String MODE = "mode";
    private static final String ESPHOME_URL = "esphome_url";
    private static final String FAN_NAME = "fan_name";

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

    static String loadEspHomeUrl(Context context) {
        return loadEspHomeUrl(context, 0);
    }

    static String loadEspHomeUrl(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(ESPHOME_URL + slot, slot == 0 ? "http://192.168.2.64" : "");
    }

    static String loadFanName(Context context) {
        return loadFanName(context, 0);
    }

    static String loadFanName(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(FAN_NAME + slot, "设备 " + (slot + 1));
    }

    static void saveDevice(Context context, String url, String fanName) {
        saveDevice(context, 0, url, fanName);
    }

    static void saveDevice(Context context, int slot, String url, String fanName) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(ESPHOME_URL + slot, url)
                .putString(FAN_NAME + slot, fanName)
                .apply();
    }
}
