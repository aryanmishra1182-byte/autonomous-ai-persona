package com.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.persona.config.PublishingConfig;
import com.persona.model.*;
import com.persona.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final DiscoveryService discoveryService;
    private final EditorialService editorialService;
    private final WriterService writerService;
    private final PublishingConfig config;
    private final AgentRepository agentRepository;
    private final PostRepository postRepository;
    private final TopicRepository topicRepository;
    private final MemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> activeSchedulers = new ConcurrentHashMap<>();

    public SchedulerService(
            DiscoveryService discoveryService,
            EditorialService editorialService,
            WriterService writerService,
            PublishingConfig config,
            AgentRepository agentRepository,
            PostRepository postRepository,
            TopicRepository topicRepository,
            MemoryRepository memoryRepository,
            ObjectMapper objectMapper
    ) {
        this.discoveryService = discoveryService;
        this.editorialService = editorialService;
        this.writerService = writerService;
        this.config = config;
        this.agentRepository = agentRepository;
        this.postRepository = postRepository;
        this.topicRepository = topicRepository;
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
    }

    /** On startup, restart schedulers for existing agents */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        List<Agent> agents = agentRepository.findAll();
        if (agents.isEmpty()) {
            log.info("No existing agents. Waiting for initialization...");
            return;
        }
        log.info("Restarting schedulers for {} existing agent(s)...", agents.size());
        for (Agent agent : agents) {
            triggerCatchUpAndSchedule(agent.getId());
        }
    }

    /** Start the autonomous scheduler for an agent */
    public void startScheduler(String agentId) {
        if (activeSchedulers.containsKey(agentId)) {
            log.warn("Scheduler already running for agent {}", agentId);
            return;
        }

        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) return;

        log.info("\n🤖 Starting autonomous scheduler for {} ({})", agent.getName(), agent.getDomain());
        log.info("   First post in ~{} seconds", config.getInitialDelaySeconds());

        ScheduledFuture<?> future = executor.schedule(
                () -> runCycleAndReschedule(agentId),
                config.getInitialDelaySeconds(),
                TimeUnit.SECONDS
        );
        activeSchedulers.put(agentId, future);
    }

    private void runCycleAndReschedule(String agentId) {
        try {
            runPublishingCycle(agentId);
        } catch (Exception e) {
            log.error("Publishing cycle failed: {}", e.getMessage(), e);
        }

        // Schedule next cycle
        long nextInterval = config.getRandomIntervalMs();
        log.info("⏰ Next cycle for agent {} in {} minutes", agentId,
                String.format("%.1f", nextInterval / 60000.0));

        ScheduledFuture<?> future = executor.schedule(
                () -> runCycleAndReschedule(agentId),
                nextInterval,
                TimeUnit.MILLISECONDS
        );
        activeSchedulers.put(agentId, future);
    }

    /** Run one complete discover → judge → write → publish cycle */
    @Transactional
    public void runPublishingCycle(String agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            log.error("Agent {} not found", agentId);
            return;
        }

        String cycleId = UUID.randomUUID().toString().substring(0, 8);
        log.info("\n🔄 [Cycle {}] Starting for {}...", cycleId, agent.getName());

        // Step 1: Discover
        log.info("  📡 Discovering topics in {}...", agent.getDomain());
        List<DiscoveryService.DiscoveredTopic> topics = discoveryService.discoverTopics(agent.getDomain());
        log.info("  📡 Found {} raw topics", topics.size());

        // Step 2: Filter already published/accepted topics
        topics = topics.stream()
                .filter(t -> t.url() == null || !topicRepository.existsByAgentIdAndUrlAndStatusIn(agentId, t.url(), List.of("published", "accepted")))
                .collect(Collectors.toList());
        log.info("  🔍 {} new topics after dedup", topics.size());

        if (topics.isEmpty()) {
            log.info("  ⏭️ No new topics. Skipping cycle.");
            return;
        }

        // Step 3: Editorial judgment
        List<Post> recentPosts = postRepository.findByAgentIdOrderByCreatedAtDesc(agentId, PageRequest.of(0, 10));
        log.info("  🧠 Running editorial judgment on {} topics...", topics.size());
        List<EditorialService.TopicEvaluation> evaluations = editorialService.evaluateTopics(topics, agent, recentPosts);

        var accepted = evaluations.stream()
                .filter(EditorialService.TopicEvaluation::accepted)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .collect(Collectors.toList());
        var rejected = evaluations.stream().filter(e -> !e.accepted()).collect(Collectors.toList());

        // Fallback: If no topic passed threshold, take the highest scoring topic if score >= 0.4
        if (accepted.isEmpty() && !evaluations.isEmpty()) {
            var bestEvaluated = evaluations.stream()
                    .max(Comparator.comparingDouble(EditorialService.TopicEvaluation::score))
                    .orElse(null);
            if (bestEvaluated != null && bestEvaluated.score() >= 0.4) {
                log.info("  💡 Using best candidate topic despite score being {}", String.format("%.2f", bestEvaluated.score()));
                accepted = List.of(bestEvaluated);
            }
        }

        // Save all evaluations to DB
        for (var eval : evaluations) {
            Topic topic = new Topic();
            topic.setId(UUID.randomUUID().toString());
            topic.setAgentId(agentId);
            topic.setTitle(eval.topic().title());
            topic.setUrl(eval.topic().url());
            topic.setSourceName(eval.topic().source());
            topic.setSummary(eval.topic().summary());
            topic.setScore(eval.score());
            boolean isPublished = !accepted.isEmpty() && accepted.get(0).topic().equals(eval.topic());
            topic.setStatus(isPublished ? "published" : (eval.accepted() ? "accepted" : "rejected"));
            topic.setRejectionReason(eval.accepted() ? null : eval.reason());
            topicRepository.save(topic);
        }

        log.info("  ✅ Accepted: {} | ❌ Rejected: {}", accepted.size(), rejected.size());

        if (!rejected.isEmpty()) {
            log.info("  📋 Rejected topics:");
            rejected.stream().limit(5).forEach(r ->
                    log.info("     - \"{}\" ({}) - {}",
                            r.topic().title().substring(0, Math.min(50, r.topic().title().length())),
                            String.format("%.2f", r.score()), r.reason()));
        }

        if (accepted.isEmpty()) {
            log.info("  ⏭️ No topics passed editorial judgment.");
            return;
        }

        // Step 4: Write the best topic
        var bestTopic = accepted.get(0).topic();
        log.info("  ✍️ Writing about: \"{}\"",
                bestTopic.title().substring(0, Math.min(60, bestTopic.title().length())));

        WriterService.GeneratedPost generated = writerService.generatePost(bestTopic, agent, recentPosts);

        // Step 5: Publish
        long postCount = postRepository.countByAgentId(agentId) + 1;
        Post post = new Post();
        post.setId("p" + postCount);
        post.setAgentId(agentId);
        post.setText(generated.text());
        post.setRationale(generated.rationale());
        try {
            List<String> sourceList = bestTopic.url() != null ? List.of(bestTopic.url()) : List.of();
            post.setSources(objectMapper.writeValueAsString(sourceList));
        } catch (Exception e) {
            post.setSources("[]");
        }
        post.setCreatedAt(Instant.now());
        postRepository.save(post);

        // Update memory
        saveMemory(agentId, "last_publish_time", Instant.now().toString());
        saveMemory(agentId, "total_posts", String.valueOf(postCount));
        saveMemory(agentId, "last_topic", bestTopic.title());

        log.info("  ✅ Published post p{}: \"{}...\"", postCount,
                generated.text().substring(0, Math.min(80, generated.text().length())));
        log.info("  📊 Total posts: {}\n", postCount);
    }

    /** Catch up on missed posts then start regular scheduling */
    public void triggerCatchUpAndSchedule(String agentId) {
        executor.submit(() -> {
            try {
                triggerCatchUp(agentId);
            } catch (Exception e) {
                log.error("Catch-up failed: {}", e.getMessage());
            }
            if (!activeSchedulers.containsKey(agentId)) {
                long nextInterval = config.getRandomIntervalMs();
                ScheduledFuture<?> future = executor.schedule(
                        () -> runCycleAndReschedule(agentId),
                        nextInterval, TimeUnit.MILLISECONDS
                );
                activeSchedulers.put(agentId, future);
                log.info("⏰ Scheduled next cycle in {} minutes",
                        String.format("%.1f", nextInterval / 60000.0));
            }
        });
    }

    @Transactional
    public void triggerCatchUp(String agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) return;

        Optional<Post> latestPost = postRepository.findLatestByAgentId(agentId);
        if (latestPost.isEmpty()) {
            log.info("🔄 No posts yet for {}, running first cycle...", agent.getName());
            runPublishingCycle(agentId);
            return;
        }

        Instant lastTime = latestPost.get().getCreatedAt();
        Instant now = Instant.now();
        long elapsedMs = ChronoUnit.MILLIS.between(lastTime, now);
        long avgIntervalMs = (long)(config.getMinIntervalMinutes() + config.getMaxIntervalMinutes()) * 30_000L;
        int missedCycles = (int)(elapsedMs / avgIntervalMs);

        if (missedCycles <= 0) {
            log.info("✅ Agent {} is up to date", agent.getName());
            return;
        }

        int catchUpCount = Math.min(missedCycles, config.getMaxCatchupPosts());
        log.info("🔄 Catching up: {} missed posts for {}", catchUpCount, agent.getName());

        for (int i = 0; i < catchUpCount; i++) {
            try {
                runPublishingCycle(agentId);
                Post latest = postRepository.findLatestByAgentId(agentId).orElse(null);
                if (latest != null) {
                    double fraction = (double)(i + 1) / (catchUpCount + 1);
                    Instant postTime = lastTime.plusMillis((long)(elapsedMs * fraction));
                    latest.setCreatedAt(postTime);
                    postRepository.save(latest);
                }
                Thread.sleep(3000);
            } catch (Exception e) {
                log.error("Catch-up post {} failed: {}", i + 1, e.getMessage());
            }
        }
    }

    @Transactional
    public void saveMemory(String agentId, String key, String value) {
        Optional<AgentMemory> existing = memoryRepository.findByAgentIdAndMemKey(agentId, key);
        AgentMemory mem;
        if (existing.isPresent()) {
            mem = existing.get();
            mem.setMemValue(value);
            mem.setCreatedAt(Instant.now());
        } else {
            mem = new AgentMemory();
            mem.setAgentId(agentId);
            mem.setMemKey(key);
            mem.setMemValue(value);
        }
        memoryRepository.save(mem);
    }
}
