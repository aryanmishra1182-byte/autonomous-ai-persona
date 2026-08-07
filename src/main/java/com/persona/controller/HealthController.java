package com.persona.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
@CrossOrigin
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "uptime", ManagementFactory.getRuntimeMXBean().getUptime()
        ));
    }

    @GetMapping("/")
    public ResponseEntity<?> root() {
        return ResponseEntity.ok(Map.of(
                "name", "Autonomous AI Persona Agent",
                "version", "1.0.0",
                "endpoints", Map.of(
                        "init", "POST /api/agent/init",
                        "feed", "GET /api/agent/feed?agentId=<id>",
                        "health", "GET /health"
                )
        ));
    }
}
