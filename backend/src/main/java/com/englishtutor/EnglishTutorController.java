package com.englishtutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EnglishTutorController {

    @Autowired
    private LLMService llmService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private SpeechService speechService;

    // 场景列表
    @GetMapping("/scenes")
    public List<Map<String, String>> scenes() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(Map.of("id", "interview", "name", "求职面试", "desc", "模拟 job interview 对话练习"));
        list.add(Map.of("id", "restaurant", "name", "餐厅点餐", "desc", "Restaurant ordering and dining"));
        list.add(Map.of("id", "meeting", "name", "商务会议", "desc", "Business meeting in English"));
        list.add(Map.of("id", "travel", "name", "旅行问路", "desc", "Asking for directions while traveling"));
        list.add(Map.of("id", "shopping", "name", "购物交流", "desc", "Shopping and bargaining in English"));
        return list;
    }

    // 开启新练习会话
    @PostMapping("/session")
    public Map<String, Object> createSession(@RequestBody Map<String, String> req) {
        String scene = req.getOrDefault("scene", "interview");
        String sessionId = conversationService.startSession(scene);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("scene", scene);
        return result;
    }

    // 对话接口（支持sessionId或旧的history方式）
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> req) {
        String scene = req.getOrDefault("scene", "interview");
        String message = req.getOrDefault("message", "");
        String history = req.getOrDefault("history", "");
        String sessionId = req.get("sessionId");

        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "消息内容不能为空");
            err.put("code", 400);
            return err;
        }

        String historyStr = history;
        if (sessionId != null && conversationService.hasSession(sessionId)) {
            historyStr = conversationService.buildHistoryString(sessionId);
            conversationService.addMessage(sessionId, "用户", message);
        }

        String systemPrompt = buildSystemPrompt(scene);
        List<Map<String, Object>> historyMessages = parseHistoryMessages(historyStr, scene, 3);
        String rawReply = llmService.chatDialog(systemPrompt, historyMessages, message.trim());
        String cleanReply = ReplySanitizer.sanitizeModelReply(rawReply);
        if (ReplySanitizer.looksBroken(cleanReply)) {
            cleanReply = ReplySanitizer.fallbackReply(scene);
        }
        Map<String, String> parsed = ReplyScoreHelper.buildScoredReply(cleanReply, message.trim());

        if (sessionId != null && conversationService.hasSession(sessionId)) {
            conversationService.addMessage(sessionId, "AI", parsed.get("reply"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("reply", parsed.get("reply"));
        result.put("cleanReply", parsed.get("cleanReply"));
        result.put("score", parsed.get("score"));
        result.put("scoreComment", parsed.get("scoreComment"));
        if (sessionId != null) {
            result.put("sessionId", sessionId);
        }
        return result;
    }

    // 获取对话历史
    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "sessionId不能为空");
            err.put("code", 400);
            return err;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("history", conversationService.getHistory(sessionId));
        return result;
    }

    // 语音转文字（硅基流动 SenseVoice）
    @PostMapping("/speech")
    public Map<String, Object> speech(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "webm") String format
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file == null || file.isEmpty()) {
                result.put("error", "音频文件不能为空");
                result.put("code", 400);
                return result;
            }
            String text = speechService.transcribe(file.getBytes(), format);
            result.put("text", text);
            result.put("transcript", text);
            return result;
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("code", 500);
            return result;
        }
    }

    // 评测接口（返回结构化JSON）
    @PostMapping("/evaluate")
    public Map<String, Object> evaluate(@RequestBody Map<String, String> req) {
        String text = req.getOrDefault("text", "");
        String scene = req.getOrDefault("scene", "interview");

        if (text == null || text.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "评测文本不能为空");
            err.put("code", 400);
            return err;
        }

        String prompt = "你是一位英语教师。请用简体中文评测下面这句英语口语表达。\n\n"
                + "场景：" + sceneNameZh(scene) + "\n"
                + "用户说：" + text + "\n\n"
                + "要求：只输出简体中文纯文本，不要 JSON，不要代码块，不要英文标点替代中文。\n"
                + "严格按以下格式输出：\n"
                + "【综合评分】\n"
                + "语法：85/100\n"
                + "词汇：80/100\n"
                + "流畅：75/100\n"
                + "自然：80/100\n\n"
                + "【改进建议】\n"
                + "1. 第一条建议\n"
                + "2. 第二条建议\n"
                + "3. 第三条建议";

        String result = SummaryHelper.sanitizeSummary(llmService.chatSummary(prompt));
        if (SummaryHelper.looksBroken(result)) {
            result = "【综合评分】\n语法：80/100\n词汇：80/100\n流畅：78/100\n自然：80/100\n\n"
                    + "【改进建议】\n"
                    + "1. 继续保持完整句表达。\n"
                    + "2. 注意介词和时态准确性。\n"
                    + "3. 可加入更多细节让表达更自然。";
        }

        Map<String, Object> res = new HashMap<>();
        res.put("text", text);
        res.put("scene", scene);
        res.put("evaluation", result);
        res.put("raw", result);
        return res;
    }

    // 课后总结
    @PostMapping("/summary")
    public Map<String, String> summary(@RequestBody Map<String, String> req) {
        String history = req.getOrDefault("history", "");
        String scene = req.getOrDefault("scene", "interview");
        String compactHistory = SummaryHelper.compactHistory(history);

        String prompt = "你是一位英语教师。根据以下对话记录，生成一份简短的课后学习总结。\n\n"
                + compactHistory + "\n\n"
                + "要求：\n"
                + "1. 只输出简体中文纯文本\n"
                + "2. 不要 JSON，不要代码块，不要英文单词 on\n"
                + "3. 每个小节最多 2 条，每条不超过 35 个汉字\n"
                + "4. 禁止重复同一个词，禁止输出乱码\n"
                + "严格按以下格式输出：\n"
                + "【练习场景】\n"
                + "...\n\n"
                + "【表现亮点】\n"
                + "1. ...\n"
                + "2. ...\n\n"
                + "【待改进】\n"
                + "1. ...\n"
                + "2. ...\n\n"
                + "【下次建议】\n"
                + "1. ...\n"
                + "2. ...";

        String result = SummaryHelper.sanitizeSummary(llmService.chatSummary(prompt));
        if (SummaryHelper.looksBroken(result)) {
            result = SummaryHelper.fallbackSummary(history, scene);
        }

        Map<String, String> res = new HashMap<>();
        res.put("summary", result);
        return res;
    }

    // 增强的健康检查
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("timestamp", Instant.now().toString());
        result.put("service", "ai-english-tutor");
        return result;
    }

    private String buildSystemPrompt(String scene) {
        String sceneDesc;
        switch (scene) {
            case "interview":
                sceneDesc = "Job interview. You are the interviewer.";
                break;
            case "restaurant":
                sceneDesc = "Restaurant ordering. You are the waiter.";
                break;
            case "meeting":
                sceneDesc = "Business meeting. You are the host.";
                break;
            case "travel":
                sceneDesc = "Travel directions. You are a local helper.";
                break;
            case "shopping":
                sceneDesc = "Shopping chat. You are the shop assistant.";
                break;
            default:
                sceneDesc = "Daily English conversation practice.";
        }

        return "You are an English speaking tutor. " + sceneDesc + " "
                + "Reply ONLY in plain English with 1-2 short sentences. "
                + "Never repeat words. Never use brackets, tags, labels, scores, or Chinese. "
                + "Never write Correction, Score, or 评分. "
                + "If needed, weave a brief correction naturally into the sentence.";
    }

    private String sceneNameZh(String scene) {
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
                return "日常英语对话";
        }
    }

    private List<Map<String, Object>> parseHistoryMessages(String history, String scene, int maxTurns) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (history == null || history.trim().isEmpty()) {
            return messages;
        }

        Pattern pattern = Pattern.compile("用户：(.*?)\\nAI：(.*?)(?=\\n用户：|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(history.trim());
        List<Map<String, Object>> all = new ArrayList<>();

        while (matcher.find()) {
            String userText = matcher.group(1).trim();
            String aiText = matcher.group(2).trim();
            if (!userText.isEmpty()) {
                Map<String, Object> user = new HashMap<>();
                user.put("role", "user");
                user.put("content", userText);
                all.add(user);
            }
            if (!aiText.isEmpty()) {
                String safeAiText = ReplySanitizer.sanitizeModelReply(aiText);
                if (ReplySanitizer.looksBroken(safeAiText)) {
                    safeAiText = ReplySanitizer.fallbackReply(scene);
                }
                Map<String, Object> assistant = new HashMap<>();
                assistant.put("role", "assistant");
                assistant.put("content", safeAiText);
                all.add(assistant);
            }
        }

        int maxMessages = maxTurns * 2;
        if (all.size() <= maxMessages) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - maxMessages, all.size()));
    }

    private int parseScore(String text, String field) {
        // 简单解析：查找 "field":N 或 "field": N 或 field N
        String[] patterns = {
            "\"" + field + "\":\\s*(\\d+)",
            field + "\"\\s*(\\d+)",
            field + "\\s*(\\d+)"
        };
        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) {
                return Math.min(100, Math.max(0, Integer.parseInt(m.group(1))));
            }
        }
        return 70; // 默认分数
    }
}