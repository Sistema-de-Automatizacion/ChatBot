package com.chatbot.motosdelcaribe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.chatbot.motosdelcaribe.model.ChatError;
import com.chatbot.motosdelcaribe.respository.ChatErrorRepository;
import com.chatbot.motosdelcaribe.security.ApiKeyFilter;

@WebMvcTest(ErrorController.class)
@TestPropertySource(properties = "app.api-key=test-key-1234")
class ErrorControllerTest {

    private static final String KEY = "test-key-1234";

    @Autowired private MockMvc mvc;
    @MockitoBean private ChatErrorRepository repo;

    @Test
    void postValidoDevuelve204YPersisteElError() throws Exception {
        Instant antes = Instant.now();

        mvc.perform(post("/api/errors")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "phone": "573041234567",
                      "context": "send_to_meta",
                      "errorMessage": "Meta API 403: blocked"
                    }
                    """))
            .andExpect(status().isNoContent());

        ArgumentCaptor<ChatError> captor = ArgumentCaptor.forClass(ChatError.class);
        verify(repo, times(1)).save(captor.capture());

        ChatError saved = captor.getValue();
        assertThat(saved.getId()).isNull();              // generado por la BD
        assertThat(saved.getPhone()).isEqualTo("573041234567");
        assertThat(saved.getContext()).isEqualTo("send_to_meta");
        assertThat(saved.getErrorMessage()).isEqualTo("Meta API 403: blocked");
        assertThat(saved.getOccurredAt()).isAfterOrEqualTo(antes);
        assertThat(saved.getOccurredAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void phoneEsOpcional() throws Exception {
        mvc.perform(post("/api/errors")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "context": "n8n_internal",
                      "errorMessage": "workflow timeout"
                    }
                    """))
            .andExpect(status().isNoContent());

        ArgumentCaptor<ChatError> captor = ArgumentCaptor.forClass(ChatError.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getPhone()).isNull();
    }

    @Test
    void sinApiKeyDevuelve401YNoPersiste() throws Exception {
        mvc.perform(post("/api/errors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"context":"x","errorMessage":"y"}
                    """))
            .andExpect(status().isUnauthorized());

        verify(repo, never()).save(any());
    }

    @Test
    void apiKeyInvalidaDevuelve401() throws Exception {
        mvc.perform(post("/api/errors")
                .header(ApiKeyFilter.HEADER, "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"context":"x","errorMessage":"y"}
                    """))
            .andExpect(status().isUnauthorized());

        verify(repo, never()).save(any());
    }

    @Test
    void contextVacioDevuelve400() throws Exception {
        mvc.perform(post("/api/errors")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"context":"","errorMessage":"y"}
                    """))
            .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    @Test
    void errorMessageVacioDevuelve400() throws Exception {
        mvc.perform(post("/api/errors")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"context":"x","errorMessage":""}
                    """))
            .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    @Test
    void contextDemasiadoLargoDevuelve400() throws Exception {
        String tooLong = "a".repeat(65);
        mvc.perform(post("/api/errors")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"context":"%s","errorMessage":"y"}
                    """.formatted(tooLong)))
            .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    @Test
    void phoneDemasiadoLargoDevuelve400() throws Exception {
        String tooLong = "1".repeat(16);
        mvc.perform(post("/api/errors")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"phone":"%s","context":"x","errorMessage":"y"}
                    """.formatted(tooLong)))
            .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    @Test
    void bodyMalformadoDevuelve400() throws Exception {
        mvc.perform(post("/api/errors")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-valid"))
            .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }
}
