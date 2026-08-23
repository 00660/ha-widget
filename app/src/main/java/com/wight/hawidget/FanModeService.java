package com.wight.hawidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class FanModeService extends Service {
    static final String ACTION_START = "com.wight.hawidget.MODE_START";
    static final String ACTION_STOP = "com.wight.hawidget.MODE_STOP";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_SLOT = "slot";
    static final String MODE_NATURAL = "natural";
    static final String MODE_SLEEP = "sleep";
    private static final String CHANNEL_ID = "fan_mode";
    private static final int NOTIFICATION_ID = 4101;
    private static final long NATURAL_INTERVAL = 20_000L;
    private static final long SLEEP_INTERVAL = 60_000L;
    private static final ExecutorService COMMANDS = Executors.newSingleThreadExecutor();

    private final Map<Integer, ModeTask> tasks = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification());
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopMode(intent == null ? 0 : intent.getIntExtra(EXTRA_SLOT, 0));
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            String requestedMode = intent.getStringExtra(EXTRA_MODE);
            int slot = intent.getIntExtra(EXTRA_SLOT, 0);
            if (MODE_NATURAL.equals(requestedMode) || MODE_SLEEP.equals(requestedMode)) {
                startMode(slot, requestedMode);
            }
        } else if (intent == null) {
            for (int slot = 0; slot < WidgetPreferences.loadDeviceCount(this); slot++) {
                String savedMode = WidgetPreferences.loadMode(this, slot);
                if (MODE_NATURAL.equals(savedMode) || MODE_SLEEP.equals(savedMode)) startMode(slot, savedMode);
            }
        }
        return START_STICKY;
    }

    private synchronized void startMode(int slot, String requestedMode) {
        ModeTask previous = tasks.remove(slot);
        if (previous != null) previous.running = false;
        ModeTask task = new ModeTask(slot, requestedMode);
        tasks.put(slot, task);
        WidgetPreferences.saveMode(this, slot, requestedMode);
        task.thread.start();
    }

    private synchronized void stopMode(int slot) {
        ModeTask task = tasks.remove(slot);
        if (task != null) task.running = false;
        WidgetPreferences.saveMode(this, slot, "");
        if (tasks.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void runLoop(ModeTask task) {
        long phase = 0;
        while (task.running) {
            int base = WidgetPreferences.loadBaseSpeed(this, task.slot);
            int speed = MODE_NATURAL.equals(task.mode)
                    ? naturalSpeed(base, phase)
                    : sleepSpeed(base, phase);
            COMMANDS.execute(() -> {
                try {
                    EspHomeClient.setFanPercentage(this, task.slot, speed);
                    HaFanWidgetProvider.requestRefresh(this);
                } catch (IOException ignored) {
                }
            });
            long interval = MODE_NATURAL.equals(task.mode) ? NATURAL_INTERVAL : SLEEP_INTERVAL;
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
            phase++;
        }
    }

    private final class ModeTask {
        final int slot;
        final String mode;
        volatile boolean running = true;
        final Thread thread;
        ModeTask(int slot, String mode) {
            this.slot = slot;
            this.mode = mode;
            this.thread = new Thread(() -> runLoop(this), "fan-mode-" + slot);
        }
    }

    private int naturalSpeed(int base, long phase) {
        final double[] pattern = {0.65, 0.90, 0.75, 1.00, 0.55, 0.80};
        return clamp((int) Math.round(base * pattern[(int) (phase % pattern.length)]));
    }

    private int sleepSpeed(int base, long phase) {
        final double[] pattern = {1.00, 0.82, 0.68, 0.52, 0.38, 0.52, 0.68, 0.82};
        return clamp((int) Math.round(base * pattern[(int) (phase % pattern.length)]));
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(100, value));
    }

    private Notification notification() {
        String title = "风扇模式运行中";
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_fan_on)
                .setContentTitle(title)
                .setContentText("风扇模式将在息屏后继续运行")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "风扇模式", NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
