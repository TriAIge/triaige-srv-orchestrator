package br.com.triaige.orchestrator.infrastructure.http.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resposta de {@code POST /api/ai/v1/analyze} no triaige-srv-mcp-ai (Fase 3, seção 4.3;
 * schema do relatório estendido pela Fase 4, seção 5, para "2.0"). Cópia local do contrato,
 * mesma convenção de {@link AnalysisRequestDto}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponseDto {

    private String schemaVersion;
    private UUID sessionId;
    private UUID correlationId;
    private String status;
    private RelatorioEstruturadoDto relatorioEstruturado;
    private UUID geminiToolCallId;
    private UUID jurisprudenceCallId;
    private LocalDateTime geradoEm;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatorioEstruturadoDto {
        private String schemaVersion;
        private UUID sessionId;
        private String protocolo;
        private String areaJuridica;
        private String tipoCaso;
        private String resumoExecutivo;
        private String fundamentacaoJuridica;
        private List<EvidenciaAnalisada> evidenciasAnalisadas;
        private List<JurisprudenciaCitada> jurisprudenciaCitada;
        private List<RiscoIdentificado> riscosIdentificados;
        private Recomendacao recomendacao;
        private Metadados metadados;

        // Fase 4 (schemaVersion "2.0") — null quando schemaVersion="1.0".
        private ClassificacaoInicial classificacaoInicial;
        private AvaliacaoCriticidade avaliacaoCriticidade;
        private PartesExtraidas partesExtraidas;
        private ControleDePrazos controleDePrazos;
        private ResumoEstruturado resumoEstruturado;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenciaAnalisada {
        private UUID attachmentGroupId;
        private String tipoDocumento;
        private String relevancia;
        private String observacao;
        private String nomeArquivoOriginal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JurisprudenciaCitada {
        private String titulo;
        private String ementa;
        private String fonte;
        private String url;
        private String aderencia;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiscoIdentificado {
        private String descricao;
        private String severidade;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recomendacao {
        private String classificacao;
        private String justificativa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadados {
        private String modeloUtilizado;
        private String promptVersion;
        private int toolCallsUsados;
        private LocalDateTime geradoEm;
        private long tempoProcessamentoMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassificacaoInicial {
        private String foroCompetenteEstimado;
        private String nivelAtendimentoSugerido;
        private String equipeResponsavelSugerida;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvaliacaoCriticidade {
        private Nivel criticidade;
        private Nivel complexidade;
        private ImpactoFinanceiroEstimado impactoFinanceiroEstimado;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Nivel {
        private String nivel;
        private Integer confianca;
        private String justificativa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactoFinanceiroEstimado {
        private String valor;
        private Integer confianca;
        private String justificativa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartesExtraidas {
        private String poloAtivo;
        private String poloPassivo;
        private String terceirosInteressados;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ControleDePrazos {
        private String dataFato;
        private String dataIntimacao;
        private String prazoFatalEstimado;
        private String tipoPrazo;
        private String riscoPrescricao;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumoEstruturado {
        private String sinteseFatos;
        private List<String> pedidos;
        private List<String> pontosCriticos;
    }
}
