package com.anezium.assistbridge.phone;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

final class AssistantTextExtractor {
    private static final int MAX_DEPTH = 48;
    private static final int MAX_NODES = 800;
    private static final int MIN_ACTIONABLE_TEXT_LENGTH = 80;

    private AssistantTextExtractor() {
    }

    static Result extract(AccessibilityNodeInfo root) {
        List<Candidate> candidates = new ArrayList<>();
        Counter counter = new Counter();
        Rect rootBounds = boundsOf(root);
        collect(root, candidates, counter, 0);
        return extractCandidates(candidates, counter.value, rootBounds);
    }

    static Result extract(List<? extends CharSequence> rawTexts) {
        return extractRawTexts(rawTexts, 0);
    }

    private static Result extractRawTexts(List<? extends CharSequence> rawTexts, int nodeCount) {
        List<String> snippets = TextNormalizer.compactDistinct(rawTexts);
        return new Result(TextNormalizer.joinCandidate(snippets), snippets, nodeCount, false, "");
    }

    private static Result extractCandidates(List<Candidate> candidates, int nodeCount, Rect rootBounds) {
        List<CharSequence> rawTexts = new ArrayList<>();
        for (Candidate candidate : candidates) {
            rawTexts.add(candidate.text);
        }

        List<String> snippets = TextNormalizer.compactDistinct(rawTexts);
        Rect textBounds = mergedTextBounds(candidates, snippets);
        boolean fullScreenTextSurface = isFullScreenTextSurface(rootBounds, textBounds);
        return new Result(
                TextNormalizer.joinCandidate(snippets),
                snippets,
                nodeCount,
                fullScreenTextSurface,
                boundsSummary(rootBounds, textBounds)
        );
    }

    private static void collect(AccessibilityNodeInfo node, List<Candidate> candidates, Counter counter, int depth) {
        collect(node, candidates, counter, depth, false, false);
    }

    private static void collect(
            AccessibilityNodeInfo node,
            List<Candidate> candidates,
            Counter counter,
            int depth,
            boolean actionableAncestor,
            boolean chromeAncestor
    ) {
        if (node == null || depth > MAX_DEPTH || counter.value >= MAX_NODES) {
            return;
        }

        counter.value++;
        String className = stringValue(node.getClassName());
        String viewId = node.getViewIdResourceName();
        boolean nodeActionable = isActionable(node, className);
        boolean insideActionable = actionableAncestor || nodeActionable;
        boolean insideChrome = chromeAncestor || isChromeContainer(className, viewId);
        Rect bounds = boundsOf(node);

        if (node.isVisibleToUser()) {
            addTextIfPresent(candidates, node.getText(), bounds, className, viewId,
                    node.isEditable(), node.isPassword(), nodeActionable, actionableAncestor, insideChrome);

            CharSequence contentDescription = node.getContentDescription();
            if (shouldCollectContentDescription(contentDescription, node.getText(), className, viewId,
                    node.isEditable(), node.isPassword(), nodeActionable, actionableAncestor, insideChrome)) {
                addIfPresent(candidates, contentDescription, bounds);
            }
        }

        int childCount = node.getChildCount();
        for (int index = 0; index < childCount && counter.value < MAX_NODES; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                collect(child, candidates, counter, depth + 1, insideActionable, insideChrome);
            } finally {
                recycle(child);
            }
        }
    }

    static boolean shouldCollectText(
            CharSequence value,
            String className,
            String viewId,
            boolean editable,
            boolean password,
            boolean nodeActionable,
            boolean actionableAncestor,
            boolean chromeAncestor
    ) {
        String normalized = TextNormalizer.normalize(value);
        if (normalized.isEmpty() || chromeAncestor || editable || password || isTextInputClass(className)) {
            return false;
        }

        if ((nodeActionable || actionableAncestor || isControlClass(className))
                && normalized.length() < MIN_ACTIONABLE_TEXT_LENGTH) {
            return false;
        }

        return !isChromeContainer(className, viewId);
    }

    static boolean shouldCollectContentDescription(
            CharSequence contentDescription,
            CharSequence nodeText,
            String className,
            String viewId,
            boolean editable,
            boolean password,
            boolean nodeActionable,
            boolean actionableAncestor,
            boolean chromeAncestor
    ) {
        String normalized = TextNormalizer.normalize(contentDescription);
        if (normalized.isEmpty()
                || chromeAncestor
                || editable
                || password
                || nodeActionable
                || actionableAncestor
                || isControlClass(className)
                || isTextInputClass(className)
                || isChromeContainer(className, viewId)) {
            return false;
        }

        return !normalized.equals(TextNormalizer.normalize(nodeText));
    }

    static boolean isChromeContainer(String className, String viewId) {
        String id = TextNormalizer.normalize(viewId).toLowerCase(java.util.Locale.ROOT);
        if (id.isEmpty()) {
            return false;
        }

        return id.contains("zero_state")
                || id.contains("chat_input")
                || id.contains("_input_")
                || id.endsWith("_input")
                || id.contains("input_collapsed")
                || id.contains("toolbar")
                || id.contains("header")
                || id.contains("button")
                || id.contains("btn")
                || id.contains("suggestion")
                || id.contains("promo")
                || id.contains("upsell");
    }

    private static void addTextIfPresent(
            List<Candidate> candidates,
            CharSequence value,
            Rect bounds,
            String className,
            String viewId,
            boolean editable,
            boolean password,
            boolean nodeActionable,
            boolean actionableAncestor,
            boolean chromeAncestor
    ) {
        if (shouldCollectText(value, className, viewId, editable, password,
                nodeActionable, actionableAncestor, chromeAncestor)) {
            candidates.add(new Candidate(value, bounds));
        }
    }

    private static void addIfPresent(List<Candidate> candidates, CharSequence value, Rect bounds) {
        if (!TextNormalizer.normalize(value).isEmpty()) {
            candidates.add(new Candidate(value, bounds));
        }
    }

    static boolean isFullScreenTextSurface(int rootWidth, int rootHeight, int left, int top, int right, int bottom) {
        if (rootWidth <= 0 || rootHeight <= 0 || right <= left || bottom <= top) {
            return false;
        }

        float widthRatio = (right - left) / (float) rootWidth;
        float heightRatio = (bottom - top) / (float) rootHeight;
        float topRatio = top / (float) rootHeight;
        float bottomRatio = bottom / (float) rootHeight;
        return widthRatio >= 0.68f
                && heightRatio >= 0.72f
                && topRatio <= 0.18f
                && bottomRatio >= 0.82f;
    }

    private static boolean isFullScreenTextSurface(Rect rootBounds, Rect textBounds) {
        if (rootBounds == null || textBounds == null || rootBounds.isEmpty() || textBounds.isEmpty()) {
            return false;
        }

        return isFullScreenTextSurface(
                rootBounds.width(),
                rootBounds.height(),
                textBounds.left - rootBounds.left,
                textBounds.top - rootBounds.top,
                textBounds.right - rootBounds.left,
                textBounds.bottom - rootBounds.top
        );
    }

    private static Rect mergedTextBounds(List<Candidate> candidates, List<String> snippets) {
        Rect merged = new Rect();
        boolean hasBounds = false;
        for (Candidate candidate : candidates) {
            if (candidate.bounds.isEmpty() || !isKeptSnippet(candidate.text, snippets)) {
                continue;
            }

            if (!hasBounds) {
                merged.set(candidate.bounds);
                hasBounds = true;
            } else {
                merged.union(candidate.bounds);
            }
        }
        return hasBounds ? merged : new Rect();
    }

    private static boolean isKeptSnippet(CharSequence value, List<String> snippets) {
        String candidate = TextNormalizer.normalize(value);
        if (candidate.isEmpty()) {
            return false;
        }

        for (String snippet : snippets) {
            if (snippet.equals(candidate) || snippet.contains(candidate) || candidate.contains(snippet)) {
                return true;
            }
        }
        return false;
    }

    private static Rect boundsOf(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        if (node != null) {
            node.getBoundsInScreen(bounds);
        }
        return bounds;
    }

    private static String boundsSummary(Rect rootBounds, Rect textBounds) {
        if (rootBounds == null || textBounds == null || rootBounds.isEmpty() || textBounds.isEmpty()) {
            return "";
        }

        return "root=" + formatBounds(rootBounds)
                + " text=" + formatBounds(textBounds);
    }

    private static String formatBounds(Rect bounds) {
        return "[" + bounds.left + "," + bounds.top + "]["
                + bounds.right + "," + bounds.bottom + "]";
    }

    private static boolean isActionable(AccessibilityNodeInfo node, String className) {
        return node.isClickable()
                || node.isLongClickable()
                || node.isCheckable()
                || node.isEditable()
                || node.isPassword()
                || isControlClass(className)
                || isTextInputClass(className);
    }

    private static boolean isControlClass(String className) {
        String normalized = TextNormalizer.normalize(className).toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("button")
                || normalized.endsWith("imagebutton")
                || normalized.endsWith("checkbox")
                || normalized.endsWith("switch")
                || normalized.endsWith("radiobutton")
                || normalized.endsWith("spinner")
                || normalized.endsWith("seekbar");
    }

    private static boolean isTextInputClass(String className) {
        String normalized = TextNormalizer.normalize(className).toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("edittext")
                || normalized.contains("textfield")
                || normalized.contains("textinput");
    }

    private static String stringValue(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("deprecation")
    private static void recycle(AccessibilityNodeInfo node) {
        node.recycle();
    }

    static final class Result {
        final String text;
        final List<String> snippets;
        final int nodeCount;
        final boolean fullScreenTextSurface;
        final String boundsSummary;

        Result(String text, List<String> snippets, int nodeCount, boolean fullScreenTextSurface, String boundsSummary) {
            this.text = text == null ? "" : text;
            this.snippets = snippets;
            this.nodeCount = nodeCount;
            this.fullScreenTextSurface = fullScreenTextSurface;
            this.boundsSummary = boundsSummary == null ? "" : boundsSummary;
        }
    }

    private static final class Candidate {
        final CharSequence text;
        final Rect bounds;

        Candidate(CharSequence text, Rect bounds) {
            this.text = text;
            this.bounds = new Rect(bounds);
        }
    }

    private static final class Counter {
        int value;
    }
}
