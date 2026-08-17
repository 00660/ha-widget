package com.wight.hawidget;

import android.content.Context;

public final class HaLightWidgetProvider extends EntityWidgetProvider {
    public static void requestRefresh(Context context) {
        requestRefresh(context, HaLightWidgetProvider.class);
    }

    @Override
    protected boolean isConfigured(HaSettings settings) {
        return settings.hasLight();
    }

    @Override
    protected String entityId(HaSettings settings) {
        return settings.lightEntity;
    }

    @Override
    protected String domain() {
        return "light";
    }

    @Override
    protected int layoutId() {
        return R.layout.ha_light_widget;
    }

    @Override
    protected int rootId() {
        return R.id.light_widget_root;
    }

    @Override
    protected int controlId() {
        return R.id.light_widget_state;
    }

    @Override
    protected int iconId() {
        return R.id.light_widget_icon;
    }

    @Override
    protected int onIconId() {
        return R.drawable.ic_light_on;
    }

    @Override
    protected int offIconId() {
        return R.drawable.ic_light_off;
    }
}
