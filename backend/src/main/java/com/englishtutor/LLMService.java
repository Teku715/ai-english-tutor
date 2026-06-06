package com.englishtutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

@Service
public class LLMService {

    @Value("${silicon.api-key}")
    private String apiKey;

    @Value("${silicon.model}")
    private String model;

    @Value("${silicon.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String chat(String prompt) {
        String url = baseUrl + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                return (String) msg.get("content");
            }
        }
        return "鎶辨瓑锛孉I鏆傛椂鏃犳硶鍥炲銆?;
    }
}