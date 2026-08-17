package com.wight.hawidget;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.net.URISyntaxException;

final class HaSettings {
    private static final String PREFERENCES = "ha_widget_preferences";
    private static final String BASE_URL = "base_url";
    private static final String TOKEN = "token";
    private static final String FAN_ENTITY = "fan_entity";
    private static final String SELECTED_PRESET = "selected_preset";

    final String baseUrl;
    final String token;
    final String fanEntity;

    HaSettings(String baseUrl, String token, String fanEntity) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.token = token == null ? "" : token.trim();
        this.fanEntity = fanEntity == null ? "" : fanEntity.trim();
    }

    static HaSettings load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return new HaSettings(
                preferences.getString(BASE_URL, ""),
                preferences.getString(TOKEN, ""),
                preferences.getString(FAN_ENTITY, "")
        );
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

    void save(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(BASE_URL, baseUrl)
                .putString(TOKEN, token)
                .putString(FAN_ENTITY, fanEntity)
                .apply();
    }

    boolean isComplete() {
        return hasFan();
    }

    boolean hasFan() {
        return hasConnection() && !fanEntity.isEmpty();
    }

    private boolean hasConnection() {
        return isValidBaseUrl() && !token.isEmpty();
    }

    private boolean isValidBaseUrl() {
        try {
            URI uri = new URI(baseUrl);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
