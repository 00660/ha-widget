package com.wight.hawidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.widget.RemoteViews;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HaFanWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_TOGGLE = "com.wight.hawidget.FAN_TOGGLE";
    private static final String ACTION_NATURAL = "com.wight.hawidget.FAN_NATURAL";
    private static final String ACTION_SLEEP = "com.wight.hawidget.FAN_SLEEP";
    private static final String ACTION_SET_SPEED = "com.wight.hawidget.FAN_SET_SPEED";
    private static final String EXTRA_SPEED = "speed";
    private static final String NATURAL_PRESET = "自然风";
    private static final String SLEEP_PRESET = "睡眠风";
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int[] SPEED_ZONE_IDS = {
            R.id.fan_speed_001, R.id.fan_speed_002, R.id.fan_speed_003, R.id.fan_speed_004,
            R.id.fan_speed_005, R.id.fan_speed_006, R.id.fan_speed_007, R.id.fan_speed_008,
            R.id.fan_speed_009, R.id.fan_speed_010, R.id.fan_speed_011, R.id.fan_speed_012,
            R.id.fan_speed_013, R.id.fan_speed_014, R.id.fan_speed_015, R.id.fan_speed_016,
            R.id.fan_speed_017, R.id.fan_speed_018, R.id.fan_speed_019, R.id.fan_speed_020,
            R.id.fan_speed_021, R.id.fan_speed_022, R.id.fan_speed_023, R.id.fan_speed_024,
            R.id.fan_speed_025, R.id.fan_speed_026, R.id.fan_speed_027, R.id.fan_speed_028,
            R.id.fan_speed_029, R.id.fan_speed_030, R.id.fan_speed_031, R.id.fan_speed_032,
            R.id.fan_speed_033, R.id.fan_speed_034, R.id.fan_speed_035, R.id.fan_speed_036,
            R.id.fan_speed_037, R.id.fan_speed_038, R.id.fan_speed_039, R.id.fan_speed_040,
            R.id.fan_speed_041, R.id.fan_speed_042, R.id.fan_speed_043, R.id.fan_speed_044,
            R.id.fan_speed_045, R.id.fan_speed_046, R.id.fan_speed_047, R.id.fan_speed_048,
            R.id.fan_speed_049, R.id.fan_speed_050, R.id.fan_speed_051, R.id.fan_speed_052,
            R.id.fan_speed_053, R.id.fan_speed_054, R.id.fan_speed_055, R.id.fan_speed_056,
            R.id.fan_speed_057, R.id.fan_speed_058, R.id.fan_speed_059, R.id.fan_speed_060,
            R.id.fan_speed_061, R.id.fan_speed_062, R.id.fan_speed_063, R.id.fan_speed_064,
            R.id.fan_speed_065, R.id.fan_speed_066, R.id.fan_speed_067, R.id.fan_speed_068,
            R.id.fan_speed_069, R.id.fan_speed_070, R.id.fan_speed_071, R.id.fan_speed_072,
            R.id.fan_speed_073, R.id.fan_speed_074, R.id.fan_speed_075, R.id.fan_speed_076,
            R.id.fan_speed_077, R.id.fan_speed_078, R.id.fan_speed_079, R.id.fan_speed_080,
            R.id.fan_speed_081, R.id.fan_speed_082, R.id.fan_speed_083, R.id.fan_speed_084,
            R.id.fan_speed_085, R.id.fan_speed_086, R.id.fan_speed_087, R.id.fan_speed_088,
            R.id.fan_speed_089, R.id.fan_speed_090, R.id.fan_speed_091, R.id.fan_speed_092,
            R.id.fan_speed_093, R.id.fan_speed_094, R.id.fan_speed_095, R.id.fan_speed_096,
            R.id.fan_speed_097, R.id.fan_speed_098, R.id.fan_speed_099, R.id.fan_speed_100
    };

    public static void requestRefresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, HaFanWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        Intent intent = new Intent(context, HaFanWidgetProvider.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        render(manager, context, appWidgetIds, null);
        refreshAsync(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (isControlAction(action)) {
            runCommand(context, action, intent.getIntExtra(EXTRA_SPEED, -1));
            return;
        }
        super.onReceive(context, intent);
    }

    private boolean isControlAction(String action) {
        return ACTION_TOGGLE.equals(action)
                || ACTION_NATURAL.equals(action)
                || ACTION_SLEEP.equals(action)
                || ACTION_SET_SPEED.equals(action);
    }

    private void runCommand(Context context, String action, int speed) {
        Context applicationContext = context.getApplicationContext();
        if (ACTION_NATURAL.equals(action) || ACTION_SLEEP.equals(action)) {
            WidgetPreferences.saveSelectedPreset(applicationContext, presetFor(action));
            renderPresetFeedback(applicationContext, action);
        }
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        NETWORK_EXECUTOR.execute(() -> {
            try {
                if (ACTION_TOGGLE.equals(action)) {
                    EspHomeClient.toggleFan();
                } else if (ACTION_SET_SPEED.equals(action) && speed >= 1 && speed <= 100) {
                    EspHomeClient.setFanPercentage(speed);
                    WidgetPreferences.saveBaseSpeed(applicationContext, speed);
                } else {
                    EspHomeClient.setFanPreset(presetFor(action));
                }
            } catch (IOException ignored) {
            }
            refresh(applicationContext);
            pendingResult.finish();
        });
    }

    private String presetFor(String action) {
        return ACTION_NATURAL.equals(action) ? NATURAL_PRESET : SLEEP_PRESET;
    }

    private void renderPresetFeedback(Context context, String action) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, HaFanWidgetProvider.class));
        boolean naturalWind = ACTION_NATURAL.equals(action);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ha_fan_widget_wide);
            views.setInt(
                    R.id.fan_natural_button,
                    "setBackgroundResource",
                    naturalWind ? R.drawable.fan_control_primary : R.drawable.fan_control_secondary
            );
            views.setInt(
                    R.id.fan_sleep_button,
                    "setBackgroundResource",
                    naturalWind ? R.drawable.fan_control_secondary : R.drawable.fan_control_primary
            );
            views.setImageViewResource(
                    R.id.fan_natural_icon,
                    naturalWind ? R.drawable.ic_wind_light : R.drawable.ic_wind
            );
            views.setImageViewResource(
                    R.id.fan_sleep_icon,
                    naturalWind ? R.drawable.ic_sleep : R.drawable.ic_sleep_light
            );
            views.setTextColor(R.id.fan_natural_label, naturalWind ? Color.WHITE : Color.rgb(71, 85, 105));
            views.setTextColor(R.id.fan_sleep_label, naturalWind ? Color.rgb(71, 85, 105) : Color.WHITE);
            manager.partiallyUpdateAppWidget(id, views);
        }
    }

    private void refreshAsync(Context context) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        NETWORK_EXECUTOR.execute(() -> {
            refresh(applicationContext);
            pendingResult.finish();
        });
    }

    private void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, HaFanWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        try {
            render(manager, context, ids, EspHomeClient.fetchFanState());
        } catch (IOException ignored) {
            render(manager, context, ids, null);
        }
    }

    private void render(AppWidgetManager manager, Context context, int[] ids, EspHomeClient.FanState state) {
        String selectedPreset = selectedPreset(context, state);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ha_fan_widget_wide);
            boolean connected = state != null;
            boolean available = connected && state.available;
            boolean on = available && state.on;
            boolean naturalWind = available && NATURAL_PRESET.equals(selectedPreset);
            boolean sleepWind = available && SLEEP_PRESET.equals(selectedPreset);
            views.setImageViewResource(R.id.fan_widget_icon, on ? R.drawable.ic_fan_on : R.drawable.ic_fan_off);
            views.setTextViewText(
                    R.id.fan_widget_state,
                    context.getString(
                            !connected ? R.string.fan_disconnected
                                    : available ? R.string.fan_connected : R.string.fan_unavailable
                    )
            );
            views.setTextViewText(R.id.fan_widget_speed, speedText(context, state));
            views.setTextViewText(R.id.fan_speed_tile_value, speedValue(context, state));
            renderSpeedRing(context, views, state);
            views.setInt(
                    R.id.fan_widget_connection_dot,
                    "setBackgroundResource",
                    available ? R.drawable.fan_connection_dot : R.drawable.fan_connection_off_dot
            );
            views.setInt(
                    R.id.fan_power_tile,
                    "setBackgroundResource",
                    on ? R.drawable.fan_control_primary : R.drawable.fan_control_power_off
            );
            views.setInt(
                    R.id.fan_natural_button,
                    "setBackgroundResource",
                    naturalWind ? R.drawable.fan_control_primary : R.drawable.fan_control_secondary
            );
            views.setInt(
                    R.id.fan_sleep_button,
                    "setBackgroundResource",
                    sleepWind ? R.drawable.fan_control_primary : R.drawable.fan_control_secondary
            );
            views.setImageViewResource(
                    R.id.fan_natural_icon,
                    naturalWind ? R.drawable.ic_wind_light : R.drawable.ic_wind
            );
            views.setImageViewResource(
                    R.id.fan_sleep_icon,
                    sleepWind ? R.drawable.ic_sleep_light : R.drawable.ic_sleep
            );
            views.setTextColor(R.id.fan_natural_label, naturalWind ? Color.WHITE : Color.rgb(71, 85, 105));
            views.setTextColor(R.id.fan_sleep_label, sleepWind ? Color.WHITE : Color.rgb(71, 85, 105));
            views.setOnClickPendingIntent(R.id.fan_power_tile, commandIntent(context, ACTION_TOGGLE, id));
            views.setOnClickPendingIntent(R.id.fan_natural_button, commandIntent(context, ACTION_NATURAL, id));
            views.setOnClickPendingIntent(R.id.fan_sleep_button, commandIntent(context, ACTION_SLEEP, id));
            for (int speed = 1; speed <= 100; speed++) {
                views.setOnClickPendingIntent(
                        SPEED_ZONE_IDS[speed - 1],
                        speedCommandIntent(context, id, speed)
                );
            }
            manager.updateAppWidget(id, views);
        }
    }

    private String selectedPreset(Context context, EspHomeClient.FanState state) {
        if (state == null || !state.available) {
            return "";
        }
        if (NATURAL_PRESET.equals(state.presetMode) || SLEEP_PRESET.equals(state.presetMode)) {
            WidgetPreferences.saveSelectedPreset(context, state.presetMode);
            return state.presetMode;
        }
        return WidgetPreferences.loadSelectedPreset(context);
    }

    private String speedText(Context context, EspHomeClient.FanState state) {
        if (state == null || !state.available || state.percentage < 0) {
            return context.getString(R.string.speed_unavailable);
        }
        return context.getString(R.string.speed_percent, state.percentage);
    }

    private String speedValue(Context context, EspHomeClient.FanState state) {
        if (state == null || !state.available || state.percentage < 0) {
            return "--";
        }
        return context.getString(R.string.speed_widget_percent, state.percentage);
    }

    private void renderSpeedRing(Context context, RemoteViews views, EspHomeClient.FanState state) {
        int percentage = state != null && state.available ? state.percentage : -1;
        if (percentage < 1 || percentage > 100) {
            return;
        }
        int progressDrawable = context.getResources().getIdentifier(
                String.format("fan_circle_marker_%03d", percentage),
                "drawable",
                context.getPackageName()
        );
        views.setImageViewResource(R.id.fan_speed_marker, progressDrawable);
    }

    private PendingIntent commandIntent(Context context, String action, int appWidgetId) {
        Intent intent = new Intent(context, HaFanWidgetProvider.class)
                .setAction(action)
                .setData(Uri.parse("hawidget://" + context.getPackageName() + "/" + appWidgetId + "/" + action));
        return PendingIntent.getBroadcast(
                context,
                appWidgetId + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent speedCommandIntent(Context context, int appWidgetId, int speed) {
        Intent intent = new Intent(context, HaFanWidgetProvider.class)
                .setAction(ACTION_SET_SPEED)
                .setData(Uri.parse(
                        "hawidget://" + context.getPackageName() + "/" + appWidgetId + "/speed/" + speed
                ))
                .putExtra(EXTRA_SPEED, speed);
        return PendingIntent.getBroadcast(
                context,
                appWidgetId * 1000 + speed,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
