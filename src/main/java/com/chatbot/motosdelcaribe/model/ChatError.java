package com.chatbot.motosdelcaribe.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Log de errores asincronos reportados por n8n. A diferencia de {@link ChatSession}
 * que tiene TTL, esta tabla es append-only: cada vez que n8n no pudo enviar una
 * respuesta a Meta (cliente bloqueado, rate limit, token vencido, network), guarda
 * una fila aqui para auditoria posterior.
 *
 * No se borra automaticamente. Si crece demasiado, agregar un @Scheduled que
 * archive lo de mas de 90 dias.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "chat_error",
    indexes = @Index(name = "idx_chat_error_occurred_at", columnList = "occurred_at")
)
public class ChatError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "context", length = 64, nullable = false)
    private String context;

    @Column(name = "error_message", columnDefinition = "TEXT", nullable = false)
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
