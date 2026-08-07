package com.persona.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiConfig {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.api-url}")
    private String apiUrl;

    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("\n❌ GEMINI_API_KEY is required!");
            System.err.println("   Get a FREE key at: https://aistudio.google.com/apikey");
            System.err.println("   Then set it: set GEMINI_API_KEY=your_key_here\n");
            System.exit(1);
        }
        System.out.println("✅ Gemini API configured (model: " + model + ")");
    }

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getApiUrl() { return apiUrl; }

    public String getGenerateUrl() {
        return apiUrl + "/" + model + ":generateContent?key=" + apiKey;
    }
}
