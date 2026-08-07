package com.persona.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    private String id;

    @Column(nullable = false)
    private String agentId;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String text;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String rationale;

    @Column(columnDefinition = "CLOB")
    private String sources; // JSON array as string, e.g. ["https://...","https://..."]

    @Column(nullable = false)
    private Instant createdAt;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getSources() { return sources; }
    public void setSources(String sources) { this.sources = sources; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
