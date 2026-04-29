package com.chatbot.motosdelcaribe.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chatbot.motosdelcaribe.respository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Garbage-collect de sesiones conversacionales abandonadas. Sin esto, una persona
 * que dejo el chat a la mitad veria al bot retomar el flujo dias despues, lo que
 * se siente roto.
 */
@Component
@RequiredArgsConstructor
public class ChatSessionCleanup {

    /** Cuanto tiempo sin actividad antes de considerar una sesion como abandonada. */
    private static final Duration TTL = Duration.ofMinutes(30);

    /** Cada cuanto corre el cleanup, en milisegundos. */
    private static final long FIXED_RATE_MS = 5 * 60 * 1000L;

    private static final Logger log = LoggerFactory.getLogger(ChatSessionCleanup.class);

    private final ChatSessionRepository repo;

    @Scheduled(fixedRate = FIXED_RATE_MS)
    public void purgeStale() {
        Instant threshold = Instant.now().minus(TTL);
        int removed = repo.deleteByLastSeenAtBefore(threshold);
        if (removed > 0) {
            log.info("ChatSessionCleanup: removed {} stale session(s) older than {}", removed, threshold);
        }
    }
}
