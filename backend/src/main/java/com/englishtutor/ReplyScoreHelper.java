package com.englishtutor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReplyScoreHelper {

    private static final Pattern SCORE_LINE = Pattern.compile("\\[评分[：:]\\s*(\\d+)\\s*/\\s*10\\]\\s*(.*)$", Pattern.DOTALL);

    private ReplyScoreHelper() {
    }

    public static Map<String, String> buildScoredReply(String cleanEnglish, String userMessage) {
        String body = cleanEnglish == null ? "" : cleanEnglish.trim();
        if (ReplySanitizer.looksBroken(body)) {
            body = "";
        }

        int score = estimateScore(userMessage, body);
        String comment = scoreComment(score);

        Map<String, String> out = new HashMap<>();
        out.put("cleanReply", body);
        out.put("score", String.valueOf(score));
        out.put("scoreComment", comment);
        out.put("reply", body + "\n[评分: " + score + "/10] " + comment);
        return out;
    }

    public static Map<String, String> splitReplyAndScore(String reply) {
        Map<String, String> out = new HashMap<>();
        String raw = reply == null ? "" : reply.trim();
        Matcher matcher = SCORE_LINE.matcher(raw);
        if (matcher.find()) {
            out.put("reply", raw);
            out.put("cleanReply", raw.substring(0, matcher.start()).trim());
            out.put("score", matcher.group(1));
            out.put("scoreComment", matcher.group(2) == null ? "" : matcher.group(2).trim());
            return out;
        }

        out.put("reply", raw);
        out.put("cleanReply", raw);
        out.put("score", "8");
        out.put("scoreComment", "表达不错，继续保持");
        return out;
    }

    private static int estimateScore(String userMessage, String reply) {
        String user = userMessage == null ? "" : userMessage.trim();
        if (user.isEmpty()) {
            return 7;
        }
        int score = 8;
        int words = user.split("\\s+").length;
        if (words >= 8) {
            score = 9;
        } else if (words <= 3) {
            score = 7;
        }
        if (reply != null && reply.length() > 40) {
            score = Math.min(10, score + 1);
        }
        return Math.max(6, Math.min(10, score));
    }

    private static String scoreComment(int score) {
        if (score >= 9) {
            return "表达流畅自然";
        }
        if (score >= 8) {
            return "表达不错，继续保持";
        }
        return "可以再丰富一点表达";
    }
}
