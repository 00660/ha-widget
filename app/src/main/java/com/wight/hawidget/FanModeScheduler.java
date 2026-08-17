package com.wight.hawidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.io.IOException;

final class FanModeScheduler {
    static final String ACTION_APPLY_MODE = "com.wight.hawidget.APPLY_FAN_MODE";
    private static final long STEP_MILLIS = 30_000L;
    private static final String NATURAL_PRESET = "自然风";
    private static final String SLEEP_PRESET = "睡眠风";

    private FanModeScheduler() {
    }

    static void enable(Context context, String mode) throws IOException {
        EspHomeClient.FanState state = EspHomeClient.fetchFanState();
        if (state.percentage >= 0) {
            WidgetPreferences.saveBaseSpeed(context, state.percentage);
        }
        WidgetPreferences.saveSelectedPreset(context, mode);
        apply(context, state);
        schedule(context);
    }

    static void apply(Context context) throws IOException {
        EspHomeClient.FanState state = EspHomeClient.fetchFanState();
        apply(context, state);
        schedule(context);
    }

    static void schedule(Context context) {
        String mode = WidgetPreferences.loadSelectedPreset(context);
        if (!NATURAL_PRESET.equals(mode) && !SLEEP_PRESET.equals(mode)) {
            return;
        }
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager != null) {
            manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + STEP_MILLIS,
                    pendingIntent(context)
            );
        }
    }

    private static void apply(Context context, EspHomeClient.FanState state) throws IOException {
        if (!state.on) {
            return;
        }
        String mode = WidgetPreferences.loadSelectedPreset(context);
        int baseSpeed = WidgetPreferences.loadBaseSpeed(context);
        if (NATURAL_PRESET.equals(mode)) {
            int[] steps = {55, 70, 85, 100, 70, 90};
            int index = (int) ((System.currentTimeMillis() / STEP_MILLIS) % steps.length);
            EspHomeClient.setFanPercentage(Math.max(15, baseSpeed * steps[index] / 100));
        } else if (SLEEP_PRESET.equals(mode)) {
            EspHomeClient.setFanPercentage(Math.max(15, baseSpeed * 40 / 100));
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, FanModeReceiver.class).setAction(ACTION_APPLY_MODE);
        return PendingIntent.getBroadcast(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
