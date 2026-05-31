package com.anezium.assistbridge.glasses;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public final class RelayService extends Service {
    private static final String TAG = "AssistBridgeService";
    static final String EXTRA_SHOW_POPUP = "show_popup";

    static void ensureStarted(Context context) {
        try {
            context.getApplicationContext().startService(new Intent(context, RelayService.class));
        } catch (RuntimeException exception) {
            Log.w(TAG, "start service failed", exception);
        }
    }

    static boolean openHud(Context context) {
        try {
            Intent intent = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_SHOW_POPUP, true);
            context.getApplicationContext().startActivity(intent);
            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "open HUD failed", exception);
            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "service created");
        GlassesRelayBridge.start(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "service start command");
        GlassesRelayBridge.start(this);
        if (intent != null && intent.hasExtra("debug_text")) {
            GlassesRelayBridge.injectAssistantMessage(intent.getStringExtra("debug_text"));
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
