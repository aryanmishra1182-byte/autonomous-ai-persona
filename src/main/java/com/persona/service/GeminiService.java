package com.persona.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.persona.config.GeminiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private final RestClient restClient;
    private final GeminiConfig geminiConfig;
    private final ObjectMapper objectMapper;

    public GeminiService(RestClient restClient, GeminiConfig geminiConfig, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.geminiConfig = geminiConfig;
        this.objectMapper = objectMapper;
    }

    public String generate(String prompt) {
        try {
            return callGemini(prompt);
        } catch (Exception e) {
            log.warn("First Gemini call failed, retrying in 2s: {}", e.getMessage());
            try {
                Thread.sleep(2000);
                return callGemini(prompt);
            } catch (Exception retryEx) {
                log.error("Gemini retry failed: {}", retryEx.getMessage());
                throw new RuntimeException("Gemini API call failed after retry", retryEx);
            }
        }
    }

    private String callGemini(String prompt) throws Exception {
        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode contents = requestBody.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode genConfig = requestBody.putObject("generationConfig");
        genConfig.put("temperature", 0.8);
        genConfig.put("maxOutputTokens", 2048);

        String body = objectMapper.writeValueAsString(requestBody);

        log.debug("Calling Gemini API...");

        String response = restClient.post()
                .uri(geminiConfig.getGenerateUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        // Parse response
        JsonNode responseJson = objectMapper.readTree(response);
        JsonNode candidates = responseJson.get("candidates");
        if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
            JsonNode firstCandidate = candidates.get(0);
            JsonNode contentNode = firstCandidate.get("content");
            if (contentNode != null) {
                JsonNode partsNode = contentNode.get("parts");
                if (partsNode != null && partsNode.isArray() && !partsNode.isEmpty()) {
                    String text = partsNode.get(0).get("text").asText();
                    log.debug("Gemini response received ({} chars)", text.length());
                    return text;
                }
            }
        }

        throw new RuntimeException("No valid response from Gemini API");
    }
}
