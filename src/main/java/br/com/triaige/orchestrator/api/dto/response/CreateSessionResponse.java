package br.com.triaige.orchestrator.api.dto.response;

import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CreateSessionResponse {

    private UUID sessionId;
    private UUID caseId;
    private UUID correlationId;
    private String protocolo;
    private SessionStatus status;
}
