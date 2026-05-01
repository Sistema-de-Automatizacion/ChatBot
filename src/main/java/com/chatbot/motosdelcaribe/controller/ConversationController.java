package com.chatbot.motosdelcaribe.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatbot.motosdelcaribe.dto.IncomingMessage;
import com.chatbot.motosdelcaribe.dto.OutgoingMessage;
import com.chatbot.motosdelcaribe.service.ConversationService;
import com.chatbot.motosdelcaribe.service.ConversationService.Reply;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint que n8n golpea por cada mensaje entrante de WhatsApp. La verificacion
 * del webhook de Meta y la firma {@code X-Hub-Signature-256} las hace n8n; aqui
 * solo viene el mensaje ya parseado y autenticado por el header X-API-Key
 * (validado en {@code ApiKeyFilter}).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/conversation")
    public OutgoingMessage handle(@Valid @RequestBody IncomingMessage msg) {
        Reply reply = conversationService.handle(msg.from(), msg.text());
        return new OutgoingMessage(msg.from(), reply.text());
    }
}
