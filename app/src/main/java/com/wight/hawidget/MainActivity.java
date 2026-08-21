package com.wight.hawidget;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_main);
        EditText url = findViewById(R.id.esphome_url);
        EditText fanName = findViewById(R.id.fan_name);
        setUrlText(url, WidgetPreferences.loadEspHomeUrl(this));
        fanName.setText(WidgetPreferences.loadFanName(this));
        EditText[] urls = {url, findViewById(R.id.esphome_url_2), findViewById(R.id.esphome_url_3), findViewById(R.id.esphome_url_4), findViewById(R.id.esphome_url_5)};
        EditText[] names = {fanName, findViewById(R.id.fan_name_2), findViewById(R.id.fan_name_3), findViewById(R.id.fan_name_4), findViewById(R.id.fan_name_5)};
        for (int i = 1; i < urls.length; i++) {
            setUrlText(urls[i], WidgetPreferences.loadEspHomeUrl(this, i));
            names[i].setText(WidgetPreferences.loadFanName(this, i));
        }
        Button save = findViewById(R.id.save_device);
        ScrollView scroll = findViewById(R.id.settings_scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), 24 + imeBottom);
                View focused = view.findFocus();
                if (focused instanceof EditText && imeBottom > 0) {
                    focused.postDelayed(() -> focused.requestRectangleOnScreen(
                            new android.graphics.Rect(0, 0, focused.getWidth(), focused.getHeight()), true), 80);
                }
                return insets;
            });
        }
        View.OnFocusChangeListener focusListener = (view, hasFocus) -> {
            if (hasFocus) view.postDelayed(() -> {
                EditText field = (EditText) view;
                field.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, field.getWidth(), field.getHeight()), true);
            }, 350);
        };
        for (EditText field : urls) field.setOnFocusChangeListener(focusListener);
        for (EditText field : names) field.setOnFocusChangeListener(focusListener);
        save.setOnClickListener(view -> {
            String value = url.getText().toString().trim();
            String name = fanName.getText().toString().trim();
            for (int i = 0; i < urls.length; i++) {
                value = urls[i].getText().toString().trim();
                if ("http://".equals(value) || "https://".equals(value)) value = "";
                else if (!value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
                while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
                if (!value.isEmpty() && (value.startsWith("http://") || value.startsWith("https://"))) {
                    String deviceName = names[i].getText().toString().trim();
                    WidgetPreferences.saveDevice(this, i, value, deviceName);
                }
            }
            if (!WidgetPreferences.loadEspHomeUrl(this).isEmpty()) {
                HaFanWidgetProvider.requestRefresh(this);
                Toast.makeText(this, "设备已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请输入有效的 ESPHome 地址", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setUrlText(EditText field, String value) {
        field.setText(value == null || value.isEmpty() ? "http://" : value);
    }

}
