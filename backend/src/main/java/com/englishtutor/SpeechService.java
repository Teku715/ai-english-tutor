package com.englishtutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class SpeechService {

    @Value("${silicon.api-key}")
    private String apiKey;

    @Value("${silicon.asr-url:https://api.siliconflow.cn/v1/audio/transcriptions}")
    private String asrUrl;

    @Value("${silicon.asr-model:FunAudioLLM/SenseVoiceSmall}")
    private String asrModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String transcribe(byte[] audioBytes, String format) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("SILICON_API_KEY 未配置");
        }

        String normalized = format == null ? "wav" : format.toLowerCase();
        String contentType;
        String extension;
        if ("ogg".equals(normalized)) {
            contentType = "audio/ogg";
            extension = "ogg";
        } else if ("webm".equals(normalized)) {
            contentType = "audio/webm";
            extension = "webm";
        } else {
            contentType = "audio/wav";
            extension = "wav";
        }

        String boundary = "----EnglishTutorBoundary" + System.currentTimeMillis();
        byte[] header = ("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"audio." + extension + "\"\r\n" +
                "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] modelPart = ("\r\n--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"model\"\r\n\r\n" +
                asrModel + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[header.length + audioBytes.length + modelPart.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(audioBytes, 0, body, header.length, audioBytes.length);
        System.arraycopy(modelPart, 0, body, header.length + audioBytes.length, modelPart.length);

        HttpURLConnection conn = (HttpURLConnection) new URL(asrUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int code = conn.getResponseCode();
        String raw = readStream(conn, code);
        if (code >= 400) {
            throw new IllegalStateException("ASR failed: " + raw);
        }

        JsonNode node = objectMapper.readTree(raw);
        if (node.has("text")) {
            return node.get("text").asText().trim();
        }
        return raw.trim();
    }

    private String readStream(HttpURLConnection conn, int code) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
