package com.anezium.assistbridge.phone;

final class CaptureSnapshot {
    static final CaptureSnapshot EMPTY = new CaptureSnapshot("", "", 0L, 0, 0);

    final String packageName;
    final String text;
    final long capturedAtMillis;
    final int nodeCount;
    final int eventType;

    CaptureSnapshot(String packageName, String text, long capturedAtMillis, int nodeCount, int eventType) {
        this.packageName = packageName == null ? "" : packageName;
        this.text = text == null ? "" : text;
        this.capturedAtMillis = capturedAtMillis;
        this.nodeCount = nodeCount;
        this.eventType = eventType;
    }

    boolean hasText() {
        return !text.isEmpty();
    }
}
