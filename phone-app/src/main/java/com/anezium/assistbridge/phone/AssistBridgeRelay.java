package com.anezium.assistbridge.phone;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import com.example.cxrglobal.CXRLink;
import com.example.cxrglobal.CxrDefs;
import com.example.cxrglobal.callbacks.ICXRLinkCbk;
import com.example.cxrglobal.callbacks.ICustomCmdCbk;
import com.rokid.cxr.Caps;

import org.json.JSONObject;

final class AssistBridgeRelay {
    static final class Snapshot {
        final boolean hasToken;
        final boolean cxrConnected;
        final boolean glassConnected;
        final String bootstrapState;
        final String lastStatus;
        final String lastSentPreview;

        Snapshot(boolean hasToken, boolean cxrConnected, boolean glassConnected,
                 String bootstrapState, String lastStatus, String lastSentPreview) {
            this.hasToken = hasToken;
            this.cxrConnected = cxrConnected;
            this.glassConnected = glassConnected;
            this.bootstrapState = bootstrapState;
            this.lastStatus = lastStatus;
            this.lastSentPreview = lastSentPreview;
        }
    }

    private static final String TAG = "AssistBridgeRelay";
    private static final long RECONNECT_INITIAL_DELAY_MS = 1000L;
    private static final long RECONNECT_MAX_DELAY_MS = 15000L;
    private static final long ASSISTANT_WAKE_LOCK_MS = 8000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile Context appContext;
    private static volatile CXRLink link;
    private static volatile String authToken = "";
    private static volatile boolean cxrConnected;
    private static volatile boolean glassConnected;
    private static volatile boolean bootstrapStarted;
    private static volatile String bootstrapState = "idle";
    private static volatile String lastStatus = "idle";
    private static volatile String lastSentPreview = "";
    private static volatile JSONObject pendingMessage;
    private static volatile Runnable reconnectRunnable;
    private static long reconnectDelayMs = RECONNECT_INITIAL_DELAY_MS;

    private AssistBridgeRelay() {
    }

    static void startFromStoredToken(Context context) {
        Context safeContext = context.getApplicationContext();
        String token = safeContext.getSharedPreferences(AssistBridgeProtocol.PREFS, Context.MODE_PRIVATE)
                .getString(AssistBridgeProtocol.PREF_AUTH_TOKEN, "");
        if (!TextUtils.isEmpty(token)) {
            Log.i(TAG, "start from stored token");
            start(safeContext, token);
        } else {
            appContext = safeContext;
            lastStatus = "Hi Rokid auth needed";
            Log.w(TAG, "start skipped: Hi Rokid auth needed");
        }
    }

    static void start(Context context, String token) {
        Context safeContext = context.getApplicationContext();
        if (TextUtils.isEmpty(token)) {
            lastStatus = "Hi Rokid auth missing";
            return;
        }

        safeContext.getSharedPreferences(AssistBridgeProtocol.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(AssistBridgeProtocol.PREF_AUTH_TOKEN, token)
                .apply();
        appContext = safeContext;
        authToken = token;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                startOnMain(safeContext, token);
            }
        });
    }

    static Snapshot snapshot(Context context) {
        if (TextUtils.isEmpty(authToken) && context != null) {
            authToken = context.getApplicationContext()
                    .getSharedPreferences(AssistBridgeProtocol.PREFS, Context.MODE_PRIVATE)
                    .getString(AssistBridgeProtocol.PREF_AUTH_TOKEN, "");
        }
        return new Snapshot(
                !TextUtils.isEmpty(authToken),
                cxrConnected,
                glassConnected,
                bootstrapState,
                lastStatus,
                lastSentPreview
        );
    }

    static void sendAssistantText(Context context, String packageName, String text, String fingerprint) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        Log.i(TAG, "send assistant text requested chars=" + text.length());
        startFromStoredToken(context);
        long now = System.currentTimeMillis();
        JSONObject json = new JSONObject();
        try {
            json.put("version", AssistBridgeProtocol.PROTOCOL_VERSION);
            json.put("type", "assistant_text");
            json.put("source", "phone");
            json.put("assistantPackage", packageName == null ? "" : packageName);
            json.put("messageId", TextUtils.isEmpty(fingerprint) ? String.valueOf(now) : fingerprint);
            json.put("text", text);
            json.put("createdAt", now);
            json.put("displayMs", AssistBridgeProtocol.displayDurationMs(text));
        } catch (Exception exception) {
            Log.w(TAG, "json build failed", exception);
            return;
        }

        if (!sendJson(AssistBridgeProtocol.KEY_EVENT, json)) {
            pendingMessage = json;
            Log.i(TAG, "assistant text queued");
        }
    }

    static void requestBootstrap(Context context) {
        startFromStoredToken(context);
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                bootstrapStarted = false;
                maybeBootstrap();
            }
        });
    }

    static boolean openGlassesAccessibilitySettings(Context context) {
        startFromStoredToken(context);
        JSONObject json = new JSONObject();
        try {
            json.put("version", AssistBridgeProtocol.PROTOCOL_VERSION);
            json.put("type", "open_accessibility_settings");
            json.put("source", "phone");
            json.put("targetPackage", AssistBridgeProtocol.CLIENT_PACKAGE);
        } catch (Exception exception) {
            Log.w(TAG, "json build failed", exception);
            return false;
        }
        boolean sent = sendJson(AssistBridgeProtocol.KEY_EVENT, json);
        if (!sent) {
            pendingMessage = json;
        }
        return sent;
    }

    private static void startOnMain(Context context, String token) {
        authToken = token;
        CXRLink localLink = ensureLink(context);
        bootstrapState = "waiting for glasses";
        lastStatus = "binding Hi Rokid service";
        Log.i(TAG, "connecting CXR-L");
        boolean bound;
        try {
            bound = localLink.connect(token);
        } catch (RuntimeException exception) {
            Log.w(TAG, "connect failed", exception);
            bound = false;
        }

        if (!bound) {
            lastStatus = "Hi Rokid bind failed";
            bootstrapState = "not connected";
            Log.w(TAG, "CXR-L bind failed");
            scheduleReconnect("bind failed");
        } else if (localLink.isServiceConnected()) {
            cxrConnected = true;
            glassConnected = localLink.isGlassBtConnected();
            Log.i(TAG, "CXR-L already connected glassConnected=" + glassConnected);
            maybeBootstrap();
        } else {
            Log.i(TAG, "CXR-L bind requested");
        }
    }

    private static CXRLink ensureLink(Context context) {
        if (link != null) {
            return link;
        }
        CXRLink localLink = new CXRLink(context);
        localLink.configCXRSession(new CxrDefs.CXRSession(
                CxrDefs.CXRSessionType.CUSTOMAPP,
                AssistBridgeProtocol.CLIENT_PACKAGE
        ));
        localLink.setCXRLinkCbk(LINK_CALLBACK);
        localLink.setCXRCustomCmdCbk(COMMAND_CALLBACK);
        link = localLink;
        return localLink;
    }

    private static final ICXRLinkCbk LINK_CALLBACK = new ICXRLinkCbk() {
        @Override
        public void onCXRLConnected(boolean connected) {
            cxrConnected = connected;
            if (connected) {
                lastStatus = "CXR-L connected";
                cancelReconnect();
            } else {
                glassConnected = false;
                bootstrapStarted = false;
                bootstrapState = "not connected";
                lastStatus = "CXR-L disconnected";
                scheduleReconnect("CXR-L disconnected");
            }
            maybeBootstrap();
        }

        @Override
        public void onGlassBtConnected(boolean connected) {
            glassConnected = connected;
            lastStatus = connected ? "glasses connected" : "glasses disconnected";
            if (connected) {
                cancelReconnect();
            } else {
                bootstrapStarted = false;
                bootstrapState = cxrConnected ? "waiting for glasses" : "not connected";
            }
            maybeBootstrap();
        }

        @Override
        public void onGlassAiAssistStart() {
            lastStatus = "AI key down";
            GeminiAccessibilityService.armForAssistantRequest("glasses AI key");
            wakePhoneForAssistant();
        }

        @Override
        public void onGlassAiAssistStop() {
            lastStatus = "AI key up";
        }
    };

    private static final ICustomCmdCbk COMMAND_CALLBACK = new ICustomCmdCbk() {
        @Override
        public void onCustomCmdResult(String key, byte[] payload) {
            if (!AssistBridgeProtocol.KEY_COMMAND.equals(key)) {
                return;
            }
            String payloadText = payloadToText(payload);
            if (!payloadText.contains("request_state")) {
                return;
            }
            sendState();
        }
    };

    private static void maybeBootstrap() {
        Context context = appContext;
        CXRLink localLink = link;
        if (context == null || localLink == null || !cxrConnected || !glassConnected) {
            return;
        }
        if (bootstrapStarted) {
            flushPending();
            return;
        }
        bootstrapStarted = true;
        bootstrapState = "starting glasses app";
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String result = new ClientBootstrap(context, localLink).ensureRunning();
                bootstrapState = result;
                lastStatus = result;
                sendState();
                flushPending();
            }
        }, "AssistBridgeBootstrap");
        thread.start();
    }

    private static void sendState() {
        JSONObject json = new JSONObject();
        try {
            json.put("version", AssistBridgeProtocol.PROTOCOL_VERSION);
            json.put("type", "state");
            json.put("source", "phone");
            json.put("cxrConnected", cxrConnected);
            json.put("glassConnected", glassConnected);
            json.put("bootstrapState", bootstrapState);
        } catch (Exception exception) {
            return;
        }
        sendJson(AssistBridgeProtocol.KEY_EVENT, json);
    }

    private static void flushPending() {
        JSONObject pending = pendingMessage;
        if (pending == null || !cxrConnected || !glassConnected) {
            return;
        }
        if (sendJson(AssistBridgeProtocol.KEY_EVENT, pending)) {
            pendingMessage = null;
        }
    }

    private static boolean sendJson(String key, JSONObject json) {
        CXRLink localLink = link;
        if (localLink == null || !cxrConnected) {
            markSendUnavailable("link not ready", json);
            return false;
        }
        try {
            Caps caps = new Caps();
            caps.write(json.toString());
            Integer result = localLink.sendCustomCmd(key, caps.serialize());
            if (result == null || result < 0) {
                markSendUnavailable("send returned " + result, json);
                return false;
            }
            lastSentPreview = preview(json.optString("text", json.optString("type")));
            lastStatus = "sent " + json.optString("type");
            Log.i(TAG, "sent " + json.optString("type") + " result=" + result);
            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "send failed", exception);
            markSendUnavailable("send exception", json);
            return false;
        }
    }

    private static void markSendUnavailable(String reason, JSONObject json) {
        Log.w(TAG, reason + " type=" + json.optString("type"));
        lastStatus = "send queued";
        if (!cxrConnected || link == null || !link.isServiceConnected()) {
            scheduleReconnect(reason);
        }
    }

    private static void scheduleReconnect(String reason) {
        Context context = appContext;
        if (context == null || TextUtils.isEmpty(authToken) || reconnectRunnable != null) {
            return;
        }
        long delayMs = reconnectDelayMs;
        Log.i(TAG, "schedule reconnect in " + delayMs + "ms: " + reason);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                reconnectRunnable = null;
                if (TextUtils.isEmpty(authToken)) {
                    return;
                }
                reconnectDelayMs = Math.min(delayMs * 2L, RECONNECT_MAX_DELAY_MS);
                bootstrapStarted = false;
                bootstrapState = "reconnecting";
                startOnMain(context, authToken);
            }
        };
        reconnectRunnable = runnable;
        MAIN.postDelayed(runnable, delayMs);
    }

    private static void cancelReconnect() {
        if (reconnectRunnable != null) {
            MAIN.removeCallbacks(reconnectRunnable);
        }
        reconnectRunnable = null;
        reconnectDelayMs = RECONNECT_INITIAL_DELAY_MS;
    }

    @SuppressWarnings("deprecation")
    private static void wakePhoneForAssistant() {
        Context context = appContext;
        if (context == null) {
            Log.w(TAG, "wake skipped: app context missing");
            return;
        }

        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                Log.w(TAG, "wake skipped: power manager missing");
                return;
            }
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AssistBridge:assistantWake");
            wakeLock.acquire(ASSISTANT_WAKE_LOCK_MS);
            Log.i(TAG, "wake phone for assistant");
        } catch (RuntimeException exception) {
            Log.w(TAG, "wake phone failed", exception);
        }
    }

    private static String payloadToText(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        try {
            Caps caps = Caps.fromBytes(payload);
            if (caps == null || caps.size() == 0) {
                return "";
            }
            return caps.at(0).getString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static String preview(String text) {
        String normalized = TextNormalizer.normalize(text);
        if (normalized.length() > 90) {
            return normalized.substring(0, 90) + "...";
        }
        return normalized;
    }
}
