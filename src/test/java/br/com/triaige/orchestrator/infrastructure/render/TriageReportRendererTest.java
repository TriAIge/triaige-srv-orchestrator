package br.com.triaige.orchestrator.infrastructure.render;

import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisResponseDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre blocos de tamanho variável (não fixo em 3/2/2 como o
 * template original), aderência renderizada como rótulo qualitativo (nunca percentual),
 * fallback "não disponível nesta versão do relatório" quando o relatório é schemaVersion
 * "1.0" (campos v2 ausentes), e hash estável para o mesmo contexto.
 */
class TriageReportRendererTest {

    private final TriageReportRenderer renderer = new TriageReportRenderer();

    private TriageReportRenderContext context() {
        return new TriageReportRenderContext(
                "PROTO-2026-001", "Escritório Teste", UUID.randomUUID(),
                LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 10, 5),
                "ANALYSIS_COMPLETED", "abc123hash");
    }

    private AnalysisResponseDto.RelatorioEstruturadoDto.RelatorioEstruturadoDtoBuilder baseRelatorio() {
        return AnalysisResponseDto.RelatorioEstruturadoDto.builder()
                .schemaVersion("2.0")
                .areaJuridica("civel")
                .tipoCaso("cobranca");
    }

    @Test
    void rendersVariableNumberOfPedidosAndPontosCriticos() {
        AnalysisResponseDto.RelatorioEstruturadoDto relatorio = baseRelatorio()
                .resumoEstruturado(AnalysisResponseDto.ResumoEstruturado.builder()
                        .sinteseFatos("sintese")
                        .pedidos(List.of("pedido A", "pedido B", "pedido C", "pedido D"))
                        .pontosCriticos(List.of("ponto único"))
                        .build())
                .build();

        String markdown = renderer.render(relatorio, context());

        assertThat(markdown).contains("**Pedido 1:** pedido A", "**Pedido 4:** pedido D");
        assertThat(markdown).doesNotContain("{{PEDIDO_01}}", "{{PEDIDO_05}}");
        assertThat(markdown).contains("* ponto único");
    }

    @Test
    void rendersAderenciaAsQualitativeLabelNeverAsPercentage() {
        AnalysisResponseDto.RelatorioEstruturadoDto relatorio = baseRelatorio()
                .jurisprudenciaCitada(List.of(
                        AnalysisResponseDto.JurisprudenciaCitada.builder()
                                .titulo("Tema 123").fonte("STJ").ementa("ementa 1").aderencia("alta").build(),
                        AnalysisResponseDto.JurisprudenciaCitada.builder()
                                .titulo("Tema 456").fonte("TJSP").ementa("ementa 2").aderencia("baixa").build()))
                .build();

        String markdown = renderer.render(relatorio, context());

        assertThat(markdown).contains("Grau de Similaridade Semântica:** Alta");
        assertThat(markdown).contains("Grau de Similaridade Semântica:** Baixa");
        assertThat(markdown).doesNotContain("Alta%", "Baixa%", "{{PRECEDENTE_01_SIMILARIDADE}}");
    }

    @Test
    void schemaVersion1_fallsBackToNotAvailableForV2OnlyFields() {
        AnalysisResponseDto.RelatorioEstruturadoDto relatorio = AnalysisResponseDto.RelatorioEstruturadoDto.builder()
                .schemaVersion("1.0")
                .areaJuridica("civel")
                .tipoCaso("cobranca")
                .build();

        String markdown = renderer.render(relatorio, context());

        assertThat(markdown).contains("não disponível nesta versão do relatório");
        assertThat(markdown).doesNotContain("{{FORO_COMPETENTE}}", "{{NIVEL_ATENDIMENTO}}");
    }

    @Test
    void includesRecomendacaoSubsection6_4() {
        AnalysisResponseDto.RelatorioEstruturadoDto relatorio = baseRelatorio()
                .recomendacao(AnalysisResponseDto.Recomendacao.builder()
                        .classificacao("viavel")
                        .justificativa("caso bem fundamentado")
                        .build())
                .build();

        String markdown = renderer.render(relatorio, context());

        assertThat(markdown).contains("### 6.4. Recomendação Preliminar da IA");
        assertThat(markdown).contains("**Classificação:** Viável");
        assertThat(markdown).contains("caso bem fundamentado");
    }

    @Test
    void hashIsStableForSameContext() {
        AnalysisResponseDto.RelatorioEstruturadoDto relatorio = baseRelatorio().build();
        TriageReportRenderContext sameContext = context();
        String markdown1 = renderer.render(relatorio, sameContext);
        String markdown2 = renderer.render(relatorio, sameContext);

        assertThat(markdown1).isEqualTo(markdown2);
        assertThat(markdown1).contains("abc123hash");
    }

    @Test
    void rendersNomeArquivoOriginalPerEvidencia() {
        AnalysisResponseDto.RelatorioEstruturadoDto relatorio = baseRelatorio()
                .evidenciasAnalisadas(List.of(
                        AnalysisResponseDto.EvidenciaAnalisada.builder()
                                .attachmentGroupId(UUID.randomUUID())
                                .tipoDocumento("peticao")
                                .relevancia("alta")
                                .nomeArquivoOriginal("peticao-inicial.pdf")
                                .build()))
                .build();

        String markdown = renderer.render(relatorio, context());

        assertThat(markdown).contains("`peticao-inicial.pdf`");
    }
}
