package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.response.SessionDetailResponse;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetSessionStatusUseCase {

    private final TriageSessionRepository sessionRepository;
    private final LegalDocumentRepository legalDocumentRepository;

    @Transactional(readOnly = true)
    public SessionDetailResponse execute(UUID sessionId, UUID lawFirmId) {
        TriageSession session = sessionRepository.findByIdAndLawFirmId(sessionId, lawFirmId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        List<LegalDocument> documents = legalDocumentRepository.findBySessionId(sessionId);

        return SessionDetailResponse.builder()
                .sessionId(session.getId())
                .protocolo(session.getProtocolo())
                .status(session.getStatus())
                .documents(documents.stream().map(this::toSummary).collect(Collectors.toList()))
                .build();
    }

    private SessionDetailResponse.DocumentSummary toSummary(LegalDocument document) {
        return SessionDetailResponse.DocumentSummary.builder()
                .documentId(document.getId())
                .status(document.getStatus())
                .nomeArquivoOriginal(document.getNomeArquivoOriginal())
                .build();
    }
}
