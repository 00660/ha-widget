package com.wight.hawidget;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.net.URISyntaxException;

final class HaSettings {
    private static final String PREFERENCES = "ha_widget_preferences";
    private static final String BASE_URL = "base_url";
    private static final String TOKEN = "token";
    private static final String LIGHT_ENTITY = "light_entity";
    private static final String FAN_ENTITY = "fan_entity";

    final String baseUrl;
    final String token;
    final String lightEntity;
    final String fanEntity;

    HaSettings(String baseUrl, String token, String lightEntity, String fanEntity) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.token = token == null ? "" : token.trim();
        this.lightEntity = lightEntity == null ? "" : lightEntity.trim();
        this.fanEntity = fanEntity == null ? "" : fanEntity.trim();
    }

    static HaSettings load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return new HaSettings(
                preferences.getString(BASE_URL, ""),
                preferences.getString(TOKEN, ""),
                preferences.getString(LIGHT_ENTITY, ""),
                preferences.getString(FAN_ENTITY, "")
        );
    }

    void save(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(BASE_URL, baseUrl)
                .putString(TOKEN, token)
                .putString(LIGHT_ENTITY, lightEntity)
                .putString(FAN_ENTITY, fanEntity)
                .apply();
    }

    boolean isComplete() {
        return isValidBaseUrl() && !token.isEmpty() && !lightEntity.isEmpty() && !fanEntity.isEmpty();
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
