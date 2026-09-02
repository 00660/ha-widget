package com.wight.hawidget;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

final class EspHomeWebClient {
    private static final String BASE_URL = "http://192.168.2.64";
    private static final String FAN_NAME = "风扇";
    private static final String LOCK_NAME = "童锁";
    private static final int TIMEOUT_MILLIS = 3000;
    private static final Pattern FAN_STATE = Pattern.compile("\\\"state\\\":\\\"(ON|OFF)\\\"");
    private static final Pattern SPEED_LEVEL = Pattern.compile("\\\"speed_level\\\":(\\d+)");
    private static final Pattern SPEED_COUNT = Pattern.compile("\\\"speed_count\\\":(\\d+)");
    private static final Pattern OSCILLATION = Pattern.compile("\\\"oscillation\\\":(true|false)");
    private static final ConcurrentHashMap<Integer, String> FAN_ENDPOINTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, EspHomeClient.FanState> FAN_STATES = new ConcurrentHashMap<>();

    private EspHomeWebClient() {
    }

    static EspHomeClient.FanState fetchFanState(Context context) throws IOException {
        return fetchFanState(context, 0);
    }

    static EspHomeClient.FanState fetchFanState(Context context, int slot) throws IOException {
        String baseUrl = WidgetPreferences.loadEspHomeUrl(context, slot);
        HttpURLConnection connection = open(baseUrl + "/events");
        connection.setRequestProperty("Accept", "text/event-stream");
        try (InputStream input = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: {\"name_id\":\"fan/")
                        || line.contains("\"id\":\"fan-")) {
                    EspHomeClient.FanState state = parseFanState(line.substring(6));
                    // Fan control must not wait for an optional child-lock event stream.
                    // The lock endpoint is queried only when the lock button is pressed.
                    cacheState(slot, state);
                    return state;
                }
            }
            throw new IOException("ESPHome fan state not found");
        } finally {
            connection.disconnect();
        }
    }

    static void toggleFan(Context context) throws IOException {
        toggleFan(context, 0);
    }

    static void toggleFan(Context context, int slot) throws IOException {
        EspHomeClient.FanState state = FAN_STATES.get(slot);
        if (state == null) state = fetchFanState(context, slot);
        boolean nextOn = !state.on;
        postFan(context, slot, state.endpointName, nextOn ? "turn_on" : "turn_off", null);
        cacheState(slot, new EspHomeClient.FanState(nextOn, state.available, state.percentage,
                state.presetMode, state.speedCount, state.oscillation, state.childLock, state.endpointName));
    }

    static void setFanPercentage(Context context, int percentage) throws IOException {
        setFanPercentage(context, 0, percentage);
    }

    static void setFanPercentage(Context context, int slot, int percentage) throws IOException {
        int clamped = Math.max(0, Math.min(100, percentage));
        if (clamped == 0) {
            postFan(context, slot, discoverEndpoint(context, slot), "turn_off", null);
            return;
        }
        EspHomeClient.FanState state = FAN_STATES.get(slot);
        if (state == null) state = fetchFanState(context, slot);
        int count = Math.max(1, state.speedCount);
        int level = Math.max(1, Math.min(count, (int) Math.round(clamped * count / 100.0)));
        postFan(context, slot, state.endpointName, "turn_on", "speed_level=" + level);
        cacheState(slot, new EspHomeClient.FanState(true, state.available, clamped,
                state.presetMode, state.speedCount, state.oscillation, state.childLock, state.endpointName));
    }

    static void toggleChildLock(Context context, int slot) {
        WidgetPreferences.toggleChildLock(context, slot);
    }

    static EspHomeClient.DeviceState fetchDeviceState(Context context, int slot) throws IOException {
        String type = WidgetPreferences.loadDeviceType(context, slot);
        if ("fan".equals(type)) throw new IOException("fan uses the fan control path");
        String configuredEndpoint = WidgetPreferences.loadDeviceEndpoint(context, slot);
        HttpURLConnection connection = open(WidgetPreferences.loadEspHomeUrl(context, slot) + "/events");
        connection.setRequestProperty("Accept", "text/event-stream");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String json = line.substring(5).trim();
                if (!json.startsWith("{")) continue;
                try {
                    JSONObject event = new JSONObject(json);
                    String id = event.optString("id", "");
                    String domain = event.optString("domain", "");
                    String endpoint = eventEndpoint(event, type);
                    if (!type.equals(domain) && !id.startsWith(type + "-")) continue;
                    if (endpoint.isEmpty()) continue;
                    if ("switch".equals(type) && isChildLock(event)) continue;
                    if (!configuredEndpoint.isEmpty() && !configuredEndpoint.equals(endpoint)) continue;
                    return new EspHomeClient.DeviceState(eventOn(event), true, endpoint);
                } catch (Exception ignored) {
                    // Ignore non-JSON SSE lines and continue until a matching entity arrives.
                }
            }
        } finally {
            connection.disconnect();
        }
        throw new IOException("ESPHome entity state not found");
    }

    static void toggleDevice(Context context, int slot) throws IOException {
        String type = WidgetPreferences.loadDeviceType(context, slot);
        if ("fan".equals(type)) throw new IOException("fan uses the fan control path");
        if ("button".equals(type)) {
            postEntity(context, slot, type,
                    WidgetPreferences.loadDeviceEndpoint(context, slot), "press");
            return;
        }
        EspHomeClient.DeviceState state = fetchDeviceState(context, slot);
        if ("cover".equals(type)) {
            postEntity(context, slot, type, state.endpointName, state.on ? "close" : "open");
        } else {
            postEntity(context, slot, type, state.endpointName,
                    state.on ? "turn_off" : "turn_on");
        }
    }

    static EspHomeClient.EnvironmentState fetchEnvironment(Context context, int slot) throws IOException {
        return fetchEnvironmentUrl(context, WidgetPreferences.loadEspHomeUrl(context, slot));
    }

    static EspHomeClient.EnvironmentState fetchEnvironmentUrl(Context context, String baseUrl) throws IOException {
        if (baseUrl.isEmpty()) throw new IOException("ESPHome URL missing");
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/events").openConnection();
        connection.setConnectTimeout(500);
        connection.setReadTimeout(900);
        connection.setRequestProperty("Accept", "text/event-stream");
        String weather = "";
        String temperature = "";
        String humidity = "";
        String airQuality = "";
        try (InputStream input = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int seen = 0;
            while ((line = reader.readLine()) != null && seen++ < 80) {
                if (!line.startsWith("data:")) continue;
                String json = line.substring(5).trim();
                if (!json.startsWith("{")) continue;
                try {
                    JSONObject event = new JSONObject(json);
                    String domain = event.optString("domain", "");
                    if (!("sensor".equals(domain) || "text_sensor".equals(domain))) continue;
                    String name = (event.optString("name", "") + " "
                            + event.optString("name_id", "") + " "
                            + event.optString("icon", "")).toLowerCase(java.util.Locale.ROOT);
                    String unit = event.optString("uom", "").toLowerCase(java.util.Locale.ROOT);
                    String value = environmentValue(event);
                    if (value.isEmpty()) continue;
                    if (weather.isEmpty() && (name.contains("天气") || name.contains("weather")
                            || name.contains("condition"))) weather = value;
                    if (temperature.isEmpty() && (name.contains("温度") || name.contains("temperature")
                            || name.contains("temp"))) {
                        temperature = normalizeEnvironmentValue(value, "°C");
                    }
                    if (humidity.isEmpty() && (name.contains("湿度") || name.contains("humidity"))) {
                        humidity = normalizeEnvironmentValue(value, "%");
                    }
                    if (airQuality.isEmpty() && (name.contains("空气") || name.contains("air quality")
                            || name.contains("pm2.5") || name.contains("aqi")
                            || unit.contains("µg") || unit.contains("ug/m"))) airQuality = value;
                    if (!weather.isEmpty() && !temperature.isEmpty() && !humidity.isEmpty()
                            && !airQuality.isEmpty()) break;
                } catch (Exception ignored) {
                    // Ignore malformed or unrelated SSE events.
                }
            }
        } finally {
            connection.disconnect();
        }
        return new EspHomeClient.EnvironmentState(weather, temperature, humidity, airQuality);
    }

    private static String environmentValue(JSONObject event) {
        String state = event.optString("state", "").trim();
        if (!state.isEmpty()) return state;
        Object value = event.opt("value");
        return value == null || JSONObject.NULL.equals(value) ? "" : String.valueOf(value).trim();
    }

    private static String normalizeEnvironmentValue(String value, String suffix) {
        String normalized = value.trim().replace(" ", "");
        if (normalized.endsWith("°C") || normalized.endsWith("%")) return normalized;
        if (normalized.endsWith("掳C")) return normalized.substring(0, normalized.length() - 2) + "°C";
        return normalized + suffix;
    }

    private static LockState fetchLockState(Context context, int slot) throws IOException {
        String base = WidgetPreferences.loadEspHomeUrl(context, slot);
        HttpURLConnection connection = open(base + "/events");
        connection.setRequestProperty("Accept", "text/event-stream");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("\"name_id\":\"switch/童锁\"") || line.contains("\"name\":\"童锁\"")) {
                    boolean on = line.contains("\"state\":\"ON\"");
                    Matcher id = Pattern.compile("\\\"id\\\":\\\"switch-([^\\\"]+)").matcher(line);
                    if (id.find()) return new LockState(on, id.group(1));
                }
            }
        } finally { connection.disconnect(); }
        throw new IOException("child lock endpoint not found");
    }

    private static void postSwitch(Context context, int slot, String endpoint, String action) throws IOException {
        String url = WidgetPreferences.loadEspHomeUrl(context, slot) + "/switch/" + encode(endpoint) + "/" + action;
        HttpURLConnection connection = open(url);
        connection.setRequestMethod("POST");
        connection.setDoOutput(false);
        connection.setRequestProperty("Content-Length", "0");
        int code = connection.getResponseCode();
        connection.disconnect();
        if (code < 200 || code >= 300) throw new IOException("ESPHome HTTP " + code);
    }

    private static void postEntity(Context context, int slot, String type, String endpoint,
                                   String action) throws IOException {
        if (endpoint == null || endpoint.isEmpty()) throw new IOException("entity endpoint missing");
        String url = WidgetPreferences.loadEspHomeUrl(context, slot) + "/" + type + "/"
                + encode(endpoint) + "/" + action;
        HttpURLConnection connection = open(url);
        connection.setRequestMethod("POST");
        connection.setDoOutput(false);
        connection.setRequestProperty("Content-Length", "0");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("ESPHome HTTP " + code);
        } finally {
            connection.disconnect();
        }
    }

    private static String eventEndpoint(JSONObject event, String type) {
        String id = event.optString("id", "");
        String prefix = type + "-";
        if (id.startsWith(prefix)) return id.substring(prefix.length());
        String nameId = event.optString("name_id", "");
        prefix = type + "/";
        return nameId.startsWith(prefix) ? nameId.substring(prefix.length()) : "";
    }

    private static boolean eventOn(JSONObject event) {
        Object state = event.opt("state");
        if (state instanceof Boolean) return (Boolean) state;
        String value = String.valueOf(state);
        return "ON".equalsIgnoreCase(value) || "OPEN".equalsIgnoreCase(value)
                || "OPENING".equalsIgnoreCase(value);
    }

    private static boolean isChildLock(JSONObject event) {
        return "童锁".equals(event.optString("name", ""))
                || event.optString("name_id", "").endsWith("/童锁");
    }

    private static final class LockState { final boolean on; final String endpoint; LockState(boolean on, String endpoint) { this.on = on; this.endpoint = endpoint; } }

    private static void postFan(Context context, int slot, String endpoint, String action, String query) throws IOException {
        String url = WidgetPreferences.loadEspHomeUrl(context, slot) + "/fan/"
                + encode(endpoint) + "/" + action;
        if (query != null) {
            url += "?" + query;
        }
        HttpURLConnection connection = open(url);
        connection.setRequestMethod("POST");
        connection.setDoOutput(false);
        try {
            connection.setRequestProperty("Content-Length", "0");
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("ESPHome HTTP " + responseCode + " for " + url);
            }
            connection.getInputStream().close();
        } finally {
            connection.disconnect();
        }
    }

    private static String discoverEndpoint(Context context, int slot) throws IOException {
        String cached = FAN_ENDPOINTS.get(slot);
        if (cached != null && !cached.isEmpty()) return cached;
        return fetchFanState(context, slot).endpointName;
    }

    private static void cacheState(int slot, EspHomeClient.FanState state) {
        if (state != null) {
            FAN_STATES.put(slot, state);
            if (state.endpointName != null && !state.endpointName.isEmpty()) {
                FAN_ENDPOINTS.put(slot, state.endpointName);
            }
        }
    }

    private static EspHomeClient.FanState parseFanState(String json) {
        boolean on = match(FAN_STATE, json, "OFF").equals("ON");
        int level = Integer.parseInt(match(SPEED_LEVEL, json, "0"));
        int count = Integer.parseInt(match(SPEED_COUNT, json, "0"));
        boolean oscillation = Boolean.parseBoolean(match(OSCILLATION, json, "false"));
        int percentage = count == 3 ? (level >= 3 ? 100 : level * 33) : level;
        if (percentage > 100) percentage = 100;
        Matcher endpoint = Pattern.compile("\\\"id\\\":\\\"fan-([^\\\"]+)").matcher(json);
        String endpointName = endpoint.find() ? endpoint.group(1) : "";
        return new EspHomeClient.FanState(on, true, percentage, "", count, oscillation, false, endpointName);
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
