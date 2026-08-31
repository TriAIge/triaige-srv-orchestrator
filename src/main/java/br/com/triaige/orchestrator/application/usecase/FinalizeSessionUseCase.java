package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.response.FinalizeSessionResponse;
import br.com.triaige.orchestrator.application.service.SessionFinalizationService;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.InvalidSessionStateException;
import br.com.triaige.orchestrator.domain.exception.NoDocumentsReceivedException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
import br.com.triaige.orchestrator.infrastructure.sqs.message.DocumentReadyMessage;
import br.com.triaige.orchestrator.shared.metrics.IngestionMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Não é @Transactional no use case: a publicação em Q2 (passo 4) precisa
 * ocorrer antes da transação MySQL (passo 5), por design (spec seção 5.4) —
 * se a transação falhar depois do publish, nada no estado muda (nada foi
 * commitado ainda), então não há inconsistência a reconciliar nesta fase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeSessionUseCase {

    private final TriageSessionRepository sessionRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final QueuePublisher queuePublisher;
    private final AwsProperties awsProperties;
    private final SessionFinalizationService sessionFinalizationService;
    private final IngestionMetrics ingestionMetrics;

    public FinalizeSessionResponse execute(UUID sessionId, UUID lawFirmId) {
        TriageSession session = sessionRepository.findByIdAndLawFirmId(sessionId, lawFirmId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getStatus() != SessionStatus.RECEIVING_DOCUMENTS) {
            ingestionMetrics.incrementFinalizeError("INVALID_SESSION_STATE");
            throw new InvalidSessionStateException(
                    "Sessão " + sessionId + " não está em RECEIVING_DOCUMENTS (status atual: " + session.getStatus() + ")");
        }

        List<LegalDocument> receivedDocuments = legalDocumentRepository.findBySessionIdAndStatus(
                sessionId, DocumentStatus.RECEIVED);
        if (receivedDocuments.isEmpty()) {
            ingestionMetrics.incrementFinalizeError("NO_DOCUMENTS_RECEIVED");
            throw new NoDocumentsReceivedException(sessionId);
        }

        DocumentReadyMessage message = buildReadyMessage(session, receivedDocuments);
        long q2PublishStartedAt = System.currentTimeMillis();
        queuePublisher.publish(awsProperties.getSqs().getDocsPreprocessingQueueUrl(), message);
        ingestionMetrics.recordQ2PublishLatency(System.currentTimeMillis() - q2PublishStartedAt);

        sessionFinalizationService.finalizeSession(sessionId, lawFirmId, receivedDocuments);

        ingestionMetrics.incrementSessionsFinalized();

        log.info("Session finalize requested: sessionId={}, documentsCount={}", sessionId, receivedDocuments.size());

        return FinalizeSessionResponse.builder()
                .sessionId(sessionId)
                .protocolo(session.getProtocolo())
                .status(SessionStatus.QUEUED_FOR_PROCESSING)
                .documentsCount(receivedDocuments.size())
                .build();
    }

    private DocumentReadyMessage buildReadyMessage(TriageSession session, List<LegalDocument> documents) {
        return DocumentReadyMessage.builder()
                .correlationId(session.getCorrelationId())
                .sessionId(session.getId())
                .protocolo(session.getProtocolo())
                .lawFirmId(session.getLawFirm().getId())
                .legalCase(DocumentReadyMessage.LegalCaseInfo.builder()
                        .id(session.getLegalCase().getId())
                        .titulo(session.getLegalCase().getTitulo())
                        .areaJuridica(session.getLegalCase().getAreaJuridica())
                        .tipoCaso(session.getLegalCase().getTipoCaso())
                        .build())
                .documents(documents.stream().map(this::toDocumentInfo).collect(Collectors.toList()))
                .publishedAt(LocalDateTime.now())
                .build();
    }

    private DocumentReadyMessage.DocumentInfo toDocumentInfo(LegalDocument document) {
        return DocumentReadyMessage.DocumentInfo.builder()
                .documentId(document.getId())
                .attachmentGroupId(document.getAttachmentGroupId())
                .partNumber(document.getPartNumber())
                .nomeArquivoOriginal(document.getNomeArquivoOriginal())
                .tipoDocumento(document.getTipoDocumento())
                .contentType(document.getContentType())
                .tamanhoBytes(document.getTamanhoBytes())
                .rawBucket(document.getRawBucket())
                .rawObjectKey(document.getRawObjectKey())
                .build();
    }
}
