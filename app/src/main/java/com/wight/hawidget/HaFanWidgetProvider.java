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
    private static final String NATURAL_PRESET = "自然风";
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
        if (ACTION_TOGGLE.equals(action) || ACTION_NATURAL.equals(action)) {
            runCommand(context, ACTION_NATURAL.equals(action));
            return;
        }
        super.onReceive(context, intent);
    }

    private void runCommand(Context context, boolean naturalWind) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        NETWORK_EXECUTOR.execute(() -> {
            HaSettings settings = HaSettings.load(applicationContext);
            if (settings.hasFan()) {
                try {
                    if (naturalWind) {
                        HaClient.setFanPreset(settings, NATURAL_PRESET);
                    } else {
                        HaClient.toggleFan(settings);
                    }
                } catch (IOException ignored) {
                }
            }
            refresh(applicationContext);
            pendingResult.finish();
        });
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
            views.setImageViewResource(R.id.fan_widget_icon, on ? R.drawable.ic_fan_on : R.drawable.ic_fan_off);
            views.setTextViewText(
                    R.id.fan_widget_state,
                    context.getString(connected ? R.string.fan_connected : R.string.fan_disconnected)
            );
            views.setTextViewText(R.id.fan_widget_mode, modeText(context, state));
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
            views.setOnClickPendingIntent(R.id.fan_power_button, commandIntent(context, ACTION_TOGGLE, id));
            views.setOnClickPendingIntent(R.id.fan_natural_button, commandIntent(context, ACTION_NATURAL, id));
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
}
