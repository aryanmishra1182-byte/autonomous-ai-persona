package com.persona.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.persona.model.Agent;
import com.persona.model.Post;
import com.persona.repository.AgentRepository;
import com.persona.repository.PostRepository;
import com.persona.service.PersonaService;
import com.persona.service.SchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentRepository agentRepository;
    private final PostRepository postRepository;
    private final PersonaService personaService;
    private final SchedulerService schedulerService;
    private final ObjectMapper objectMapper;

    public AgentController(
            AgentRepository agentRepository,
            PostRepository postRepository,
            PersonaService personaService,
            SchedulerService schedulerService,
            ObjectMapper objectMapper
    ) {
        this.agentRepository = agentRepository;
        this.postRepository = postRepository;
        this.personaService = personaService;
        this.schedulerService = schedulerService;
        this.objectMapper = objectMapper;
    }

    // ======== DTOs ========
    public record PersonaInput(String name, String domain) {}
    public record InitRequest(PersonaInput persona) {}
    public record InitResponse(String agentId) {}
    public record PostDto(String id, String createdAt, String text, String rationale, List<String> sources) {}
    public record FeedResponse(String agentName, String agentDomain, List<PostDto> posts) {}

    /**
     * POST /api/agent/init
     * Initialize a new autonomous agent
     */
    @PostMapping("/init")
    public ResponseEntity<?> initAgent(@RequestBody InitRequest request) {
        try {
            if (request.persona() == null || request.persona().name() == null || request.persona().domain() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing required fields: persona.name and persona.domain"));
            }

            String name = request.persona().name();
            String domain = request.persona().domain();
            String agentId = UUID.randomUUID().toString().substring(0, 12);

            // Generate persona
            String personaPrompt = personaService.generatePersonaPrompt(name, domain);
            String bio = personaService.generateBio(name, domain);

            // Save agent
            Agent agent = new Agent();
            agent.setId(agentId);
            agent.setName(name);
            agent.setDomain(domain);
            agent.setPersonaPrompt(personaPrompt);
            agent.setCreatedAt(Instant.now());
            agentRepository.save(agent);

            // Initialize memory
            schedulerService.saveMemory(agentId, "bio", bio);
            schedulerService.saveMemory(agentId, "total_posts", "0");
            schedulerService.saveMemory(agentId, "initialized_at", Instant.now().toString());

            log.info("\n🎉 Agent initialized!");
            log.info("   ID: {}", agentId);
            log.info("   Name: {}", name);
            log.info("   Domain: {}", domain);

            // Start autonomous scheduler
            schedulerService.startScheduler(agentId);

            // Trigger immediate first publishing cycle asynchronously
            new Thread(() -> {
                try {
                    log.info("🚀 Triggering immediate initial post cycle for {}", name);
                    schedulerService.runPublishingCycle(agentId);
                } catch (Exception e) {
                    log.error("Initial cycle error: {}", e.getMessage());
                }
            }).start();

            return ResponseEntity.status(HttpStatus.CREATED).body(new InitResponse(agentId));
        } catch (Exception e) {
            log.error("Init error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to initialize agent"));
        }
    }

    /**
     * GET /api/agent/feed?agentId=<id>
     * Retrieve the agent's published feed (Read-only, compliant with hackathon spec)
     */
    @GetMapping("/feed")
    public ResponseEntity<?> getFeed(@RequestParam String agentId) {
        try {
            Optional<Agent> agentOpt = agentRepository.findById(agentId);
            if (agentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Agent " + agentId + " not found"));
            }

            Agent agent = agentOpt.get();

            // Get posts in reverse chronological order
            List<Post> posts = postRepository.findByAgentIdOrderByCreatedAtDesc(agentId);

            List<PostDto> postDtos = posts.stream().map(post -> {
                List<String> sources;
                try {
                    sources = objectMapper.readValue(post.getSources(), new TypeReference<List<String>>() {});
                } catch (Exception e) {
                    sources = List.of();
                }
                return new PostDto(
                        post.getId(),
                        post.getCreatedAt().toString(),
                        post.getText(),
                        post.getRationale(),
                        sources
                );
            }).collect(Collectors.toList());

            return ResponseEntity.ok(new FeedResponse(agent.getName(), agent.getDomain(), postDtos));
        } catch (Exception e) {
            log.error("Feed error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve feed"));
        }
    }
}
