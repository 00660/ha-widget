package com.wight.hawidget;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class WidgetPreferences {
    private static final String PREFERENCES = "widget_preferences";
    private static final String SELECTED_PRESET = "selected_preset";
    private static final String BASE_SPEED = "base_speed";
    private static final String LAST_KNOWN_SPEED = "last_known_speed";
    private static final String MODE = "mode";
    private static final String ESPHOME_URL = "esphome_url";
    private static final String FAN_NAME = "fan_name";
    private static final String ROOM = "room";
    private static final String CHILD_LOCK = "child_lock";
    private static final String DEVICE_COUNT = "device_count";
    private static final String DEVICE_TYPE = "device_type";
    private static final String DEVICE_ENDPOINT = "device_endpoint";
    private static final String WIDGET_DEVICE = "widget_device_";
    private static final String CUSTOM_ROOMS = "custom_rooms";
    private static final String[] DEFAULT_ROOMS = {"客厅", "卧室", "厨房", "卫生间"};

    private WidgetPreferences() {
    }

    static String loadSelectedPreset(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(SELECTED_PRESET + slot, "");
    }

    static void saveSelectedPreset(Context context, int slot, String preset) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(SELECTED_PRESET + slot, preset)
                .apply();
    }

    static int loadBaseSpeed(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getInt(BASE_SPEED + slot, 100);
    }

    static void saveBaseSpeed(Context context, int slot, int percentage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt(BASE_SPEED + slot, Math.max(0, Math.min(100, percentage)))
                .apply();
    }

    static int loadLastKnownSpeed(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getInt(LAST_KNOWN_SPEED + slot, -1);
    }

    static void saveLastKnownSpeed(Context context, int slot, int percentage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putInt(LAST_KNOWN_SPEED + slot, Math.max(0, Math.min(100, percentage))).apply();
    }

    static boolean loadChildLock(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(CHILD_LOCK + slot, false);
    }

    static boolean toggleChildLock(Context context, int slot) {
        boolean locked = !loadChildLock(context, slot);
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putBoolean(CHILD_LOCK + slot, locked).apply();
        return locked;
    }

    static String loadMode(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(MODE + slot, "");
    }

    static void saveMode(Context context, int slot, String mode) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(MODE + slot, mode == null ? "" : mode)
                .apply();
    }

    static String loadEspHomeUrl(Context context) {
        return loadEspHomeUrl(context, 0);
    }

    static String loadEspHomeUrl(Context context, int slot) {
        String[] defaults = {
                "http://192.168.2.64",
                "http://192.168.2.62",
                "http://192.168.2.10",
                "http://192.168.2.199",
                "",
                ""
        };
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(ESPHOME_URL + slot, slot >= 0 && slot < defaults.length ? defaults[slot] : "");
    }

    static int loadDeviceCount(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getInt(DEVICE_COUNT, 4);
    }

    static int loadWidgetDevice(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getInt(WIDGET_DEVICE + appWidgetId, -1);
    }

    static void bindWidget(Context context, int appWidgetId, int deviceId) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putInt(WIDGET_DEVICE + appWidgetId, deviceId).apply();
    }

    static void removeWidget(Context context, int appWidgetId) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().remove(WIDGET_DEVICE + appWidgetId).apply();
    }

    static String loadFanName(Context context) {
        return loadFanName(context, 0);
    }

    static String loadFanName(Context context, int slot) {
        String[] defaults = {"风扇 64", "风扇 62", "风扇 10", "风扇 199", "", ""};
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(FAN_NAME + slot, slot >= 0 && slot < defaults.length ? defaults[slot] : "");
    }

    static String loadDeviceType(Context context, int slot) {
        String type = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(DEVICE_TYPE + slot, "fan");
        return "light".equals(type) || "switch".equals(type)
                || "button".equals(type) || "cover".equals(type)
                || "environment".equals(type) ? type : "fan";
    }

    static String loadDeviceEndpoint(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(DEVICE_ENDPOINT + slot, "");
    }

    static String loadRoom(Context context, int slot) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(ROOM + slot, "未分配");
    }

    static void saveRoom(Context context, int slot, String room) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(ROOM + slot, room == null ? "未分配" : room).apply();
    }

    static List<String> loadRooms(Context context) {
        List<String> rooms = new ArrayList<>();
        for (String room : DEFAULT_ROOMS) rooms.add(room);
        String stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(CUSTOM_ROOMS, "[]");
        try {
            JSONArray customRooms = new JSONArray(stored);
            for (int index = 0; index < customRooms.length(); index++) {
                String room = customRooms.optString(index, "").trim();
                if (!room.isEmpty() && !rooms.contains(room)) rooms.add(room);
            }
        } catch (Exception ignored) {
            // Ignore damaged custom-room data and keep the built-in rooms available.
        }
        return rooms;
    }

    static boolean addRoom(Context context, String roomName) {
        String room = roomName == null ? "" : roomName.trim();
        if (room.isEmpty() || "全部".equals(room) || "未分配".equals(room)
                || loadRooms(context).contains(room)) return false;
        JSONArray customRooms = new JSONArray();
        for (String existing : loadRooms(context)) {
            boolean builtIn = false;
            for (String defaultRoom : DEFAULT_ROOMS) {
                if (defaultRoom.equals(existing)) {
                    builtIn = true;
                    break;
                }
            }
            if (!builtIn) customRooms.put(existing);
        }
        customRooms.put(room);
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(CUSTOM_ROOMS, customRooms.toString()).apply();
        return true;
    }

    static boolean isBuiltInRoom(String room) {
        for (String defaultRoom : DEFAULT_ROOMS) {
            if (defaultRoom.equals(room)) return true;
        }
        return false;
    }

    static boolean renameRoom(Context context, String oldName, String newName) {
        String replacement = newName == null ? "" : newName.trim();
        if (oldName == null || oldName.trim().isEmpty() || isBuiltInRoom(oldName)
                || replacement.isEmpty() || "全部".equals(replacement)
                || "未分配".equals(replacement) || loadRooms(context).contains(replacement)) {
            return false;
        }
        List<String> rooms = loadRooms(context);
        JSONArray customRooms = new JSONArray();
        for (String room : rooms) {
            if (isBuiltInRoom(room)) continue;
            customRooms.put(oldName.equals(room) ? replacement : room);
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(CUSTOM_ROOMS, customRooms.toString()).apply();
        return true;
    }

    static boolean removeRoom(Context context, String roomName) {
        if (roomName == null || isBuiltInRoom(roomName) || !loadRooms(context).contains(roomName)) {
            return false;
        }
        JSONArray customRooms = new JSONArray();
        for (String room : loadRooms(context)) {
            if (!isBuiltInRoom(room) && !roomName.equals(room)) customRooms.put(room);
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(CUSTOM_ROOMS, customRooms.toString()).apply();
        return true;
    }

    static void saveDevice(Context context, String url, String fanName) {
        saveDevice(context, 0, url, fanName, "fan", "");
    }

    static void saveDevice(Context context, int slot, String url, String fanName) {
        saveDevice(context, slot, url, fanName, "fan", "");
    }

    static void saveDevice(Context context, int slot, String url, String fanName,
                           String type, String endpoint) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = preferences.edit()
                .putString(ESPHOME_URL + slot, url)
                .putString(FAN_NAME + slot, fanName)
                .putString(DEVICE_TYPE + slot, type == null ? "fan" : type)
                .putString(DEVICE_ENDPOINT + slot, endpoint == null ? "" : endpoint);
        if (slot >= loadDeviceCount(context)) editor.putInt(DEVICE_COUNT, slot + 1);
        editor.apply();
    }

    static void removeDevice(Context context, int slot) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(ESPHOME_URL + slot, "")
                .putString(FAN_NAME + slot, "")
                .putString(DEVICE_TYPE + slot, "fan")
                .putString(DEVICE_ENDPOINT + slot, "")
                .putString(ROOM + slot, "未分配")
                .apply();
    }
}
