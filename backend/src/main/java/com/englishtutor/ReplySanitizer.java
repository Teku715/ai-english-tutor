package com.englishtutor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReplySanitizer {

    private static final Pattern REPEATED_WORD = Pattern.compile("(\\b[\\w']+\\b)(?:\\s+\\1){2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKET_BLOCK = Pattern.compile("\\[(?:评分|Correction|Score|AI|纠错)[^\\]]*\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern BROKEN_QUOTE = Pattern.compile("\"\\s*instead\\s*\"?", Pattern.CASE_INSENSITIVE);

    private ReplySanitizer() {
    }

    public static String sanitizeModelReply(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text.trim();
        cleaned = BRACKET_BLOCK.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\[评分[^\\]]*\\]", " ");
        cleaned = cleaned.replaceAll("评分[：:]\\s*\\d+\\s*/\\s*10", " ");
        cleaned = BROKEN_QUOTE.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = dedupeAdjacentWords(cleaned);
        cleaned = truncateRepeatingTail(cleaned);
        cleaned = collapseRepeatedWords(cleaned);

        if (cleaned.length() > 280) {
            cleaned = cleaned.substring(0, 280).trim();
            int lastSpace = cleaned.lastIndexOf(' ');
            if (lastSpace > 100) {
                cleaned = cleaned.substring(0, lastSpace).trim();
            }
        }

        return cleaned.trim();
    }

    public static boolean looksBroken(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }
        String value = text.trim();
        if (value.length() < 8) {
            return true;
        }
        if (value.contains("[评分") || value.contains("[Correction") || value.contains("[Score")) {
            return true;
        }
        if (REPEATED_WORD.matcher(value).find()) {
            return true;
        }
        if (hasAdjacentDuplicateWords(value)) {
            return true;
        }
        if (value.toLowerCase().contains(" on on")) {
            return true;
        }
        String[] words = value.split("\\s+");
        if (words.length >= 6) {
            String last = words[words.length - 1].toLowerCase();
            int repeat = 0;
            for (int i = words.length - 1; i >= 0; i--) {
                if (words[i].equalsIgnoreCase(last)) {
                    repeat++;
                } else {
                    break;
                }
            }
            if (repeat >= 4) {
                return true;
            }
        }
        return false;
    }

    public static String fallbackReply(String scene) {
        switch (scene) {
            case "interview":
                return "Thank you for sharing that. Could you give me a specific example from your previous work?";
            case "restaurant":
                return "Sure. Would you like anything to drink while you decide on your main course?";
            case "meeting":
                return "Good point. What do you think is the best next step for our team?";
            case "travel":
                return "No problem. It is about a ten-minute walk from here. Would you like more detailed directions?";
            case "shopping":
                return "Of course. What size and color are you looking for today?";
            default:
                return "That sounds good. Could you tell me a little more about that?";
        }
    }

    private static boolean hasAdjacentDuplicateWords(String text) {
        String[] words = text.split("\\s+");
        for (int i = 1; i < words.length; i++) {
            if (words[i].equalsIgnoreCase(words[i - 1])) {
                return true;
            }
        }
        return false;
    }

    private static String dedupeAdjacentWords(String text) {
        String[] words = text.split("\\s+");
        if (words.length < 2) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        String prev = null;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (prev != null && word.equalsIgnoreCase(prev)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(word);
            prev = word;
        }
        return sb.toString().trim();
    }

    private static String truncateRepeatingTail(String text) {
        String[] words = text.split("\\s+");
        if (words.length < 5) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        String prev = null;
        int streak = 0;
        for (String word : words) {
            if (word.equalsIgnoreCase(prev)) {
                streak++;
                if (streak >= 1) {
                    break;
                }
            } else {
                streak = 0;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(word);
            prev = word;
        }
        return sb.toString().trim();
    }

    private static String collapseRepeatedWords(String text) {
        Matcher matcher = REPEATED_WORD.matcher(text);
        if (matcher.find()) {
            return text.substring(0, matcher.start()).trim();
        }
        return text;
    }
}
