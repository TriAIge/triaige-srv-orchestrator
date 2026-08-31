-- Suporte a idempotência (seção 8 da spec). Não existe em script.sql — criada
-- como parte da Fase 1. TTL de 24h aplicado via expires_at (limpeza por job
-- futuro ou consulta filtrando expires_at > NOW(6); não há DROP automático).
CREATE TABLE idempotency_records (
    idempotency_key CHAR(36)     NOT NULL,
    endpoint        VARCHAR(150) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_body   MEDIUMTEXT   NOT NULL,
    status_code     INT          NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (idempotency_key, endpoint),
    INDEX idx_idempotency_records_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
