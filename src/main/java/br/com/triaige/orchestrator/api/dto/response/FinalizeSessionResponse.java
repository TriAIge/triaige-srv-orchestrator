package br.com.triaige.orchestrator.api.dto.response;

import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeSessionResponse {

    private UUID sessionId;
    private String protocolo;
    private SessionStatus status;
    private int documentsCount;
}
