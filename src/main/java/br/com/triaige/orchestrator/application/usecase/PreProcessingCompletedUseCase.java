package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.AiProcessingCompletedRequest;
import br.com.triaige.orchestrator.api.dto.request.PreProcessingCompletedRequest;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.LegalArea;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.AiAnalysisException;
import br.com.triaige.orchestrator.domain.exception.BusinessRuleException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.ai.AiAnalysisClient;
import br.com.triaige.orchestrator.infrastructure.ai.AiAnalysisRequest;
import br.com.triaige.orchestrator.infrastructure.ai.AiAnalysisResponse;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.s3.DocumentStoragePort;
import br.com.triaige.orchestrator.infrastructure.s3.StoredDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PreProcessingCompletedUseCase {

    private static final List<String> DEFAULT_REQUESTED_TOOLS = List.of("CASE_SUMMARY", "JURISPRUDENCE_SEARCH");
    private static final String JURISPRUDENCE_SEARCH_TOOL = "JURISPRUDENCE_SEARCH";

    private final TriageSessionRepository sessionRepository;
    private final LegalDocumentRepository documentRepository;
    private final AuditService auditService;
    private final AiAnalysisClient aiAnalysisClient;
    private final DocumentStoragePort documentStoragePort;
    private final AiProcessingCompletedUseCase aiProcessingCompletedUseCase;
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(UUID sessionId, PreProcessingCompletedRequest request) {
        TriageSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // Valida a correlação
        if (!session.getCorrelationId().equals(request.getCorrelationId())) {
            throw new BusinessRuleException("correlationId inválido para esta sessão");
        }

        // Idempotência: se já avançou para IA, não reprocessar
        if (session.getStatus() == SessionStatus.AGUARDANDO_PROCESSAMENTO_IA ||
            session.getStatus() == SessionStatus.EM_PROCESSAMENTO_IA ||
            session.getStatus() == SessionStatus.CONCLUIDA) {
            log.warn("Pre-processing callback received but session already at status {}. Skipping. sessionId={}",
                    session.getStatus(), sessionId);
            return;
        }

        SessionStatus previousStatus = session.getStatus();

        List<LegalDocument> docs = documentRepository.findBySessionId(sessionId);
        Map<UUID, LegalDocument> docMap = docs.stream()
                .collect(Collectors.toMap(LegalDocument::getId, Function.identity()));

        // Atualiza o ponteiro de cada documento processado
        for (PreProcessingCompletedRequest.ProcessedDocumentInfo info : request.getProcessedDocuments()) {
            LegalDocument doc = docMap.get(info.getDocumentId());
            if (doc == null) continue;

            // Contrato do triaige-fn-ocr-normalizer: status "OK" ou "EMPTY" indicam que o
            // arquivo normalizado foi gravado no bucket trusted (mesmo que vazio); apenas
            // "ERROR" indica falha no pré-processamento.
            if ("OK".equalsIgnoreCase(info.getStatus()) || "EMPTY".equalsIgnoreCase(info.getStatus())) {
                doc.setProcessedBucket(info.getProcessedBucket());
                doc.setProcessedObjectKey(info.getProcessedObjectKey());
                doc.setStatus(DocumentStatus.PRE_PROCESSADO);
            } else {
                doc.setStatus(DocumentStatus.FALHA_PRE_PROCESSAMENTO);
                doc.setErrorMessage("Falha no pré-processamento reportada pelo serviço externo (status=" + info.getStatus() + ")");
            }
        }
        documentRepository.saveAll(docs);

        // Verifica se houve alguma falha
        boolean anyFailed = docs.stream()
                .anyMatch(d -> d.getStatus() == DocumentStatus.FALHA_PRE_PROCESSAMENTO);
        if (anyFailed) {
            session.setStatus(SessionStatus.FALHA);
            sessionRepository.save(session);
            auditService.record(sessionId, session.getCorrelationId(), EventType.SESSION_FAILED,
                    "Falha no pré-processamento de um ou mais documentos");
            log.error("Pre-processing failed for session: {}", sessionId);
            return;
        }

        session.setStatus(SessionStatus.AGUARDANDO_PROCESSAMENTO_IA);
        sessionRepository.save(session);

        auditService.record(
                sessionId,
                session.getCorrelationId(),
                EventType.PRE_PROCESSING_COMPLETED,
                String.format("Pré-processamento concluído. Status: %s -> %s",
                        previousStatus, SessionStatus.AGUARDANDO_PROCESSAMENTO_IA)
        );

        log.info("Pre-processing completed: sessionId={}, documentsUpdated={}, correlationId={}",
                sessionId, docs.size(), session.getCorrelationId());

        // Chama o serviço de IA de forma síncrona e completa o fluxo (resultado em S3 + triaige-results-ready)
        AiAnalysisResponse aiResponse = callAiService(session, docs);
        AiProcessingCompletedRequest completedRequest = buildCompletedRequest(session, aiResponse);
        aiProcessingCompletedUseCase.execute(sessionId, completedRequest);
    }

    private AiAnalysisResponse callAiService(TriageSession session, List<LegalDocument> docs) {
        List<AiAnalysisRequest.DocumentRef> documentRefs = docs.stream()
                .filter(doc -> doc.getProcessedBucket() != null)
                .map(doc -> AiAnalysisRequest.DocumentRef.builder()
                        .documentId(doc.getId().toString())
                        .documentType(doc.getTipoDocumento().name())
                        .s3Key(doc.getProcessedObjectKey())
                        .build())
                .toList();

        String s3Bucket = docs.stream()
                .map(LegalDocument::getProcessedBucket)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        AiAnalysisRequest aiRequest = AiAnalysisRequest.builder()
                .correlationId(session.getCorrelationId().toString())
                .sessionId(session.getId().toString())
                .caseId(session.getLegalCase().getId().toString())
                .tenantId(session.getLawFirm().getId().toString())
                .legalArea(toAiLegalArea(session.getLegalCase().getAreaJuridica()))
                .s3Bucket(s3Bucket)
                .documents(documentRefs)
                .requestedTools(DEFAULT_REQUESTED_TOOLS)
                .build();

        return aiAnalysisClient.analyze(aiRequest);
    }

    private AiProcessingCompletedRequest buildCompletedRequest(TriageSession session, AiAnalysisResponse aiResponse) {
        AiProcessingCompletedRequest completedRequest = new AiProcessingCompletedRequest();
        completedRequest.setCorrelationId(session.getCorrelationId());

        if (!"COMPLETED".equalsIgnoreCase(aiResponse.getStatus())) {
            AiAnalysisResponse.AnalysisError error = aiResponse.getError();

            // Erros retentáveis (ex.: timeout do Gemini) não encerram a sessão aqui:
            // a exceção propaga, a transação é desfeita e o callback retorna erro
            // para que o SQS reentregue a mensagem de pré-processamento.
            if (error != null && error.isRetryable()) {
                throw new AiAnalysisException(String.format(
                        "Falha retentável do serviço de IA: sessionId=%s, errorCode=%s, message=%s",
                        session.getId(), error.getCode(), error.getMessage()), null);
            }

            log.error("AI analysis did not complete for session {}: status={}, errorCode={}",
                    session.getId(), aiResponse.getStatus(), error != null ? error.getCode() : null);
            completedRequest.setStatus("FALHA");
            return completedRequest;
        }

        StoredDocument storedResult = storeAnalysisResult(session, aiResponse);

        boolean jurisprudenceUsed = aiResponse.getMetadata() != null
                && aiResponse.getMetadata().getToolsUsed() != null
                && aiResponse.getMetadata().getToolsUsed().contains(JURISPRUDENCE_SEARCH_TOOL);

        completedRequest.setStatus("CONCLUIDO");
        completedRequest.setResultBucket(storedResult.getBucket());
        completedRequest.setResultObjectKey(storedResult.getObjectKey());
        completedRequest.setJurisprudenceUsed(jurisprudenceUsed);
        return completedRequest;
    }

    private StoredDocument storeAnalysisResult(TriageSession session, AiAnalysisResponse aiResponse) {
        try {
            String resultJson = objectMapper.writeValueAsString(aiResponse);
            return documentStoragePort.storeResult(session.getLawFirm().getId(), session.getId(), resultJson);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("Falha ao serializar resultado da análise de IA");
        }
    }

    /**
     * O serviço de IA usa o vocabulário "CIVIL"/"TRABALHISTA"; o domínio do
     * orquestrador usa "CIVEL"/"TRABALHISTA".
     */
    private String toAiLegalArea(LegalArea legalArea) {
        return switch (legalArea) {
            case CIVEL -> "CIVIL";
            case TRABALHISTA -> "TRABALHISTA";
        };
    }
}
