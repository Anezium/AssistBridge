package com.anezium.assistbridge.glasses;

import android.content.Context;
import android.content.SharedPreferences;

import com.anezium.assistbridge.protocol.HudTextSize;

final class HudSettings {
    private static final String PREFS = "assistbridge_hud_settings";
    private static final String KEY_FONT_SIZE_SP = "font_size_sp";

    private HudSettings() {
    }

    static float fontSizeSp(Context context) {
        return clamp(prefs(context).getFloat(KEY_FONT_SIZE_SP, HudTextSize.DEFAULT_SP));
    }

    static float setFontSizeSp(Context context, float value) {
        float safeValue = clamp(value);
        prefs(context).edit().putFloat(KEY_FONT_SIZE_SP, safeValue).apply();
        return safeValue;
    }

    static float clamp(float value) {
        return HudTextSize.clamp(value);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
