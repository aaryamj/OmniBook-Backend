package com.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class GeminiClientService {

    // Strictly user-requested model: gemini-3.5-flash-lite
    private static final String GEMINI_MODEL = "gemini-3.5-flash-lite";
    private static final String GEMINI_API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    @Value("${gemini.api.key:}")
    private String configuredGeminiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Retrieves and sanitizes the Gemini API Key from environment or properties.
     */
    public String getSanitizedApiKey() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.trim().isEmpty()) {
            key = configuredGeminiKey;
        }
        if (key != null) {
            // Strip any accidental spaces or hidden control characters
            key = key.replaceAll("\\s+", "");
        }
        return (key != null && !key.isEmpty()) ? key : "";
    }

    /**
     * Sends a full conversation context (system instructions + active 20 turns) to Gemini 3.5 Flash-Lite.
     */
    public Optional<String> generateChatResponse(String systemInstruction, List<Map<String, Object>> contents) {
        String apiKey = getSanitizedApiKey();
        if (apiKey.isEmpty()) {
            log.warn("Gemini API key is not configured. Falling back to local intelligence.");
            return Optional.empty();
        }

        String url = String.format(GEMINI_API_URL_TEMPLATE, GEMINI_MODEL, apiKey);

        Map<String, Object> payload = new HashMap<>();

        // 1. System instruction
        if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
            Map<String, Object> systemPart = Map.of("text", systemInstruction);
            Map<String, Object> systemInstructionObj = Map.of("parts", List.of(systemPart));
            payload.put("system_instruction", systemInstructionObj);
        }

        // 2. Multi-turn conversational contents
        payload.put("contents", contents);

        // 3. Generation configuration
        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("temperature", 0.4);
        genConfig.put("maxOutputTokens", 1024);
        payload.put("generationConfig", genConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            log.info("Invoking Gemini AI model: {}", GEMINI_MODEL);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            return extractTextFromGeminiResponse(response.getBody());
        } catch (Exception e) {
            log.error("Gemini 3.5 Flash-Lite API request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Summarizes older conversations exceeding the active 20 turns so context is preserved.
     */
    public Optional<String> summarizeConversation(String previousSummary, List<Map<String, String>> olderTurns) {
        String apiKey = getSanitizedApiKey();
        if (apiKey.isEmpty() || olderTurns.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an intelligent appointment memory summarizer. ");
        promptBuilder.append("Summarize the following prior user conversation turns concisely. ");
        promptBuilder.append("Retain key details: requested services, organization/clinic/salon/college names, preferred staff/providers, agreed dates, times, booking references, or cancellation requests.\n\n");

        if (previousSummary != null && !previousSummary.trim().isEmpty()) {
            promptBuilder.append("Existing Archived Summary:\n").append(previousSummary).append("\n\n");
        }

        promptBuilder.append("Older Conversation Turns to Compress:\n");
        for (Map<String, String> turn : olderTurns) {
            promptBuilder.append(turn.get("role").toUpperCase()).append(": ").append(turn.get("text")).append("\n");
        }
        promptBuilder.append("\nConcise Running Summary:");

        List<Map<String, Object>> contents = List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", promptBuilder.toString())))
        );

        return generateChatResponse("You are a concise medical memory summarizer.", contents);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractTextFromGeminiResponse(Map responseBody) {
        if (responseBody == null || !responseBody.containsKey("candidates")) {
            return Optional.empty();
        }

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> firstCandidate = candidates.get(0);
        Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
        if (content != null && content.containsKey("parts")) {
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts != null && !parts.isEmpty()) {
                String text = (String) parts.get(0).get("text");
                return Optional.ofNullable(text != null ? text.trim() : null);
            }
        }

        return Optional.empty();
    }
}
