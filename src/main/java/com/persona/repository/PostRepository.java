package com.persona.repository;

import com.persona.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, String> {

    List<Post> findByAgentIdOrderByCreatedAtDesc(String agentId);

    List<Post> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);

    long countByAgentId(String agentId);

    @Query("SELECT p FROM Post p WHERE p.agentId = :agentId ORDER BY p.createdAt DESC LIMIT 1")
    Optional<Post> findLatestByAgentId(String agentId);
}
