package com.wight.hawidget;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EspHomeWebClient {
    private static final String BASE_URL = "http://192.168.2.64";
    private static final String FAN_NAME = "风扇";
    private static final String LOCK_NAME = "童锁";
    private static final int TIMEOUT_MILLIS = 3000;
    private static final Pattern FAN_STATE = Pattern.compile("\\\"state\\\":\\\"(ON|OFF)\\\"");
    private static final Pattern SPEED_LEVEL = Pattern.compile("\\\"speed_level\\\":(\\d+)");
    private static final Pattern SPEED_COUNT = Pattern.compile("\\\"speed_count\\\":(\\d+)");
    private static final Pattern OSCILLATION = Pattern.compile("\\\"oscillation\\\":(true|false)");

    private EspHomeWebClient() {
    }

    static EspHomeClient.FanState fetchFanState(Context context) throws IOException {
        String baseUrl = WidgetPreferences.loadEspHomeUrl(context);
        String fanName = WidgetPreferences.loadFanName(context);
        HttpURLConnection connection = open(baseUrl + "/events");
        connection.setRequestProperty("Accept", "text/event-stream");
        try (InputStream input = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: {\"name_id\":\"fan/" + fanName)) {
                    return parseFanState(line.substring(6));
                }
            }
            throw new IOException("ESPHome fan state not found");
        } finally {
            connection.disconnect();
        }
    }

    static void toggleFan(Context context) throws IOException {
        EspHomeClient.FanState state = fetchFanState(context);
        postFan(context, state.on ? "turn_off" : "turn_on", null);
    }

    static void setFanPercentage(Context context, int percentage) throws IOException {
        int clamped = Math.max(0, Math.min(100, percentage));
        if (clamped == 0) {
            postFan(context, "turn_off", null);
            return;
        }
        int level = clamped <= 33 ? 1 : clamped <= 66 ? 2 : 3;
        postFan(context, "turn_on", "speed_level=" + level);
    }

    private static void postFan(Context context, String action, String query) throws IOException {
        String url = WidgetPreferences.loadEspHomeUrl(context) + "/fan/"
                + encode(WidgetPreferences.loadFanName(context)) + "/" + action;
        if (query != null) {
            url += "?" + query;
        }
        HttpURLConnection connection = open(url);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(0);
        try {
            connection.getInputStream().close();
        } finally {
            connection.disconnect();
        }
    }

    private static EspHomeClient.FanState parseFanState(String json) {
        boolean on = match(FAN_STATE, json, "OFF").equals("ON");
        int level = Integer.parseInt(match(SPEED_LEVEL, json, "0"));
        int count = Integer.parseInt(match(SPEED_COUNT, json, "0"));
        boolean oscillation = Boolean.parseBoolean(match(OSCILLATION, json, "false"));
        int percentage = count == 3 ? (level >= 3 ? 100 : level * 33) : level;
        if (percentage > 100) percentage = 100;
        return new EspHomeClient.FanState(on, true, percentage, "", count, oscillation, false);
    }

    private static String match(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        return connection;
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }
}
