package com.wight.hawidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
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
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(24);
    private LinearLayout deviceList;
    private TextView deviceCount;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        deviceList = findViewById(R.id.device_list);
        deviceCount = findViewById(R.id.device_count);
        findViewById(R.id.add_device).setOnClickListener(view -> showAddDevice());
        // Reapply current RemoteViews so restored widgets cannot retain old click actions.
        HaFanWidgetProvider.requestRefresh(this);
        renderDeviceList();
    }

    private void renderDeviceList() {
        deviceList.removeAllViews();
        boolean hasDevice = false;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(false);
        int configuredCount = 0;
        for (int slot = 0; slot < WidgetPreferences.loadDeviceCount(this); slot++) {
            String url = WidgetPreferences.loadEspHomeUrl(this, slot);
            if (url.isEmpty()) continue;
            hasDevice = true;
            addDeviceCard(grid, configuredCount, slot,
                    WidgetPreferences.loadFanName(this, slot), url);
            configuredCount++;
        }
        if (hasDevice) deviceList.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        deviceCount.setText(configuredCount + " 台");
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

    private void addDeviceCard(GridLayout grid, int index, int slot, String name, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.settings_card_background);
        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0;
        cardParams.height = dp(182);
        cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(index % 2 == 0 ? 0 : dp(6), 0,
                index % 2 == 0 ? dp(6) : 0, dp(12));
        grid.addView(card, cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        ImageView icon = new ImageView(this);
        icon.setContentDescription("风扇");
        icon.setImageResource(R.drawable.ic_fan_on);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackgroundResource(R.drawable.fan_title_badge);
        header.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1);
        detailsParams.leftMargin = dp(12);
        header.addView(details, detailsParams);
        TextView title = text(name.isEmpty() ? "未命名风扇" : name, 17, Color.rgb(15, 23, 42));
        title.setSingleLine(true);
        details.addView(title);
        TextView address = text("局域网 · " + url.replace("http://", "").replace("https://", ""),
                12, Color.rgb(100, 116, 139));
        address.setSingleLine(true);
        details.addView(address);

        Switch control = new Switch(this);
        control.setContentDescription("开关");
        control.setShowText(false);
        header.addView(control, new LinearLayout.LayoutParams(dp(52), dp(48)));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, dp(30));
        footerParams.topMargin = dp(10);
        card.addView(footer, footerParams);
        TextView status = text("正在读取设备状态…", 12, Color.rgb(100, 116, 139));
        footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        TextView action = text("点击添加挂件", 12, Color.rgb(37, 99, 235));
        footer.addView(action, new LinearLayout.LayoutParams(-2, -2));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4));
        progressParams.topMargin = dp(4);
        card.addView(progress, progressParams);

        control.setOnClickListener(view -> toggleFromHome(slot, control, status, progress));
        refreshHomeState(slot, control, status, progress);
        card.setOnLongClickListener(view -> { showEditDevice(slot); return true; });
        card.setOnClickListener(view -> pinDevice(slot));
        card.setContentDescription("配置 " + (name.isEmpty() ? "未命名风扇" : name) + " 挂件");
    }

    private void refreshHomeState(int slot, Switch control, TextView status, ProgressBar progress) {
        scanExecutor.execute(() -> {
            try {
                EspHomeClient.FanState state = EspHomeClient.fetchFanState(this, slot);
                runOnUiThread(() -> applyHomeState(control, status, progress, state));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    control.setEnabled(false);
                    progress.setProgress(0);
                    status.setText("设备离线");
                    status.setTextColor(Color.rgb(148, 163, 184));
                });
            }
        });
    }

    private void applyHomeState(Switch control, TextView status, ProgressBar progress,
                                EspHomeClient.FanState state) {
        control.setEnabled(state.available);
        control.setChecked(state.available && state.on);
        progress.setProgress(state.available && state.percentage >= 0 ? state.percentage : 0);
        if (!state.available) {
            status.setText("设备离线");
            status.setTextColor(Color.rgb(148, 163, 184));
        } else if (state.on) {
            String speed = state.percentage >= 0 ? " · 风速 " + state.percentage + "%" : "";
            status.setText("已开启" + speed);
            status.setTextColor(Color.rgb(22, 163, 74));
        } else {
            status.setText("已关闭 · 可直接控制");
            status.setTextColor(Color.rgb(100, 116, 139));
        }
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void toggleFromHome(int slot, Switch control, TextView status, ProgressBar progress) {
        boolean requestedState = control.isChecked();
        control.setAlpha(0.45f);
        control.setEnabled(false);
        scanExecutor.execute(() -> {
            try {
                EspHomeClient.toggleFan(this, slot);
                EspHomeClient.FanState state = EspHomeClient.fetchFanState(this, slot);
                runOnUiThread(() -> {
                    control.setAlpha(1f);
                    applyHomeState(control, status, progress, state);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    control.setAlpha(1f);
                    control.setChecked(!requestedState);
                    control.setEnabled(true);
                    Toast.makeText(this, "设备控制失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showAddDevice() {
        int slot = WidgetPreferences.loadDeviceCount(this);
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
                pinDevice(slot);
            });
            scan.setOnClickListener(view -> scanDevices(scan, results, name, address));
        });
        dialog.show();
    }

    private void pinDevice(int deviceId) {
        scanExecutor.execute(() -> {
            try {
                EspHomeClient.FanState state = EspHomeClient.fetchFanState(this, deviceId);
                Class<?> provider = state.speedCount == 3
                        ? DiscreteFanWidgetProvider.class : HaFanWidgetProvider.class;
                AppWidgetManager manager = AppWidgetManager.getInstance(this);
                if (!manager.isRequestPinAppWidgetSupported()) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "当前桌面不支持自动添加挂件", Toast.LENGTH_SHORT).show());
                    return;
                }
                Intent callback = new Intent(this, WidgetPinReceiver.class)
                        .putExtra(WidgetPinReceiver.EXTRA_DEVICE_ID, deviceId);
                PendingIntent success = PendingIntent.getBroadcast(this, 5000 + deviceId, callback,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                manager.requestPinAppWidget(new ComponentName(this, provider), null,
                        success);
            } catch (Exception exception) {
                runOnUiThread(() -> Toast.makeText(this,
                        "无法读取设备调速能力", Toast.LENGTH_SHORT).show());
            }
        });
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

    private String normalizeUrl(String input) { String value = input.trim(); if (value.equals("http://") || value.equals("https://")) return ""; if (!value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value; while (value.endsWith("/")) value = value.substring(0, value.length() - 1); return value; }
    private static final class Device { final int host; final String url, name, features; final boolean fan; Device(int host, String url, String name, String features, boolean fan) { this.host = host; this.url = url; this.name = name; this.features = features; this.fan = fan; } }
}
