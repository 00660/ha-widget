package com.wight.hawidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FanModeReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!FanModeScheduler.ACTION_APPLY_MODE.equals(intent.getAction())) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        EXECUTOR.execute(() -> {
            try {
                FanModeScheduler.apply(applicationContext);
                HaFanWidgetProvider.requestRefresh(applicationContext);
            } catch (IOException ignored) {
                FanModeScheduler.schedule(applicationContext);
            }
            pendingResult.finish();
        });
    }
}
