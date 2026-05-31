package com.anezium.assistbridge.phone;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TextNormalizerTest {
    @Test
    public void compactDistinctDropsCommonAssistantChrome() {
        List<String> result = TextNormalizer.compactDistinct(Arrays.asList(
                "Gemini",
                "Ask Gemini",
                "Microphone",
                "Voici un resume court de tes emails Gmail."
        ));

        assertEquals(1, result.size());
        assertEquals("Voici un resume court de tes emails Gmail.", result.get(0));
    }

    @Test
    public void compactDistinctKeepsLongerParentText() {
        List<String> result = TextNormalizer.compactDistinct(Arrays.asList(
                "Un resume",
                "Un resume propre et actionnable"
        ));

        assertEquals(1, result.size());
        assertEquals("Un resume propre et actionnable", result.get(0));
    }

    @Test
    public void fingerprintIgnoresWhitespaceAndPunctuation() {
        assertEquals(
                TextNormalizer.fingerprint("Salut, ca marche ?"),
                TextNormalizer.fingerprint("Salut ca marche")
        );
    }

    @Test
    public void noiseFilterKeepsRealSentences() {
        assertTrue(TextNormalizer.isUiNoise("Copy"));
        assertFalse(TextNormalizer.isUiNoise("Je peux verifier tes notes et faire un resume."));
    }

    @Test
    public void transientAssistantStatesAreNotRelayed() {
        assertTrue(TextNormalizer.isTransientAssistantState("Un instant..."));
        assertTrue(TextNormalizer.isTransientAssistantState("Arreter de generer"));
        assertTrue(TextNormalizer.isTransientAssistantState("Arrête la saisie vocale"));
        assertFalse(TextNormalizer.isTransientAssistantState("La meteo designe le temps qu'il fait a un endroit donne."));
    }

    @Test
    public void fullGeminiAppSurfacesAreNotRelayed() {
        assertTrue(TextNormalizer.isFullAssistantAppSurface(
                "Liste de conversations quelle sera la meteo demain Dimanche France Nuageux"));
        assertTrue(TextNormalizer.isFullAssistantAppSurface(
                "Comment puis-je vous aider, Saim ? Ajouter des pieces jointes Demandez a Gemini Ouvrir la barre laterale Gemini Flash Nouvelle discussion"));
        assertFalse(TextNormalizer.isFullAssistantAppSurface(
                "Dimanche France Nuageux Elevee: 22 Basse: 12 Precip.: 10 %"));
    }
}
