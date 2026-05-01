package com.chatbot.motosdelcaribe.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.chatbot.motosdelcaribe.security.ApiKeyFilter;
import com.chatbot.motosdelcaribe.service.ConversationService;
import com.chatbot.motosdelcaribe.service.ConversationService.Reply;

/**
 * Tests del endpoint en aislamiento (sin DB ni JPA). El ConversationService se
 * mockea — sus reglas se verifican en ConversationServiceTest. Aqui solo se
 * verifica el contrato HTTP: status codes, validacion del body, header de auth
 * y el shape del JSON de respuesta.
 */
@WebMvcTest(ConversationController.class)
@TestPropertySource(properties = "app.api-key=test-key-1234")
class ConversationControllerTest {

    private static final String KEY = "test-key-1234";

    @Autowired private MockMvc mvc;
    @MockitoBean private ConversationService service;

    @Test
    void postValidoDevuelve200YElTextoDelService() throws Exception {
        when(service.handle("573041234567", "Hola"))
            .thenReturn(new Reply("Hola Sebastian, dime tu placa."));

        mvc.perform(post("/api/conversation")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"from":"573041234567","text":"Hola"}
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.to").value("573041234567"))
            .andExpect(jsonPath("$.text").value("Hola Sebastian, dime tu placa."));

        verify(service).handle("573041234567", "Hola");
    }

    @Test
    void sinApiKeyDevuelve401YNoInvocaService() throws Exception {
        mvc.perform(post("/api/conversation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"from":"573041234567","text":"Hola"}
                    """))
            .andExpect(status().isUnauthorized());

        verify(service, never()).handle(any(), any());
    }

    @Test
    void apiKeyInvalidaDevuelve401() throws Exception {
        mvc.perform(post("/api/conversation")
                .header(ApiKeyFilter.HEADER, "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"from":"573041234567","text":"Hola"}
                    """))
            .andExpect(status().isUnauthorized());

        verify(service, never()).handle(any(), any());
    }

    @Test
    void fromConCaracteresNoNumericosDevuelve400() throws Exception {
        mvc.perform(post("/api/conversation")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"from":"abc123","text":"Hola"}
                    """))
            .andExpect(status().isBadRequest());

        verify(service, never()).handle(any(), any());
    }

    @Test
    void textoVacioDevuelve400() throws Exception {
        mvc.perform(post("/api/conversation")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"from":"573041234567","text":""}
                    """))
            .andExpect(status().isBadRequest());

        verify(service, never()).handle(any(), any());
    }

    @Test
    void bodyMalformadoDevuelve400() throws Exception {
        mvc.perform(post("/api/conversation")
                .header(ApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-valid-json"))
            .andExpect(status().isBadRequest());

        verify(service, never()).handle(any(), any());
    }
}
