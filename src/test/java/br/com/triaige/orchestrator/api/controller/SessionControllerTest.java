package br.com.triaige.orchestrator.api.controller;

import br.com.triaige.orchestrator.api.dto.response.CreateSessionResponse;
import br.com.triaige.orchestrator.api.dto.response.RegisterDocumentResponse;
import br.com.triaige.orchestrator.api.dto.response.SessionDetailResponse;
import br.com.triaige.orchestrator.application.usecase.*;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.LegalArea;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.BusinessRuleException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.shared.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionController")
class SessionControllerTest {

    @Mock private CreateSessionUseCase createSessionUseCase;
    @Mock private RegisterDocumentUseCase registerDocumentUseCase;
    @Mock private TriggerProcessingUseCase triggerProcessingUseCase;
    @Mock private GetSessionStatusUseCase getSessionStatusUseCase;

    @InjectMocks
    private SessionController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/v1/sessions — deve criar sessão e retornar 201")
    void shouldCreateSessionAndReturn201() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        CreateSessionResponse response = CreateSessionResponse.builder()
                .sessionId(sessionId)
                .caseId(caseId)
                .correlationId(correlationId)
                .protocolo("TRI-2026-000001")
                .status(SessionStatus.ABERTA)
                .build();

        when(createSessionUseCase.execute(any(), any())).thenReturn(response);

        String body = """
            {
              "lawFirmId": "11111111-1111-1111-1111-111111111111",
              "titulo": "Reclamação trabalhista",
              "areaJuridica": "TRABALHISTA",
              "tipoCaso": "RECLAMACAO_TRABALHISTA",
              "remetente": {
                "nome": "João Silva",
                "email": "joao@email.com",
                "telefone": "+5511999999999",
                "canalPreferencial": "EMAIL"
              }
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.protocolo").value("TRI-2026-000001"))
                .andExpect(jsonPath("$.status").value("ABERTA"));
    }

    @Test
    @DisplayName("POST /api/v1/sessions — deve retornar 422 para área criminal (regra de negócio)")
    void shouldReturn422ForBusinessRuleViolation() throws Exception {
        // Arrange
        when(createSessionUseCase.execute(any(), any()))
                .thenThrow(new BusinessRuleException("Área jurídica inválida"));

        String body = """
            {
              "lawFirmId": "11111111-1111-1111-1111-111111111111",
              "titulo": "Caso inválido",
              "areaJuridica": "TRABALHISTA",
              "tipoCaso": "RECLAMACAO_TRABALHISTA",
              "remetente": {
                "nome": "João",
                "email": "joao@email.com",
                "canalPreferencial": "EMAIL"
              }
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Área jurídica inválida"));
    }

    @Test
    @DisplayName("POST /api/v1/sessions — deve retornar 400 quando payload inválido")
    void shouldReturn400ForInvalidPayload() throws Exception {
        // Arrange — campos obrigatórios ausentes
        String body = """
            {
              "titulo": "Sem lawFirmId"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/documents — deve registrar documento e retornar 201")
    void shouldRegisterDocumentAndReturn201() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        RegisterDocumentResponse response = RegisterDocumentResponse.builder()
                .documentId(documentId)
                .sessionId(sessionId)
                .status(DocumentStatus.REGISTRADO)
                .bucket("triaige-raw-documents")
                .objectKey("law-firm/11111111-1111-1111-1111-111111111111/sessions/" + sessionId + "/raw/peticao.pdf")
                .build();

        when(registerDocumentUseCase.execute(eq(sessionId), any(), any(), any(), any()))
                .thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "peticao-inicial.pdf", "application/pdf", "conteudo".getBytes());

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/sessions/{id}/documents", sessionId)
                        .file(file)
                        .param("tipoDocumento", "PETICAO_INICIAL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.status").value("REGISTRADO"));
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/process — deve disparar processamento e retornar 202")
    void shouldTriggerProcessingAndReturn202() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        doNothing().when(triggerProcessingUseCase).execute(eq(sessionId), any());

        // Act & Assert
        mockMvc.perform(post("/api/v1/sessions/{id}/process", sessionId))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/process — deve retornar 422 sem documentos")
    void shouldReturn422WhenNoDocuments() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        doThrow(new BusinessRuleException("Sessão não pode ser processada sem documentos registrados"))
                .when(triggerProcessingUseCase).execute(eq(sessionId), any());

        // Act & Assert
        mockMvc.perform(post("/api/v1/sessions/{id}/process", sessionId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Sessão não pode ser processada sem documentos registrados"));
    }

    @Test
    @DisplayName("GET /api/v1/sessions/{id} — deve retornar detalhes da sessão")
    void shouldReturnSessionDetails() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();

        SessionDetailResponse response = SessionDetailResponse.builder()
                .sessionId(sessionId)
                .protocolo("TRI-2026-000001")
                .correlationId(UUID.randomUUID())
                .status(SessionStatus.CONCLUIDA)
                .areaJuridica(LegalArea.TRABALHISTA)
                .documents(List.of())
                .createdAt(LocalDateTime.of(2026, 6, 10, 18, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 6, 10, 18, 10, 0))
                .build();

        when(getSessionStatusUseCase.getSession(eq(sessionId), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/v1/sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.protocolo").value("TRI-2026-000001"))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    @Test
    @DisplayName("GET /api/v1/sessions/{id} — deve retornar 404 para sessão inexistente")
    void shouldReturn404ForUnknownSession() throws Exception {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        when(getSessionStatusUseCase.getSession(eq(sessionId), any()))
                .thenThrow(new SessionNotFoundException(sessionId));

        // Act & Assert
        mockMvc.perform(get("/api/v1/sessions/{id}", sessionId))
                .andExpect(status().isNotFound());
    }
}
