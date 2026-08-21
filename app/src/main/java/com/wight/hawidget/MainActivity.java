package com.wight.hawidget;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText url = findViewById(R.id.esphome_url);
        EditText fanName = findViewById(R.id.fan_name);
        url.setText(WidgetPreferences.loadEspHomeUrl(this));
        fanName.setText(WidgetPreferences.loadFanName(this));
        EditText[] urls = {url, findViewById(R.id.esphome_url_2), findViewById(R.id.esphome_url_3), findViewById(R.id.esphome_url_4)};
        EditText[] names = {fanName, findViewById(R.id.fan_name_2), findViewById(R.id.fan_name_3), findViewById(R.id.fan_name_4)};
        for (int i = 1; i < urls.length; i++) {
            urls[i].setText(WidgetPreferences.loadEspHomeUrl(this, i));
            names[i].setText(WidgetPreferences.loadFanName(this, i));
        }
        Button save = findViewById(R.id.save_device);
        View root = findViewById(R.id.settings_root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect visibleFrame = new Rect();
            root.getWindowVisibleDisplayFrame(visibleFrame);
            int keyboardHeight = root.getRootView().getHeight() - visibleFrame.bottom;
            save.setTranslationY(keyboardHeight > root.getHeight() / 5 ? -keyboardHeight : 0);
        });
        save.setOnClickListener(view -> {
            String value = url.getText().toString().trim();
            String name = fanName.getText().toString().trim();
            for (int i = 0; i < urls.length; i++) {
                value = urls[i].getText().toString().trim();
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
}
