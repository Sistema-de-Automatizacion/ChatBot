-- Idempotente: se ejecuta en cada arranque por spring.sql.init.mode=always.
-- Usar solo CREATE ... IF NOT EXISTS para no destruir data ya escrita.

CREATE TABLE IF NOT EXISTS chat_session (
    phone         VARCHAR(10)  NOT NULL,
    step          VARCHAR(32)  NOT NULL,
    contract_id   VARCHAR(30),
    last_seen_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (phone),
    INDEX idx_chat_session_last_seen (last_seen_at)
);

-- Log append-only de errores asincronos reportados por n8n (no se borra; si
-- crece mucho, archivar lo de >90 dias con un @Scheduled posterior).
CREATE TABLE IF NOT EXISTS chat_error (
    id            BIGINT        AUTO_INCREMENT PRIMARY KEY,
    phone         VARCHAR(15),
    context       VARCHAR(64)   NOT NULL,
    error_message TEXT          NOT NULL,
    occurred_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_chat_error_occurred_at (occurred_at)
);
