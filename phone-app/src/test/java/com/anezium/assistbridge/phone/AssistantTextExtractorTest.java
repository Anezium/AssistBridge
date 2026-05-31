package com.anezium.assistbridge.phone;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AssistantTextExtractorTest {
    @Test
    public void chromeContainersAreDetectedFromViewIds() {
        assertTrue(AssistantTextExtractor.isChromeContainer(
                "android.view.ViewGroup",
                "com.google.android.googlequicksearchbox:id/assistant_robin_zero_state_compose_view"));
        assertTrue(AssistantTextExtractor.isChromeContainer(
                "android.widget.FrameLayout",
                "com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_container"));
        assertTrue(AssistantTextExtractor.isChromeContainer(
                "androidx.compose.ui.platform.ComposeView",
                "com.google.android.googlequicksearchbox:id/assistant_robin_chat_compose_toolbar"));
        assertFalse(AssistantTextExtractor.isChromeContainer(
                "android.view.ViewGroup",
                "com.google.android.googlequicksearchbox:id/assistant_robin_conversation_container"));
    }

    @Test
    public void textExtractionDropsInputHintsAndChrome() {
        assertFalse(AssistantTextExtractor.shouldCollectText(
                "Ask Gemini",
                "android.widget.EditText",
                "com.google.android.googlequicksearchbox:id/assistant_robin_input_collapsed_text_half_sheet",
                true,
                false,
                true,
                false,
                true));
        assertFalse(AssistantTextExtractor.shouldCollectText(
                "How can I help?",
                "android.widget.TextView",
                "",
                false,
                false,
                false,
                false,
                true));
        assertFalse(AssistantTextExtractor.shouldCollectText(
                "Gemini 2.5 Flash",
                "android.widget.TextView",
                "",
                false,
                false,
                false,
                true,
                false));
    }

    @Test
    public void textExtractionKeepsAssistantBodyText() {
        assertTrue(AssistantTextExtractor.shouldCollectText(
                "Tomorrow should be cloudy in Paris, with light rain in the evening and a cooler night.",
                "android.widget.TextView",
                "",
                false,
                false,
                false,
                false,
                false));
        assertTrue(AssistantTextExtractor.shouldCollectText(
                "Tomorrow should be cloudy in Paris, with light rain in the evening and a cooler night.",
                "android.widget.TextView",
                "",
                false,
                false,
                false,
                true,
                false));
    }

    @Test
    public void contentDescriptionsFromControlsAreIgnored() {
        assertFalse(AssistantTextExtractor.shouldCollectContentDescription(
                "Open sidebar",
                "",
                "android.view.View",
                "",
                false,
                false,
                false,
                true,
                false));
        assertFalse(AssistantTextExtractor.shouldCollectContentDescription(
                "Open sidebar",
                "",
                "android.widget.Button",
                "",
                false,
                false,
                true,
                false,
                false));
    }

    @Test
    public void fullScreenTextSurfacesAreRejected() {
        assertTrue(AssistantTextExtractor.isFullScreenTextSurface(
                1080,
                2340,
                80,
                120,
                1000,
                2220));
        assertFalse(AssistantTextExtractor.isFullScreenTextSurface(
                1080,
                2340,
                80,
                980,
                1000,
                1550));
        assertFalse(AssistantTextExtractor.isFullScreenTextSurface(
                1080,
                2340,
                80,
                120,
                1000,
                1300));
    }
}
