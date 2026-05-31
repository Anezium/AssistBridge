package com.anezium.assistbridge.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.util.Log;

public final class DebugRelayReceiver extends BroadcastReceiver {
    static final String ACTION_DEBUG_SEND = "com.anezium.assistbridge.phone.DEBUG_SEND";
    private static final String TAG = "AssistBridgeDebug";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || !isDebuggable(context)) {
            return;
        }
        String text = intent == null ? "" : intent.getStringExtra("text");
        if (TextUtils.isEmpty(text)) {
            text = "AssistBridge debug relay test.";
        }
        Log.i(TAG, "debug send chars=" + text.length());
        AssistBridgeRelay.sendAssistantText(
                context,
                "debug",
                text,
                "debug-" + System.currentTimeMillis()
        );
    }

    private boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
