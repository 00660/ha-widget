package com.wight.hawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class WidgetConfigureActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        list.setPadding(padding, padding, padding, padding);
        list.setBackgroundColor(Color.rgb(244, 247, 251));
        TextView title = new TextView(this);
        title.setText("选择风扇设备");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        list.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = new TextView(this);
        hint.setText("选择后，桌面挂件会连接到对应设备");
        hint.setTextSize(13);
        hint.setTextColor(Color.rgb(100, 116, 139));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        list.addView(hint, hintParams);

        int count = WidgetPreferences.loadDeviceCount(this);
        for (int deviceId = 0; deviceId < count; deviceId++) {
            String url = WidgetPreferences.loadEspHomeUrl(this, deviceId);
            if (url.isEmpty()) continue;
            TextView device = new TextView(this);
            device.setText(WidgetPreferences.loadFanName(this, deviceId) + "\n" + url);
            device.setTextSize(16);
            device.setTextColor(Color.rgb(15, 23, 42));
            device.setGravity(android.view.Gravity.CENTER_VERTICAL);
            device.setPadding((int) (16 * getResources().getDisplayMetrics().density), 0,
                    (int) (16 * getResources().getDisplayMetrics().density), 0);
            device.setBackgroundResource(R.drawable.settings_card_background);
            device.setMinHeight((int) (64 * getResources().getDisplayMetrics().density));
            final int selected = deviceId;
            device.setOnClickListener(view -> validateAndBind(view, selected));
            LinearLayout.LayoutParams deviceParams = new LinearLayout.LayoutParams(-1,
                    (int) (72 * getResources().getDisplayMetrics().density));
            deviceParams.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
            list.addView(device, deviceParams);
        }
        setContentView(list);
    }

    private void validateAndBind(View view, int deviceId) {
        view.setEnabled(false);
        new Thread(() -> {
            try {
                EspHomeClient.FanState state = EspHomeClient.fetchFanState(this, deviceId);
                boolean deviceIsDiscrete = state.speedCount == 3;
                boolean widgetIsDiscrete = AppWidgetManager.getInstance(this)
                        .getAppWidgetInfo(appWidgetId).provider.getClassName()
                        .equals(DiscreteFanWidgetProvider.class.getName());
                runOnUiThread(() -> {
                    if (deviceIsDiscrete == widgetIsDiscrete) {
                        bind(deviceId);
                    } else {
                        view.setEnabled(true);
                        Toast.makeText(this, deviceIsDiscrete
                                ? "该设备是三档风扇，请添加三档挂件"
                                : "该设备支持无级调速，请添加无级挂件", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    view.setEnabled(true);
                    Toast.makeText(this, "无法读取设备调速能力", Toast.LENGTH_SHORT).show();
                });
            }
        }, "widget-capability").start();
    }

    private void bind(int deviceId) {
        WidgetPreferences.bindWidget(this, appWidgetId, deviceId);
        ComponentName provider = AppWidgetManager.getInstance(this)
                .getAppWidgetInfo(appWidgetId).provider;
        Intent update = new Intent()
                .setComponent(provider)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        sendBroadcast(update);
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
