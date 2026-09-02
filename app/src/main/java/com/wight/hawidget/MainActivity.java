package com.wight.hawidget;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String ALL_ROOMS = "全部";
    private static final int LOCATION_REQUEST_CODE = 4101;
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(24);
    private LinearLayout deviceList;
    private LinearLayout roomTabs;
    private TextView environmentWeather;
    private TextView environmentTemperature;
    private TextView environmentHumidity;
    private TextView environmentAirQuality;
    private String selectedRoom = ALL_ROOMS;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        deviceList = findViewById(R.id.device_list);
        roomTabs = findViewById(R.id.room_tabs);
        environmentWeather = findViewById(R.id.environment_weather);
        environmentTemperature = findViewById(R.id.environment_temperature);
        environmentHumidity = findViewById(R.id.environment_humidity);
        environmentAirQuality = findViewById(R.id.environment_air_quality);
        findViewById(R.id.add_device).setOnClickListener(view -> showAddDevice());
        findViewById(R.id.nav_home).setOnClickListener(view -> setRoomFilter(ALL_ROOMS));
        findViewById(R.id.nav_scenes).setOnClickListener(view -> showSceneManager());
        findViewById(R.id.nav_rooms).setOnClickListener(view -> showRoomManager());
        findViewById(R.id.nav_profile).setOnClickListener(view -> showAddDevice());
        // Reapply current RemoteViews so restored widgets cannot retain old click actions.
        HaFanWidgetProvider.requestRefresh(this);
        EntityWidgetTileProvider.requestRefresh(this);
        updateRoomTabColors();
        refreshEnvironmentWithLocation();
        renderDeviceList();
    }

    private void refreshEnvironmentWithLocation() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, LOCATION_REQUEST_CODE);
            return;
        }
        refreshEnvironment();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE) refreshEnvironment();
    }

    private void refreshEnvironment() {
        scanExecutor.execute(() -> {
            EnvironmentValues publicValues = fetchPublicEnvironment();
            if (publicValues != null) {
                publishEnvironment(publicValues);
                return;
            }
            java.util.concurrent.CountDownLatch pending = new java.util.concurrent.CountDownLatch(254);
            java.util.concurrent.atomic.AtomicReference<String> weatherRef = new java.util.concurrent.atomic.AtomicReference<>("");
            java.util.concurrent.atomic.AtomicReference<String> temperatureRef = new java.util.concurrent.atomic.AtomicReference<>("");
            java.util.concurrent.atomic.AtomicReference<String> humidityRef = new java.util.concurrent.atomic.AtomicReference<>("");
            java.util.concurrent.atomic.AtomicReference<String> airQualityRef = new java.util.concurrent.atomic.AtomicReference<>("");
            for (int host = 1; host <= 254; host++) {
                final String baseUrl = "http://192.168.2." + host;
                scanExecutor.execute(() -> {
                    try {
                        EspHomeClient.EnvironmentState state = EspHomeClient.fetchEnvironmentUrl(this, baseUrl);
                        weatherRef.compareAndSet("", state.weather);
                        temperatureRef.compareAndSet("", state.temperature);
                        humidityRef.compareAndSet("", state.humidity);
                        airQualityRef.compareAndSet("", state.airQuality);
                    } catch (Exception ignored) {
                        // Most LAN hosts are not ESPHome devices; skip them silently.
                    } finally {
                        pending.countDown();
                    }
                });
            }
            try {
                pending.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            String weather = weatherRef.get();
            String temperature = temperatureRef.get();
            String humidity = humidityRef.get();
            String airQuality = airQualityRef.get();
            publishEnvironment(new EnvironmentValues(
                    weather.isEmpty() ? "--" : weather,
                    temperature.isEmpty() ? "--" : temperature,
                    humidity.isEmpty() ? "--" : humidity,
                    airQuality.isEmpty() ? "--" : airQuality));
        });
    }

    private void publishEnvironment(EnvironmentValues values) {
        runOnUiThread(() -> {
            environmentWeather.setText(values.weather);
            environmentTemperature.setText(values.temperature);
            environmentHumidity.setText(values.humidity);
            environmentAirQuality.setText(values.airQuality);
        });
    }

    private EnvironmentValues fetchPublicEnvironment() {
        Location location = lastKnownLocation();
        if (location == null) return null;
        try {
            String coordinates = "latitude=" + location.getLatitude()
                    + "&longitude=" + location.getLongitude();
            JSONObject weather = getJson("https://api.open-meteo.com/v1/forecast?"
                    + coordinates + "&current=temperature_2m,relative_humidity_2m,weather_code&timezone=auto");
            JSONObject current = weather.optJSONObject("current");
            if (current == null) return null;
            double temperature = current.optDouble("temperature_2m", Double.NaN);
            int humidity = current.optInt("relative_humidity_2m", -1);
            int weatherCode = current.optInt("weather_code", -1);
            if (Double.isNaN(temperature) || humidity < 0) return null;
            String airQuality = "--";
            try {
                JSONObject air = getJson("https://air-quality-api.open-meteo.com/v1/air-quality?"
                        + coordinates + "&current=us_aqi");
                JSONObject airCurrent = air.optJSONObject("current");
                if (airCurrent != null && airCurrent.has("us_aqi")
                        && airCurrent.optInt("us_aqi", -1) >= 0) {
                    airQuality = "AQI " + airCurrent.optInt("us_aqi", -1);
                }
            } catch (Exception ignored) {
                // Weather data remains useful when the optional air-quality request fails.
            }
            return new EnvironmentValues(weatherLabel(weatherCode),
                    String.format(java.util.Locale.US, "%.1f°C", temperature),
                    humidity + "%", airQuality);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Location lastKnownLocation() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return null;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) return null;
        Location best = null;
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            } catch (SecurityException ignored) {
                return null;
            }
        }
        if (best == null) {
            final Location[] received = new Location[1];
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    received[0] = location;
                    latch.countDown();
                }
            };
            try {
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, getMainLooper());
                } else if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, getMainLooper());
                }
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                best = received[0];
                manager.removeUpdates(listener);
            } catch (Exception ignored) {
                // Fall back to ESPHome when no provider can produce a fix.
            }
        }
        return best;
    }

    private JSONObject getJson(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(5000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    private String weatherLabel(int code) {
        if (code == 0) return "晴";
        if (code <= 3) return "多云";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 67) return "降雨";
        if (code >= 71 && code <= 77) return "降雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code <= 99) return "雷雨";
        return "--";
    }

    private static final class EnvironmentValues {
        final String weather;
        final String temperature;
        final String humidity;
        final String airQuality;

        EnvironmentValues(String weather, String temperature, String humidity, String airQuality) {
            this.weather = weather;
            this.temperature = temperature;
            this.humidity = humidity;
            this.airQuality = airQuality;
        }
    }

    private void renderDeviceList() {
        deviceList.removeAllViews();
        boolean hasDevice = false;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setUseDefaultMargins(false);
        int configuredCount = 0;
        for (int slot = 0; slot < WidgetPreferences.loadDeviceCount(this); slot++) {
            String url = WidgetPreferences.loadEspHomeUrl(this, slot);
            if (url.isEmpty()) continue;
            String room = WidgetPreferences.loadRoom(this, slot);
            if (!ALL_ROOMS.equals(selectedRoom) && !selectedRoom.equals(room)) continue;
            if ("environment".equals(WidgetPreferences.loadDeviceType(this, slot))) continue;
            hasDevice = true;
            String type = WidgetPreferences.loadDeviceType(this, slot);
            if ("fan".equals(type)) {
                addDeviceCard(grid, configuredCount, slot,
                        WidgetPreferences.loadFanName(this, slot), room);
            } else {
                addEntityCard(grid, configuredCount, slot,
                        WidgetPreferences.loadFanName(this, slot), room, type);
            }
            configuredCount++;
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

    private void addDeviceCard(GridLayout grid, int index, int slot, String name, String room) {
        DeviceTile tile = createDeviceTile(grid, index,
                name.isEmpty() ? "未命名风扇" : name, room, "fan");
        tile.card.setOnClickListener(view -> toggleFanTile(slot, tile));
        tile.card.setOnLongClickListener(view -> {
            showDeviceActions(slot, "fan");
            return true;
        });
        refreshFanTile(slot, tile);
    }

    private void addEntityCard(GridLayout grid, int index, int slot, String name,
                               String room, String type) {
        String displayName = name.isEmpty() ? "未命名" + entityLabel(type) : name;
        DeviceTile tile = createDeviceTile(grid, index, displayName, room, type);
        tile.card.setOnClickListener(view -> toggleEntityTile(slot, tile));
        tile.card.setOnLongClickListener(view -> {
            showDeviceActions(slot, type);
            return true;
        });
        refreshEntityTile(slot, tile);
    }

    private DeviceTile createDeviceTile(GridLayout grid, int index, String name,
                                        String room, String type) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(5), dp(3), dp(5), dp(2));
        card.setBackgroundResource(tileBackground(type, false));
        card.setForeground(getDrawable(R.drawable.home_device_card_ripple));
        card.setClickable(true);
        card.setFocusable(true);
        card.setClipToOutline(true);
        card.setElevation(dp(2));
        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        int contentWidth = getResources().getDisplayMetrics().widthPixels - dp(40);
        int tileWidth = Math.max(dp(76), (contentWidth - dp(24)) / 4);
        cardParams.width = tileWidth;
        cardParams.height = tileWidth;
        cardParams.columnSpec = GridLayout.spec(index % 4);
        cardParams.setMargins(index % 4 == 0 ? 0 : dp(4), 0,
                index % 4 == 3 ? 0 : dp(4), dp(9));
        grid.addView(card, cardParams);

        LinearLayout indicatorRow = new LinearLayout(this);
        indicatorRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        View stateDot = new View(this);
        indicatorRow.addView(stateDot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        card.addView(indicatorRow, new LinearLayout.LayoutParams(-1, dp(6)));

        ImageView icon = new ImageView(this);
        icon.setContentDescription(entityLabel(type));
        icon.setImageResource(entityIcon(type));
        icon.setPadding(dp(4), dp(3), dp(4), dp(3));
        card.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView title = text(name, 12, Color.rgb(39, 44, 50));
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(17));
        titleParams.topMargin = dp(2);
        card.addView(title, titleParams);

        TextView status = text("正在连接", 10, Color.rgb(125, 133, 142));
        status.setSingleLine(true);
        status.setIncludeFontPadding(false);
        status.setGravity(Gravity.CENTER);
        card.addView(status, new LinearLayout.LayoutParams(-1, dp(14)));

        DeviceTile tile = new DeviceTile(card, stateDot, icon, title, status, name, room, type);
        applyTileState(tile, false, false, -1);
        return tile;
    }

    private String entityLabel(String type) {
        if ("fan".equals(type)) return "风扇";
        if ("environment".equals(type)) return "温湿度传感器";
        if ("switch".equals(type)) return "开关";
        if ("button".equals(type)) return "无线按钮";
        if ("cover".equals(type)) return "窗帘";
        return "灯具";
    }

    private int entityIcon(String type) {
        if ("fan".equals(type)) return R.drawable.ic_fan_on;
        if ("switch".equals(type)) return R.drawable.ic_switch;
        if ("button".equals(type)) return R.drawable.ic_wireless_button;
        if ("cover".equals(type)) return R.drawable.ic_curtain;
        return R.drawable.ic_light;
    }

    private void refreshEntityTile(int slot, DeviceTile tile) {
        scanExecutor.execute(() -> {
            try {
                EspHomeClient.DeviceState state = EspHomeClient.fetchDeviceState(this, slot);
                runOnUiThread(() -> applyTileState(tile, state.available, state.on, -1));
            } catch (Exception ignored) {
                runOnUiThread(() -> applyTileState(tile, false, false, -1));
            }
        });
    }

    private void toggleEntityTile(int slot, DeviceTile tile) {
        if (tile.controlling) return;
        if (!tile.available) {
            Toast.makeText(this, "设备离线", Toast.LENGTH_SHORT).show();
            return;
        }
        tile.controlling = true;
        tile.card.setAlpha(0.72f);
        tile.status.setText("正在控制");
        scanExecutor.execute(() -> {
            try {
                EspHomeClient.toggleDevice(this, slot);
                EspHomeClient.DeviceState state = EspHomeClient.fetchDeviceState(this, slot);
                runOnUiThread(() -> {
                    tile.controlling = false;
                    tile.card.setAlpha(1f);
                    applyTileState(tile, state.available, state.on, -1);
                    if ("button".equals(tile.type)) tile.status.setText("已执行");
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    tile.controlling = false;
                    tile.card.setAlpha(1f);
                    applyTileState(tile, tile.available, tile.on, -1);
                    Toast.makeText(this, "设备控制失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showDeviceActions(int slot, String type) {
        String name = WidgetPreferences.loadFanName(this, slot);
        LinearLayout panel = panel(name.isEmpty() ? entityLabel(type) : name);
        panel.addView(text("选择设备操作", 13, Color.rgb(100, 116, 139)),
                new LinearLayout.LayoutParams(-1, dp(36)));
        Dialog dialog = panelDialog(panel);
        if (!"environment".equals(type)) {
            TextView widget = panelRow("添加到桌面", false);
            widget.setOnClickListener(view -> {
                dialog.dismiss();
                if ("fan".equals(type)) pinDevice(slot);
                else roomTabs.post(() -> showEntityWidgetChooser(slot, type));
            });
            panel.addView(widget, rowParams());
        }
        TextView room = panelRow("分配房间    " + WidgetPreferences.loadRoom(this, slot), false);
        room.setOnClickListener(view -> {
            dialog.dismiss();
            roomTabs.post(() -> showRoomChooser(slot));
        });
        panel.addView(room, rowParams());
        TextView edit = panelRow("编辑设备", false);
        edit.setOnClickListener(view -> {
            dialog.dismiss();
            roomTabs.post(() -> showEditDevice(slot));
        });
        panel.addView(edit, rowParams());
        TextView cancel = actionText("取消", Color.rgb(100, 116, 139), false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        panel.addView(cancel, actionParams());
        showPanel(dialog);
    }

    private void showEntityWidgetChooser(int slot, String type) {
        LinearLayout panel = panel("添加" + entityLabel(type) + "挂件");
        panel.addView(text("选择桌面卡片样式", 13, Color.rgb(100, 116, 139)),
                new LinearLayout.LayoutParams(-1, dp(36)));
        Dialog dialog = panelDialog(panel);
        TextView tile = panelRow("方卡    96 × 96", false);
        tile.setOnClickListener(view -> { dialog.dismiss(); pinEntityWidget(slot); });
        panel.addView(tile, rowParams());
        TextView cancel = actionText("取消", Color.rgb(100, 116, 139), false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        panel.addView(cancel, actionParams());
        showPanel(dialog);
    }

    private void pinEntityWidget(int slot) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (!manager.isRequestPinAppWidgetSupported()) {
            Toast.makeText(this, "当前桌面不支持自动添加挂件，请从桌面挂件列表添加", Toast.LENGTH_LONG).show();
            return;
        }
        Intent callback = new Intent(this, WidgetPinReceiver.class)
                .putExtra(WidgetPinReceiver.EXTRA_DEVICE_ID, slot);
        PendingIntent success = PendingIntent.getBroadcast(this, 7000 + slot,
                callback, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        manager.requestPinAppWidget(new ComponentName(this, EntityWidgetTileProvider.class), null, success);
    }

    private void setRoomFilter(String room) {
        selectedRoom = room;
        updateRoomTabColors();
        renderDeviceList();
    }

    private void updateRoomTabColors() {
        roomTabs.removeAllViews();
        int active = Color.rgb(15, 23, 42);
        int inactive = Color.rgb(100, 116, 139);
        addRoomTab(ALL_ROOMS, active, inactive);
        for (String room : WidgetPreferences.loadRooms(this)) addRoomTab(room, active, inactive);
        ImageView manage = new ImageView(this);
        manage.setContentDescription("添加或管理房间");
        manage.setImageResource(R.drawable.ic_nav_menu);
        manage.setPadding(dp(15), dp(15), dp(15), dp(15));
        manage.setOnClickListener(view -> showRoomManager());
        roomTabs.addView(manage, new LinearLayout.LayoutParams(dp(50), -1));
    }

    private void addRoomTab(String room, int active, int inactive) {
        TextView tab = text(room, 16, inactive);
        tab.setGravity(Gravity.CENTER);
        tab.setMinWidth(dp(room.length() > 3 ? 82 : 66));
        tab.setPadding(dp(12), 0, dp(12), 0);
        boolean selected = room.equals(selectedRoom);
        tab.setTextColor(selected ? active : inactive);
        tab.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setBackgroundResource(selected ? R.drawable.home_tab_selected : android.R.color.transparent);
        tab.setOnClickListener(view -> setRoomFilter(room));
        roomTabs.addView(tab, new LinearLayout.LayoutParams(-2, -1));
    }

    private void showRoomChooser(int slot) {
        String current = WidgetPreferences.loadRoom(this, slot);
        LinearLayout panel = panel("分配房间");
        panel.addView(text("选择后立即保存到设备", 13, Color.rgb(100, 116, 139)),
                new LinearLayout.LayoutParams(-1, dp(36)));
        Dialog dialog = panelDialog(panel);
        List<String> roomOptions = new ArrayList<>();
        roomOptions.add("未分配");
        roomOptions.addAll(WidgetPreferences.loadRooms(this));
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (String room : roomOptions) {
            TextView row = panelRow(room, room.equals(current));
            row.setOnClickListener(view -> {
                WidgetPreferences.saveRoom(this, slot, room);
                dialog.dismiss();
                updateRoomTabColors();
                renderDeviceList();
                EntityWidgetTileProvider.requestRefresh(this);
            });
            rows.addView(row, rowParams());
        }
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(dp(300), dp(roomOptions.size() * 58))));
        panel.addView(actionText("取消", Color.rgb(37, 99, 235), false), actionParams());
        ((TextView) panel.getChildAt(panel.getChildCount() - 1)).setOnClickListener(view -> dialog.dismiss());
        showPanel(dialog);
    }

    private void showRoomManager() {
        LinearLayout panel = panel("房间");
        panel.addView(text("选择房间后，首页只显示该房间的设备", 13, Color.rgb(100, 116, 139)),
                new LinearLayout.LayoutParams(-1, dp(42)));
        List<String> roomOptions = new ArrayList<>();
        roomOptions.add(ALL_ROOMS);
        roomOptions.add("未分配");
        roomOptions.addAll(WidgetPreferences.loadRooms(this));
        Dialog dialog = panelDialog(panel);
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (String room : roomOptions) {
            int count = countDevices(room);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = panelRow(room + "    " + count + " 台设备", room.equals(selectedRoom));
            label.setOnClickListener(view -> {
                setRoomFilter(room);
                dialog.dismiss();
            });
            row.addView(label, new LinearLayout.LayoutParams(0, -1, 1f));
            if (!ALL_ROOMS.equals(room) && !"未分配".equals(room)
                    && !WidgetPreferences.isBuiltInRoom(room)) {
                TextView edit = actionText("编辑", Color.rgb(37, 99, 235), false);
                edit.setContentDescription("编辑房间 " + room);
                edit.setOnClickListener(view -> {
                    dialog.dismiss();
                    roomTabs.post(() -> showEditRoom(room));
                });
                row.addView(edit, new LinearLayout.LayoutParams(dp(72), dp(50)));
            }
            LinearLayout.LayoutParams rowLayout = rowParams();
            rows.addView(row, rowLayout);
        }
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(dp(300), dp(roomOptions.size() * 58))));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView add = actionText("添加房间", Color.rgb(37, 99, 235), true);
        TextView close = actionText("关闭", Color.rgb(37, 99, 235), false);
        add.setOnClickListener(view -> {
            dialog.dismiss();
            roomTabs.post(this::showAddRoom);
        });
        close.setOnClickListener(view -> dialog.dismiss());
        actions.addView(add, actionParams());
        actions.addView(close, actionParams());
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(62)));
        showPanel(dialog);
    }

    private void showAddRoom() {
        LinearLayout panel = panel("添加房间");
        EditText name = input("房间名称", "", InputType.TYPE_CLASS_TEXT);
        panel.addView(name, fieldParams());
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = actionText("取消", Color.rgb(100, 116, 139), false);
        TextView save = actionText("添加", Color.rgb(37, 99, 235), true);
        actions.addView(cancel, actionParams());
        actions.addView(save, actionParams());
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(62)));
        Dialog dialog = panelDialog(panel);
        cancel.setOnClickListener(view -> dialog.dismiss());
        save.setOnClickListener(view -> {
            String room = name.getText().toString().trim();
            if (room.isEmpty()) {
                name.setError("请输入房间名称");
                return;
            }
            if (room.length() > 12) {
                name.setError("房间名称不能超过 12 个字");
                return;
            }
            if (!WidgetPreferences.addRoom(this, room)) {
                name.setError("房间已存在");
                return;
            }
            dialog.dismiss();
            setRoomFilter(room);
            Toast.makeText(this, "已添加房间：" + room, Toast.LENGTH_SHORT).show();
        });
        showPanel(dialog);
        name.requestFocus();
    }

    private void showEditRoom(String roomName) {
        LinearLayout panel = panel("编辑房间");
        EditText name = input("房间名称", roomName, InputType.TYPE_CLASS_TEXT);
        panel.addView(name, fieldParams());
        TextView hint = text("重命名会同步更新该房间下的设备", 13, Color.rgb(100, 116, 139));
        panel.addView(hint, new LinearLayout.LayoutParams(-1, dp(34)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView remove = actionText("删除", Color.rgb(220, 38, 38), false);
        TextView cancel = actionText("取消", Color.rgb(100, 116, 139), false);
        TextView save = actionText("保存", Color.rgb(37, 99, 235), true);
        actions.addView(remove, actionParams());
        actions.addView(cancel, actionParams());
        actions.addView(save, actionParams());
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(62)));
        Dialog dialog = panelDialog(panel);
        remove.setOnClickListener(view -> {
            if (!WidgetPreferences.removeRoom(this, roomName)) return;
            for (int slot = 0; slot < WidgetPreferences.loadDeviceCount(this); slot++) {
                if (roomName.equals(WidgetPreferences.loadRoom(this, slot))) {
                    WidgetPreferences.saveRoom(this, slot, "未分配");
                }
            }
            if (roomName.equals(selectedRoom)) selectedRoom = ALL_ROOMS;
            dialog.dismiss();
            updateRoomTabColors();
            renderDeviceList();
            EntityWidgetTileProvider.requestRefresh(this);
            Toast.makeText(this, "房间已删除，设备已移至未分配", Toast.LENGTH_SHORT).show();
        });
        cancel.setOnClickListener(view -> dialog.dismiss());
        save.setOnClickListener(view -> {
            String replacement = name.getText().toString().trim();
            if (replacement.length() > 12) {
                name.setError("房间名称不能超过 12 个字");
                return;
            }
            if (!WidgetPreferences.renameRoom(this, roomName, replacement)) {
                name.setError("名称为空、重复或不可用");
                return;
            }
            for (int slot = 0; slot < WidgetPreferences.loadDeviceCount(this); slot++) {
                if (roomName.equals(WidgetPreferences.loadRoom(this, slot))) {
                    WidgetPreferences.saveRoom(this, slot, replacement);
                }
            }
            if (roomName.equals(selectedRoom)) selectedRoom = replacement;
            dialog.dismiss();
            updateRoomTabColors();
            renderDeviceList();
            EntityWidgetTileProvider.requestRefresh(this);
            Toast.makeText(this, "房间已重命名", Toast.LENGTH_SHORT).show();
        });
        showPanel(dialog);
        name.requestFocus();
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

    private void refreshFanTile(int slot, DeviceTile tile) {
        scanExecutor.execute(() -> {
            try {
                EspHomeClient.FanState state = EspHomeClient.fetchFanState(this, slot);
                runOnUiThread(() -> applyTileState(tile, state.available, state.on, state.percentage));
            } catch (Exception exception) {
                runOnUiThread(() -> applyTileState(tile, false, false, -1));
            }
        });
    }

    private void applyTileState(DeviceTile tile, boolean available, boolean on, int percentage) {
        tile.available = available;
        tile.on = available && on;
        boolean active = tile.on && !"button".equals(tile.type);
        tile.card.setBackgroundResource(tileBackground(tile.type, active));
        tile.card.setAlpha(available ? 1f : 0.68f);

        String stateLabel;
        if (!available) {
            stateLabel = "设备离线";
        } else if ("button".equals(tile.type)) {
            stateLabel = "轻触执行";
        } else if ("cover".equals(tile.type)) {
            stateLabel = active ? "已打开" : "已关闭";
        } else if (active && "fan".equals(tile.type) && percentage >= 0) {
            stateLabel = "开启 · " + percentage + "%";
        } else {
            stateLabel = active ? "已开启" : "已关闭";
        }

        int activeText = activeTextColor(tile.type);
        int titleColor = active && "fan".equals(tile.type)
                ? Color.WHITE : Color.rgb(39, 44, 50);
        int statusColor = !available ? Color.rgb(125, 133, 142)
                : active || "button".equals(tile.type) ? activeText : Color.rgb(125, 133, 142);
        int dotColor = !available ? Color.rgb(174, 181, 188)
                : active && "fan".equals(tile.type) ? Color.WHITE
                : active || "button".equals(tile.type) ? activeText : Color.rgb(145, 151, 158);
        tile.title.setTextColor(titleColor);
        tile.status.setText(stateLabel);
        tile.status.setTextColor(statusColor);
        tile.stateDot.setBackground(statusDot(dotColor));
        tile.icon.clearColorFilter();
        if ("fan".equals(tile.type)) {
            tile.icon.setColorFilter(active ? Color.WHITE : Color.rgb(76, 140, 193));
        }
        tile.card.setContentDescription(tile.name + "，" + tile.room + "，" + stateLabel
                + "。轻触控制，长按更多操作");
    }

    private int tileBackground(String type, boolean active) {
        if ("button".equals(type)) return R.drawable.home_device_card_button;
        if (!active) return R.drawable.home_device_card_off;
        if ("fan".equals(type)) return R.drawable.home_device_card_fan_on;
        if ("light".equals(type)) return R.drawable.home_device_card_light_on;
        if ("cover".equals(type)) return R.drawable.home_device_card_cover_on;
        return R.drawable.home_device_card_switch_on;
    }

    private int activeTextColor(String type) {
        if ("fan".equals(type)) return Color.WHITE;
        if ("light".equals(type)) return Color.rgb(154, 98, 0);
        if ("cover".equals(type)) return Color.rgb(22, 116, 122);
        if ("button".equals(type)) return Color.rgb(128, 104, 70);
        return Color.rgb(23, 129, 93);
    }

    private GradientDrawable statusDot(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        return dot;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void toggleFanTile(int slot, DeviceTile tile) {
        if (tile.controlling) return;
        if (!tile.available) {
            Toast.makeText(this, "设备离线", Toast.LENGTH_SHORT).show();
            return;
        }
        tile.controlling = true;
        tile.card.setAlpha(0.72f);
        tile.status.setText("正在控制");
        scanExecutor.execute(() -> {
            try {
                EspHomeClient.toggleFan(this, slot);
                EspHomeClient.FanState state = EspHomeClient.fetchFanState(this, slot);
                runOnUiThread(() -> {
                    tile.controlling = false;
                    tile.card.setAlpha(1f);
                    applyTileState(tile, state.available, state.on, state.percentage);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    tile.controlling = false;
                    tile.card.setAlpha(1f);
                    applyTileState(tile, tile.available, tile.on, -1);
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
        TextView action = text(label, 15, filled ? Color.WHITE : color);
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
        List<String> roomOptions = new ArrayList<>();
        roomOptions.add("未分配");
        roomOptions.addAll(WidgetPreferences.loadRooms(this));
        for (String room : roomOptions) {
            TextView chip = panelRow(room, room.equals(selectedRoom[0]));
            chip.setTextSize(12);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(4), 0, dp(4), 0);
            chip.setOnClickListener(view -> {
                selectedRoom[0] = room;
                renderRoomChoices(container, selectedRoom);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(room.length() > 4 ? 96 : 82), -1);
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
        String[] selectedType = {WidgetPreferences.loadDeviceType(this, slot)};
        String[] selectedEndpoint = {WidgetPreferences.loadDeviceEndpoint(this, slot)};
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
        HorizontalScrollView roomScroll = new HorizontalScrollView(this);
        roomScroll.setHorizontalScrollBarEnabled(false);
        roomScroll.addView(roomPicker, new HorizontalScrollView.LayoutParams(-2, -1));
        form.addView(roomScroll, new LinearLayout.LayoutParams(-1, dp(44)));
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
            WidgetPreferences.saveDevice(this, slot, url, name.getText().toString().trim(),
                    selectedType[0], selectedEndpoint[0]);
            WidgetPreferences.saveRoom(this, slot, selectedRoom[0]);
            HaFanWidgetProvider.requestRefresh(this);
                EntityWidgetTileProvider.requestRefresh(this);
            dialog.dismiss();
            renderDeviceList();
            if ("fan".equals(selectedType[0])) pinDevice(slot);
        });
        scan.setOnClickListener(view -> scanDevices(scan, results, name, address,
                selectedType, selectedEndpoint));
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

    private void scanDevices(TextView scan, LinearLayout results, EditText name, EditText address,
                             String[] selectedType, String[] selectedEndpoint) {
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
                    choice.setOnClickListener(view -> {
                        name.setText(device.name);
                        address.setText(device.url);
                        selectedType[0] = device.type;
                        selectedEndpoint[0] = device.endpoint;
                        results.removeAllViews();
                    });
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
            String type = "";
            String endpoint = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int seen = 0;
                while ((line = reader.readLine()) != null && seen++ < 40) {
                    Matcher titleMatcher = Pattern.compile("\\\"title\\\":\\\"([^\\\"]+)").matcher(line);
                    if (titleMatcher.find()) name = titleMatcher.group(1);
                    Matcher endpointMatcher = Pattern.compile("\\\"id\\\":\\\"(fan|light|switch|button|cover)-([^\\\"]+)").matcher(line);
                    if (!endpointMatcher.find()) continue;
                    String candidateType = endpointMatcher.group(1);
                    if ("sensor".equals(candidateType)
                            && !line.matches("(?is).*\\\"(name|name_id)\\\"\\s*:\\s*\\\"[^\\\"]*(temperature|temp|humidity|湿度|温度).*")) {
                        continue;
                    }
                    if ("switch".equals(candidateType)
                            && (line.contains("\"name\":\"童锁\"")
                            || line.contains("\"name_id\":\"switch/童锁\""))) {
                        continue;
                    }
                    type = candidateType;
                    endpoint = endpointMatcher.group(2);
                    Matcher nameMatcher = Pattern.compile("\\\"name\\\":\\\"([^\\\"]+)").matcher(line);
                    if (nameMatcher.find()) name = nameMatcher.group(1);
                    break;
                }
            }
            if (type.isEmpty() || endpoint.isEmpty()) return null;
            String features = entityLabel(type);
            return new Device(host, url, name, features, type, endpoint);
        } catch (Exception ignored) {
            return null;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private String normalizeUrl(String input) { String value = input.trim(); if (value.equals("http://") || value.equals("https://")) return ""; if (!value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value; while (value.endsWith("/")) value = value.substring(0, value.length() - 1); return value; }
    private static final class DeviceTile {
        final LinearLayout card;
        final View stateDot;
        final ImageView icon;
        final TextView title;
        final TextView status;
        final String name;
        final String room;
        final String type;
        boolean available;
        boolean on;
        boolean controlling;

        DeviceTile(LinearLayout card, View stateDot, ImageView icon, TextView title,
                   TextView status, String name, String room, String type) {
            this.card = card;
            this.stateDot = stateDot;
            this.icon = icon;
            this.title = title;
            this.status = status;
            this.name = name;
            this.room = room;
            this.type = type;
        }
    }

    private static final class Device {
        final int host;
        final String url, name, features, type, endpoint;
        Device(int host, String url, String name, String features, String type, String endpoint) {
            this.host = host;
            this.url = url;
            this.name = name;
            this.features = features;
            this.type = type;
            this.endpoint = endpoint;
        }
    }
}
