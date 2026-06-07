package com.englishtutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LLMService {

    @Value("${silicon.api-key}")
    private String apiKey;

    @Value("${silicon.model}")
    private String model;

    @Value("${silicon.base-url}")
    private String baseUrl;

    @Value("${silicon.chat-max-tokens:220}")
    private int chatMaxTokens;

    @Value("${silicon.summary-max-tokens:800}")
    private int summaryMaxTokens;

    private final RestTemplate restTemplate;

    public LLMService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String chat(String prompt) {
        return chat(prompt, summaryMaxTokens, false);
    }

    public String chatSummary(String prompt) {
        return chat(prompt, Math.min(summaryMaxTokens, 420), true);
    }

    public String chatDialog(String systemPrompt, List<Map<String, Object>> historyMessages, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.add(system);

        if (historyMessages != null) {
            messages.addAll(historyMessages);
        }

        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", userMessage);
        messages.add(user);

        return requestChat(messages, chatMaxTokens, true);
    }

    private String chat(String prompt, int maxTokens, boolean strictMode) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message);

        return requestChat(messages, maxTokens, strictMode);
    }

    private String requestChat(List<Map<String, Object>> messages, int maxTokens, boolean dialogMode) {
        String url = baseUrl + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", dialogMode ? 0.4 : 0.6);
        if (dialogMode) {
            body.put("frequency_penalty", 0.8);
            body.put("presence_penalty", 0.5);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                Object content = msg.get("content");
                return content == null ? "Sorry, no reply." : content.toString();
            }
        }
        return "Sorry, AI is temporarily unavailable.";
    }
}
