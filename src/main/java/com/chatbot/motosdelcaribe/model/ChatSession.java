package com.chatbot.motosdelcaribe.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Estado conversacional del bot por cliente. Una fila representa "este telefono
 * esta a la mitad de un flujo y el bot espera el input X". Cuando el flujo termina
 * o pasan {@code ChatSessionCleanup#TTL} minutos sin actividad, la fila desaparece.
 *
 * El telefono usado como PK es la version normalizada a 10 digitos (ultimos del
 * numero E.164 que entrega Meta), que es la misma forma en la que se guarda en
 * la columna TELULT de la vista de cartera.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_session")
public class ChatSession {

    public enum Step {
        AWAITING_PLATE,
        AWAITING_OPTION
    }

    @Id
    @Column(name = "phone", length = 10)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", length = 32, nullable = false)
    private Step step;

    @Column(name = "contract_id", length = 30)
    private String contractId;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
