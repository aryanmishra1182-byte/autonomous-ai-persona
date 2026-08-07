package com.persona.repository;

import com.persona.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;

@Repository
public interface TopicRepository extends JpaRepository<Topic, String> {

    boolean existsByAgentIdAndUrl(String agentId, String url);

    boolean existsByAgentIdAndUrlAndStatusIn(String agentId, String url, Collection<String> statuses);
}
