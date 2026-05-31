package com.anezium.assistbridge.glasses;

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
        String id = object.optString("messageId", String.valueOf(System.currentTimeMillis()));
        String text = object.optString("text", "");
        long createdAt = object.optLong("createdAt", System.currentTimeMillis());
        long duration = object.optLong("displayMs", defaultDisplayMs(text));
        return new AssistantMessage(id, "Gemini", text, createdAt, duration);
    }

    boolean isExpired(long nowMillis) {
        return nowMillis > createdAtMillis + displayMs + 1000L;
    }

    static long defaultDisplayMs(String text) {
        int chars = text == null ? 0 : text.length();
        long duration = 6500L + (chars * 55L);
        return Math.max(7000L, Math.min(duration, 45000L));
    }
}
