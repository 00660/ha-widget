package com.wight.hawidget;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private EditText baseUrl;
    private EditText token;
    private EditText lightEntity;
    private EditText fanEntity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        baseUrl = findViewById(R.id.base_url);
        token = findViewById(R.id.token);
        lightEntity = findViewById(R.id.light_entity);
        fanEntity = findViewById(R.id.fan_entity);

        HaSettings settings = HaSettings.load(this);
        baseUrl.setText(settings.baseUrl);
        token.setText(settings.token);
        lightEntity.setText(settings.lightEntity);
        fanEntity.setText(settings.fanEntity);

        findViewById(R.id.save_button).setOnClickListener(this::saveSettings);
    }

    private void saveSettings(View ignored) {
        HaSettings settings = new HaSettings(
                baseUrl.getText().toString(),
                token.getText().toString(),
                lightEntity.getText().toString(),
                fanEntity.getText().toString()
        );
        if (!settings.isComplete()) {
            Toast.makeText(this, R.string.invalid_configuration, Toast.LENGTH_LONG).show();
            return;
        }
        settings.save(this);
        HaLightWidgetProvider.requestRefresh(this);
        HaFanWidgetProvider.requestRefresh(this);
        Toast.makeText(this, R.string.saved_configuration, Toast.LENGTH_SHORT).show();
    }
}
