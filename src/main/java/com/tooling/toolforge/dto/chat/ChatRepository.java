package com.tooling.toolforge.dto.chat;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatRepository extends MongoRepository<ChatSession, String> {
    List<ChatSession> findByUserIdOrderByLastUpdatedDesc(String userId);
}