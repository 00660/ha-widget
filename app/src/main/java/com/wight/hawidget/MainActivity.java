package com.wight.hawidget;

import android.app.Activity;
import android.os.Bundle;
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
        Button save = findViewById(R.id.save_device);
        save.setOnClickListener(view -> {
            String value = url.getText().toString().trim();
            String name = fanName.getText().toString().trim();
            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            if (!value.isEmpty() && (value.startsWith("http://") || value.startsWith("https://"))) {
                WidgetPreferences.saveDevice(this, value, name.isEmpty() ? "风扇" : name);
                HaFanWidgetProvider.requestRefresh(this);
                Toast.makeText(this, "设备已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请输入有效的 ESPHome 地址", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
