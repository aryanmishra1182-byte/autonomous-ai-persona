package com.persona.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PublishingConfig {

    @Value("${publishing.min-interval-minutes}")
    private int minIntervalMinutes;

    @Value("${publishing.max-interval-minutes}")
    private int maxIntervalMinutes;

    @Value("${publishing.initial-delay-seconds}")
    private int initialDelaySeconds;

    @Value("${publishing.max-catchup-posts}")
    private int maxCatchupPosts;

    @Value("${editorial.min-score}")
    private double minEditorialScore;

    @Value("${discovery.max-topics}")
    private int maxTopicsPerCycle;

    public int getMinIntervalMinutes() { return minIntervalMinutes; }
    public int getMaxIntervalMinutes() { return maxIntervalMinutes; }
    public int getInitialDelaySeconds() { return initialDelaySeconds; }
    public int getMaxCatchupPosts() { return maxCatchupPosts; }
    public double getMinEditorialScore() { return minEditorialScore; }
    public int getMaxTopicsPerCycle() { return maxTopicsPerCycle; }

    public long getRandomIntervalMs() {
        long minMs = minIntervalMinutes * 60_000L;
        long maxMs = maxIntervalMinutes * 60_000L;
        return minMs + (long)(Math.random() * (maxMs - minMs));
    }
}
