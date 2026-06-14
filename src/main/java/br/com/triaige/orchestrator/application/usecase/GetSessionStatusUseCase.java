package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.response.AuditEventResponse;
import br.com.triaige.orchestrator.api.dto.response.SessionDetailResponse;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.persistence.AuditEventRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetSessionStatusUseCase {

    private final TriageSessionRepository sessionRepository;
    private final AuditEventRepository auditEventRepository;

    @Transactional(readOnly = true)
    public SessionDetailResponse getSession(UUID sessionId, UUID lawFirmId) {
        TriageSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (lawFirmId != null && !session.getLawFirm().getId().equals(lawFirmId)) {
            throw new SessionNotFoundException(sessionId);
        }

        var documents = session.getDocuments().stream()
                .map(doc -> SessionDetailResponse.DocumentSummary.builder()
                        .documentId(doc.getId())
                        .nomeArquivoOriginal(doc.getNomeArquivoOriginal())
                        .tipoDocumento(doc.getTipoDocumento())
                        .status(doc.getStatus())
                        .build())
                .toList();

        SessionDetailResponse.ResultSummary resultSummary = null;
        if (session.getResult() != null) {
            resultSummary = SessionDetailResponse.ResultSummary.builder()
                    .resultBucket(session.getResult().getResultBucket())
                    .resultObjectKey(session.getResult().getResultObjectKey())
                    .summaryObjectKey(session.getResult().getSummaryObjectKey())
                    .build();
        }

        return SessionDetailResponse.builder()
                .sessionId(session.getId())
                .caseId(session.getLegalCase() != null ? session.getLegalCase().getId() : null)
                .protocolo(session.getProtocolo())
                .correlationId(session.getCorrelationId())
                .status(session.getStatus())
                .areaJuridica(session.getLegalCase() != null ? session.getLegalCase().getAreaJuridica() : null)
                .tipoCaso(session.getLegalCase() != null ? session.getLegalCase().getTipoCaso() : null)
                .documents(documents)
                .result(resultSummary)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getEvents(UUID sessionId, UUID lawFirmId) {
        // Validate session exists and law firm has access
        TriageSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (lawFirmId != null && !session.getLawFirm().getId().equals(lawFirmId)) {
            throw new SessionNotFoundException(sessionId);
        }

        return auditEventRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(event -> AuditEventResponse.builder()
                        .eventType(event.getEventType())
                        .description(event.getDescription())
                        .createdAt(event.getCreatedAt())
                        .build())
                .toList();
    }
}
