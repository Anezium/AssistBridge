package com.anezium.assistbridge.phone;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.anezium.assistbridge.protocol.HudTextSize;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String TAG = "AssistBridge";
    private static final String GEMINI_PACKAGE = "com.google.android.apps.bard";
    private static final String GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final int BG = Color.rgb(3, 8, 5);
    private static final int SURFACE = Color.rgb(8, 18, 11);
    private static final int SURFACE_2 = Color.rgb(11, 25, 15);
    private static final int BORDER = Color.rgb(35, 70, 43);
    private static final int ACCENT = Color.rgb(156, 255, 98);
    private static final int ACCENT_DARK = Color.rgb(18, 46, 23);
    private static final int TEXT = Color.rgb(224, 255, 231);
    private static final int MUTED = Color.rgb(135, 174, 145);
    private static final int WARNING = Color.rgb(255, 130, 104);

    private final android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView statusView;
    private TextView relayView;
    private TextView fontSizeValueView;
    private TextView metaView;
    private TextView captureView;
    private Button relayButton;

    private final CaptureStore.Listener captureListener = new CaptureStore.Listener() {
        @Override
        public void onCaptureChanged(CaptureSnapshot snapshot) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    renderSnapshot(snapshot);
                }
            });
        }
    };

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            renderStatus();
            refreshHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildContentView());
        AssistBridgeRelay.startFromStoredToken(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        CaptureStore.addListener(captureListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus();
        renderSnapshot(CaptureStore.latest());
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onStop() {
        CaptureStore.removeListener(captureListener);
        super.onStop();
    }

    private View buildContentView() {
        int padding = dp(18);
        ScrollView page = new ScrollView(this);
        page.setFillViewport(false);
        page.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding + statusBarHeight(), padding, padding);
        page.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = label("AssistBridge", 30, TEXT, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, matchWrap());

        statusView = new TextView(this);
        relayView = new TextView(this);
        root.addView(statusPanel("Phone capture", statusView), matchWrapWithTopMargin(10));
        root.addView(statusPanel("Glasses relay", relayView), matchWrapWithTopMargin(8));

        root.addView(sectionLabel("Controls"), matchWrapWithTopMargin(18));

        relayButton = primaryButton("Authorize Hi Rokid");
        relayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestRokidAuthorization();
            }
        });
        root.addView(relayButton, matchWrapWithTopMargin(6));

        Button accessibilityButton = secondaryButton("Phone accessibility");
        accessibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAccessibilitySettings();
            }
        });

        Button glassesAccessibilityButton = secondaryButton("Glasses accessibility");
        glassesAccessibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (AssistBridgeRelay.openGlassesAccessibilitySettings(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "Opening glasses accessibility", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Glasses relay is not ready yet", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button assistantButton = secondaryButton("Open assistant");
        assistantButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAssistant();
            }
        });

        LinearLayout actionRow = horizontalRow();
        actionRow.addView(assistantButton, weightedRowItem());
        actionRow.addView(glassesAccessibilityButton, weightedRowItemWithLeftMargin());
        root.addView(actionRow, matchWrapWithTopMargin(8));

        LinearLayout setupRow = horizontalRow();
        setupRow.addView(accessibilityButton, weightedRowItem());
        root.addView(setupRow, matchWrapWithTopMargin(8));

        root.addView(buildHudControl(), matchWrapWithTopMargin(16));

        LinearLayout captureHeader = horizontalRow();
        LinearLayout captureTitle = new LinearLayout(this);
        captureTitle.setOrientation(LinearLayout.VERTICAL);
        captureTitle.setGravity(Gravity.CENTER_VERTICAL);
        captureTitle.addView(sectionLabel("Latest capture"), matchWrap());
        metaView = label("NONE", 12, MUTED, Typeface.BOLD);
        metaView.setPadding(0, dp(4), 0, 0);
        captureTitle.addView(metaView, matchWrap());
        captureHeader.addView(captureTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout utilityRow = horizontalRow();
        Button copyButton = rowButton("Copy");
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyLatest();
            }
        });
        utilityRow.addView(copyButton, weightedRowItem());

        Button clearButton = rowButton("Clear");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CaptureStore.clear();
            }
        });
        utilityRow.addView(clearButton, weightedRowItemWithLeftMargin());
        captureHeader.addView(utilityRow, new LinearLayout.LayoutParams(dp(176), LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(captureHeader, matchWrapWithTopMargin(18));

        captureView = new TextView(this);
        captureView.setTextSize(16);
        captureView.setTextColor(TEXT);
        captureView.setLineSpacing(dp(2), 1.08f);
        captureView.setTextIsSelectable(true);
        captureView.setMinHeight(dp(260));
        captureView.setPadding(dp(14), dp(13), dp(14), dp(13));
        captureView.setBackground(roundRect(SURFACE, BORDER, 8));
        root.addView(captureView, matchWrapWithTopMargin(8));

        return page;
    }

    private TextView label(String text, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView sectionLabel(String text) {
        TextView view = label(text.toUpperCase(Locale.ROOT), 11, MUTED, Typeface.BOLD);
        view.setGravity(Gravity.START);
        return view;
    }

    private LinearLayout statusPanel(String title, TextView valueView) {
        LinearLayout panel = panel();
        panel.addView(sectionLabel(title), matchWrap());
        valueView.setTextSize(16);
        valueView.setTextColor(TEXT);
        valueView.setLineSpacing(dp(1), 1.05f);
        valueView.setPadding(0, dp(7), 0, 0);
        panel.addView(valueView, matchWrap());
        return panel;
    }

    private LinearLayout buildHudControl() {
        LinearLayout panel = panel();
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labelColumn = new LinearLayout(this);
        labelColumn.setOrientation(LinearLayout.VERTICAL);
        labelColumn.setGravity(Gravity.CENTER_VERTICAL);
        labelColumn.addView(sectionLabel("Glasses HUD"), matchWrap());

        fontSizeValueView = label("", 18, TEXT, Typeface.BOLD);
        fontSizeValueView.setPadding(0, dp(5), 0, 0);
        labelColumn.addView(fontSizeValueView, matchWrap());
        panel.addView(labelColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button decreaseFontButton = compactButton("-");
        decreaseFontButton.setTextSize(21);
        decreaseFontButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adjustHudFontSize(-1.0f);
            }
        });
        panel.addView(decreaseFontButton, squareRowItem());

        Button increaseFontButton = compactButton("+");
        increaseFontButton.setTextSize(21);
        increaseFontButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adjustHudFontSize(1.0f);
            }
        });
        panel.addView(increaseFontButton, squareRowItemWithLeftMargin());
        return panel;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundRect(SURFACE, BORDER, 8));
        return panel;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button primaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(BG);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundRect(ACCENT, ACCENT, 8));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(TEXT);
        button.setBackground(roundRect(SURFACE_2, BORDER, 8));
        return button;
    }

    private Button baseButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setStateListAnimator(null);
        return button;
    }

    private Button rowButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(15);
        button.setTextColor(ACCENT);
        button.setBackground(roundRect(ACCENT_DARK, BORDER, 8));
        return button;
    }

    private Button compactButton(String label) {
        Button button = rowButton(label);
        button.setMinHeight(dp(44));
        button.setMinWidth(dp(48));
        button.setPadding(0, 0, 0, dp(2));
        return button;
    }

    private void renderStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        boolean running = CaptureStore.isServiceRunning();
        statusView.setText(enabled
                ? "Accessibility ON" + (running ? " / service active" : " / waiting")
                : "Accessibility OFF");
        statusView.setTextColor(enabled ? ACCENT : WARNING);

        AssistBridgeRelay.Snapshot relay = AssistBridgeRelay.snapshot(this);
        String relayText = relay.hasToken
                ? (relay.cxrConnected ? "CXR-L ON" : "CXR-L waiting")
                + " / " + (relay.glassConnected ? "connected" : "disconnected")
                + "\n" + relay.bootstrapState
                : "Relay: Hi Rokid authorization required";
        if (!relay.lastSentPreview.isEmpty()) {
            relayText += "\nSent: " + relay.lastSentPreview;
        } else if (!relay.lastStatus.isEmpty()) {
            relayText += "\n" + relay.lastStatus;
        }
        relayView.setText(relayText);
        relayView.setTextColor(relay.hasToken ? TEXT : WARNING);
        relayButton.setText(relay.hasToken ? "Restart glasses relay" : "Authorize Hi Rokid");
        relayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (AssistBridgeRelay.snapshot(MainActivity.this).hasToken) {
                    AssistBridgeRelay.requestBootstrap(MainActivity.this);
                    Toast.makeText(MainActivity.this, "Glasses relay restarted", Toast.LENGTH_SHORT).show();
                } else {
                    requestRokidAuthorization();
                }
            }
        });
        renderHudFontSize();
    }

    private void renderHudFontSize() {
        if (fontSizeValueView == null) {
            return;
        }
        fontSizeValueView.setText("Font size: " + formatHudFontSize(HudSettingsStore.fontSizeSp(this)));
        fontSizeValueView.setTextColor(TEXT);
    }

    private void adjustHudFontSize(float deltaSp) {
        float current = HudSettingsStore.fontSizeSp(this);
        float next = HudSettingsStore.setFontSizeSp(this, HudTextSize.clamp(current + deltaSp));
        AssistBridgeRelay.sendHudSettings(this);
        renderHudFontSize();
        Toast.makeText(this, "Glasses font size " + formatHudFontSize(next), Toast.LENGTH_SHORT).show();
    }

    private String formatHudFontSize(float value) {
        if (Math.abs(value - Math.round(value)) < 0.01f) {
            return Math.round(value) + "sp";
        }
        return String.format(Locale.ROOT, "%.1fsp", value);
    }

    private void requestRokidAuthorization() {
        if (!RokidAuthorization.request(this)) {
            Toast.makeText(this, "Hi Rokid Global not found or authorization failed", Toast.LENGTH_LONG).show();
        }
    }

    private void openAccessibilitySettings() {
        ComponentName componentName = new ComponentName(this, GeminiAccessibilityService.class);
        Intent detailsIntent = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME", componentName.flattenToString());
        if (tryStart(detailsIntent)) {
            return;
        }
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RokidAuthorization.REQUEST_CODE) {
            return;
        }

        String token = RokidAuthorization.parseToken(resultCode, data);
        if (token.isEmpty()) {
            Toast.makeText(this, "Hi Rokid authorization canceled", Toast.LENGTH_SHORT).show();
            renderStatus();
            return;
        }

        AssistBridgeRelay.start(this, token);
        Toast.makeText(this, "Glasses relay authorized", Toast.LENGTH_SHORT).show();
        renderStatus();
    }

    private void renderSnapshot(CaptureSnapshot snapshot) {
        CaptureSnapshot safeSnapshot = snapshot == null ? CaptureSnapshot.EMPTY : snapshot;
        if (!safeSnapshot.hasText()) {
            metaView.setText("None yet");
            captureView.setText("Waiting for visible Gemini text...");
            return;
        }

        String time = DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault())
                .format(safeSnapshot.capturedAtMillis);
        metaView.setText(time
                + "  /  " + safeSnapshot.nodeCount + " nodes"
                + "  /  " + safeSnapshot.packageName);
        captureView.setText(safeSnapshot.text);
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null) {
            List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            ComponentName expected = new ComponentName(this, GeminiAccessibilityService.class);
            for (AccessibilityServiceInfo service : services) {
                if (service.getResolveInfo() == null || service.getResolveInfo().serviceInfo == null) {
                    continue;
                }
                ComponentName actual = new ComponentName(
                        service.getResolveInfo().serviceInfo.packageName,
                        service.getResolveInfo().serviceInfo.name
                );
                if (expected.equals(actual)) {
                    return true;
                }
            }
        }

        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        String expectedName = new ComponentName(this, GeminiAccessibilityService.class).flattenToString();
        return enabledServices.toLowerCase(Locale.ROOT).contains(expectedName.toLowerCase(Locale.ROOT));
    }

    private void openAssistant() {
        GeminiAccessibilityService.armForAssistantRequest("phone open assistant");
        String assistantPackage = defaultAssistantPackage();
        if (tryStartVoiceCommand(assistantPackage)) {
            return;
        }

        if (!GOOGLE_PACKAGE.equals(assistantPackage) && tryStartVoiceCommand(GOOGLE_PACKAGE)) {
            return;
        }

        if (tryStartHandsFreeSearch(assistantPackage)) {
            return;
        }

        Intent assistIntent = new Intent(Intent.ACTION_ASSIST);
        assistIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!tryStart(assistIntent)) {
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GEMINI_PACKAGE));
            if (!tryStart(marketIntent)) {
                Toast.makeText(this, "Unable to open the assistant", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean tryStartVoiceCommand(String packageName) {
        Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!TextUtils.isEmpty(packageName)) {
            intent.setPackage(packageName);
        }
        return tryStart(intent);
    }

    private boolean tryStartHandsFreeSearch(String packageName) {
        Intent intent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(RecognizerIntent.EXTRA_SECURE, false);
        if (!TextUtils.isEmpty(packageName)) {
            intent.setPackage(packageName);
        }
        return tryStart(intent);
    }

    private String defaultAssistantPackage() {
        String voiceInteractionService = Settings.Secure.getString(
                getContentResolver(),
                "voice_interaction_service"
        );
        String packageName = packageFromComponent(voiceInteractionService);
        if (!TextUtils.isEmpty(packageName)) {
            return packageName;
        }

        String assistant = Settings.Secure.getString(getContentResolver(), "assistant");
        return packageFromComponent(assistant);
    }

    private String packageFromComponent(String flattenedComponent) {
        if (TextUtils.isEmpty(flattenedComponent)) {
            return "";
        }
        ComponentName componentName = ComponentName.unflattenFromString(flattenedComponent);
        if (componentName != null) {
            return componentName.getPackageName();
        }
        int separator = flattenedComponent.indexOf('/');
        return separator > 0 ? flattenedComponent.substring(0, separator) : flattenedComponent;
    }

    private boolean tryStart(Intent intent) {
        try {
            startActivity(intent);
            Log.i(TAG, "started intent action=" + intent.getAction() + " package=" + intent.getPackage());
            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "failed intent action=" + intent.getAction() + " package=" + intent.getPackage(), exception);
            return false;
        }
    }

    private void copyLatest() {
        CaptureSnapshot snapshot = CaptureStore.latest();
        if (snapshot == null || !snapshot.hasText()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("AssistBridge capture", snapshot.text));
            Toast.makeText(this, "Text copied", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams matchWrapWithTopMargin(int topDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams weightedRowItem() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightedRowItemWithLeftMargin() {
        LinearLayout.LayoutParams params = weightedRowItem();
        params.leftMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams squareRowItem() {
        return new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams squareRowItemWithLeftMargin() {
        LinearLayout.LayoutParams params = squareRowItem();
        params.leftMargin = dp(8);
        return params;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int statusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId <= 0) {
            return 0;
        }
        return getResources().getDimensionPixelSize(resourceId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
