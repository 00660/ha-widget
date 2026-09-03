package com.wight.hawidget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class CaptivePortalClient {
    static final String DEFAULT_URL = "http://192.168.4.1";
    private static final int TIMEOUT_MILLIS = 4000;

    private CaptivePortalClient() {
    }

    static PortalState fetchState(String baseUrl) throws IOException {
        String normalized = normalize(baseUrl);
        HttpURLConnection connection = open(normalized + "/config.json");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            JSONObject json = new JSONObject(readBody(connection));
            JSONArray aps = json.optJSONArray("aps");
            List<AccessPoint> networks = new ArrayList<>();
            if (aps != null) {
                for (int index = 0; index < aps.length(); index++) {
                    JSONObject ap = aps.optJSONObject(index);
                    if (ap == null) continue;
                    String ssid = ap.optString("ssid", "").trim();
                    if (ssid.isEmpty()) continue;
                    networks.add(new AccessPoint(ssid, ap.optInt("rssi", -100), ap.optInt("lock", 0) != 0));
                }
            }
            Collections.sort(networks);
            return new PortalState(json.optString("mac", ""), json.optString("name", ""), networks);
        } finally {
            connection.disconnect();
        }
    }

    static void saveWiFi(String baseUrl, String ssid, String password) throws IOException {
        if (ssid == null || ssid.trim().isEmpty()) throw new IOException("SSID missing");
        String normalized = normalize(baseUrl);
        String query = "ssid=" + encode(ssid) + "&psk=" + encode(password == null ? "" : password);
        HttpURLConnection connection = open(normalized + "/wifisave?" + query);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static String readBody(HttpURLConnection connection) throws IOException {
        try (InputStream input = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    private static String normalize(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.isEmpty()) return DEFAULT_URL;
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    static final class PortalState {
        final String mac;
        final String name;
        final List<AccessPoint> networks;

        PortalState(String mac, String name, List<AccessPoint> networks) {
            this.mac = mac == null ? "" : mac;
            this.name = name == null ? "" : name;
            this.networks = networks == null ? Collections.<AccessPoint>emptyList() : networks;
        }
    }

    static final class AccessPoint implements Comparable<AccessPoint> {
        final String ssid;
        final int rssi;
        final boolean secure;

        AccessPoint(String ssid, int rssi, boolean secure) {
            this.ssid = ssid;
            this.rssi = rssi;
            this.secure = secure;
        }

        @Override
        public int compareTo(AccessPoint other) {
            return Integer.compare(other.rssi, rssi);
        }
    }
}