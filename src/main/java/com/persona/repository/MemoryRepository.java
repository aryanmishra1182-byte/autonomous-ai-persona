package com.persona.repository;

import com.persona.model.AgentMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemoryRepository extends JpaRepository<AgentMemory, Long> {

    Optional<AgentMemory> findByAgentIdAndMemKey(String agentId, String memKey);

    void deleteByAgentIdAndMemKey(String agentId, String memKey);
}
