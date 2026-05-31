package com.anezium.assistbridge.phone;

import android.content.Context;

import com.anezium.assistbridge.protocol.HudTextSize;

final class HudSettingsStore {
    private HudSettingsStore() {
    }

    static float fontSizeSp(Context context) {
        if (context == null) {
            return HudTextSize.DEFAULT_SP;
        }
        return HudTextSize.clamp(context.getApplicationContext()
                .getSharedPreferences(PhonePrefs.PREFS, Context.MODE_PRIVATE)
                .getFloat(PhonePrefs.HUD_FONT_SIZE_SP, HudTextSize.DEFAULT_SP));
    }

    static float setFontSizeSp(Context context, float value) {
        float safeValue = HudTextSize.clamp(value);
        context.getApplicationContext()
                .getSharedPreferences(PhonePrefs.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putFloat(PhonePrefs.HUD_FONT_SIZE_SP, safeValue)
                .apply();
        return safeValue;
    }
}
