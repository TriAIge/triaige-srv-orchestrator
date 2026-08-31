package br.com.triaige.orchestrator.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignDocumentRequest {

    /** Mesmo valor para partes do mesmo anexo fragmentado; se ausente, um novo é gerado. */
    private UUID attachmentGroupId;

    @Min(value = 1, message = "partNumber deve ser >= 1")
    private Integer partNumber = 1;

    @NotBlank(message = "nomeArquivoOriginal é obrigatório")
    @Size(max = 255, message = "nomeArquivoOriginal deve ter no máximo 255 caracteres")
    private String nomeArquivoOriginal;

    @NotBlank(message = "tipoDocumento é obrigatório")
    @Size(max = 30, message = "tipoDocumento deve ter no máximo 30 caracteres")
    private String tipoDocumento;

    @NotBlank(message = "contentType é obrigatório")
    @Size(max = 100, message = "contentType deve ter no máximo 100 caracteres")
    private String contentType;

    @NotNull(message = "tamanhoBytesEstimado é obrigatório")
    @Positive(message = "tamanhoBytesEstimado deve ser > 0")
    private Long tamanhoBytesEstimado;
}
