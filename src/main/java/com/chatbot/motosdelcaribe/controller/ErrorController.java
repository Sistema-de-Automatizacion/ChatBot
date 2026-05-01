package com.chatbot.motosdelcaribe.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chatbot.motosdelcaribe.dto.ErrorReport;
import com.chatbot.motosdelcaribe.model.ChatError;
import com.chatbot.motosdelcaribe.respository.ChatErrorRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint que n8n llama cuando algo fallo despues de que el bot ya respondio:
 * tipicamente errores enviando el mensaje a Meta Graph API. Persiste cada
 * reporte en {@code chat_error} para auditoria posterior.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ErrorController {

    private final ChatErrorRepository repo;

    @PostMapping("/errors")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void log(@Valid @RequestBody ErrorReport report) {
        ChatError row = new ChatError(
            null,
            report.phone(),
            report.context(),
            report.errorMessage(),
            Instant.now()
        );
        repo.save(row);
    }
}
