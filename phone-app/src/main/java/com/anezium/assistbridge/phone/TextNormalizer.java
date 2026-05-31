package com.anezium.assistbridge.phone;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class TextNormalizer {
    private static final int MAX_TEXT_LENGTH = 6000;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private TextNormalizer() {
    }

    static String normalize(CharSequence value) {
        if (value == null) {
            return "";
        }

        return value.toString()
                .replace('\u200B', ' ')
                .replace('\u200C', ' ')
                .replace('\u200D', ' ')
                .replace('\uFEFF', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    static boolean isUiNoise(String value) {
        String text = normalize(value);
        if (text.length() <= 1) {
            return true;
        }

        String lower = foldForCompare(text);
        if (lower.matches("\\d{1,2}:\\d{2}")) {
            return true;
        }

        switch (lower) {
            case "gemini":
            case "google":
            case "assistant":
            case "google assistant":
            case "ask gemini":
            case "talk to gemini":
            case "type, talk, or share a photo":
            case "type, talk, or share a photo with gemini":
            case "microphone":
            case "mic":
            case "keyboard":
            case "send":
            case "close":
            case "back":
            case "more":
            case "menu":
            case "settings":
            case "share":
            case "copy":
            case "open":
            case "search":
            case "listen":
            case "stop":
            case "pause":
            case "resume":
            case "read aloud":
            case "new chat":
            case "chats":
            case "recent":
            case "account":
            case "profile picture":
            case "good response":
            case "bad response":
            case "like":
            case "dislike":
            case "fermer":
            case "retour":
            case "plus":
            case "menu principal":
            case "parametres":
            case "envoyer":
            case "micro":
            case "clavier":
            case "partager":
            case "copier":
            case "poignee de deplacement":
            case "bonne reponse":
            case "mauvaise reponse":
            case "options de partage":
            case "options d'actions":
            case "ecouter":
            case "ajouter une piece jointe":
            case "demander a gemini":
            case "open gemini live":
            case "mettre a jour la position":
                return true;
            default:
                return lower.startsWith("gemini est une ia et peut se tromper")
                        || lower.startsWith("votre confidentialite et gemini")
                        || lower.startsWith("en savoir plus sur la facon dont la localisation est utilisee");
        }
    }

    static boolean isTransientAssistantState(String value) {
        String text = normalize(value);
        if (text.isEmpty()) {
            return true;
        }

        String lower = foldForCompare(text);
        if (lower.equals("un instant")
                || lower.equals("just a moment")
                || lower.equals("thinking")
                || lower.equals("generating")
                || lower.equals("generation en cours")) {
            return true;
        }

        boolean shortText = lower.length() < 140;
        return shortText && (lower.contains("un instant")
                || lower.contains("arreter de generer")
                || lower.contains("stop generating")
                || lower.contains("arrete la saisie vocale")
                || lower.contains("arreter la saisie vocale")
                || lower.contains("stop voice input")
                || lower.contains("stop listening")
                || lower.contains("apercu de l'image")
                || lower.contains("apercu de limage"));
    }

    static boolean isFullAssistantAppSurface(String value) {
        String lower = foldForCompare(value);
        if (lower.isEmpty()) {
            return false;
        }

        if (lower.startsWith("liste de conversations")
                || lower.startsWith("conversation list")
                || lower.contains("liste de conversations ")) {
            return true;
        }

        if ((lower.startsWith("comment puis-je vous aider")
                || lower.startsWith("how can i help"))
                && (lower.contains("nouvelle discussion")
                || lower.contains("new chat")
                || lower.contains("demandez a gemini")
                || lower.contains("ask gemini")
                || lower.contains("ouvrir la barre laterale")
                || lower.contains("open sidebar")
                || lower.contains("gemini flash"))) {
            return true;
        }

        int appChromeSignals = 0;
        if (lower.contains("comment puis-je vous aider") || lower.contains("how can i help")) {
            appChromeSignals++;
        }
        if (lower.contains("ouvrir la barre laterale") || lower.contains("open sidebar")) {
            appChromeSignals++;
        }
        if (lower.contains("gemini flash")) {
            appChromeSignals++;
        }
        if (lower.contains("ajouter des pieces jointes") || lower.contains("add attachments")) {
            appChromeSignals++;
        }
        if (lower.contains("demander a gemini") || lower.contains("ask gemini")) {
            appChromeSignals++;
        }
        if (lower.contains("demandez a gemini")) {
            appChromeSignals++;
        }
        if (lower.contains("nouvelle discussion") || lower.contains("new chat")) {
            appChromeSignals++;
        }
        if (lower.contains("gemini peut se tromper") || lower.contains("gemini can make mistakes")) {
            appChromeSignals++;
        }
        if (lower.contains("historique") || lower.contains("recent chats")) {
            appChromeSignals++;
        }
        return appChromeSignals >= 2;
    }

    static List<String> compactDistinct(List<? extends CharSequence> rawTexts) {
        List<String> result = new ArrayList<>();
        for (CharSequence rawText : rawTexts) {
            String candidate = normalize(rawText);
            if (candidate.isEmpty() || isUiNoise(candidate)) {
                continue;
            }

            boolean covered = false;
            for (Iterator<String> iterator = result.iterator(); iterator.hasNext(); ) {
                String existing = iterator.next();
                if (existing.equals(candidate) || existing.contains(candidate)) {
                    covered = true;
                    break;
                }
                if (candidate.contains(existing)) {
                    iterator.remove();
                }
            }

            if (!covered) {
                result.add(candidate);
            }
        }
        return result;
    }

    static String joinCandidate(List<String> snippets) {
        StringBuilder builder = new StringBuilder();
        for (String snippet : snippets) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(snippet);
            if (builder.length() >= MAX_TEXT_LENGTH) {
                return builder.substring(0, MAX_TEXT_LENGTH).trim();
            }
        }
        return builder.toString().trim();
    }

    static String fingerprint(String value) {
        return foldForCompare(value)
                .replaceAll("[\\p{Punct}\\s]+", "");
    }

    private static String foldForCompare(String value) {
        String normalized = java.text.Normalizer.normalize(normalize(value), java.text.Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }
}
