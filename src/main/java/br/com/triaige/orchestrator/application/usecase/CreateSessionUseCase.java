package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.CreateSessionRequest;
import br.com.triaige.orchestrator.api.dto.response.CreateSessionResponse;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.application.service.ProtocolGeneratorService;
import br.com.triaige.orchestrator.domain.entity.*;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.BusinessRuleException;
import br.com.triaige.orchestrator.domain.exception.LawFirmNotFoundException;
import br.com.triaige.orchestrator.infrastructure.persistence.LawFirmRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateSessionUseCase {

    private final TriageSessionRepository sessionRepository;
    private final LawFirmRepository lawFirmRepository;
    private final ProtocolGeneratorService protocolGenerator;
    private final AuditService auditService;

    @Transactional
    public CreateSessionResponse execute(CreateSessionRequest request, UUID correlationId) {
        // BR: no criminal area
        if (request.getAreaJuridica() == null) {
            throw new BusinessRuleException("Área jurídica inválida");
        }

        log.info("Creating triage session: lawFirmId={}, tipoCaso={}, correlationId={}",
                request.getLawFirmId(), request.getTipoCaso(), correlationId);

        LawFirm lawFirm = lawFirmRepository.findById(request.getLawFirmId())
                .orElseThrow(() -> new LawFirmNotFoundException(request.getLawFirmId()));

        String protocolo = protocolGenerator.generateNextProtocol();

        TriageSession session = TriageSession.builder()
                .id(UUID.randomUUID())
                .lawFirm(lawFirm)
                .protocolo(protocolo)
                .correlationId(correlationId)
                .status(SessionStatus.ABERTA)
                .build();

        LegalCase legalCase = LegalCase.builder()
                .id(UUID.randomUUID())
                .session(session)
                .titulo(request.getTitulo())
                .areaJuridica(request.getAreaJuridica())
                .tipoCaso(request.getTipoCaso())
                .build();

        NotificationRecipient recipient = NotificationRecipient.builder()
                .id(UUID.randomUUID())
                .session(session)
                .nome(request.getRemetente().getNome())
                .email(request.getRemetente().getEmail())
                .telefone(request.getRemetente().getTelefone())
                .canalPreferencial(request.getRemetente().getCanalPreferencial())
                .build();

        session.setLegalCase(legalCase);
        session.setRecipient(recipient);

        TriageSession saved = sessionRepository.save(session);

        auditService.record(
                saved.getId(),
                correlationId,
                EventType.SESSION_CREATED,
                "Sessão criada com protocolo " + protocolo
        );

        log.info("Session created: sessionId={}, caseId={}, protocolo={}, correlationId={}",
                saved.getId(), legalCase.getId(), protocolo, correlationId);

        return CreateSessionResponse.builder()
                .sessionId(saved.getId())
                .caseId(legalCase.getId())
                .correlationId(correlationId)
                .protocolo(protocolo)
                .status(SessionStatus.ABERTA)
                .build();
    }
}
