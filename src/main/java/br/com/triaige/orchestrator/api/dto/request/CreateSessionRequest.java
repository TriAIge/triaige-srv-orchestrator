package br.com.triaige.orchestrator.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

    @NotNull(message = "caso é obrigatório")
    @Valid
    private CasoRequest caso;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CasoRequest {

        @NotBlank(message = "caso.titulo é obrigatório")
        @Size(max = 255, message = "caso.titulo deve ter no máximo 255 caracteres")
        private String titulo;

        @NotBlank(message = "caso.areaJuridica é obrigatório")
        @Size(max = 30, message = "caso.areaJuridica deve ter no máximo 30 caracteres")
        private String areaJuridica;

        @NotBlank(message = "caso.tipoCaso é obrigatório")
        @Size(max = 50, message = "caso.tipoCaso deve ter no máximo 50 caracteres")
        private String tipoCaso;
    }
}
