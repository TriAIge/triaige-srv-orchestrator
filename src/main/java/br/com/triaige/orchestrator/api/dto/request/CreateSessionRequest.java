package br.com.triaige.orchestrator.api.dto.request;

import br.com.triaige.orchestrator.domain.enums.CaseType;
import br.com.triaige.orchestrator.domain.enums.LegalArea;
import br.com.triaige.orchestrator.domain.enums.NotificationChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateSessionRequest {

    @NotNull(message = "lawFirmId é obrigatório")
    private UUID lawFirmId;

    @NotBlank(message = "titulo é obrigatório")
    @Size(max = 255)
    private String titulo;

    @NotNull(message = "areaJuridica é obrigatória")
    private LegalArea areaJuridica;

    @NotNull(message = "tipoCaso é obrigatório")
    private CaseType tipoCaso;

    @NotNull(message = "remetente é obrigatório")
    @Valid
    private RemetenteRequest remetente;

    @Data
    public static class RemetenteRequest {

        @NotBlank(message = "nome do remetente é obrigatório")
        @Size(max = 200)
        private String nome;

        @Email(message = "email inválido")
        @Size(max = 200)
        private String email;

        @Size(max = 30)
        private String telefone;

        @NotNull(message = "canalPreferencial é obrigatório")
        private NotificationChannel canalPreferencial;
    }
}
