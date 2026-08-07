package com.persona.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.persona.model.Agent;
import com.persona.model.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WriterService {

    private static final Logger log = LoggerFactory.getLogger(WriterService.class);
    private final GeminiService geminiService;
    private final PersonaService personaService;
    private final ObjectMapper objectMapper;

    public record GeneratedPost(String text, String rationale) {}

    public WriterService(GeminiService geminiService, PersonaService personaService, ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.personaService = personaService;
        this.objectMapper = objectMapper;
    }

    public GeneratedPost generatePost(
            DiscoveryService.DiscoveredTopic topic,
            Agent agent,
            List<Post> recentPosts
    ) {
        String styleGuide = personaService.getWritingStyleGuide(agent.getName(), agent.getDomain());
        String recentContext = recentPosts.stream()
                .map(p -> p.getText().substring(0, Math.min(100, p.getText().length())) + "...")
                .collect(Collectors.joining("\n- ", "- ", ""));
        if (recentPosts.isEmpty()) recentContext = "None yet.";

        String prompt = """
                You are the AI persona described below. Write a social media post (LinkedIn/X style) about the given topic.
                
                --- YOUR PERSONA ---
                %s
                
                --- WRITING STYLE GUIDE ---
                %s
                
                --- RECENT POSTS (do not repeat) ---
                %s
                
                --- TOPIC TO WRITE ABOUT ---
                Title: %s
                Source: %s
                URL: %s
                Summary: %s
                
                --- INSTRUCTIONS ---
                Write a compelling post (200-400 words) in your authentic persona voice.
                Also provide a rationale explaining why you chose this topic, why it is relevant now, and the source.
                
                Respond ONLY with valid JSON:
                {"text": "<the post>", "rationale": "<why this topic was selected>"}
                """.formatted(
                agent.getPersonaPrompt(),
                styleGuide,
                recentContext,
                topic.title(),
                topic.source(),
                topic.url(),
                topic.summary()
        );

        try {
            String response = geminiService.generate(prompt);
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Gemini LLM call failed ({}), activating persona template engine...", e.getMessage());
            return generateFallbackPost(topic, agent);
        }
    }

    private GeneratedPost generateFallbackPost(DiscoveryService.DiscoveredTopic topic, Agent agent) {
        String title = topic.title();
        String source = topic.source();
        String summary = topic.summary() != null ? topic.summary() : title;
        String domain = agent.getDomain();

        String postText = String.format(
                "I've been analyzing recent developments in %s, and this update caught my attention:\n\n" +
                "\"%s\"\n\n" +
                "%s\n\n" +
                "Key takeaway: As AI architectures evolve, staying ahead of security and performance risks requires continuous scrutiny. " +
                "What are your thoughts on how this impacts current deployments?\n\n" +
                "#%s #TechAnalysis #AI",
                domain,
                title,
                summary,
                domain.replaceAll("\\s+", "")
        );

        String rationale = String.format(
                "Selected topic '%s' from %s because it highlights recent advancements and critical considerations in %s. " +
                "Timely and relevant for practitioners evaluating modern tech stacks.",
                title, source, domain
        );

        return new GeneratedPost(postText, rationale);
    }

    private GeneratedPost parseResponse(String response) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String jsonStr = response.substring(start, end + 1);
                JsonNode json = objectMapper.readTree(jsonStr);
                String text = json.get("text").asText();
                String rationale = json.get("rationale").asText();
                return new GeneratedPost(text, rationale);
            }
        } catch (Exception e) {
            log.error("Failed to parse writer response: {}", e.getMessage());
        }
        throw new RuntimeException("Could not parse post from Gemini response");
    }
}
