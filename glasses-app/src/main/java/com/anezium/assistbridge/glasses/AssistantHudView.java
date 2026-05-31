package com.anezium.assistbridge.glasses;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AssistantHudView extends LinearLayout {
    interface Listener {
        void onHudDismissed();
    }

    private static final int MAX_PARAGRAPH_CHARS = 230;
    private static final int ACCENT = Color.rgb(156, 255, 98);
    private static final int TEXT = Color.rgb(224, 255, 231);
    private static final int DIM = Color.rgb(122, 166, 132);
    private static final int RULE = Color.rgb(43, 84, 50);

    private final TextView appLabel;
    private final TextView titleLabel;
    private final TextView bodyLabel;
    private final TextView hintLabel;
    private final List<String> wrappedLines = new ArrayList<>();
    private final List<String> pages = new ArrayList<>();

    private AssistantMessage message;
    private Listener listener;
    private boolean visible;
    private int pageIndex;
    private int visibleLineCount = 3;
    private int lastWrapWidth;

    AssistantHudView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(android.view.Gravity.START);
        setVisibility(INVISIBLE);
        setPadding(dp(14), dp(12), dp(14), dp(12));
        setBackground(outline());

        appLabel = label(12f, ACCENT);
        titleLabel = label(18f, TEXT);
        bodyLabel = label(17f, TEXT);
        hintLabel = label(13f, DIM);

        titleLabel.setSingleLine(true);
        titleLabel.setEllipsize(TextUtils.TruncateAt.END);
        bodyLabel.setSingleLine(false);
        bodyLabel.setLineSpacing(dp(2), 1.06f);
        hintLabel.setSingleLine(true);
        hintLabel.setEllipsize(TextUtils.TruncateAt.END);

        addView(appLabel, matchWrap());
        addView(titleLabel, matchWrap(3));
        addView(bodyLabel, matchWrap(8));
        addView(hintLabel, matchWrap(9));
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setConnectionState(String state) {
        // Idle state stays invisible/black on the glasses. The phone app shows connection details.
    }

    void showMessage(AssistantMessage nextMessage) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        showMessage(nextMessage, Math.max(dp(240), screenWidth - dp(20)), getResources().getDisplayMetrics().heightPixels);
    }

    void showMessage(AssistantMessage nextMessage, int widthPx, int screenHeightPx) {
        if (nextMessage == null || nextMessage.text.isEmpty()) {
            return;
        }
        boolean changedMessage = message == null
                || !message.id.equals(nextMessage.id)
                || !message.text.equals(nextMessage.text);
        message = nextMessage;
        pageIndex = 0;
        visible = true;
        visibleLineCount = targetVisibleLines(nextMessage.text);
        if (changedMessage) {
            lastWrapWidth = 0;
            wrappedLines.clear();
            pages.clear();
        }
        rebuildPages(widthPx);
        render();
        setVisibility(VISIBLE);
    }

    boolean hasVisibleMessage() {
        return visible && message != null;
    }

    int recommendedHeightPx(int widthPx, int screenHeightPx) {
        if (message == null) {
            return dp(120);
        }
        rebuildPages(widthPx);
        float lineHeight = bodyLabel.getLineHeight();
        int wanted = dp(72) + Math.round(lineHeight * visibleLineCount);
        int max = Math.max(dp(180), screenHeightPx - dp(48));
        return Math.max(dp(126), Math.min(wanted, max));
    }

    boolean handleKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return dismissOrUnpin();
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case 183:
                scrollForward();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case 184:
                scrollBack();
                return true;
            case KeyEvent.KEYCODE_BACK:
                return dismissOrUnpin();
            default:
                return false;
        }
    }

    void togglePinned() {
        dismissOrUnpin();
    }

    void scrollForward() {
        if (!visible || pages.isEmpty()) {
            return;
        }
        pageIndex = Math.min(pages.size() - 1, pageIndex + 1);
        render();
    }

    void scrollBack() {
        if (!visible || pages.isEmpty()) {
            return;
        }
        pageIndex = Math.max(0, pageIndex - 1);
        render();
    }

    boolean dismissOrUnpin() {
        if (!visible) {
            return false;
        }
        visible = false;
        setVisibility(INVISIBLE);
        notifyDismissed();
        return true;
    }

    private void rebuildPages(int widthPx) {
        int wrapWidth = Math.max(1, widthPx - getPaddingLeft() - getPaddingRight() - dp(2));
        if (wrapWidth == lastWrapWidth && !pages.isEmpty()) {
            return;
        }
        lastWrapWidth = wrapWidth;
        wrappedLines.clear();
        pages.clear();
        if (message == null) {
            return;
        }

        for (String paragraph : buildParagraphs(message.text)) {
            wrapParagraph(paragraph, wrapWidth, wrappedLines);
            wrappedLines.add("");
        }
        while (!wrappedLines.isEmpty() && wrappedLines.get(wrappedLines.size() - 1).isEmpty()) {
            wrappedLines.remove(wrappedLines.size() - 1);
        }
        buildPagesFromLines();
    }

    private void buildPagesFromLines() {
        StringBuilder current = new StringBuilder();
        int count = 0;
        for (String line : wrappedLines) {
            if (count > 0 && count + 1 > visibleLineCount) {
                addPage(current);
                current.setLength(0);
                count = 0;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
            count++;
        }
        addPage(current);
        if (pages.isEmpty()) {
            pages.add(message == null ? "" : message.text.trim());
        }
        pageIndex = Math.max(0, Math.min(pageIndex, pages.size() - 1));
    }

    private void addPage(StringBuilder builder) {
        String page = trimPage(builder.toString());
        if (!page.isEmpty()) {
            pages.add(page);
        }
    }

    private String trimPage(String value) {
        String clean = value == null ? "" : value.trim();
        while (clean.contains("\n\n\n")) {
            clean = clean.replace("\n\n\n", "\n\n");
        }
        return clean;
    }

    private void render() {
        if (!visible || message == null) {
            setVisibility(INVISIBLE);
            return;
        }
        if (pages.isEmpty()) {
            rebuildPages(getResources().getDisplayMetrics().widthPixels - dp(20));
        }

        appLabel.setText("ASSIST BRIDGE");
        titleLabel.setText(message.source.isEmpty() ? "Gemini" : message.source);
        bodyLabel.setMaxLines(Math.max(3, visibleLineCount));
        bodyLabel.setMaxHeight(dp(34) * Math.max(3, visibleLineCount));
        bodyLabel.setText(pages.get(pageIndex));
        hintLabel.setText(hintText());
        hintLabel.setTextColor(DIM);
        setVisibility(VISIBLE);
    }

    private String hintText() {
        String page = (pageIndex + 1) + "/" + Math.max(1, pages.size());
        String time = timeText();
        return time.isEmpty() ? page : time + "  " + page;
    }

    private void wrapParagraph(String paragraph, int wrapWidth, List<String> out) {
        String clean = paragraph.trim();
        if (clean.isEmpty()) {
            return;
        }
        String[] words = clean.split("\\s+");
        StringBuilder line = new StringBuilder();
        Paint paint = bodyLabel.getPaint();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) <= wrapWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
            }
            appendWord(word, wrapWidth, out, line, paint);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
    }

    private void appendWord(String word, int wrapWidth, List<String> out, StringBuilder line, Paint paint) {
        if (paint.measureText(word) <= wrapWidth) {
            line.append(word);
            return;
        }
        int start = 0;
        while (start < word.length()) {
            int count = paint.breakText(word, start, word.length(), true, wrapWidth, null);
            if (count <= 0) {
                break;
            }
            out.add(word.substring(start, start + count));
            start += count;
        }
    }

    private List<String> buildParagraphs(String raw) {
        List<String> result = new ArrayList<>();
        String normalized = raw == null ? "" : raw.replace('\r', '\n');
        String[] blocks = normalized.split("\\n\\s*\\n");
        for (String block : blocks) {
            String clean = block.replaceAll("[ \\t]+", " ").trim();
            if (clean.isEmpty()) {
                continue;
            }
            String[] explicitLines = clean.split("\\n");
            for (String line : explicitLines) {
                splitBlock(line.trim(), result);
            }
        }
        if (result.isEmpty() && raw != null) {
            result.add(raw.replaceAll("\\s+", " ").trim());
        }
        return result;
    }

    private void splitBlock(String block, List<String> out) {
        if (block.length() <= MAX_PARAGRAPH_CHARS || looksStructured(block)) {
            out.add(block);
            return;
        }

        String[] sentences = block.split("(?<=[.!?])\\s+");
        StringBuilder paragraph = new StringBuilder();
        for (String sentence : sentences) {
            String cleanSentence = sentence.trim();
            if (cleanSentence.isEmpty()) {
                continue;
            }
            if (paragraph.length() > 0 && paragraph.length() + cleanSentence.length() + 1 > MAX_PARAGRAPH_CHARS) {
                out.add(paragraph.toString());
                paragraph.setLength(0);
            }
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(cleanSentence);
        }
        if (paragraph.length() > 0) {
            out.add(paragraph.toString());
        }
    }

    private boolean looksStructured(String block) {
        return block.startsWith("- ")
                || block.startsWith("* ")
                || block.startsWith("• ")
                || block.matches("^\\d+[.)].*");
    }

    private int targetVisibleLines(String text) {
        int chars = text == null ? 0 : text.length();
        int lineBreaks = text == null ? 0 : Math.max(0, text.split("\\R", -1).length - 1);
        if (chars > 920 || lineBreaks >= 10) {
            return 13;
        }
        if (chars > 700 || lineBreaks >= 8) {
            return 11;
        }
        if (chars > 500 || lineBreaks >= 6) {
            return 9;
        }
        if (chars > 330 || lineBreaks >= 4) {
            return 7;
        }
        if (chars > 170 || lineBreaks >= 2) {
            return 5;
        }
        return 3;
    }

    private String timeText() {
        if (message == null || message.createdAtMillis <= 0L) {
            return "";
        }
        return DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(message.createdAtMillis);
    }

    private TextView label(float size, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
        return view;
    }

    private GradientDrawable outline() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.BLACK);
        drawable.setStroke(dp(1), RULE);
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private LayoutParams matchWrap() {
        return matchWrap(0);
    }

    private LayoutParams matchWrap(int topMarginDp) {
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private void notifyDismissed() {
        Listener localListener = listener;
        if (localListener != null) {
            localListener.onHudDismissed();
        }
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
