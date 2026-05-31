package com.anezium.assistbridge.phone;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GeminiAccessibilityService extends AccessibilityService {
    private static final String TAG = "AssistBridgeA11y";
    private static final String GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String GEMINI_APP_PACKAGE = "com.google.android.apps.bard";
    private static final String[] TARGET_PACKAGE_ARRAY = {GOOGLE_APP_PACKAGE, GEMINI_APP_PACKAGE};
    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(TARGET_PACKAGE_ARRAY));
    private static final long CAPTURE_DELAY_MS = 350L;
    private static final long CAPTURE_RETRY_DELAY_MS = 1200L;
    private static final long CAPTURE_FINAL_RETRY_DELAY_MS = 2500L;
    private static final long ASSISTANT_SESSION_MS = 45000L;
    private static volatile long explicitArmExpiresAt;
    private static volatile String explicitArmReason = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingPackageName = "";
    private String pendingClassName = "";
    private int pendingEventType;
    private long assistantSessionExpiresAt;
    private final List<CharSequence> pendingEventTexts = new ArrayList<>();
    private String lastFingerprint = "";

    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                captureCurrentWindow();
            } catch (RuntimeException exception) {
                Log.w(TAG, "capture failed", exception);
            }
        }
    };

    static void armForAssistantRequest(String reason) {
        explicitArmExpiresAt = System.currentTimeMillis() + ASSISTANT_SESSION_MS;
        explicitArmReason = reason == null ? "" : reason;
        Log.i(TAG, "capture armed reason=" + explicitArmReason);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.packageNames = TARGET_PACKAGE_ARRAY;
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    | AccessibilityEvent.TYPE_ANNOUNCEMENT;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            info.notificationTimeout = 200;
            setServiceInfo(info);
        }
        CaptureStore.setServiceRunning(true);
        AssistBridgeRelay.startFromStoredToken(this);
        Log.i(TAG, "service connected targetPackages=" + Arrays.toString(TARGET_PACKAGE_ARRAY));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();
        if (!TARGET_PACKAGES.contains(packageName)) {
            return;
        }

        CharSequence className = event.getClassName();
        long now = System.currentTimeMillis();
        boolean explicitActive = now <= explicitArmExpiresAt;
        if (explicitArmExpiresAt > assistantSessionExpiresAt) {
            assistantSessionExpiresAt = explicitArmExpiresAt;
        }
        boolean assistantEvent = isAssistantSessionEvent(event);
        if (assistantEvent) {
            assistantSessionExpiresAt = now + ASSISTANT_SESSION_MS;
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !explicitActive) {
            assistantSessionExpiresAt = 0L;
            explicitArmExpiresAt = 0L;
        }

        pendingPackageName = packageName;
        pendingClassName = className == null ? "" : className.toString();
        pendingEventType = event.getEventType();
        pendingEventTexts.clear();
        pendingEventTexts.addAll(event.getText());
        if (event.getContentDescription() != null) {
            pendingEventTexts.add(event.getContentDescription());
        }
        Log.d(TAG, "event type=" + eventTypeName(event.getEventType())
                + " package=" + packageName
                + " eventTextCount=" + pendingEventTexts.size()
                + " assistantSession=" + isAssistantSessionActive()
                + " class=" + className);
        if (!isAssistantSessionActive()) {
            handler.removeCallbacks(captureRunnable);
            Log.d(TAG, "event ignored outside assistant session class=" + className);
            return;
        }
        handler.removeCallbacks(captureRunnable);
        handler.postDelayed(captureRunnable, CAPTURE_DELAY_MS);
        handler.postDelayed(captureRunnable, CAPTURE_RETRY_DELAY_MS);
        handler.postDelayed(captureRunnable, CAPTURE_FINAL_RETRY_DELAY_MS);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(captureRunnable);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(captureRunnable);
        CaptureStore.setServiceRunning(false);
        Log.i(TAG, "service destroyed");
        super.onDestroy();
    }

    private void captureCurrentWindow() {
        if (!isAssistantSessionActive()) {
            Log.d(TAG, "capture skipped: outside assistant session class=" + pendingClassName);
            return;
        }

        CaptureAttempt windowsAttempt = captureTargetWindows();
        if (windowsAttempt.matchedTargetWindow) {
            if (windowsAttempt.hasText()) {
                publishIfNew(windowsAttempt);
            } else {
                Log.d(TAG, "capture skipped: target window had no assistant answer source="
                        + windowsAttempt.source
                        + " nodes=" + windowsAttempt.nodeCount);
            }
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            CaptureAttempt eventAttempt = captureEventTextFallback();
            if (eventAttempt.hasText()) {
                publishIfNew(eventAttempt);
            } else {
                Log.d(TAG, "capture skipped: no active root and no event text");
            }
            return;
        }

        try {
            AssistantTextExtractor.Result result = AssistantTextExtractor.extract(root);
            String rootPackage = packageNameOf(root);
            if (!TARGET_PACKAGES.contains(rootPackage)) {
                CaptureAttempt eventAttempt = captureEventTextFallback();
                if (eventAttempt.hasText()) {
                    publishIfNew(eventAttempt);
                } else {
                    Log.d(TAG, "active root ignored package=" + rootPackage
                            + " nodes=" + result.nodeCount);
                }
                return;
            }

            if (result.text.isEmpty()) {
                Log.d(TAG, "capture skipped: active target root had no assistant answer package="
                        + rootPackage
                        + " nodes=" + result.nodeCount);
                return;
            }

            publishIfNew(new CaptureAttempt(rootPackage, result.text, result.nodeCount, "active-root",
                    false, result.fullScreenTextSurface, result.boundsSummary));
        } finally {
            recycle(root);
        }
    }

    private CaptureAttempt captureTargetWindows() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) {
            Log.d(TAG, "window scan: no windows available");
            return CaptureAttempt.empty("windows");
        }

        List<CharSequence> snippets = new ArrayList<>();
        int nodeCount = 0;
        String sourcePackage = pendingPackageName;
        int matchedWindows = 0;
        boolean hasFullScreenTextSurface = false;
        String fullScreenBoundsSummary = "";

        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                continue;
            }

            try {
                String rootPackage = packageNameOf(root);
                String title = windowTitle(window);
                if (!isTargetWindow(rootPackage, title)) {
                    continue;
                }

                matchedWindows++;
                sourcePackage = rootPackage.isEmpty() ? sourcePackage : rootPackage;
                AssistantTextExtractor.Result result = AssistantTextExtractor.extract(root);
                nodeCount += result.nodeCount;
                snippets.addAll(result.snippets);
                if (result.fullScreenTextSurface) {
                    hasFullScreenTextSurface = true;
                    fullScreenBoundsSummary = result.boundsSummary;
                }
                Log.d(TAG, "window match package=" + rootPackage
                        + " title=" + title
                        + " nodes=" + result.nodeCount
                        + " snippets=" + result.snippets.size()
                        + " fullScreenText=" + result.fullScreenTextSurface
                        + " bounds=" + result.boundsSummary
                        + " preview=" + preview(result.text));
            } finally {
                recycle(root);
            }
        }

        if (matchedWindows == 0) {
            Log.d(TAG, "window scan matched no target windows total=" + windows.size());
            return CaptureAttempt.empty("windows");
        }

        AssistantTextExtractor.Result combined = AssistantTextExtractor.extract(snippets);
        Log.d(TAG, "window scan matched=" + matchedWindows
                + " combinedSnippets=" + combined.snippets.size()
                + " preview=" + preview(combined.text));
        return new CaptureAttempt(sourcePackage, combined.text, nodeCount, "windows:" + matchedWindows,
                true, hasFullScreenTextSurface, fullScreenBoundsSummary);
    }

    private CaptureAttempt captureEventTextFallback() {
        AssistantTextExtractor.Result result = AssistantTextExtractor.extract(pendingEventTexts);
        if (result.text.isEmpty()) {
            return CaptureAttempt.empty("event");
        }
        Log.d(TAG, "event fallback snippets=" + result.snippets.size()
                + " preview=" + preview(result.text));
        return new CaptureAttempt(pendingPackageName, result.text, result.nodeCount, "event");
    }

    private void publishIfNew(CaptureAttempt attempt) {
        String fingerprint = TextNormalizer.fingerprint(attempt.text);
        if (fingerprint.isEmpty()) {
            Log.d(TAG, "capture ignored empty fingerprint source=" + attempt.source);
            return;
        }
        if (fingerprint.equals(lastFingerprint)) {
            Log.d(TAG, "capture duplicate source=" + attempt.source + " preview=" + preview(attempt.text));
            return;
        }
        if (TextNormalizer.isTransientAssistantState(attempt.text)) {
            Log.d(TAG, "capture transient ignored source=" + attempt.source + " preview=" + preview(attempt.text));
            return;
        }
        if (TextNormalizer.isFullAssistantAppSurface(attempt.text)) {
            assistantSessionExpiresAt = 0L;
            explicitArmExpiresAt = 0L;
            Log.d(TAG, "capture app surface ignored source=" + attempt.source + " preview=" + preview(attempt.text));
            return;
        }
        if (attempt.fullScreenTextSurface) {
            assistantSessionExpiresAt = 0L;
            explicitArmExpiresAt = 0L;
            Log.d(TAG, "capture full-screen text surface ignored source=" + attempt.source
                    + " bounds=" + attempt.boundsSummary
                    + " preview=" + preview(attempt.text));
            return;
        }

        lastFingerprint = fingerprint;
        Log.i(TAG, "capture publish source=" + attempt.source
                + " package=" + attempt.packageName
                + " chars=" + attempt.text.length()
                + " nodes=" + attempt.nodeCount
                + " preview=" + preview(attempt.text));
        CaptureSnapshot snapshot = new CaptureSnapshot(
                attempt.packageName,
                attempt.text,
                System.currentTimeMillis(),
                attempt.nodeCount,
                pendingEventType
        );
        CaptureStore.update(snapshot);
        AssistBridgeRelay.sendAssistantText(this, attempt.packageName, attempt.text, fingerprint);
    }

    private static boolean isTargetWindow(String packageName, String title) {
        if (TARGET_PACKAGES.contains(packageName)) {
            return true;
        }

        String normalizedTitle = TextNormalizer.normalize(title).toLowerCase(Locale.ROOT);
        return normalizedTitle.equals("gemini") || normalizedTitle.equals("google");
    }

    private boolean isAssistantSessionActive() {
        return System.currentTimeMillis() <= assistantSessionExpiresAt;
    }

    private static boolean isAssistantSessionEvent(AccessibilityEvent event) {
        if (event == null) {
            return false;
        }

        String className = event.getClassName() == null
                ? ""
                : event.getClassName().toString().toLowerCase(Locale.ROOT);
        if (className.contains(".assistant.")
                || className.contains("floaty")
                || className.contains("voice")
                || className.contains("opa")) {
            return true;
        }

        List<CharSequence> eventTexts = new ArrayList<>();
        eventTexts.addAll(event.getText());
        if (event.getContentDescription() != null) {
            eventTexts.add(event.getContentDescription());
        }
        String eventText = TextNormalizer.normalize(TextUtils.join(" ", eventTexts)).toLowerCase(Locale.ROOT);
        return eventText.contains("arrête la saisie vocale")
                || eventText.contains("arrete la saisie vocale")
                || eventText.contains("arrêter la saisie vocale")
                || eventText.contains("arreter la saisie vocale")
                || eventText.contains("stop voice input")
                || eventText.contains("stop listening")
                || eventText.contains("arrêter de générer")
                || eventText.contains("arreter de generer")
                || eventText.contains("stop generating");
    }

    private static String packageNameOf(AccessibilityNodeInfo node) {
        CharSequence packageName = node == null ? null : node.getPackageName();
        return packageName == null ? "" : packageName.toString();
    }

    private static String windowTitle(AccessibilityWindowInfo window) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return "";
        }
        CharSequence title = window.getTitle();
        return title == null ? "" : title.toString();
    }

    private String preview(String text) {
        if (!isDebuggable()) {
            return "[redacted]";
        }
        String normalized = TextNormalizer.normalize(text);
        if (normalized.length() > 140) {
            return normalized.substring(0, 140) + "...";
        }
        return normalized;
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static String eventTypeName(int eventType) {
        switch (eventType) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                return "WINDOW_STATE";
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                return "WINDOWS";
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                return "CONTENT";
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                return "TEXT";
            case AccessibilityEvent.TYPE_ANNOUNCEMENT:
                return "ANNOUNCEMENT";
            default:
                return String.valueOf(eventType);
        }
    }

    @SuppressWarnings("deprecation")
    private static void recycle(AccessibilityNodeInfo node) {
        node.recycle();
    }

    private static final class CaptureAttempt {
        final String packageName;
        final String text;
        final int nodeCount;
        final String source;
        final boolean matchedTargetWindow;
        final boolean fullScreenTextSurface;
        final String boundsSummary;

        CaptureAttempt(String packageName, String text, int nodeCount, String source) {
            this(packageName, text, nodeCount, source, false, false, "");
        }

        CaptureAttempt(String packageName, String text, int nodeCount, String source, boolean matchedTargetWindow) {
            this(packageName, text, nodeCount, source, matchedTargetWindow, false, "");
        }

        CaptureAttempt(
                String packageName,
                String text,
                int nodeCount,
                String source,
                boolean matchedTargetWindow,
                boolean fullScreenTextSurface,
                String boundsSummary
        ) {
            this.packageName = packageName == null ? "" : packageName;
            this.text = text == null ? "" : text;
            this.nodeCount = nodeCount;
            this.source = source == null ? "" : source;
            this.matchedTargetWindow = matchedTargetWindow;
            this.fullScreenTextSurface = fullScreenTextSurface;
            this.boundsSummary = boundsSummary == null ? "" : boundsSummary;
        }

        boolean hasText() {
            return !text.isEmpty();
        }

        static CaptureAttempt empty(String source) {
            return new CaptureAttempt("", "", 0, source);
        }
    }
}
