package com.wight.hawidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HaFanWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_TOGGLE = "com.wight.hawidget.FAN_TOGGLE";
    private static final String ACTION_NATURAL = "com.wight.hawidget.FAN_NATURAL";
    private static final String ACTION_SLEEP = "com.wight.hawidget.FAN_SLEEP";
    private static final String ACTION_SPEED_ONE = "com.wight.hawidget.FAN_SPEED_ONE";
    private static final String ACTION_SPEED_TWO = "com.wight.hawidget.FAN_SPEED_TWO";
    private static final String ACTION_SPEED_THREE = "com.wight.hawidget.FAN_SPEED_THREE";
    private static final String NATURAL_PRESET = "自然风";
    private static final String SLEEP_PRESET = "睡眠风";
    private static final String SPEED_ONE_PRESET = "1档微风";
    private static final String SPEED_TWO_PRESET = "2档柔风";
    private static final String SPEED_THREE_PRESET = "3档清风";
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
                || ACTION_SLEEP.equals(action)
                || ACTION_SPEED_ONE.equals(action)
                || ACTION_SPEED_TWO.equals(action)
                || ACTION_SPEED_THREE.equals(action);
    }

    private void runCommand(Context context, String action) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        NETWORK_EXECUTOR.execute(() -> {
            HaSettings settings = HaSettings.load(applicationContext);
            if (settings.hasFan()) {
                try {
                    if (ACTION_TOGGLE.equals(action)) {
                        HaClient.toggleFan(settings);
                    } else {
                        HaClient.setFanPreset(settings, presetFor(action));
                    }
                } catch (IOException ignored) {
                }
            }
            refresh(applicationContext);
            pendingResult.finish();
        });
    }

    private String presetFor(String action) {
        if (ACTION_NATURAL.equals(action)) {
            return NATURAL_PRESET;
        }
        if (ACTION_SLEEP.equals(action)) {
            return SLEEP_PRESET;
        }
        if (ACTION_SPEED_ONE.equals(action)) {
            return SPEED_ONE_PRESET;
        }
        if (ACTION_SPEED_TWO.equals(action)) {
            return SPEED_TWO_PRESET;
        }
        return SPEED_THREE_PRESET;
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
        HaSettings settings = HaSettings.load(context);
        if (!settings.hasFan()) {
            render(manager, context, ids, null);
            return;
        }
        try {
            render(manager, context, ids, HaClient.fetchFanState(settings));
        } catch (IOException | JSONException ignored) {
            render(manager, context, ids, null);
        }
    }

    private void render(AppWidgetManager manager, Context context, int[] ids, HaClient.FanState state) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ha_fan_widget);
            boolean connected = state != null;
            boolean on = connected && state.on;
            boolean naturalWind = connected && NATURAL_PRESET.equals(state.presetMode);
            boolean sleepWind = connected && SLEEP_PRESET.equals(state.presetMode);
            views.setImageViewResource(R.id.fan_widget_icon, on ? R.drawable.ic_fan_on : R.drawable.ic_fan_off);
            views.setTextViewText(
                    R.id.fan_widget_state,
                    context.getString(connected ? R.string.fan_connected : R.string.fan_disconnected)
            );
            views.setTextViewText(R.id.fan_widget_mode, modeText(context, state));
            views.setTextViewText(R.id.fan_widget_speed_text, speedText(context, state));
            views.setInt(
                    R.id.fan_widget_connection_dot,
                    "setBackgroundResource",
                    connected ? R.drawable.fan_connection_dot : R.drawable.fan_connection_off_dot
            );
            views.setInt(
                    R.id.fan_power_button,
                    "setBackgroundResource",
                    on ? R.drawable.fan_control_primary : R.drawable.fan_control_power_off
            );
            views.setInt(
                    R.id.fan_natural_button,
                    "setBackgroundResource",
                    naturalWind ? R.drawable.fan_control_natural_active : R.drawable.fan_control_secondary
            );
            views.setInt(
                    R.id.fan_sleep_button,
                    "setBackgroundResource",
                    sleepWind ? R.drawable.fan_control_natural_active : R.drawable.fan_control_secondary
            );
            views.setInt(
                    R.id.fan_speed_one_button,
                    "setBackgroundResource",
                    connected && SPEED_ONE_PRESET.equals(state.presetMode)
                            ? R.drawable.fan_control_natural_active : R.drawable.fan_control_secondary
            );
            views.setInt(
                    R.id.fan_speed_two_button,
                    "setBackgroundResource",
                    connected && SPEED_TWO_PRESET.equals(state.presetMode)
                            ? R.drawable.fan_control_natural_active : R.drawable.fan_control_secondary
            );
            views.setInt(
                    R.id.fan_speed_three_button,
                    "setBackgroundResource",
                    connected && SPEED_THREE_PRESET.equals(state.presetMode)
                            ? R.drawable.fan_control_natural_active : R.drawable.fan_control_secondary
            );
            views.setOnClickPendingIntent(R.id.fan_power_button, commandIntent(context, ACTION_TOGGLE, id));
            views.setOnClickPendingIntent(R.id.fan_natural_button, commandIntent(context, ACTION_NATURAL, id));
            views.setOnClickPendingIntent(R.id.fan_sleep_button, commandIntent(context, ACTION_SLEEP, id));
            views.setOnClickPendingIntent(R.id.fan_speed_one_button, commandIntent(context, ACTION_SPEED_ONE, id));
            views.setOnClickPendingIntent(R.id.fan_speed_two_button, commandIntent(context, ACTION_SPEED_TWO, id));
            views.setOnClickPendingIntent(R.id.fan_speed_three_button, commandIntent(context, ACTION_SPEED_THREE, id));
            views.setOnClickPendingIntent(R.id.fan_widget_speed, speedIntent(context, id));
            manager.updateAppWidget(id, views);
        }
    }

    private String modeText(Context context, HaClient.FanState state) {
        if (state == null) {
            return context.getString(R.string.fan_not_configured);
        }
        if (!state.on) {
            return context.getString(R.string.state_off);
        }
        return state.presetMode.isEmpty() ? context.getString(R.string.fan_default_mode) : state.presetMode;
    }

    private String speedText(Context context, HaClient.FanState state) {
        if (state == null || state.percentage < 0) {
            return context.getString(R.string.speed_unavailable);
        }
        return context.getString(R.string.speed_percent, state.percentage);
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
