-- TriAIge Orchestrator final schema

CREATE TABLE law_firms (
    id              CHAR(36)     NOT NULL,
    nome            VARCHAR(200) NOT NULL,
    cnpj            VARCHAR(20),
    email_contato   VARCHAR(200),
    telefone        VARCHAR(30),
    status          VARCHAR(30)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_law_firms_cnpj (cnpj),
    INDEX idx_law_firms_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE law_firm_contacts (
    id                 CHAR(36)     NOT NULL,
    law_firm_id         CHAR(36)     NOT NULL,
    nome               VARCHAR(200) NOT NULL,
    email              VARCHAR(200),
    telefone           VARCHAR(30),
    canal_preferencial VARCHAR(20)  NOT NULL,
    ativo              TINYINT(1)   NOT NULL DEFAULT 1,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_law_firm_contacts_law_firm_id (law_firm_id),
    INDEX idx_law_firm_contacts_email (email),
    CONSTRAINT fk_law_firm_contacts_law_firm FOREIGN KEY (law_firm_id)
        REFERENCES law_firms(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE api_credentials (
    id              CHAR(36)     NOT NULL,
    law_firm_id      CHAR(36)     NOT NULL,
    name            VARCHAR(100) NOT NULL,
    token_hash      VARCHAR(255) NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    last_used_at    DATETIME(6),
    expires_at      DATETIME(6),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_credentials_token_hash (token_hash),
    INDEX idx_api_credentials_law_firm_id (law_firm_id),
    INDEX idx_api_credentials_status (status),
    CONSTRAINT fk_api_credentials_law_firm FOREIGN KEY (law_firm_id)
        REFERENCES law_firms(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE protocol_sequences (
    year          INT    NOT NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE triage_sessions (
    id                CHAR(36)     NOT NULL,
    law_firm_id        CHAR(36)     NOT NULL,
    api_credential_id  CHAR(36),
    protocolo         VARCHAR(30)  NOT NULL,
    correlation_id    CHAR(36)     NOT NULL,
    status            VARCHAR(40)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_triage_sessions_protocolo (protocolo),
    INDEX idx_triage_sessions_law_firm_id (law_firm_id),
    INDEX idx_triage_sessions_api_credential_id (api_credential_id),
    INDEX idx_triage_sessions_status (status),
    INDEX idx_triage_sessions_correlation_id (correlation_id),
    CONSTRAINT fk_triage_sessions_law_firm FOREIGN KEY (law_firm_id)
        REFERENCES law_firms(id),
    CONSTRAINT fk_triage_sessions_api_credential FOREIGN KEY (api_credential_id)
        REFERENCES api_credentials(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE legal_cases (
    id             CHAR(36)     NOT NULL,
    session_id     CHAR(36)     NOT NULL,
    titulo         VARCHAR(255) NOT NULL,
    area_juridica  VARCHAR(30)  NOT NULL,
    tipo_caso      VARCHAR(50)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_legal_cases_session_id (session_id),
    INDEX idx_legal_cases_area_juridica (area_juridica),
    INDEX idx_legal_cases_tipo_caso (tipo_caso),
    CONSTRAINT fk_legal_cases_session FOREIGN KEY (session_id)
        REFERENCES triage_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE notification_recipients (
    id                  CHAR(36)     NOT NULL,
    session_id           CHAR(36)     NOT NULL,
    contact_id           CHAR(36),
    nome                 VARCHAR(200) NOT NULL,
    email                VARCHAR(200),
    telefone             VARCHAR(30),
    canal_preferencial   VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_recipients_session_id (session_id),
    INDEX idx_notification_recipients_contact_id (contact_id),
    CONSTRAINT fk_notification_recipients_session FOREIGN KEY (session_id)
        REFERENCES triage_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_recipients_contact FOREIGN KEY (contact_id)
        REFERENCES law_firm_contacts(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE legal_documents (
    id                     CHAR(36)     NOT NULL,
    session_id             CHAR(36)     NOT NULL,
    nome_arquivo_original  VARCHAR(255) NOT NULL,
    tipo_documento         VARCHAR(30)  NOT NULL,
    content_type           VARCHAR(100) NOT NULL,
    tamanho_bytes          BIGINT,
    raw_bucket             VARCHAR(200) NOT NULL,
    raw_object_key         VARCHAR(500) NOT NULL,
    processed_bucket       VARCHAR(200),
    processed_object_key   VARCHAR(500),
    status                 VARCHAR(40)  NOT NULL,
    error_message          VARCHAR(1000),
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_legal_documents_session_id (session_id),
    INDEX idx_legal_documents_status (status),
    INDEX idx_legal_documents_tipo_documento (tipo_documento),
    CONSTRAINT fk_legal_documents_session FOREIGN KEY (session_id)
        REFERENCES triage_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE processing_steps (
    id              CHAR(36)     NOT NULL,
    session_id      CHAR(36)     NOT NULL,
    document_id     CHAR(36),
    step_name       VARCHAR(50)  NOT NULL,
    status          VARCHAR(40)  NOT NULL,
    started_at      DATETIME(6),
    finished_at     DATETIME(6),
    error_message   VARCHAR(1000),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_processing_steps_session_id (session_id),
    INDEX idx_processing_steps_document_id (document_id),
    INDEX idx_processing_steps_step_name (step_name),
    INDEX idx_processing_steps_status (status),
    CONSTRAINT fk_processing_steps_session FOREIGN KEY (session_id)
        REFERENCES triage_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_processing_steps_document FOREIGN KEY (document_id)
        REFERENCES legal_documents(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE ai_tool_calls (
    id                CHAR(36)     NOT NULL,
    session_id        CHAR(36)     NOT NULL,
    tool_name         VARCHAR(80)  NOT NULL,
    provider          VARCHAR(80),
    request_payload   TEXT,
    response_payload  TEXT,
    status            VARCHAR(40)  NOT NULL,
    error_message     VARCHAR(1000),
    started_at        DATETIME(6)  NOT NULL,
    finished_at       DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_ai_tool_calls_session_id (session_id),
    INDEX idx_ai_tool_calls_tool_name (tool_name),
    INDEX idx_ai_tool_calls_status (status),
    CONSTRAINT fk_ai_tool_calls_session FOREIGN KEY (session_id)
        REFERENCES triage_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE triage_results (
    id                   CHAR(36)     NOT NULL,
    session_id           CHAR(36)     NOT NULL,
    result_bucket        VARCHAR(200) NOT NULL,
    result_object_key    VARCHAR(500) NOT NULL,
    summary_object_key   VARCHAR(500),
    jurisprudence_used   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_triage_results_session_id (session_id),
    CONSTRAINT fk_triage_results_session FOREIGN KEY (session_id)
        REFERENCES triage_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE notification_deliveries (
    id                    CHAR(36)     NOT NULL,
    session_id             CHAR(36)     NOT NULL,
    recipient_id           CHAR(36)     NOT NULL,
    canal                  VARCHAR(20)  NOT NULL,
    destino                VARCHAR(200) NOT NULL,
    status                 VARCHAR(40)  NOT NULL,
    provider               VARCHAR(50),
    provider_message_id    VARCHAR(200),
    error_message          VARCHAR(1000),
    sent_at                DATETIME(6),
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notification_deliveries_session_id (session_id),
    INDEX idx_notification_deliveries_recipient_id (recipient_id),
    INDEX idx_notification_deliveries_status (status),
    INDEX idx_notification_deliveries_canal (canal),
    CONSTRAINT fk_notification_deliveries_session FOREIGN KEY (session_id)
        REFERENCES triage_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_deliveries_recipient FOREIGN KEY (recipient_id)
        REFERENCES notification_recipients(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE audit_events (
    id              CHAR(36)     NOT NULL,
    session_id      CHAR(36),
    law_firm_id      CHAR(36),
    correlation_id  CHAR(36),
    event_type      VARCHAR(50)  NOT NULL,
    description     VARCHAR(500) NOT NULL,
    payload_json    TEXT,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_session_id (session_id),
    INDEX idx_audit_law_firm_id (law_firm_id),
    INDEX idx_audit_correlation_id (correlation_id),
    INDEX idx_audit_event_type (event_type),
    CONSTRAINT fk_audit_events_session FOREIGN KEY (session_id)
        REFERENCES triage_sessions(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_events_law_firm FOREIGN KEY (law_firm_id)
        REFERENCES law_firms(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;