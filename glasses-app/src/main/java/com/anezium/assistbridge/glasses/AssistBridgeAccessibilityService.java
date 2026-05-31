package com.anezium.assistbridge.glasses;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;

public final class AssistBridgeAccessibilityService extends AccessibilityService {
    private static final String TAG = "AssistBridgeA11yGlass";
    private static final String ACTION_TWO_FINGER_SWIPE_FORWARD = "com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD";
    private static final String ACTION_TWO_FINGER_SWIPE_BACK = "com.android.action.ACTION_TWO_FINGER_SWIPE_BACK";
    private static final int KEYCODE_SWIPE_FORWARD = 183;
    private static final int KEYCODE_SWIPE_BACK = 184;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HashSet<Integer> grabbedKeys = new HashSet<>();
    private WindowManager windowManager;
    private AssistantHudView overlay;
    private WindowManager.LayoutParams overlayParams;

    private final GlassesRelayBridge.Listener bridgeListener = new GlassesRelayBridge.Listener() {
        @Override
        public void onAssistantMessage(AssistantMessage message) {
            if (!RelayService.openHud(AssistBridgeAccessibilityService.this)) {
                showOverlay(message);
            }
        }

        @Override
        public void onConnectionChanged(String state) {
        }

        @Override
        public void onSettingsChanged() {
            AssistantHudView view = overlay;
            if (view != null) {
                view.refreshSettings();
            }
        }
    };

    private final BroadcastReceiver swipeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? "" : intent.getAction();
            if (ACTION_TWO_FINGER_SWIPE_FORWARD.equals(action)) {
                handleOverlayKey(KEYCODE_SWIPE_FORWARD);
            } else if (ACTION_TWO_FINGER_SWIPE_BACK.equals(action)) {
                handleOverlayKey(KEYCODE_SWIPE_BACK);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TWO_FINGER_SWIPE_FORWARD);
        filter.addAction(ACTION_TWO_FINGER_SWIPE_BACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(swipeReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(swipeReceiver, filter);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = info.flags | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.notificationTimeout = 500;
            setServiceInfo(info);
        }
        GlassesRelayBridge.start(this);
        GlassesRelayBridge.addListener(bridgeListener);
        Log.i(TAG, "glasses accessibility relay connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // The service is only the background anchor for the CXR relay.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }

        int keyCode = event.getKeyCode();
        boolean relayKey = isRelayControlKey(keyCode);
        AssistantHudView view = overlay;
        boolean relayActive = view != null && view.hasVisibleMessage();

        if (event.getAction() == KeyEvent.ACTION_UP && grabbedKeys.remove(keyCode)) {
            return true;
        }
        if (!relayActive || !relayKey) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return grabbedKeys.contains(keyCode);
        }

        grabbedKeys.add(keyCode);
        Log.d(TAG, "overlay key=" + keyCode);
        return handleOverlayKey(keyCode);
    }

    @Override
    public void onDestroy() {
        GlassesRelayBridge.removeListener(bridgeListener);
        grabbedKeys.clear();
        hideOverlay();
        try {
            unregisterReceiver(swipeReceiver);
        } catch (RuntimeException ignored) {
        }
        super.onDestroy();
    }

    private void showOverlay(AssistantMessage message) {
        if (message == null || message.text.isEmpty()) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                WindowManager wm = ensureWindowManager();
                AssistantHudView view = overlay;
                if (view == null) {
                    view = new AssistantHudView(AssistBridgeAccessibilityService.this);
                    view.setListener(new AssistantHudView.Listener() {
                        @Override
                        public void onHudDismissed() {
                            hideOverlay();
                        }
                    });
                    overlay = view;
                }

                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int popupWidth = Math.min(screenWidth, AssistantHudView.popupWidthPx(AssistBridgeAccessibilityService.this));
                view.showMessage(message, popupWidth, screenHeight);

                if (overlayParams == null) {
                    overlayParams = new WindowManager.LayoutParams(
                            popupWidth,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT
                    );
                    overlayParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    overlayParams.y = dp(18);
                    try {
                        wm.addView(view, overlayParams);
                    } catch (RuntimeException exception) {
                        Log.w(TAG, "overlay add failed", exception);
                        overlay = null;
                        overlayParams = null;
                    }
                } else {
                    overlayParams.width = popupWidth;
                    overlayParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    try {
                        wm.updateViewLayout(view, overlayParams);
                    } catch (RuntimeException exception) {
                        Log.w(TAG, "overlay update failed", exception);
                    }
                }
            }
        });
    }

    private void hideOverlay() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (overlay == null) {
                    return;
                }
                try {
                    ensureWindowManager().removeView(overlay);
                } catch (RuntimeException exception) {
                    Log.w(TAG, "overlay remove failed", exception);
                }
                overlay = null;
                overlayParams = null;
            }
        });
    }

    private WindowManager ensureWindowManager() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }
        return windowManager;
    }

    private boolean handleOverlayKey(int keyCode) {
        AssistantHudView view = overlay;
        if (view == null || !view.hasVisibleMessage()) {
            return false;
        }
        return view.handleKey(keyCode);
    }

    private boolean isRelayControlKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_BACK:
            case KEYCODE_SWIPE_FORWARD:
            case KEYCODE_SWIPE_BACK:
                return true;
            default:
                return false;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
