package com.chatbot.motosdelcaribe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chatbot.motosdelcaribe.respository.ChatSessionRepository;

@ExtendWith(MockitoExtension.class)
class ChatSessionCleanupTest {

    @Mock private ChatSessionRepository repo;
    @InjectMocks private ChatSessionCleanup cleanup;

    @Test
    void llamaAlRepoConThresholdDeAproximadamenteAhoraMenosTtl() {
        when(repo.deleteByLastSeenAtBefore(any(Instant.class))).thenReturn(0);

        Instant antes = Instant.now();
        cleanup.purgeStale();
        Instant despues = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repo, times(1)).deleteByLastSeenAtBefore(captor.capture());

        Instant threshold = captor.getValue();
        // El threshold debe estar entre [antes - 30min, despues - 30min] (margen de
        // ejecucion). Verificamos que respeta el TTL declarado.
        assertThat(threshold).isAfterOrEqualTo(antes.minus(30, ChronoUnit.MINUTES).minusSeconds(1));
        assertThat(threshold).isBeforeOrEqualTo(despues.minus(30, ChronoUnit.MINUTES).plusSeconds(1));
    }

    @Test
    void llamadasSucesivasUsanThresholdsActualizados() throws InterruptedException {
        when(repo.deleteByLastSeenAtBefore(any(Instant.class))).thenReturn(0);

        cleanup.purgeStale();
        Thread.sleep(5);
        cleanup.purgeStale();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repo, times(2)).deleteByLastSeenAtBefore(captor.capture());
        assertThat(captor.getAllValues().get(0)).isBefore(captor.getAllValues().get(1));
    }

    @Test
    void siNoHayBorrablesNoTocaOtrosMetodos() {
        when(repo.deleteByLastSeenAtBefore(any(Instant.class))).thenReturn(0);
        cleanup.purgeStale();
        verify(repo, never()).deleteAll();
        verify(repo, never()).save(any());
    }
}
