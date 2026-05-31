package com.anezium.assistbridge.glasses;

import com.anezium.assistbridge.protocol.AssistBridgeProtocol;

import org.json.JSONObject;

final class AssistantMessage {
    final String id;
    final String source;
    final String text;
    final long createdAtMillis;
    final long displayMs;

    AssistantMessage(String id, String source, String text, long createdAtMillis, long displayMs) {
        this.id = id == null ? "" : id;
        this.source = source == null ? "Gemini" : source;
        this.text = text == null ? "" : text;
        this.createdAtMillis = createdAtMillis;
        this.displayMs = Math.max(7000L, Math.min(displayMs, 45000L));
    }

    static AssistantMessage fromJson(JSONObject object) {
        String id = object.optString(AssistBridgeProtocol.FIELD_MESSAGE_ID, String.valueOf(System.currentTimeMillis()));
        String text = object.optString(AssistBridgeProtocol.FIELD_TEXT, "");
        long createdAt = object.optLong(AssistBridgeProtocol.FIELD_CREATED_AT, System.currentTimeMillis());
        long duration = object.optLong(AssistBridgeProtocol.FIELD_DISPLAY_MS, defaultDisplayMs(text));
        return new AssistantMessage(id, "Gemini", text, createdAt, duration);
    }

    boolean isExpired(long nowMillis) {
        return nowMillis > createdAtMillis + displayMs + 1000L;
    }

    static long defaultDisplayMs(String text) {
        return AssistBridgeProtocol.displayDurationMs(text);
    }
}
