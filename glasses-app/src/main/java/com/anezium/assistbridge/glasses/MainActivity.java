package com.anezium.assistbridge.glasses;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int ACCENT = Color.rgb(156, 255, 98);
    private static final int TEXT = Color.rgb(224, 255, 231);
    private static final int MUTED = Color.rgb(122, 166, 132);
    private static final int RULE = Color.rgb(43, 84, 50);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private AssistantHudView hudView;
    private LinearLayout setupView;
    private TextView setupTitle;
    private TextView setupBody;
    private TextView setupButton;
    private boolean finishingFromHud;
    private boolean debugOverlayOnly;
    private boolean popupLaunch;
    private boolean setupMode;

    private final GlassesRelayBridge.Listener bridgeListener = new GlassesRelayBridge.Listener() {
        @Override
        public void onAssistantMessage(AssistantMessage message) {
            showSetup(false);
            hudView.showMessage(message);
        }

        @Override
        public void onConnectionChanged(String state) {
            hudView.setConnectionState(state);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        hudView = new AssistantHudView(this);
        hudView.setListener(new AssistantHudView.Listener() {
            @Override
            public void onHudDismissed() {
                finishHud();
            }
        });
        FrameLayout.LayoutParams hudParams = new FrameLayout.LayoutParams(
                Math.max(dp(240), getResources().getDisplayMetrics().widthPixels - dp(20)),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        hudParams.topMargin = dp(28);
        root.addView(hudView, hudParams);
        setupView = buildSetupView();
        root.addView(setupView, setupParams());
        setContentView(root);
        GlassesRelayBridge.start(this);
        debugOverlayOnly = hasDebugText(getIntent());
        popupLaunch = hasPopupLaunch(getIntent());
        maybeShowDebugMessage(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!debugOverlayOnly) {
            GlassesRelayBridge.addListener(bridgeListener);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                RelayService.ensureStarted(MainActivity.this);
            }
        }, 450L);
        renderCurrentIntentState();
    }

    private void renderCurrentIntentState() {
        AssistantMessage latest = GlassesRelayBridge.latestMessage();
        if (debugOverlayOnly) {
            showSetup(false);
            finishIfStillIdle();
        } else if (popupLaunch && latest != null && !latest.isExpired(System.currentTimeMillis())) {
            showSetup(false);
            hudView.showMessage(latest);
        } else {
            showSetup(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (setupMode) {
            renderSetupStatus();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        debugOverlayOnly = hasDebugText(intent);
        popupLaunch = hasPopupLaunch(intent);
        maybeShowDebugMessage(intent);
        if (!debugOverlayOnly) {
            renderCurrentIntentState();
        }
    }

    @Override
    protected void onStop() {
        GlassesRelayBridge.removeListener(bridgeListener);
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event != null && event.getRepeatCount() > 0) {
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (setupMode) {
                    openAccessibilitySettings();
                    return true;
                }
                hudView.togglePinned();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case 183:
                hudView.scrollForward();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case 184:
                hudView.scrollBack();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (setupMode) {
                    finish();
                    return true;
                }
                if (hudView.dismissOrUnpin()) {
                    return true;
                }
                return false;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void maybeShowDebugMessage(Intent intent) {
        if (intent == null || !intent.hasExtra("debug_text")) {
            return;
        }
        String text = intent.getStringExtra("debug_text");
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
        showSetup(false);
        hudView.showMessage(message);
    }

    private boolean hasDebugText(Intent intent) {
        return intent != null && intent.hasExtra("debug_text");
    }

    private boolean hasPopupLaunch(Intent intent) {
        return intent != null && intent.getBooleanExtra(RelayService.EXTRA_SHOW_POPUP, false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout buildSetupView() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.START);
        container.setPadding(dp(18), dp(17), dp(18), dp(17));
        container.setBackgroundColor(Color.BLACK);
        container.setVisibility(android.view.View.INVISIBLE);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getApplicationInfo().icon);
        logo.setAdjustViewBounds(true);
        brandRow.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView label = label("ASSIST BRIDGE", 15, ACCENT);
        label.setPadding(dp(10), 0, 0, 0);
        brandRow.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        setupTitle = label("", 25, TEXT);
        setupBody = label("", 17, TEXT);
        setupButton = label("", 16, ACCENT);
        setupButton.setGravity(Gravity.CENTER);
        setupButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        setupButton.setBackground(outline());

        container.addView(brandRow, matchWrap());
        container.addView(setupTitle, matchWrap(13));
        container.addView(setupBody, matchWrap(14));
        container.addView(setupButton, matchWrap(22));
        renderSetupStatus();
        return container;
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(1), 1.04f);
        view.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
        return view;
    }

    private GradientDrawable outline() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.BLACK);
        drawable.setStroke(dp(1), RULE);
        drawable.setCornerRadius(dp(5));
        return drawable;
    }

    private FrameLayout.LayoutParams setupParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(dp(240), getResources().getDisplayMetrics().widthPixels - dp(36)),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        params.topMargin = dp(48);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return matchWrap(0);
    }

    private LinearLayout.LayoutParams matchWrap(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private void showSetup(boolean show) {
        setupMode = show;
        if (show) {
            renderSetupStatus();
        }
        setupView.setVisibility(show ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        if (show) {
            hudView.dismissOrUnpin();
        }
    }

    private void renderSetupStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        setupTitle.setText(enabled ? "Accessibility ON" : "Accessibility OFF");
        setupTitle.setTextColor(enabled ? ACCENT : TEXT);
        setupBody.setText(enabled
                ? "Ready. Gemini replies will appear here as a popup."
                : "Tap to open settings and enable Assist Bridge.");
        setupBody.setTextColor(MUTED);
        setupButton.setText(enabled ? "ACCESSIBILITY SETTINGS" : "OPEN ACCESSIBILITY");
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        ComponentName expected = new ComponentName(this, AssistBridgeAccessibilityService.class);
        String expectedLong = expected.flattenToString();
        String expectedShort = expected.flattenToShortString();
        for (String value : enabledServices.split(":")) {
            if (value.equalsIgnoreCase(expectedLong) || value.equalsIgnoreCase(expectedShort)) {
                return true;
            }
            ComponentName actual = ComponentName.unflattenFromString(value);
            if (expected.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    private void openAccessibilitySettings() {
        ComponentName component = new ComponentName(this, AssistBridgeAccessibilityService.class);
        Intent detailsIntent = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME", component.flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStart(detailsIntent)) {
            return;
        }
        tryStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private boolean tryStart(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void finishHud() {
        if (setupMode) {
            return;
        }
        if (finishingFromHud || isFinishing()) {
            return;
        }
        GlassesRelayBridge.clearLatestMessage();
        finishingFromHud = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 80L);
    }

    private void finishIfStillIdle() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!hudView.hasVisibleMessage()) {
                    if (setupMode) {
                        return;
                    }
                    finishHud();
                }
            }
        }, 1400L);
    }
}
