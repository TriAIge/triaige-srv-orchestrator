package br.com.triaige.orchestrator.domain.enums;

public enum SessionStatus {
    ABERTA,
    AGUARDANDO_PRE_PROCESSAMENTO,
    EM_PRE_PROCESSAMENTO,
    AGUARDANDO_PROCESSAMENTO_IA,
    EM_PROCESSAMENTO_IA,
    CONCLUIDA,
    FALHA,
    CANCELADA
}
