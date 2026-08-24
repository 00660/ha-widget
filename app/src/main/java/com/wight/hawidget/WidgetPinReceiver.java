package com.wight.hawidget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class WidgetPinReceiver extends BroadcastReceiver {
    static final String EXTRA_DEVICE_ID = "device_id";
    static final String EXTRA_WIDGET_STYLE = "widget_style";

    @Override
    public void onReceive(Context context, Intent intent) {
        int appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        int deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, -1);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || deviceId < 0) return;
        WidgetPreferences.bindWidget(context, appWidgetId, deviceId);
        if (intent.hasExtra(EXTRA_WIDGET_STYLE)) {
            WidgetPreferences.bindEntityWidget(context, appWidgetId, deviceId,
                    intent.getStringExtra(EXTRA_WIDGET_STYLE));
            EntityWidgetProvider.requestRefresh(context);
        }
        HaFanWidgetProvider.requestRefresh(context);
    }
}
