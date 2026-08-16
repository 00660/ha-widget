package com.wight.hawidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HaWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_REFRESH = "com.wight.hawidget.REFRESH";
    private static final String ACTION_TOGGLE_LIGHT = "com.wight.hawidget.TOGGLE_LIGHT";
    private static final String ACTION_TOGGLE_FAN = "com.wight.hawidget.TOGGLE_FAN";
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        render(manager, context, appWidgetIds, new WidgetState("unknown", "unknown"), context.getString(R.string.loading));
        refreshFromBroadcast(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_TOGGLE_LIGHT.equals(action)) {
            executeAction(context, "light");
            return;
        }
        if (ACTION_TOGGLE_FAN.equals(action)) {
            executeAction(context, "fan");
            return;
        }
        if (ACTION_REFRESH.equals(action)) {
            refreshFromBroadcast(context);
            return;
        }
        super.onReceive(context, intent);
    }

    static void requestRefresh(Context context) {
        Context applicationContext = context.getApplicationContext();
        NETWORK_EXECUTOR.execute(() -> updateAll(applicationContext, null));
    }

    private void refreshFromBroadcast(Context context) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        NETWORK_EXECUTOR.execute(() -> {
            updateAll(applicationContext, null);
            pendingResult.finish();
        });
    }

    private void executeAction(Context context, String domain) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        NETWORK_EXECUTOR.execute(() -> {
            String message = null;
            HaSettings settings = HaSettings.load(applicationContext);
            if (!settings.isComplete()) {
                message = applicationContext.getString(R.string.setup_required);
            } else {
                try {
                    HaClient.toggle(settings, domain, "light".equals(domain) ? settings.lightEntity : settings.fanEntity);
                } catch (IOException exception) {
                    message = applicationContext.getString(R.string.request_failed);
                }
            }
            updateAll(applicationContext, message);
            pendingResult.finish();
        });
    }

    private static void updateAll(Context context, String message) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, HaWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }

        HaSettings settings = HaSettings.load(context);
        if (!settings.isComplete()) {
            render(manager, context, ids, new WidgetState("unknown", "unknown"), context.getString(R.string.setup_required));
            return;
        }

        String lightState = "unknown";
        String fanState = "unknown";
        String status = message;
        try {
            lightState = HaClient.fetchState(settings, settings.lightEntity);
        } catch (IOException | JSONException exception) {
            status = context.getString(R.string.connection_failed);
        }
        try {
            fanState = HaClient.fetchState(settings, settings.fanEntity);
        } catch (IOException | JSONException exception) {
            status = context.getString(R.string.connection_failed);
        }
        render(manager, context, ids, new WidgetState(lightState, fanState), status);
    }

    private static void render(AppWidgetManager manager, Context context, int[] ids, WidgetState state, String status) {
        for (int id : ids) {
            manager.updateAppWidget(id, createRemoteViews(context, state, status));
        }
    }

    private static RemoteViews createRemoteViews(Context context, WidgetState state, String status) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ha_widget);
        views.setImageViewResource(R.id.light_icon, state.lightOn ? R.drawable.ic_light_on : R.drawable.ic_light_off);
        views.setImageViewResource(R.id.fan_icon, state.fanOn ? R.drawable.ic_fan_on : R.drawable.ic_fan_off);
        views.setTextViewText(R.id.light_state, stateText(context, R.string.light_name, state.lightState));
        views.setTextViewText(R.id.fan_state, stateText(context, R.string.fan_name, state.fanState));
        views.setTextViewText(R.id.widget_status, status == null ? context.getString(R.string.tap_to_toggle) : status);
        views.setViewVisibility(R.id.widget_status, View.VISIBLE);
        views.setOnClickPendingIntent(R.id.light_control, commandIntent(context, ACTION_TOGGLE_LIGHT, 1));
        views.setOnClickPendingIntent(R.id.fan_control, commandIntent(context, ACTION_TOGGLE_FAN, 2));
        views.setOnClickPendingIntent(R.id.widget_header, commandIntent(context, ACTION_REFRESH, 3));
        return views;
    }

    private static CharSequence stateText(Context context, int label, String state) {
        int stateLabel = "on".equalsIgnoreCase(state) ? R.string.state_on
                : "off".equalsIgnoreCase(state) ? R.string.state_off : R.string.state_unknown;
        return context.getString(label) + "  " + context.getString(stateLabel);
    }

    private static PendingIntent commandIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, HaWidgetProvider.class).setAction(action);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static final class WidgetState {
        final String lightState;
        final String fanState;
        final boolean lightOn;
        final boolean fanOn;

        WidgetState(String lightState, String fanState) {
            this.lightState = lightState;
            this.fanState = fanState;
            this.lightOn = "on".equalsIgnoreCase(lightState);
            this.fanOn = "on".equalsIgnoreCase(fanState);
        }
    }
}
