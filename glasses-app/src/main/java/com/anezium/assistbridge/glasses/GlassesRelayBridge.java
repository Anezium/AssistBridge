package com.anezium.assistbridge.glasses;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.rokid.cxr.CXRServiceBridge;
import com.rokid.cxr.Caps;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class GlassesRelayBridge {
    interface Listener {
        void onAssistantMessage(AssistantMessage message);

        void onConnectionChanged(String state);
    }

    private static final String TAG = "AssistBridgeBridge";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private static CXRServiceBridge bridge;
    private static Context appContext;
    private static AssistantMessage latestMessage;
    private static String connectionState = "connecting";

    private GlassesRelayBridge() {
    }

    static void start(Context context) {
        appContext = context.getApplicationContext();
        if (bridge != null) {
            requestState();
            return;
        }

        CXRServiceBridge cxr = new CXRServiceBridge();
        bridge = cxr;
        setConnectionState("connecting");
        cxr.setStatusListener(STATUS_LISTENER);
        int result = cxr.subscribe(AssistBridgeProtocol.KEY_EVENT, MSG_CALLBACK);
        Log.i(TAG, "subscribe result=" + result);
        requestState();
    }

    static void setListener(Listener nextListener) {
        if (nextListener != null) {
            addListener(nextListener);
        }
    }

    static void addListener(Listener nextListener) {
        if (nextListener == null || LISTENERS.contains(nextListener)) {
            return;
        }
        LISTENERS.add(nextListener);
        nextListener.onConnectionChanged(connectionState);
    }

    static void removeListener(Listener nextListener) {
        LISTENERS.remove(nextListener);
    }

    static AssistantMessage latestMessage() {
        return latestMessage;
    }

    static void clearLatestMessage() {
        latestMessage = null;
    }

    static void sendCommand(String type) {
        JSONObject object = new JSONObject();
        try {
            object.put("version", AssistBridgeProtocol.PROTOCOL_VERSION);
            object.put("type", type);
            object.put("source", "glasses");
        } catch (Exception exception) {
            return;
        }
        try {
            Caps caps = new Caps();
            caps.write(object.toString());
            CXRServiceBridge localBridge = bridge;
            if (localBridge != null) {
                localBridge.sendMessage(AssistBridgeProtocol.KEY_COMMAND, caps);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "send command failed", exception);
        }
    }

    static void injectAssistantMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        AssistantMessage message = new AssistantMessage(
                "debug",
                "Gemini",
                text,
                System.currentTimeMillis(),
                AssistantMessage.defaultDisplayMs(text)
        );
        deliverAssistantMessage(message);
    }

    private static void requestState() {
        sendCommand("request_state");
    }

    private static final CXRServiceBridge.StatusListener STATUS_LISTENER = new CXRServiceBridge.StatusListener() {
        @Override
        public void onConnected(String name, String mac, int deviceType) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    setConnectionState("connected");
                }
            });
        }

        @Override
        public void onDisconnected() {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    setConnectionState("disconnected");
                }
            });
        }

        @Override
        public void onConnecting(String name, String mac, int deviceType) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    setConnectionState("connecting");
                }
            });
        }

        @Override
        public void onARTCStatus(float latency, boolean connected) {
            if (connected) {
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        setConnectionState("connected");
                    }
                });
            }
        }

        @Override
        public void onRokidAccountChanged(String account) {
        }
    };

    private static final CXRServiceBridge.MsgCallback MSG_CALLBACK = new CXRServiceBridge.MsgCallback() {
        @Override
        public void onReceive(String msgType, Caps caps, byte[] data) {
            String payload = payloadToText(caps, data);
            if (payload.isEmpty()) {
                return;
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    handleEvent(payload);
                }
            });
        }
    };

    private static void handleEvent(String payload) {
        JSONObject object;
        try {
            object = new JSONObject(payload);
        } catch (Exception exception) {
            return;
        }

        String type = object.optString("type");
        if ("state".equals(type)) {
            setConnectionState(object.optBoolean("glassConnected") ? "connected" : "waiting");
            return;
        }
        if ("open_accessibility_settings".equals(type)) {
            openAccessibilitySettings();
            return;
        }
        if (!"assistant_text".equals(type)) {
            return;
        }

        AssistantMessage message = AssistantMessage.fromJson(object);
        if (message.text.isEmpty()) {
            return;
        }
        Log.i(TAG, "assistant_text received chars=" + message.text.length());
        deliverAssistantMessage(message);
    }

    private static void deliverAssistantMessage(AssistantMessage message) {
        latestMessage = message;
        Log.i(TAG, "deliver assistant_text listeners=" + LISTENERS.size());
        for (Listener localListener : LISTENERS) {
            localListener.onAssistantMessage(message);
        }
        Context context = appContext;
        if (context != null && LISTENERS.isEmpty()) {
            RelayService.openHud(context);
        }
    }

    private static void setConnectionState(String state) {
        connectionState = state == null ? "unknown" : state;
        for (Listener localListener : LISTENERS) {
            localListener.onConnectionChanged(connectionState);
        }
    }

    private static void openAccessibilitySettings() {
        Context context = appContext;
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (RuntimeException exception) {
            Log.w(TAG, "open accessibility settings failed", exception);
        }
    }

    private static String payloadToText(Caps caps, byte[] data) {
        if (data != null && data.length > 0) {
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (caps == null || caps.size() == 0) {
            return "";
        }
        try {
            return caps.at(0).getString();
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
