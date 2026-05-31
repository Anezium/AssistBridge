package com.anezium.assistbridge.protocol;

public final class AssistBridgeProtocol {
    public static final int PROTOCOL_VERSION = 1;
    public static final String CLIENT_PACKAGE = "com.anezium.assistbridge.glasses";
    public static final String CLIENT_MAIN_ACTIVITY = "com.anezium.assistbridge.glasses.MainActivity";
    public static final String CLIENT_ASSET_NAME = "assist-bridge-glasses.apk";
    public static final String KEY_EVENT = "assist_bridge.event";
    public static final String KEY_COMMAND = "assist_bridge.command";

    public static final String TYPE_ASSISTANT_TEXT = "assistant_text";
    public static final String TYPE_HUD_SETTINGS = "hud_settings";
    public static final String TYPE_OPEN_ACCESSIBILITY_SETTINGS = "open_accessibility_settings";
    public static final String TYPE_REQUEST_STATE = "request_state";
    public static final String TYPE_STATE = "state";

    public static final String FIELD_ASSISTANT_PACKAGE = "assistantPackage";
    public static final String FIELD_BOOTSTRAP_STATE = "bootstrapState";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_CXR_CONNECTED = "cxrConnected";
    public static final String FIELD_DISPLAY_MS = "displayMs";
    public static final String FIELD_FONT_SIZE_SP = "fontSizeSp";
    public static final String FIELD_GLASS_CONNECTED = "glassConnected";
    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_SOURCE = "source";
    public static final String FIELD_TARGET_PACKAGE = "targetPackage";
    public static final String FIELD_TEXT = "text";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_VERSION = "version";

    private AssistBridgeProtocol() {
    }

    public static long displayDurationMs(String text) {
        int chars = text == null ? 0 : text.length();
        long duration = 6500L + (chars * 55L);
        return Math.max(7000L, Math.min(duration, 45000L));
    }
}
