package com.wight.hawidget;

import android.content.Context;

public final class HaFanWidgetProvider extends EntityWidgetProvider {
    public static void requestRefresh(Context context) {
        requestRefresh(context, HaFanWidgetProvider.class);
    }

    @Override
    protected boolean isConfigured(HaSettings settings) {
        return settings.hasFan();
    }

    @Override
    protected String entityId(HaSettings settings) {
        return settings.fanEntity;
    }

    @Override
    protected String domain() {
        return "fan";
    }

    @Override
    protected int layoutId() {
        return R.layout.ha_fan_widget;
    }

    @Override
    protected int rootId() {
        return R.id.fan_widget_root;
    }

    @Override
    protected int controlId() {
        return R.id.fan_widget_state;
    }

    @Override
    protected int iconId() {
        return R.id.fan_widget_icon;
    }

    @Override
    protected int onIconId() {
        return R.drawable.ic_fan_on;
    }

    @Override
    protected int offIconId() {
        return R.drawable.ic_fan_off;
    }
}
