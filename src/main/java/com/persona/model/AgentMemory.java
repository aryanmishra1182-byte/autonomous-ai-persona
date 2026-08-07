package com.persona.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_memory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agentId", "memKey"})
})
public class AgentMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String agentId;

    @Column(nullable = false)
    private String memKey;

    @Column(columnDefinition = "CLOB")
    private String memValue;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getMemKey() { return memKey; }
    public void setMemKey(String memKey) { this.memKey = memKey; }
    public String getMemValue() { return memValue; }
    public void setMemValue(String memValue) { this.memValue = memValue; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
