package com.wight.hawidget;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SpeedActivity extends Activity {
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newSingleThreadExecutor();

    private SeekBar speed;
    private TextView value;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed);

        speed = findViewById(R.id.speed_seekbar);
        value = findViewById(R.id.speed_value);
        speed.setMax(100);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                showValue(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        findViewById(R.id.speed_save_button).setOnClickListener(this::saveSpeed);
        loadSpeed();
    }

    private void loadSpeed() {
        NETWORK_EXECUTOR.execute(() -> {
            try {
                EspHomeClient.FanState state = EspHomeClient.fetchFanState();
                if (state.percentage >= 0) {
                    runOnUiThread(() -> {
                        speed.setProgress(state.percentage);
                        showValue(state.percentage);
                    });
                }
            } catch (IOException ignored) {
            }
        });
    }

    private void saveSpeed(View ignored) {
        int percentage = speed.getProgress();
        NETWORK_EXECUTOR.execute(() -> {
            try {
                EspHomeClient.setFanPercentage(percentage);
                WidgetPreferences.saveBaseSpeed(this, percentage);
                HaFanWidgetProvider.requestRefresh(this);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.speed_saved, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (IOException exception) {
                runOnUiThread(() -> Toast.makeText(this, R.string.speed_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showValue(int percentage) {
        value.setText(getString(R.string.speed_percent, percentage));
    }
}
