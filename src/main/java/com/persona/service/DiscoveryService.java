package com.persona.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.persona.config.PublishingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PublishingConfig config;

    public record DiscoveredTopic(String title, String url, String source, String summary) {}

    public DiscoveryService(RestClient restClient, ObjectMapper objectMapper, PublishingConfig config) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public List<DiscoveredTopic> discoverTopics(String domain) {
        List<String> keywords = buildKeywords(domain);
        log.info("Discovering topics for domain '{}' with keywords: {}", domain, keywords);

        // Fetch from all sources in parallel
        var hnFuture = CompletableFuture.supplyAsync(() -> fetchHackerNews(keywords));
        var devtoFuture = CompletableFuture.supplyAsync(() -> fetchDevTo());
        var arxivFuture = CompletableFuture.supplyAsync(() -> fetchArxiv(keywords));
        var githubFuture = CompletableFuture.supplyAsync(() -> fetchGitHub());

        List<DiscoveredTopic> allTopics = new ArrayList<>();
        try {
            allTopics.addAll(hnFuture.get());
            allTopics.addAll(devtoFuture.get());
            allTopics.addAll(arxivFuture.get());
            allTopics.addAll(githubFuture.get());
        } catch (Exception e) {
            log.error("Error combining discovery results: {}", e.getMessage());
        }

        // Shuffle for variety
        Collections.shuffle(allTopics);

        // Limit
        if (allTopics.size() > config.getMaxTopicsPerCycle()) {
            allTopics = allTopics.subList(0, config.getMaxTopicsPerCycle());
        }

        log.info("Total discovered topics: {}", allTopics.size());
        return allTopics;
    }

    private List<String> buildKeywords(String domain) {
        List<String> keywords = new ArrayList<>();
        String lower = domain.toLowerCase();
        keywords.add(lower);
        // Add domain-specific keywords
        if (lower.contains("security")) { keywords.addAll(List.of("security", "vuln", "hack", "cve", "attack")); }
        if (lower.contains("machine learning") || lower.contains("ml")) { keywords.addAll(List.of("ml", "model", "training", "dataset", "neural")); }
        if (lower.contains("ai")) { keywords.addAll(List.of("ai", "llm", "gpt", "artificial intelligence", "gemini", "openai", "anthropic")); }
        if (lower.contains("ethics")) { keywords.addAll(List.of("ethics", "bias", "fairness", "responsible", "safety")); }
        if (lower.contains("robotics")) { keywords.addAll(List.of("robot", "autonomous", "embodied", "hardware")); }
        // Always include generic AI keywords
        keywords.addAll(List.of("ai", "llm", "machine learning"));
        return keywords.stream().distinct().collect(Collectors.toList());
    }

    private boolean matchesKeywords(String text, List<String> keywords) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    private List<DiscoveredTopic> fetchHackerNews(List<String> keywords) {
        List<DiscoveredTopic> topics = new ArrayList<>();
        try {
            String idsJson = restClient.get()
                    .uri("https://hacker-news.firebaseio.com/v0/topstories.json")
                    .retrieve().body(String.class);
            JsonNode ids = objectMapper.readTree(idsJson);

            // Fetch first 30 stories
            List<CompletableFuture<DiscoveredTopic>> futures = new ArrayList<>();
            for (int i = 0; i < Math.min(30, ids.size()); i++) {
                int storyId = ids.get(i).asInt();
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String storyJson = restClient.get()
                                .uri("https://hacker-news.firebaseio.com/v0/item/" + storyId + ".json")
                                .retrieve().body(String.class);
                        JsonNode story = objectMapper.readTree(storyJson);
                        String title = story.has("title") ? story.get("title").asText() : null;
                        String url = story.has("url") ? story.get("url").asText() : null;
                        if (title != null && url != null && matchesKeywords(title, keywords)) {
                            return new DiscoveredTopic(title, url, "Hacker News", title);
                        }
                    } catch (Exception ignored) {}
                    return null;
                }));
            }

            for (var future : futures) {
                try {
                    DiscoveredTopic topic = future.get();
                    if (topic != null) topics.add(topic);
                } catch (Exception ignored) {}
            }
            log.info("  Hacker News: {} topics", topics.size());
        } catch (Exception e) {
            log.warn("HN discovery failed: {}", e.getMessage());
        }
        return topics;
    }

    private List<DiscoveredTopic> fetchDevTo() {
        List<DiscoveredTopic> topics = new ArrayList<>();
        try {
            String json = restClient.get()
                    .uri("https://dev.to/api/articles?tag=ai&per_page=10")
                    .retrieve().body(String.class);
            JsonNode articles = objectMapper.readTree(json);
            for (JsonNode article : articles) {
                String title = article.has("title") ? article.get("title").asText() : null;
                String url = article.has("url") ? article.get("url").asText() : null;
                String desc = article.has("description") ? article.get("description").asText() : title;
                if (title != null && url != null) {
                    topics.add(new DiscoveredTopic(title, url, "Dev.to", desc));
                }
            }
            log.info("  Dev.to: {} topics", topics.size());
        } catch (Exception e) {
            log.warn("Dev.to discovery failed: {}", e.getMessage());
        }
        return topics;
    }

    private List<DiscoveredTopic> fetchArxiv(List<String> keywords) {
        List<DiscoveredTopic> topics = new ArrayList<>();
        try {
            String xml = restClient.get()
                    .uri("http://export.arxiv.org/api/query?search_query=cat:cs.AI+OR+cat:cs.LG+OR+cat:cs.CR&sortBy=submittedDate&sortOrder=descending&max_results=10")
                    .retrieve().body(String.class);

            // Parse with regex
            Pattern entryPattern = Pattern.compile("<entry>([\\s\\S]*?)</entry>");
            Pattern titlePattern = Pattern.compile("<title>([\\s\\S]*?)</title>");
            Pattern summaryPattern = Pattern.compile("<summary>([\\s\\S]*?)</summary>");
            Pattern idPattern = Pattern.compile("<id>([\\s\\S]*?)</id>");

            Matcher entryMatcher = entryPattern.matcher(xml);
            while (entryMatcher.find()) {
                String entry = entryMatcher.group(1);
                Matcher titleM = titlePattern.matcher(entry);
                Matcher summaryM = summaryPattern.matcher(entry);
                Matcher idM = idPattern.matcher(entry);

                if (titleM.find() && idM.find()) {
                    String title = titleM.group(1).replaceAll("\\s+", " ").trim();
                    String url = idM.group(1).trim();
                    String summary = summaryM.find() ? summaryM.group(1).replaceAll("\\s+", " ").trim() : title;
                    if (summary.length() > 300) summary = summary.substring(0, 300) + "...";

                    if (matchesKeywords(title, keywords) || keywords.isEmpty()) {
                        topics.add(new DiscoveredTopic(title, url, "ArXiv", summary));
                    }
                }
            }
            log.info("  ArXiv: {} topics", topics.size());
        } catch (Exception e) {
            log.warn("ArXiv discovery failed: {}", e.getMessage());
        }
        return topics;
    }

    private List<DiscoveredTopic> fetchGitHub() {
        List<DiscoveredTopic> topics = new ArrayList<>();
        try {
            String json = restClient.get()
                    .uri("https://api.github.com/search/repositories?q=ai+OR+llm+OR+machine-learning&sort=stars&order=desc&per_page=10")
                    .retrieve().body(String.class);
            JsonNode data = objectMapper.readTree(json);
            JsonNode items = data.get("items");
            if (items != null && items.isArray()) {
                for (JsonNode repo : items) {
                    String name = repo.has("full_name") ? repo.get("full_name").asText() : null;
                    String url = repo.has("html_url") ? repo.get("html_url").asText() : null;
                    String desc = repo.has("description") && !repo.get("description").isNull() ? repo.get("description").asText() : name;
                    if (name != null && url != null) {
                        topics.add(new DiscoveredTopic(name, url, "GitHub", desc));
                    }
                }
            }
            log.info("  GitHub: {} topics", topics.size());
        } catch (Exception e) {
            log.warn("GitHub discovery failed: {}", e.getMessage());
        }
        return topics;
    }
}
