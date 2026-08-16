package com.wight.hawidget;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class HaClient {
    private static final int TIMEOUT_MILLIS = 3000;

    private HaClient() {
    }

    static String fetchState(HaSettings settings, String entityId) throws IOException, JSONException {
        String body = request(settings, "GET", "/api/states/" + Uri.encode(entityId), null);
        return new JSONObject(body).optString("state", "unknown");
    }

    static void toggle(HaSettings settings, String domain, String entityId) throws IOException {
        try {
            JSONObject request = new JSONObject();
            request.put("entity_id", entityId);
            request(settings, "POST", "/api/services/" + domain + "/toggle", request.toString());
        } catch (JSONException exception) {
            throw new IOException("Could not create Home Assistant request", exception);
        }
    }

    private static String request(HaSettings settings, String method, String path, String requestBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(settings.baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Authorization", "Bearer " + settings.token);
        connection.setRequestProperty("Accept", "application/json");

        if (requestBody != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = requestBody.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int responseCode = connection.getResponseCode();
        InputStream stream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String response = readResponse(stream);
        connection.disconnect();

        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("Home Assistant returned HTTP " + responseCode);
        }
        return response;
    }

    private static String readResponse(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }
}
