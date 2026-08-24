package com.wight.hawidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class EntityWidgetConfigureActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(20), dp(20), dp(20));
        list.setBackgroundColor(Color.rgb(244, 247, 251));
        TextView title = text("选择灯具或开关", 24, Color.rgb(15, 23, 42));
        list.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView hint = text("先选择设备，再选择挂件风格", 13, Color.rgb(100, 116, 139));
        list.addView(hint, new LinearLayout.LayoutParams(-1, dp(34)));
        for (int slot = 0; slot < WidgetPreferences.loadDeviceCount(this); slot++) {
            String type = WidgetPreferences.loadDeviceType(this, slot);
            if ("fan".equals(type) || WidgetPreferences.loadEspHomeUrl(this, slot).isEmpty()) continue;
            int selectedSlot = slot;
            TextView device = text(WidgetPreferences.loadFanName(this, slot) + "  ·  "
                    + ("light".equals(type) ? "灯具" : "开关") + "\n"
                    + WidgetPreferences.loadRoom(this, slot), 16, Color.rgb(15, 23, 42));
            device.setGravity(Gravity.CENTER_VERTICAL);
            device.setPadding(dp(14), 0, dp(14), 0);
            device.setBackgroundResource(R.drawable.settings_card_background);
            device.setOnClickListener(view -> showStyleChoice(selectedSlot, type));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(70));
            params.topMargin = dp(10);
            list.addView(device, params);
        }
        if (list.getChildCount() == 2) {
            TextView empty = text("请先在应用首页扫描并保存灯具或开关", 14, Color.rgb(100, 116, 139));
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
        }
        setContentView(list);
    }

    private void showStyleChoice(int slot, String type) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(16), dp(20), dp(16));
        list.setBackgroundResource(R.drawable.settings_card_background);
        TextView title = text("选择" + ("light".equals(type) ? "灯具" : "开关") + "挂件风格", 21, Color.rgb(15, 23, 42));
        list.addView(title, new LinearLayout.LayoutParams(-1, dp(46)));
        addStyle(list, slot, type, "compact", "紧凑卡片", "名称、房间、开关");
        addStyle(list, slot, type, "tile", "设备卡片", "大图、状态、开关");
        setContentView(list);
    }

    private void addStyle(LinearLayout list, int slot, String type, String style,
                          String title, String subtitle) {
        TextView option = text(title + "\n" + subtitle, 16, Color.rgb(15, 23, 42));
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(14), 0, dp(14), 0);
        option.setBackgroundResource(R.drawable.settings_input_background);
        option.setOnClickListener(view -> bind(slot, style));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.topMargin = dp(10);
        list.addView(option, params);
    }

    private void bind(int slot, String style) {
        WidgetPreferences.bindEntityWidget(this, appWidgetId, slot, style);
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        EntityWidgetProvider.requestRefresh(this);
        finish();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
