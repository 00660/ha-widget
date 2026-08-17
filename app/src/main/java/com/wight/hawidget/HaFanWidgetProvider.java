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
    private static final String NATURAL_PRESET = "自然风";
    private static final String SLEEP_PRESET = "睡眠风";
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newSingleThreadExecutor();

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
            runCommand(context, action);
            return;
        }
        super.onReceive(context, intent);
    }

    private boolean isControlAction(String action) {
        return ACTION_TOGGLE.equals(action)
                || ACTION_NATURAL.equals(action)
                || ACTION_SLEEP.equals(action);
    }

    private void runCommand(Context context, String action) {
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
                } else {
                    FanModeScheduler.enable(applicationContext, presetFor(action));
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
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ha_fan_widget);
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
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ha_fan_widget);
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
            views.setOnClickPendingIntent(R.id.fan_speed_tile, speedIntent(context, id));
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

    private PendingIntent speedIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, SpeedActivity.class)
                .setData(Uri.parse("hawidget://" + context.getPackageName() + "/" + appWidgetId + "/speed"));
        return PendingIntent.getActivity(
                context,
                appWidgetId + 100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
