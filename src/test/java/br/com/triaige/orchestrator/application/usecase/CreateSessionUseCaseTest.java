package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.CreateSessionRequest;
import br.com.triaige.orchestrator.api.dto.response.CreateSessionResponse;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.application.service.ProtocolGeneratorService;
import br.com.triaige.orchestrator.domain.entity.LawFirm;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.*;
import br.com.triaige.orchestrator.infrastructure.persistence.LawFirmRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateSessionUseCase")
class CreateSessionUseCaseTest {

    @Mock private TriageSessionRepository sessionRepository;
    @Mock private LawFirmRepository lawFirmRepository;
    @Mock private ProtocolGeneratorService protocolGenerator;
    @Mock private AuditService auditService;

    @InjectMocks
    private CreateSessionUseCase useCase;

    private CreateSessionRequest validRequest;
    private UUID lawFirmId;
    private LawFirm lawFirm;

    @BeforeEach
    void setUp() {
        lawFirmId = UUID.randomUUID();
        lawFirm = LawFirm.builder().id(lawFirmId).nome("Escritório Demo").status("ATIVO").build();
        lenient().when(lawFirmRepository.findById(lawFirmId)).thenReturn(Optional.of(lawFirm));

        validRequest = new CreateSessionRequest();
        validRequest.setLawFirmId(lawFirmId);
        validRequest.setTitulo("Reclamação trabalhista - horas extras");
        validRequest.setAreaJuridica(LegalArea.TRABALHISTA);
        validRequest.setTipoCaso(CaseType.RECLAMACAO_TRABALHISTA);

        var remetente = new CreateSessionRequest.RemetenteRequest();
        remetente.setNome("João Silva");
        remetente.setEmail("joao@email.com");
        remetente.setTelefone("+5511999999999");
        remetente.setCanalPreferencial(NotificationChannel.EMAIL);
        validRequest.setRemetente(remetente);
    }

    @Test
    @DisplayName("deve criar sessão com sucesso e retornar response com protocolo")
    void shouldCreateSessionSuccessfully() {
        // Arrange
        String expectedProtocolo = "TRI-2026-000001";
        UUID correlationId = UUID.randomUUID();
        when(protocolGenerator.generateNextProtocol()).thenReturn(expectedProtocolo);
        when(sessionRepository.save(any(TriageSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        CreateSessionResponse response = useCase.execute(validRequest, correlationId);

        // Assert
        assertThat(response.getSessionId()).isNotNull();
        assertThat(response.getCaseId()).isNotNull();
        assertThat(response.getCorrelationId()).isEqualTo(correlationId);
        assertThat(response.getProtocolo()).isEqualTo(expectedProtocolo);
        assertThat(response.getStatus()).isEqualTo(SessionStatus.ABERTA);
    }

    @Test
    @DisplayName("deve persistir sessão com status ABERTA e escritório jurídico correto")
    void shouldPersistSessionWithCorrectData() {
        // Arrange
        when(protocolGenerator.generateNextProtocol()).thenReturn("TRI-2026-000001");
        ArgumentCaptor<TriageSession> captor = ArgumentCaptor.forClass(TriageSession.class);
        when(sessionRepository.save(captor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        useCase.execute(validRequest, UUID.randomUUID());

        // Assert
        TriageSession saved = captor.getValue();
        assertThat(saved.getLawFirm().getId()).isEqualTo(lawFirmId);
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.ABERTA);
        assertThat(saved.getLegalCase()).isNotNull();
        assertThat(saved.getLegalCase().getAreaJuridica()).isEqualTo(LegalArea.TRABALHISTA);
        assertThat(saved.getRecipient()).isNotNull();
        assertThat(saved.getRecipient().getEmail()).isEqualTo("joao@email.com");
    }

    @Test
    @DisplayName("deve registrar evento de auditoria SESSION_CREATED")
    void shouldRecordAuditEvent() {
        // Arrange
        when(protocolGenerator.generateNextProtocol()).thenReturn("TRI-2026-000001");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        useCase.execute(validRequest, UUID.randomUUID());

        // Assert
        verify(auditService, times(1)).record(
                any(UUID.class),
                any(UUID.class),
                eq(EventType.SESSION_CREATED),
                anyString()
        );
    }

    @Test
    @DisplayName("deve rejeitar área jurídica nula")
    void shouldRejectNullLegalArea() {
        // Arrange
        validRequest.setAreaJuridica(null);

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                br.com.triaige.orchestrator.domain.exception.BusinessRuleException.class,
                () -> useCase.execute(validRequest, UUID.randomUUID())
        );
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve lançar LawFirmNotFoundException quando escritório não existe")
    void shouldThrowWhenLawFirmNotFound() {
        // Arrange
        UUID unknownLawFirmId = UUID.randomUUID();
        validRequest.setLawFirmId(unknownLawFirmId);
        when(lawFirmRepository.findById(unknownLawFirmId)).thenReturn(Optional.empty());

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(
                br.com.triaige.orchestrator.domain.exception.LawFirmNotFoundException.class,
                () -> useCase.execute(validRequest, UUID.randomUUID())
        );
        verify(sessionRepository, never()).save(any());
    }
}
