package com.chatbot.motosdelcaribe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mensaje entrante que n8n entrega al bot. n8n ya extrajo el {@code from}
 * (telefono del cliente en formato E.164 sin "+") y el {@code text} (cuerpo
 * del mensaje del usuario) del payload original de Meta.
 */
public record IncomingMessage(

    @NotBlank
    @Pattern(regexp = "\\d{10,15}", message = "from debe ser un numero E.164 sin '+' (10-15 digitos)")
    String from,

    @NotBlank
    @Size(max = 500, message = "text no puede exceder 500 caracteres")
    String text
) {}
