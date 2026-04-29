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
