package com.englishtutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EnglishTutorController {

    @Autowired
    private LLMService llmService;

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

    // 对话接口
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> req) {
        String scene = req.getOrDefault("scene", "interview");
        String message = req.getOrDefault("message", "");
        String history = req.getOrDefault("history", "");

        String prompt = buildChatPrompt(scene, message, history);
        String reply = llmService.chat(prompt);

        Map<String, String> result = new HashMap<>();
        result.put("reply", reply);
        return result;
    }

    // 评测接口（语音转文字后的评测）
    @PostMapping("/evaluate")
    public Map<String, String> evaluate(@RequestBody Map<String, String> req) {
        String text = req.getOrDefault("text", "");
        String scene = req.getOrDefault("scene", "interview");

        String prompt = "你是一个英语教师。请评测用户在下述场景中的英语表达。\n\n" +
                "场景：" + scene + "\n" +
                "用户说：" + text + "\n\n" +
                "请从以下维度评分（0-100）：\n" +
                "1. 语法正确性\n" +
                "2. 词汇适当性\n" +
                "3. 发音流畅度（根据文字判断）\n" +
                "4. 表达自然度\n\n" +
                "然后给出2-3个纠错建议。\n\n" +
                "输出格式：\n" +
                "语法：85分 | 词汇：80分 | 流畅度：75分 | 自然度：80分\n" +
                "建议：1. ... 2. ... 3. ...";

        String result = llmService.chat(prompt);
        Map<String, String> res = new HashMap<>();
        res.put("evaluation", result);
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

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    private String buildChatPrompt(String scene, String message, String history) {
        String sceneDesc;
        if ("interview".equals(scene)) {
            sceneDesc = "求职面试场景。你扮演面试官，提问专业且有挑战性的问题。";
        } else if ("restaurant".equals(scene)) {
            sceneDesc = "餐厅点餐场景。你扮演服务员，帮助顾客完成点餐。";
        } else if ("meeting".equals(scene)) {
            sceneDesc = "商务会议场景。你扮演会议主持人，引导讨论。";
        } else if ("travel".equals(scene)) {
            sceneDesc = "旅行问路场景。你扮演当地人，帮助游客。";
        } else if ("shopping".equals(scene)) {
            sceneDesc = "购物交流场景。你扮演店员，与顾客沟通需求。";
        } else {
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
}