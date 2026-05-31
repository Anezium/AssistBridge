package com.anezium.assistbridge.phone;

final class AssistBridgeProtocol {
    static final int PROTOCOL_VERSION = 1;
    static final String CLIENT_PACKAGE = "com.anezium.assistbridge.glasses";
    static final String CLIENT_MAIN_ACTIVITY = "com.anezium.assistbridge.glasses.MainActivity";
    static final String CLIENT_ASSET_NAME = "assist-bridge-glasses.apk";
    static final String KEY_EVENT = "assist_bridge.event";
    static final String KEY_COMMAND = "assist_bridge.command";
    static final String PREFS = "assist_bridge_phone";
    static final String PREF_AUTH_TOKEN = "cxr_l_auth_token";
    static final String PREF_CLIENT_APK_FINGERPRINT = "client_apk_fingerprint";

    private AssistBridgeProtocol() {
    }

    static long displayDurationMs(String text) {
        int chars = text == null ? 0 : text.length();
        long duration = 6500L + (chars * 55L);
        return Math.max(7000L, Math.min(duration, 45000L));
    }
}
