package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.CreateSessionRequest;
import br.com.triaige.orchestrator.api.dto.response.CreateSessionResponse;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.application.service.ProtocolGeneratorService;
import br.com.triaige.orchestrator.domain.entity.ApiCredential;
import br.com.triaige.orchestrator.domain.entity.LawFirm;
import br.com.triaige.orchestrator.domain.entity.LawFirmContact;
import br.com.triaige.orchestrator.domain.entity.LegalCase;
import br.com.triaige.orchestrator.domain.entity.NotificationRecipient;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.infrastructure.persistence.LawFirmContactRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.NotificationRecipientRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.shared.metrics.IngestionMetrics;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateSessionUseCase {

    private final TriageSessionRepository sessionRepository;
    private final LawFirmContactRepository lawFirmContactRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final ProtocolGeneratorService protocolGenerator;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final IngestionMetrics ingestionMetrics;

    @Transactional
    public CreateSessionResponse execute(CreateSessionRequest request, UUID lawFirmId, UUID apiCredentialId) {
        UUID correlationId = UUID.randomUUID();

        LawFirm lawFirmRef = entityManager.getReference(LawFirm.class, lawFirmId);
        ApiCredential apiCredentialRef = entityManager.getReference(ApiCredential.class, apiCredentialId);

        String protocolo = protocolGenerator.generateNextProtocol();

        TriageSession session = TriageSession.builder()
                .id(UUID.randomUUID())
                .lawFirm(lawFirmRef)
                .apiCredential(apiCredentialRef)
                .protocolo(protocolo)
                .correlationId(correlationId)
                .status(SessionStatus.RECEIVING_DOCUMENTS)
                .build();

        LegalCase legalCase = LegalCase.builder()
                .id(UUID.randomUUID())
                .session(session)
                .titulo(request.getCaso().getTitulo())
                .areaJuridica(request.getCaso().getAreaJuridica())
                .tipoCaso(request.getCaso().getTipoCaso())
                .build();
        session.setLegalCase(legalCase);

        TriageSession saved = sessionRepository.save(session);

        linkActiveContactsAsRecipients(saved);

        auditService.record(saved.getId(), lawFirmId, correlationId, EventType.SESSION_CREATED,
                "Sessão criada com protocolo " + protocolo);

        ingestionMetrics.incrementSessionsCreated();

        log.info("Session created: sessionId={}, protocolo={}, correlationId={}",
                saved.getId(), protocolo, correlationId);

        return CreateSessionResponse.builder()
                .sessionId(saved.getId())
                .protocolo(protocolo)
                .correlationId(correlationId)
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    /**
     * Vincula os contatos ativos do escritório como destinatários de notificação da sessão
     * (notification_recipients), snapshot no momento da criação. Sem isso, o triaige-srv-notification consome o resultado da IA mas não encontra
     * ninguém para notificar (RecipientRepositoryPort.findActiveBySessionId retorna vazio).
     */
    private void linkActiveContactsAsRecipients(TriageSession session) {
        List<LawFirmContact> activeContacts =
                lawFirmContactRepository.findByLawFirmIdAndAtivoTrue(session.getLawFirm().getId());

        for (LawFirmContact contact : activeContacts) {
            NotificationRecipient recipient = NotificationRecipient.builder()
                    .session(session)
                    .contact(contact)
                    .nome(contact.getNome())
                    .email(contact.getEmail())
                    .telefone(contact.getTelefone())
                    .canalPreferencial(contact.getCanalPreferencial())
                    .build();
            notificationRecipientRepository.save(recipient);
        }

        log.info("Notification recipients linked: sessionId={}, count={}", session.getId(), activeContacts.size());
    }
}
