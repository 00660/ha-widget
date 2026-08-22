package com.wight.hawidget;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final Pattern FAN_EVENT = Pattern.compile("\\\"(?:name_id\\\":\\\"fan/([^\\\"]+)|id\\\":\\\"fan-([^\\\"]+))\\\".*?\\\"name\\\":\\\"([^\\\"]*)");
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(24);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_main);
        EditText url = findViewById(R.id.esphome_url);
        EditText fanName = findViewById(R.id.fan_name);
        setUrlText(url, WidgetPreferences.loadEspHomeUrl(this));
        fanName.setText(WidgetPreferences.loadFanName(this));
        EditText[] urls = {url, findViewById(R.id.esphome_url_2), findViewById(R.id.esphome_url_3), findViewById(R.id.esphome_url_4), findViewById(R.id.esphome_url_5)};
        EditText[] names = {fanName, findViewById(R.id.fan_name_2), findViewById(R.id.fan_name_3), findViewById(R.id.fan_name_4), findViewById(R.id.fan_name_5)};
        for (int i = 1; i < urls.length; i++) {
            setUrlText(urls[i], WidgetPreferences.loadEspHomeUrl(this, i));
            names[i].setText(WidgetPreferences.loadFanName(this, i));
        }
        Button save = findViewById(R.id.save_device);
        Button scan = findViewById(R.id.scan_devices);
        ScrollView scroll = findViewById(R.id.settings_scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), 24 + imeBottom);
                View focused = view.findFocus();
                if (focused instanceof EditText && imeBottom > 0) {
                    focused.post(() -> {
                        android.graphics.Rect visible = new android.graphics.Rect();
                        focused.getWindowVisibleDisplayFrame(visible);
                        android.graphics.Rect fieldRect = new android.graphics.Rect();
                        focused.getGlobalVisibleRect(fieldRect);
                        int delta = fieldRect.bottom - visible.bottom + 24;
                        if (delta > 0) view.scrollBy(0, delta);
                    });
                }
                return insets;
            });
        }
        save.setOnClickListener(view -> {
            String value = url.getText().toString().trim();
            String name = fanName.getText().toString().trim();
            for (int i = 0; i < urls.length; i++) {
                value = urls[i].getText().toString().trim();
                if ("http://".equals(value) || "https://".equals(value)) value = "";
                else if (!value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
                while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
                if (!value.isEmpty() && (value.startsWith("http://") || value.startsWith("https://"))) {
                    String deviceName = names[i].getText().toString().trim();
                    WidgetPreferences.saveDevice(this, i, value, deviceName);
                }
            }
            if (!WidgetPreferences.loadEspHomeUrl(this).isEmpty()) {
                HaFanWidgetProvider.requestRefresh(this);
                Toast.makeText(this, "设备已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请输入有效的 ESPHome 地址", Toast.LENGTH_SHORT).show();
            }
        });
        scan.setOnClickListener(view -> scanDevices(scan, urls, names));
    }

    private void scanDevices(Button scan, EditText[] urls, EditText[] names) {
        scan.setEnabled(false);
        scan.setText("正在扫描局域网...");
        scanExecutor.execute(() -> {
            List<Device> found = new ArrayList<>();
            CountDownLatch pending = new CountDownLatch(254);
            for (int host = 1; host <= 254; host++) {
                final int address = host;
                scanExecutor.execute(() -> {
                    try {
                        Device device = probe(address);
                        if (device != null) synchronized (found) { found.add(device); }
                    } finally {
                        pending.countDown();
                    }
                });
            }
            try { pending.await(15, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            runOnUiThread(() -> {
                int count = Math.min(found.size(), urls.length);
                found.sort((a, b) -> Integer.compare(a.host, b.host));
                for (int i = 0; i < count; i++) {
                    Device device = found.get(i);
                    urls[i].setText(device.url);
                    names[i].setText(device.name.isEmpty() ? "风扇 " + device.host : device.name);
                    WidgetPreferences.saveDevice(this, i, device.url, names[i].getText().toString());
                }
                scan.setEnabled(true);
                scan.setText("扫描局域网风扇");
                Toast.makeText(this, "发现 " + count + " 台风扇", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private Device probe(int host) {
        String ip = "192.168.2." + host;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://" + ip + "/events").openConnection();
            connection.setConnectTimeout(450);
            connection.setReadTimeout(700);
            connection.setRequestProperty("Accept", "text/event-stream");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = FAN_EVENT.matcher(line);
                    if (matcher.find()) return new Device(host, "http://" + ip, matcher.group(3));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private static final class Device {
        final int host; final String url; final String name;
        Device(int host, String url, String name) { this.host = host; this.url = url; this.name = name; }
    }

    private void setUrlText(EditText field, String value) {
        field.setText(value == null || value.isEmpty() ? "http://" : value);
    }

}
