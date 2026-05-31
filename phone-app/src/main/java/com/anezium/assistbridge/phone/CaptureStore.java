package com.anezium.assistbridge.phone;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class CaptureStore {
    interface Listener {
        void onCaptureChanged(CaptureSnapshot snapshot);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile CaptureSnapshot latest = CaptureSnapshot.EMPTY;
    private static volatile boolean serviceRunning;

    private CaptureStore() {
    }

    static CaptureSnapshot latest() {
        return latest;
    }

    static boolean isServiceRunning() {
        return serviceRunning;
    }

    static void setServiceRunning(boolean running) {
        serviceRunning = running;
        notifyListeners(latest);
    }

    static void update(CaptureSnapshot snapshot) {
        latest = snapshot == null ? CaptureSnapshot.EMPTY : snapshot;
        notifyListeners(latest);
    }

    static void clear() {
        latest = CaptureSnapshot.EMPTY;
        notifyListeners(latest);
    }

    static void addListener(Listener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
            listener.onCaptureChanged(latest);
        }
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(CaptureSnapshot snapshot) {
        for (Listener listener : LISTENERS) {
            listener.onCaptureChanged(snapshot);
        }
    }
}
