package com.persona.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.persona.config.PublishingConfig;
import com.persona.model.Agent;
import com.persona.model.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EditorialService {

    private static final Logger log = LoggerFactory.getLogger(EditorialService.class);
    private final GeminiService geminiService;
    private final PublishingConfig config;
    private final ObjectMapper objectMapper;

    public record TopicEvaluation(
            DiscoveryService.DiscoveredTopic topic,
            double score,
            boolean accepted,
            String reason
    ) {}

    public EditorialService(GeminiService geminiService, PublishingConfig config, ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public List<TopicEvaluation> evaluateTopics(
            List<DiscoveryService.DiscoveredTopic> topics,
            Agent agent,
            List<Post> recentPosts
    ) {
        if (topics == null || topics.isEmpty()) return Collections.emptyList();

        String recentPostsSummary = recentPosts.stream()
                .map(p -> p.getText().substring(0, Math.min(100, p.getText().length())) + "...")
                .collect(Collectors.joining("\n- ", "- ", ""));
        if (recentPosts.isEmpty()) recentPostsSummary = "None yet.";

        StringBuilder topicsList = new StringBuilder();
        for (int i = 0; i < topics.size(); i++) {
            var t = topics.get(i);
            topicsList.append(String.format("{\"id\": %d, \"title\": \"%s\", \"summary\": \"%s\", \"source\": \"%s\"}\n",
                    i, escapeJson(t.title()), escapeJson(t.summary()), t.source()));
        }

        String prompt = """
                You are an editorial assistant for an AI persona named %s, whose domain is %s.
                Here is their persona description:
                %s
                
                The persona recently posted about these topics (DO NOT select similar topics):
                %s
                
                Evaluate the following topics and score each 0.0 to 1.0 based on:
                1. Relevance to %s
                2. Timeliness and newsworthiness
                3. Uniqueness (different from recent posts)
                4. Potential for insightful commentary
                
                Score >= %.1f means approved for posting.
                
                Topics:
                %s
                
                Respond ONLY with a valid JSON array:
                [{"id": <index>, "score": <float>, "reason": "<brief reason>"}]
                """.formatted(
                agent.getName(), agent.getDomain(),
                agent.getPersonaPrompt(),
                recentPostsSummary,
                agent.getDomain(),
                config.getMinEditorialScore(),
                topicsList.toString()
        );

        try {
            String response = geminiService.generate(prompt);
            String jsonStr = extractJsonArray(response);
            JsonNode parsed = objectMapper.readTree(jsonStr);

            List<TopicEvaluation> results = new ArrayList<>();
            for (int i = 0; i < topics.size(); i++) {
                var topic = topics.get(i);
                double score = 0.0;
                String reason = "Not evaluated";

                for (JsonNode node : parsed) {
                    if (node.has("id") && node.get("id").asInt() == i) {
                        score = node.get("score").asDouble();
                        reason = node.has("reason") ? node.get("reason").asText() : "No reason";
                        break;
                    }
                }

                boolean accepted = score >= config.getMinEditorialScore();
                log.info("  [Editorial] \"{}\" | Score: {} | {} | {}",
                        topic.title().substring(0, Math.min(50, topic.title().length())),
                        String.format("%.2f", score), accepted ? "ACCEPTED" : "REJECTED", reason);
                results.add(new TopicEvaluation(topic, score, accepted, reason));
            }
            return results;
        } catch (Exception e) {
            log.error("Editorial evaluation failed, using fallback: {}", e.getMessage());
            return fallbackEvaluation(topics);
        }
    }

    private List<TopicEvaluation> fallbackEvaluation(List<DiscoveryService.DiscoveredTopic> topics) {
        return topics.stream().map(t -> {
            double score = 0.4 + Math.random() * 0.5;
            boolean accepted = score >= config.getMinEditorialScore();
            return new TopicEvaluation(t, score, accepted, "Fallback heuristic");
        }).collect(Collectors.toList());
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        throw new RuntimeException("No JSON array found in response");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
