package com.chatbot.motosdelcaribe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reporte de error que n8n envia al bot cuando algo fallo despues de la
 * respuesta sincrona del bot — tipicamente un fallo enviando el mensaje a
 * Meta Graph API.
 */
public record ErrorReport(

    @Size(max = 15, message = "phone no puede exceder 15 digitos")
    String phone,

    @NotBlank
    @Size(max = 64, message = "context no puede exceder 64 caracteres")
    String context,

    @NotBlank
    @Size(max = 5000, message = "errorMessage no puede exceder 5000 caracteres")
    String errorMessage
) {}
