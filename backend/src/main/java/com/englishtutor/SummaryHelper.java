package com.englishtutor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SummaryHelper {

    private static final Pattern TURN_PATTERN = Pattern.compile("用户：(.*?)\nAI：(.*?)(?=\n用户：|$)", Pattern.DOTALL);
    private static final Pattern ON_REPEAT = Pattern.compile("(?i)(?:\\bon\\b\\s*){3,}");

    private SummaryHelper() {
    }

    public static String compactHistory(String history) {
        if (history == null || history.trim().isEmpty()) {
            return "（暂无对话记录）";
        }

        Matcher matcher = TURN_PATTERN.matcher(history.trim());
        StringBuilder sb = new StringBuilder();
        int turns = 0;
        while (matcher.find() && turns < 4) {
            String user = matcher.group(1).trim();
            String ai = ReplySanitizer.sanitizeModelReply(matcher.group(2).trim());
            if (ReplySanitizer.looksBroken(ai)) {
                ai = "Good response.";
            }
            if (ai.length() > 100) {
                ai = ai.substring(0, 100).trim() + "...";
            }
            sb.append("用户: ").append(user).append('\n');
            sb.append("AI: ").append(ai).append("\n\n");
            turns++;
        }

        String compact = sb.toString().trim();
        return compact.isEmpty() ? "（暂无有效对话记录）" : compact;
    }

    public static String sanitizeSummary(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text.trim();
        cleaned = cleaned.replaceAll("[\\uFFFD]", "");
        cleaned = ON_REPEAT.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("(?i)\\bon\\b(?=\\s+on\\b)", "");
        cleaned = cleaned.replaceAll("在在+", "在");
        cleaned = cleaned.replaceAll("，{2,}", "，");
        cleaned = cleaned.replaceAll("\\.{4,}", "...");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = dedupeBrokenLines(cleaned);
        cleaned = truncateIfRunaway(cleaned);

        return cleaned.trim();
    }

    public static boolean looksBroken(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }
        String value = text.trim();
        if (ON_REPEAT.matcher(value).find()) {
            return true;
        }
        if (value.toLowerCase().contains(" on on on")) {
            return true;
        }
        if (value.indexOf('\uFFFD') >= 0) {
            return true;
        }
        int onCount = countToken(value, " on ");
        return onCount >= 4 || value.length() > 1200;
    }

    public static String fallbackSummary(String history, String scene) {
        List<String> userLines = extractUserLines(history);
        String sceneName = sceneNameZh(scene);

        StringBuilder sb = new StringBuilder();
        sb.append("【练习场景】\n").append(sceneName).append('\n').append('\n');
        sb.append("【表现亮点】\n");
        sb.append("1. 能够主动用英语开口表达。\n");
        if (!userLines.isEmpty()) {
            sb.append("2. 能说出完整句子，例如：").append(userLines.get(0)).append('\n').append('\n');
        } else {
            sb.append("2. 愿意持续进行场景化对话练习。\n\n");
        }
        sb.append("【待改进】\n");
        sb.append("1. 可进一步丰富词汇和句型。\n");
        sb.append("2. 尝试用更长的句子描述经历和观点。\n\n");
        sb.append("【下次建议】\n");
        sb.append("1. 继续练习 ").append(sceneName).append(" 场景。\n");
        sb.append("2. 每轮尽量说 2 句以上，并注意语法准确性。");
        return sb.toString();
    }

    private static List<String> extractUserLines(String history) {
        List<String> lines = new ArrayList<>();
        if (history == null) {
            return lines;
        }
        Matcher matcher = TURN_PATTERN.matcher(history.trim());
        while (matcher.find()) {
            String user = matcher.group(1).trim();
            if (!user.isEmpty()) {
                lines.add(user);
            }
        }
        return lines;
    }

    private static String sceneNameZh(String scene) {
        switch (scene) {
            case "interview":
                return "求职面试";
            case "restaurant":
                return "餐厅点餐";
            case "meeting":
                return "商务会议";
            case "travel":
                return "旅行问路";
            case "shopping":
                return "购物交流";
            default:
                return "英语口语练习";
        }
    }

    private static String dedupeBrokenLines(String text) {
        String[] lines = text.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.matches("(?i)^on\\.?$")) {
                continue;
            }
            if (ON_REPEAT.matcher(trimmed).find()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
        }
        return sb.toString();
    }

    private static String truncateIfRunaway(String text) {
        if (text.length() <= 900) {
            return text;
        }
        int cut = text.indexOf("【下次建议】");
        if (cut > 0) {
            String head = text.substring(0, cut);
            return head + "【下次建议】\n1. 继续本场景练习。\n2. 多说完整句子，注意语法。";
        }
        return text.substring(0, 900).trim() + "...";
    }

    private static int countToken(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
