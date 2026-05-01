package com.chatbot.motosdelcaribe.dto;

/**
 * Respuesta que el bot devuelve a n8n; n8n la usa para llamar al Graph API
 * de Meta. {@code to} es el mismo telefono entrante (en E.164 sin "+");
 * {@code text} es el mensaje a enviar al cliente final.
 */
public record OutgoingMessage(String to, String text) {}
