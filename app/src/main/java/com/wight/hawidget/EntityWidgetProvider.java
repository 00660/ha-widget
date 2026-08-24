package com.wight.hawidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EntityWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_TOGGLE = "com.wight.hawidget.ENTITY_TOGGLE";
    private static final String EXTRA_WIDGET_ID = "widget_id";
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    static void requestRefresh(Context context) {
        requestRefresh(context, EntityWidgetTileProvider.class);
    }

    private static void requestRefresh(Context context, Class<?> provider) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        if (ids.length == 0) return;
        Intent update = new Intent(context, provider)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(update);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(manager, context, id, null);
        refreshAsync(context, ids);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetPreferences.removeWidget(context, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            int id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            int slot = WidgetPreferences.loadWidgetDevice(context, id);
            if (slot < 0) return;
            goAsyncCommand(context, id, slot);
            return;
        }
        super.onReceive(context, intent);
    }

    private void goAsyncCommand(Context context, int id, int slot) {
        PendingResult pending = goAsync();
        NETWORK.execute(() -> {
            try { EspHomeClient.toggleDevice(context, slot); } catch (IOException ignored) { }
            renderOne(context, id);
            pending.finish();
        });
    }

    private void refreshAsync(Context context, int[] ids) {
        PendingResult pending = goAsync();
        NETWORK.execute(() -> {
            for (int id : ids) renderOne(context, id);
            pending.finish();
        });
    }

    private void renderOne(Context context, int id) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int slot = WidgetPreferences.loadWidgetDevice(context, id);
        if (slot < 0) { render(manager, context, id, null); return; }
        try {
            render(manager, context, id, EspHomeClient.fetchDeviceState(context, slot));
        } catch (IOException ignored) {
            render(manager, context, id, null);
        }
    }

    private void render(AppWidgetManager manager, Context context, int id,
                        EspHomeClient.DeviceState state) {
        int slot = WidgetPreferences.loadWidgetDevice(context, id);
        String type = slot < 0 ? "light" : WidgetPreferences.loadDeviceType(context, slot);
        String name = slot < 0 ? "未配置" : WidgetPreferences.loadFanName(context, slot);
        String room = slot < 0 ? "" : WidgetPreferences.loadRoom(context, slot);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.entity_widget_tile);
        boolean available = state != null && state.available;
        boolean on = available && state.on;
        views.setTextViewText(R.id.entity_widget_name, name.isEmpty() ? entityLabel(type) : name);
        views.setTextViewText(R.id.entity_widget_room, room);
        views.setTextViewText(R.id.entity_widget_state, available ? (on ? "已开启" : "已关闭") : "未连接");
        views.setViewVisibility(R.id.entity_widget_online_dot, available
                ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        views.setImageViewResource(R.id.entity_widget_icon,
                "switch".equals(type) ? R.drawable.ic_switch : on ? R.drawable.ic_light_on : R.drawable.ic_light);
        views.setInt(R.id.entity_widget_power, "setBackgroundResource",
                on ? R.drawable.entity_widget_state_on : R.drawable.entity_widget_state_off);
        PendingIntent command = commandIntent(context, id);
        views.setOnClickPendingIntent(R.id.entity_widget_root, command);
        views.setOnClickPendingIntent(R.id.entity_widget_power, command);
        manager.updateAppWidget(id, views);
    }

    private PendingIntent commandIntent(Context context, int id) {
        Intent intent = new Intent(context, getClass())
                .setAction(ACTION_TOGGLE)
                .setData(Uri.parse("hawidget://entity/" + id))
                .putExtra(EXTRA_WIDGET_ID, id);
        return PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private String entityLabel(String type) { return "switch".equals(type) ? "开关" : "灯具"; }
}
