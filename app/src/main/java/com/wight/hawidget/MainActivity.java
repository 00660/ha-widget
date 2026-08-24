package com.wight.hawidget;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
    private static final String ALL_ROOMS = "全部";
    private static final String[] ROOMS = {"未分配", "客厅", "卧室", "厨房", "卫生间"};
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(24);
    private LinearLayout deviceList;
    private TextView deviceCount;
    private TextView homeSummary;
    private String selectedRoom = ALL_ROOMS;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        deviceList = findViewById(R.id.device_list);
        deviceCount = findViewById(R.id.device_count);
        homeSummary = findViewById(R.id.home_summary);
        findViewById(R.id.add_device).setOnClickListener(view -> showAddDevice());
        findViewById(R.id.room_all).setOnClickListener(view -> setRoomFilter(ALL_ROOMS));
        findViewById(R.id.room_living).setOnClickListener(view -> setRoomFilter("客厅"));
        findViewById(R.id.room_bedroom).setOnClickListener(view -> setRoomFilter("卧室"));
        findViewById(R.id.room_kitchen).setOnClickListener(view -> setRoomFilter("厨房"));
        findViewById(R.id.room_bathroom).setOnClickListener(view -> setRoomFilter("卫生间"));
        findViewById(R.id.room_manage).setOnClickListener(view -> showRoomManager());
        findViewById(R.id.nav_home).setOnClickListener(view -> setRoomFilter(ALL_ROOMS));
        findViewById(R.id.nav_scenes).setOnClickListener(view -> showSceneManager());
        findViewById(R.id.nav_rooms).setOnClickListener(view -> showRoomManager());
        findViewById(R.id.nav_profile).setOnClickListener(view -> showAddDevice());
        // Reapply current RemoteViews so restored widgets cannot retain old click actions.
        HaFanWidgetProvider.requestRefresh(this);
        updateRoomTabColors();
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
            String room = WidgetPreferences.loadRoom(this, slot);
            if (!ALL_ROOMS.equals(selectedRoom) && !selectedRoom.equals(room)) continue;
            hasDevice = true;
            addDeviceCard(grid, configuredCount, slot,
                    WidgetPreferences.loadFanName(this, slot), url, room);
            configuredCount++;
        }
        if (hasDevice) deviceList.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        deviceCount.setText(configuredCount + " 台");
        homeSummary.setText(ALL_ROOMS.equals(selectedRoom)
                ? "ESPHome 局域网设备 · 全部房间"
                : "ESPHome 局域网设备 · " + selectedRoom);
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

    private void addDeviceCard(GridLayout grid, int index, int slot, String name, String url, String room) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.settings_card_background);
        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0;
        cardParams.height = -2;
        cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(index % 2 == 0 ? 0 : dp(6), 0,
                index % 2 == 0 ? dp(6) : 0, dp(12));
        grid.addView(card, cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
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
        TextView title = text(name.isEmpty() ? "未命名风扇" : name, 16, Color.rgb(15, 23, 42));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(title);
        TextView address = text("局域网 · " + url.replace("http://", "").replace("https://", ""),
                12, Color.rgb(100, 116, 139));
        address.setSingleLine(true);
        address.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(address);
        TextView roomTag = text(room, 11, Color.rgb(37, 99, 235));
        roomTag.setSingleLine(true);
        roomTag.setOnClickListener(view -> showRoomChooser(slot));
        details.addView(roomTag);

        Switch control = new Switch(this);
        control.setContentDescription("开关");
        control.setShowText(false);
        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.addView(controlRow, new LinearLayout.LayoutParams(-1, dp(34)));
        controlRow.addView(control, new LinearLayout.LayoutParams(dp(52), dp(34)));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, dp(30));
        card.addView(footer, footerParams);
        TextView status = text("正在读取设备状态…", 12, Color.rgb(100, 116, 139));
        footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        TextView action = text("添加挂件", 12, Color.rgb(37, 99, 235));
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

    private void setRoomFilter(String room) {
        selectedRoom = room;
        updateRoomTabColors();
        renderDeviceList();
    }

    private void updateRoomTabColors() {
        int active = Color.rgb(15, 23, 42);
        int inactive = Color.rgb(100, 116, 139);
        updateRoomTab((TextView) findViewById(R.id.room_all), ALL_ROOMS, active, inactive);
        updateRoomTab((TextView) findViewById(R.id.room_living), "客厅", active, inactive);
        updateRoomTab((TextView) findViewById(R.id.room_bedroom), "卧室", active, inactive);
        updateRoomTab((TextView) findViewById(R.id.room_kitchen), "厨房", active, inactive);
        updateRoomTab((TextView) findViewById(R.id.room_bathroom), "卫生间", active, inactive);
    }

    private void updateRoomTab(TextView tab, String room, int active, int inactive) {
        boolean selected = room.equals(selectedRoom);
        tab.setTextColor(selected ? active : inactive);
        tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tab.setBackgroundResource(selected ? R.drawable.home_tab_selected : android.R.color.transparent);
    }

    private void showRoomChooser(int slot) {
        String current = WidgetPreferences.loadRoom(this, slot);
        LinearLayout panel = panel("分配房间");
        panel.addView(text("选择后立即保存到设备", 13, Color.rgb(100, 116, 139)),
                new LinearLayout.LayoutParams(-1, dp(36)));
        Dialog dialog = panelDialog(panel);
        for (String room : ROOMS) {
            TextView row = panelRow(room, room.equals(current));
            row.setOnClickListener(view -> {
                WidgetPreferences.saveRoom(this, slot, room);
                dialog.dismiss();
                updateRoomTabColors();
                renderDeviceList();
            });
            panel.addView(row, rowParams());
        }
        panel.addView(actionText("取消", Color.rgb(37, 99, 235), false), actionParams());
        ((TextView) panel.getChildAt(panel.getChildCount() - 1)).setOnClickListener(view -> dialog.dismiss());
        showPanel(dialog);
    }

    private void showRoomManager() {
        LinearLayout panel = panel("房间");
        panel.addView(text("选择房间后，首页只显示该房间的设备", 13, Color.rgb(100, 116, 139)),
                new LinearLayout.LayoutParams(-1, dp(42)));
        String[] roomOptions = {ALL_ROOMS, "未分配", "客厅", "卧室", "厨房", "卫生间"};
        Dialog dialog = panelDialog(panel);
        for (String room : roomOptions) {
            int count = countDevices(room);
            TextView row = panelRow(room + "    " + count + " 台设备", room.equals(selectedRoom));
            row.setOnClickListener(view -> {
                setRoomFilter(room);
                dialog.dismiss();
            });
            panel.addView(row, rowParams());
        }
        TextView close = actionText("关闭", Color.rgb(37, 99, 235), false);
        close.setOnClickListener(view -> dialog.dismiss());
        panel.addView(close, actionParams());
        showPanel(dialog);
    }

    private int countDevices(String room) {
        int count = 0;
        for (int slot = 0; slot < WidgetPreferences.loadDeviceCount(this); slot++) {
            if (WidgetPreferences.loadEspHomeUrl(this, slot).isEmpty()) continue;
            if (ALL_ROOMS.equals(room) || room.equals(WidgetPreferences.loadRoom(this, slot))) count++;
        }
        return count;
    }

    private void showSceneManager() {
        LinearLayout panel = panel("场景");
        panel.addView(text("批量控制所有已配置风扇", 13, Color.rgb(100, 116, 139)),
                new LinearLayout.LayoutParams(-1, dp(36)));
        Dialog dialog = panelDialog(panel);
        TextView home = panelRow("回家模式    开启所有风扇", false);
        home.setOnClickListener(view -> { dialog.dismiss(); applyScene(true); });
        panel.addView(home, rowParams());
        TextView away = panelRow("离家模式    关闭所有风扇", false);
        away.setOnClickListener(view -> { dialog.dismiss(); applyScene(false); });
        panel.addView(away, rowParams());
        TextView close = actionText("取消", Color.rgb(37, 99, 235), false);
        close.setOnClickListener(view -> dialog.dismiss());
        panel.addView(close, actionParams());
        showPanel(dialog);
    }

    private void applyScene(boolean targetOn) {
        scanExecutor.execute(() -> {
            for (int slot = 0; slot < WidgetPreferences.loadDeviceCount(this); slot++) {
                if (WidgetPreferences.loadEspHomeUrl(this, slot).isEmpty()) continue;
                try {
                    EspHomeClient.FanState state = EspHomeClient.fetchFanState(this, slot);
                    if (state.available && state.on != targetOn) EspHomeClient.toggleFan(this, slot);
                } catch (Exception ignored) {
                    // A single offline device must not prevent the scene from reaching others.
                }
            }
            runOnUiThread(this::renderDeviceList);
        });
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
            String speed = state.percentage >= 0 ? " " + state.percentage + "%" : "";
            status.setText("开启" + speed);
            status.setTextColor(Color.rgb(22, 163, 74));
        } else {
            status.setText("已关闭");
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

    private LinearLayout panel(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(14));
        panel.setBackgroundResource(R.drawable.settings_card_background);
        TextView heading = text(title, 22, Color.rgb(15, 23, 42));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, dp(42)));
        return panel;
    }

    private Dialog panelDialog(View content) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private void showPanel(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.38f;
        attributes.gravity = Gravity.CENTER;
        window.setAttributes(attributes);
        int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private TextView panelRow(String label, boolean selected) {
        TextView row = text(label, 15, selected ? Color.rgb(37, 99, 235) : Color.rgb(15, 23, 42));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setMinHeight(dp(48));
        row.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        row.setBackgroundResource(selected
                ? R.drawable.fan_control_natural_active : R.drawable.settings_input_background);
        return row;
    }

    private TextView actionText(String label, int color, boolean filled) {
        TextView action = text(label, 15, color);
        action.setGravity(Gravity.CENTER);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setPadding(dp(16), 0, dp(16), 0);
        action.setBackgroundResource(filled
                ? R.drawable.fan_control_primary : R.drawable.fan_control_secondary);
        return action;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.bottomMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(46));
        params.leftMargin = dp(8);
        params.topMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.bottomMargin = dp(10);
        return params;
    }

    private void renderRoomChoices(LinearLayout container, String[] selectedRoom) {
        container.removeAllViews();
        for (String room : ROOMS) {
            TextView chip = panelRow(room, room.equals(selectedRoom[0]));
            chip.setTextSize(12);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(4), 0, dp(4), 0);
            chip.setOnClickListener(view -> {
                selectedRoom[0] = room;
                renderRoomChoices(container, selectedRoom);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
            params.leftMargin = dp(2);
            params.rightMargin = dp(2);
            container.addView(chip, params);
        }
    }

    private void showAddDevice() {
        int slot = WidgetPreferences.loadDeviceCount(this);
        showDeviceDialog(slot, "添加设备");
    }

    private void showEditDevice(int slot) { showDeviceDialog(slot, "编辑设备"); }

    private void showDeviceDialog(int slot, String title) {
        String[] selectedRoom = {WidgetPreferences.loadRoom(this, slot)};
        LinearLayout panel = panel(title);
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, dp(4), 0, 0);
        EditText name = input("设备名称", WidgetPreferences.loadFanName(this, slot), InputType.TYPE_CLASS_TEXT);
        EditText address = input("ESPHome 地址", WidgetPreferences.loadEspHomeUrl(this, slot), InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(name, fieldParams());
        form.addView(address, fieldParams());
        TextView roomLabel = text("所属房间", 13, Color.rgb(51, 65, 85));
        roomLabel.setPadding(0, dp(12), 0, dp(8));
        form.addView(roomLabel, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout roomPicker = new LinearLayout(this);
        roomPicker.setOrientation(LinearLayout.HORIZONTAL);
        roomPicker.setGravity(Gravity.CENTER_VERTICAL);
        form.addView(roomPicker, new LinearLayout.LayoutParams(-1, dp(44)));
        renderRoomChoices(roomPicker, selectedRoom);
        TextView scan = actionText("扫描局域网 ESPHome 设备", Color.rgb(37, 99, 235), true);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(-1, dp(46));
        scanParams.bottomMargin = dp(10);
        form.addView(scan, scanParams);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        form.addView(results, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = actionText("取消", Color.rgb(100, 116, 139), false);
        TextView save = actionText("保存", Color.rgb(37, 99, 235), false);
        actions.addView(cancel, actionParams());
        actions.addView(save, actionParams());
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(56)));

        Dialog dialog = panelDialog(panel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        save.setOnClickListener(view -> {
            String url = normalizeUrl(address.getText().toString());
            if (url.isEmpty()) { address.setError("请输入 ESPHome 地址"); return; }
            WidgetPreferences.saveDevice(this, slot, url, name.getText().toString().trim());
            WidgetPreferences.saveRoom(this, slot, selectedRoom[0]);
            HaFanWidgetProvider.requestRefresh(this);
            dialog.dismiss();
            renderDeviceList();
            pinDevice(slot);
        });
        scan.setOnClickListener(view -> scanDevices(scan, results, name, address));
        showPanel(dialog);
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
        input.setTextColor(Color.rgb(15, 23, 42));
        input.setHintTextColor(Color.rgb(100, 116, 139));
        input.setBackgroundResource(R.drawable.settings_input_background);
        input.setPadding(dp(14), 0, dp(14), 0);
        return input;
    }

    private void scanDevices(TextView scan, LinearLayout results, EditText name, EditText address) {
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
                    TextView choice = panelRow(device.name + "  " + device.features + "\n" + device.url, false);
                    choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    choice.setEnabled(device.fan);
                    if (device.fan) {
                        choice.setOnClickListener(view -> { name.setText(device.name); address.setText(device.url); results.removeAllViews(); });
                    }
                    results.addView(choice, rowParams());
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
