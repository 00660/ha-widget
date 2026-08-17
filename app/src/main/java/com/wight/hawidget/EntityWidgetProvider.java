package com.wight.hawidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class EntityWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_TOGGLE = "com.wight.hawidget.TOGGLE";
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        render(manager, context, appWidgetIds, "unknown");
        refreshFromBroadcast(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            toggleFromBroadcast(context);
            return;
        }
        super.onReceive(context, intent);
    }

    protected static void requestRefresh(Context context, Class<? extends EntityWidgetProvider> providerClass) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, providerClass));
        if (ids.length == 0) {
            return;
        }
        Intent intent = new Intent(context, providerClass).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(intent);
    }

    protected abstract boolean isConfigured(HaSettings settings);

    protected abstract String entityId(HaSettings settings);

    protected abstract String domain();

    protected abstract int layoutId();

    protected abstract int rootId();

    protected abstract int controlId();

    protected abstract int iconId();

    protected abstract int onIconId();

    protected abstract int offIconId();

    private void toggleFromBroadcast(Context context) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        NETWORK_EXECUTOR.execute(() -> {
            HaSettings settings = HaSettings.load(applicationContext);
            if (isConfigured(settings)) {
                try {
                    HaClient.toggle(settings, domain(), entityId(settings));
                } catch (IOException ignored) {
                }
            }
            refresh(applicationContext);
            pendingResult.finish();
        });
    }

    private void refreshFromBroadcast(Context context) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        NETWORK_EXECUTOR.execute(() -> {
            refresh(applicationContext);
            pendingResult.finish();
        });
    }

    private void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, getClass()));
        if (ids.length == 0) {
            return;
        }

        HaSettings settings = HaSettings.load(context);
        String state = "unknown";
        if (isConfigured(settings)) {
            try {
                state = HaClient.fetchState(settings, entityId(settings));
            } catch (IOException | JSONException ignored) {
            }
        }
        render(manager, context, ids, state);
    }

    private void render(AppWidgetManager manager, Context context, int[] ids, String state) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutId());
            boolean isOn = "on".equalsIgnoreCase(state);
            views.setImageViewResource(iconId(), isOn ? onIconId() : offIconId());
            views.setTextViewText(controlId(), stateText(context, state));
            views.setOnClickPendingIntent(rootId(), commandIntent(context, ACTION_TOGGLE, 1));
            manager.updateAppWidget(id, views);
        }
    }

    private CharSequence stateText(Context context, String state) {
        int label = "on".equalsIgnoreCase(state) ? R.string.state_on
                : "off".equalsIgnoreCase(state) ? R.string.state_off : R.string.state_unknown;
        return context.getString(label);
    }

    private PendingIntent commandIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, getClass()).setAction(action);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
