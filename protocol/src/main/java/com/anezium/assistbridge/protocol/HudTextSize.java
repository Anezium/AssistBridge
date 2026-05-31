package com.anezium.assistbridge.protocol;

public final class HudTextSize {
    public static final float DEFAULT_SP = 15.0f;
    public static final float MIN_SP = 11.0f;
    public static final float MAX_SP = 24.0f;

    private HudTextSize() {
    }

    public static float clamp(float value) {
        if (Float.isNaN(value)) {
            return DEFAULT_SP;
        }
        return Math.max(MIN_SP, Math.min(MAX_SP, value));
    }
}
