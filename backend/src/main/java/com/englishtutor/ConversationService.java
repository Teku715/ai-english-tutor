package com.englishtutor;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationService {

    // sessionId -> list of conversation entries
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 20;

    public String startSession(String scene) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(sessionId, new ArrayList<>());
        return sessionId;
    }

    public void addMessage(String sessionId, String role, String content) {
        List<Map<String, String>> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(Map.of("role", role, "content", content));
        // keep last MAX_HISTORY messages per side
        while (history.size() > MAX_HISTORY * 2) {
            history.remove(0);
        }
    }

    public List<Map<String, String>> getHistory(String sessionId) {
        return sessions.getOrDefault(sessionId, Collections.emptyList());
    }

    public String buildHistoryString(String sessionId) {
        List<Map<String, String>> history = getHistory(sessionId);
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> entry : history) {
            sb.append(entry.get("role")).append("：").append(entry.get("content")).append("\n");
        }
        return sb.toString();
    }

    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}