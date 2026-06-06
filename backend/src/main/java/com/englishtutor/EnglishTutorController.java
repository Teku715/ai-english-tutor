package com.englishtutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.Instant;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EnglishTutorController {

    @Autowired
    private LLMService llmService;

    @Autowired
    private ConversationService conversationService;

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

        String prompt;
        if (sessionId != null && conversationService.hasSession(sessionId)) {
            String historyStr = conversationService.buildHistoryString(sessionId);
            prompt = buildChatPrompt(scene, message, historyStr);
            conversationService.addMessage(sessionId, "用户", message);
        } else {
            prompt = buildChatPrompt(scene, message, history);
        }

        String reply = llmService.chat(prompt);

        if (sessionId != null && conversationService.hasSession(sessionId)) {
            conversationService.addMessage(sessionId, "AI", reply);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("reply", reply);
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

        String prompt = "你是一个英语教师。请评测用户在下述场景中的英语表达。\n\n" +
                "场景：" + scene + "\n" +
                "用户说：" + text + "\n\n" +
                "请从以下维度评分（每项0-100）：\n" +
                "1. 语法正确性\n" +
                "2. 词汇适当性\n" +
                "3. 发音流畅度（根据文字判断）\n" +
                "4. 表达自然度\n\n" +
                "然后给出2-3个纠错建议。\n\n" +
                "输出格式（严格按此JSON格式，不要有其他内容）：\n" +
                "{\"grammar\":85,\"vocabulary\":80,\"fluency\":75,\"naturalness\":80,\"suggestions\":[\"建议1\",\"建议2\",\"建议3\"]}";

        String result = llmService.chat(prompt);

        Map<String, Object> res = new HashMap<>();
        res.put("text", text);
        res.put("scene", scene);
        res.put("raw", result);

        // 尝试解析JSON结构化分数
        try {
            // 简单解析：grammar/N, vocabulary/N, fluency/N, naturalness/N
            Map<String, Object> scores = new HashMap<>();
            scores.put("grammar", parseScore(result, "grammar"));
            scores.put("vocabulary", parseScore(result, "vocabulary"));
            scores.put("fluency", parseScore(result, "fluency"));
            scores.put("naturalness", parseScore(result, "naturalness"));
            res.put("scores", scores);
        } catch (Exception e) {
            // 解析失败时返回原始结果
        }

        return res;
    }

    // 课后总结
    @PostMapping("/summary")
    public Map<String, String> summary(@RequestBody Map<String, String> req) {
        String history = req.getOrDefault("history", "");

        String prompt = "你是一个英语教师。根据以下对话记录，生成一份课后学习总结：\n\n" +
                history + "\n\n" +
                "总结要包括：\n" +
                "1. 本次练习的场景\n" +
                "2. 用户表现好的地方（列出具体例子）\n" +
                "3. 需要改进的地方（语法/词汇/表达方式）\n" +
                "4. 下次练习建议（1-2条）\n\n" +
                "用中文写，面向学生用户。";

        String result = llmService.chat(prompt);
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

    private String buildChatPrompt(String scene, String message, String history) {
        String sceneDesc;
        switch (scene) {
            case "interview":
                sceneDesc = "求职面试场景。你扮演面试官，提问专业且有挑战性的问题。";
                break;
            case "restaurant":
                sceneDesc = "餐厅点餐场景。你扮演服务员，帮助顾客完成点餐。";
                break;
            case "meeting":
                sceneDesc = "商务会议场景。你扮演会议主持人，引导讨论。";
                break;
            case "travel":
                sceneDesc = "旅行问路场景。你扮演当地人，帮助游客。";
                break;
            case "shopping":
                sceneDesc = "购物交流场景。你扮演店员，与顾客沟通需求。";
                break;
            default:
                sceneDesc = "日常英语对话练习。";
        }

        String systemPrompt = "你是AI英语口语陪练。" + sceneDesc +
                "\n要求：\n" +
                "1. 用户用英语说，你用英语回复（偶尔可以用中文提示）\n" +
                "2. 每轮对话后，给出当前回复的地道程度评分（1-10分）\n" +
                "3. 如果用户表达有明显错误，回复后指出问题\n" +
                "4. 保持对话自然流畅，模拟真实对话\n" +
                "5. 每3-5轮后，询问用户是否需要总结\n\n" +
                "格式示例：\n" +
                "[AI]: How can I help you today?\n" +
                "[评分: 9/10] 回复地道，发音清晰。\n\n" +
                "现在开始对话：";

        if (!history.isEmpty()) {
            systemPrompt = "对话历史：\n" + history + "\n\n" + systemPrompt;
        }

        return systemPrompt + "\n\n用户：" + message + "\n[AI]:";
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