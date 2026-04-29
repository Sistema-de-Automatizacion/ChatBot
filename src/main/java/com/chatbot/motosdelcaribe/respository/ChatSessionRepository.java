package com.chatbot.motosdelcaribe.respository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.chatbot.motosdelcaribe.model.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    /**
     * Borra todas las sesiones cuyo ultimo mensaje fue antes del threshold.
     * Usado por {@code ChatSessionCleanup} para garbage-collect de sesiones
     * abandonadas mas alla del TTL.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ChatSession s WHERE s.lastSeenAt < :threshold")
    int deleteByLastSeenAtBefore(Instant threshold);
}
