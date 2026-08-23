package com.wight.hawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
        TextView title = new TextView(this);
        title.setText("选择风扇设备");
        title.setTextSize(24);
        list.addView(title, new LinearLayout.LayoutParams(-1, -2));

        int count = WidgetPreferences.loadDeviceCount(this);
        for (int deviceId = 0; deviceId < count; deviceId++) {
            String url = WidgetPreferences.loadEspHomeUrl(this, deviceId);
            if (url.isEmpty()) continue;
            Button device = new Button(this);
            device.setAllCaps(false);
            device.setText(WidgetPreferences.loadFanName(this, deviceId) + "\n" + url);
            final int selected = deviceId;
            device.setOnClickListener(view -> validateAndBind(view, selected));
            list.addView(device, new LinearLayout.LayoutParams(-1, -2));
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
        Intent update = new Intent(this, HaFanWidgetProvider.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        sendBroadcast(update);
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
