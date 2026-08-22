package com.wight.hawidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final int SLOT_COUNT = 6;
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(24);
    private LinearLayout deviceList;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        deviceList = findViewById(R.id.device_list);
        findViewById(R.id.add_device).setOnClickListener(view -> showAddDevice());
        renderDeviceList();
    }

    private void renderDeviceList() {
        deviceList.removeAllViews();
        boolean hasDevice = false;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(false);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            String url = WidgetPreferences.loadEspHomeUrl(this, slot);
            if (url.isEmpty()) continue;
            hasDevice = true;
            addDeviceCard(grid, slot, WidgetPreferences.loadFanName(this, slot), url);
        }
        if (hasDevice) deviceList.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        if (!hasDevice) {
            TextView empty = new TextView(this);
            empty.setText("还没有设备\n点击右上角 + 扫描 ESPHome 设备");
            empty.setTextColor(Color.rgb(100, 116, 139));
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(24, 80, 24, 80);
            deviceList.addView(empty);
        }
    }

    private void addDeviceCard(GridLayout grid, int slot, String name, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(14, 18, 14, 16);
        card.setBackgroundResource(R.drawable.settings_card_background);
        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0;
        cardParams.height = (int) (164 * getResources().getDisplayMetrics().density);
        cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(slot % 2 == 0 ? 0 : 6, 0, slot % 2 == 0 ? 6 : 0, 12);
        grid.addView(card, cardParams);

        TextView icon = new TextView(this);
        icon.setText("风");
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(17);
        icon.setBackgroundResource(R.drawable.fan_control_primary);
        card.addView(icon, new LinearLayout.LayoutParams(50, 50));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, 0, 1);
        detailsParams.topMargin = 12;
        card.addView(details, detailsParams);
        TextView title = text(name.isEmpty() ? "未命名风扇" : name, 17, Color.rgb(15, 23, 42));
        title.setSingleLine(true);
        details.addView(title);
        TextView address = text(url.replace("http://", ""), 12, Color.rgb(100, 116, 139));
        address.setSingleLine(true);
        details.addView(address);

        ImageView control = new ImageView(this);
        control.setContentDescription("开关");
        control.setImageResource(R.drawable.ic_power);
        control.setPadding(12, 12, 12, 12);
        control.setBackgroundResource(R.drawable.fan_control_primary);
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(46, 46);
        controlParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(control, controlParams);
        control.setOnClickListener(view -> toggleFromHome(slot, control));
        card.setOnLongClickListener(view -> { showEditDevice(slot); return true; });
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void toggleFromHome(int slot, ImageView control) {
        control.setAlpha(0.45f);
        scanExecutor.execute(() -> {
            try {
                EspHomeClient.toggleFan(this, slot);
                runOnUiThread(() -> control.setAlpha(1f));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    control.setAlpha(1f);
                    Toast.makeText(this, "设备控制失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showAddDevice() {
        int slot = firstEmptySlot();
        if (slot < 0) {
            Toast.makeText(this, "已达到 6 个桌面挂件", Toast.LENGTH_SHORT).show();
            return;
        }
        showDeviceDialog(slot, "添加设备");
    }

    private void showEditDevice(int slot) { showDeviceDialog(slot, "编辑设备"); }

    private void showDeviceDialog(int slot, String title) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, 8, pad, 0);
        EditText name = input("设备名称", WidgetPreferences.loadFanName(this, slot), InputType.TYPE_CLASS_TEXT);
        EditText address = input("ESPHome 地址", WidgetPreferences.loadEspHomeUrl(this, slot), InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(name);
        form.addView(address);
        Button scan = new Button(this);
        scan.setText("扫描局域网 ESPHome 设备");
        scan.setAllCaps(false);
        form.addView(scan, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        form.addView(results);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String url = normalizeUrl(address.getText().toString());
                if (url.isEmpty()) { address.setError("请输入 ESPHome 地址"); return; }
                WidgetPreferences.saveDevice(this, slot, url, name.getText().toString().trim());
                HaFanWidgetProvider.requestRefresh(this);
                dialog.dismiss();
                renderDeviceList();
            });
            scan.setOnClickListener(view -> scanDevices(scan, results, name, address));
        });
        dialog.show();
    }

    private EditText input(String hint, String value, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setInputType(inputType);
        return input;
    }

    private void scanDevices(Button scan, LinearLayout results, EditText name, EditText address) {
        scan.setEnabled(false);
        scan.setText("正在扫描...");
        scanExecutor.execute(() -> {
            List<Device> found = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch pending = new CountDownLatch(254);
            for (int host = 1; host <= 254; host++) {
                final int currentHost = host;
                scanExecutor.execute(() -> { try { Device device = probe(currentHost); if (device != null) found.add(device); } finally { pending.countDown(); } });
            }
            try { pending.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            runOnUiThread(() -> {
                found.sort((left, right) -> Integer.compare(left.host, right.host));
                results.removeAllViews();
                for (Device device : found) {
                    Button choice = new Button(this);
                    choice.setAllCaps(false);
                    choice.setText(device.name + "  " + device.features + "\n" + device.url);
                    choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    choice.setEnabled(device.fan);
                    if (device.fan) {
                        choice.setOnClickListener(view -> { name.setText(device.name); address.setText(device.url); results.removeAllViews(); });
                    }
                    results.addView(choice, new LinearLayout.LayoutParams(-1, -2));
                }
                if (found.isEmpty()) results.addView(text("未发现公开 ESPHome Web Server 的设备", 14, Color.rgb(100, 116, 139)));
                scan.setEnabled(true);
                scan.setText("重新扫描局域网 ESPHome 设备");
            });
        });
    }

    private Device probe(int host) {
        HttpURLConnection connection = null;
        try {
            String url = "http://192.168.2." + host;
            connection = (HttpURLConnection) new URL(url + "/events").openConnection();
            connection.setConnectTimeout(450);
            connection.setReadTimeout(800);
            connection.setRequestProperty("Accept", "text/event-stream");
            String name = "ESPHome " + host;
            boolean fan = false, light = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int seen = 0;
                while ((line = reader.readLine()) != null && seen++ < 40) {
                    if (line.contains("\"domain\":\"fan\"") || line.contains("\"id\":\"fan-")) fan = true;
                    if (line.contains("\"domain\":\"light\"") || line.contains("\"id\":\"light-")) light = true;
                    java.util.regex.Matcher matcher = Pattern.compile("\\\"title\\\":\\\"([^\\\"]+)").matcher(line);
                    if (matcher.find()) name = matcher.group(1);
                    if (fan || light) break;
                }
            }
            if (!fan && !light) return null;
            String features = fan && light ? "风扇 · 灯光" : fan ? "风扇" : "灯光";
            return new Device(host, url, name, features, fan);
        } catch (Exception ignored) {
            return null;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private int firstEmptySlot() { for (int i = 0; i < SLOT_COUNT; i++) if (WidgetPreferences.loadEspHomeUrl(this, i).isEmpty()) return i; return -1; }
    private String normalizeUrl(String input) { String value = input.trim(); if (value.equals("http://") || value.equals("https://")) return ""; if (!value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value; while (value.endsWith("/")) value = value.substring(0, value.length() - 1); return value; }
    private static final class Device { final int host; final String url, name, features; final boolean fan; Device(int host, String url, String name, String features, boolean fan) { this.host = host; this.url = url; this.name = name; this.features = features; this.fan = fan; } }
}
