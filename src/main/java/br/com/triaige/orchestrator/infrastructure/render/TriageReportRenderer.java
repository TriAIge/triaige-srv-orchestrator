package br.com.triaige.orchestrator.infrastructure.render;

import br.com.triaige.orchestrator.domain.exception.TemplateRenderException;
import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisResponseDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Renderiza o relatório estruturado no formato do template canônico
 * {@code template_triagem_triaige.md} (Fase 4, spec seção 6). Classe pura — sem I/O de rede
 * ou banco, só o template já carregado do classpath na construção.
 *
 * <p>O template tem blocos de tamanho fixo (3 pedidos, 2 pontos críticos, 2 precedentes,
 * 2 nomes de arquivo) mas a spec exige 1 bloco por item do array recebido — em vez de um
 * {@code String.replace} ingênuo, esta classe localiza cada região variável por âncoras
 * literais estáveis do template (cabeçalhos de seção, textos fixos) e substitui todo o
 * conteúdo entre elas por blocos gerados dinamicamente em Java.</p>
 */
@Component
public class TriageReportRenderer {

    private static final String TEMPLATE_PATH = "templates/template_triagem_triaige.md";
    static final String NOT_AVAILABLE_V1 = "não disponível nesta versão do relatório";
    private static final String CANAL_ENTRADA = "Não informado";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final Map<String, String> NIVEL_LABELS = Map.of(
            "alta", "Alta", "media", "Média", "baixa", "Baixa");
    private static final Map<String, String> NIVEL_ATENDIMENTO_LABELS = Map.of(
            "junior", "Júnior", "pleno", "Pleno", "socio", "Sócio");
    private static final Map<String, String> RISCO_PRESCRICAO_LABELS = Map.of(
            "baixo", "Baixo", "alerta_iminente", "Alerta de Prescrição Iminente", "não_identificado", "Não identificado");
    private static final Map<String, String> CLASSIFICACAO_LABELS = Map.of(
            "viavel", "Viável", "inviavel", "Inviável", "necessita_mais_documentos", "Necessita mais documentos");

    private final String template;

    public TriageReportRenderer() {
        this.template = loadTemplate();
    }

    private String loadTemplate() {
        try {
            byte[] bytes = new ClassPathResource(TEMPLATE_PATH).getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TemplateRenderException("Template canônico não encontrado no classpath: " + TEMPLATE_PATH, e);
        }
    }

    public String render(AnalysisResponseDto.RelatorioEstruturadoDto relatorio, TriageReportRenderContext context) {
        try {
            String result = template;
            result = renderSecao1(result, context);
            result = renderSecao2(result, relatorio);
            result = renderSecao3(result, relatorio);
            result = renderSecao4(result, relatorio);
            result = renderSecao5(result, relatorio);
            result = renderSecao6(result, relatorio);
            result = renderSecao7(result, relatorio);
            return result;
        } catch (TemplateRenderException e) {
            throw e;
        } catch (Exception e) {
            throw new TemplateRenderException("Falha ao renderizar o relatório final: " + e.getMessage(), e);
        }
    }

    // ---- Seção 1: Metadados e auditoria ----

    private String renderSecao1(String source, TriageReportRenderContext context) {
        return source
                .replace("{{CASE_ID}}", value(context.protocolo()))
                .replace("{{NOME_ESCRITORIO}}", value(context.nomeEscritorio()))
                .replace("{{ESCRITORIO_ID}}", context.escritorioId() == null ? "" : context.escritorioId().toString())
                .replace("{{CANAL_ENTRADA}}", CANAL_ENTRADA)
                .replace("{{DATA_HORA_RECEBIMENTO}}", formatDate(context.dataHoraRecebimento()))
                .replace("{{DATA_HORA_PROCESSAMENTO}}", formatDate(context.dataHoraProcessamento()))
                .replace("{{STATUS_PROCESSAMENTO}}", value(context.statusProcessamento()))
                .replace("{{HASH_AUDITORIA_LOG}}", value(context.hashAuditoriaLog()));
    }

    // ---- Seção 2: Classificação e direcionamento inicial ----

    private String renderSecao2(String source, AnalysisResponseDto.RelatorioEstruturadoDto relatorio) {
        AnalysisResponseDto.ClassificacaoInicial classificacao = relatorio.getClassificacaoInicial();
        return source
                .replace("{{RAMO_DIREITO}}", value(relatorio.getAreaJuridica()))
                .replace("{{TIPO_ACAO}}", value(relatorio.getTipoCaso()))
                .replace("{{FORO_COMPETENTE}}", classificacao == null ? NOT_AVAILABLE_V1 : value(classificacao.getForoCompetenteEstimado()))
                .replace("{{NIVEL_ATENDIMENTO}}", classificacao == null ? NOT_AVAILABLE_V1
                        : label(NIVEL_ATENDIMENTO_LABELS, classificacao.getNivelAtendimentoSugerido()))
                .replace("{{EQUIPE_RESPONSAVEL}}", classificacao == null ? NOT_AVAILABLE_V1 : value(classificacao.getEquipeResponsavelSugerida()));
    }

    // ---- Seção 3: Avaliação de criticidade e complexidade ----

    private String renderSecao3(String source, AnalysisResponseDto.RelatorioEstruturadoDto relatorio) {
        AnalysisResponseDto.AvaliacaoCriticidade av = relatorio.getAvaliacaoCriticidade();
        AnalysisResponseDto.Nivel criticidade = av == null ? null : av.getCriticidade();
        AnalysisResponseDto.Nivel complexidade = av == null ? null : av.getComplexidade();
        AnalysisResponseDto.ImpactoFinanceiroEstimado impacto = av == null ? null : av.getImpactoFinanceiroEstimado();

        return source
                .replace("{{NIVEL_CRITICIDADE}}", criticidade == null ? NOT_AVAILABLE_V1 : label(NIVEL_LABELS, criticidade.getNivel()))
                .replace("{{CONF_CRITICIDADE}}", criticidade == null || criticidade.getConfianca() == null ? "-" : criticidade.getConfianca().toString())
                .replace("{{MOTIVO_CRITICIDADE}}", criticidade == null ? NOT_AVAILABLE_V1 : value(criticidade.getJustificativa()))
                .replace("{{NIVEL_COMPLEXIDADE}}", complexidade == null ? NOT_AVAILABLE_V1 : label(NIVEL_LABELS, complexidade.getNivel()))
                .replace("{{CONF_COMPLEXIDADE}}", complexidade == null || complexidade.getConfianca() == null ? "-" : complexidade.getConfianca().toString())
                .replace("{{MOTIVO_COMPLEXIDADE}}", complexidade == null ? NOT_AVAILABLE_V1 : value(complexidade.getJustificativa()))
                .replace("{{VALOR_CAUSA_ESTIMADO}}", impacto == null ? NOT_AVAILABLE_V1 : value(impacto.getValor()))
                .replace("{{CONF_VALOR}}", impacto == null || impacto.getConfianca() == null ? "-" : impacto.getConfianca().toString())
                .replace("{{MOTIVO_IMPACTO_FIN}}", impacto == null ? NOT_AVAILABLE_V1 : value(impacto.getJustificativa()));
    }

    // ---- Seção 4: Partes e entidades extraídas + documentos processados ----

    private String renderSecao4(String source, AnalysisResponseDto.RelatorioEstruturadoDto relatorio) {
        AnalysisResponseDto.PartesExtraidas partes = relatorio.getPartesExtraidas();
        String result = source
                .replace("{{POLO_ATIVO_ANONIMIZADO}}", partes == null ? NOT_AVAILABLE_V1 : value(partes.getPoloAtivo()))
                .replace("{{POLO_PASSIVO_ANONIMIZADO}}", partes == null ? NOT_AVAILABLE_V1 : value(partes.getPoloPassivo()))
                .replace("{{TERCEIROS_INTERESSADOS}}", partes == null ? NOT_AVAILABLE_V1 : value(partes.getTerceirosInteressados()));

        String documentosBlock = renderDocumentosProcessados(relatorio.getEvidenciasAnalisadas());
        return replaceBetween(result, "(via OCR/Pipeline):**\n", "\n\n---\n\n## 5.", documentosBlock);
    }

    private String renderDocumentosProcessados(List<AnalysisResponseDto.EvidenciaAnalisada> evidencias) {
        if (evidencias == null || evidencias.isEmpty()) {
            return "  - _Nenhum documento processado._";
        }
        StringBuilder sb = new StringBuilder();
        for (AnalysisResponseDto.EvidenciaAnalisada evidencia : evidencias) {
            String nome = evidencia.getNomeArquivoOriginal() != null ? evidencia.getNomeArquivoOriginal() : NOT_AVAILABLE_V1;
            sb.append("  - `").append(nome).append("` — *(Status OCR: Concluído com Sucesso)*\n");
        }
        return sb.substring(0, sb.length() - 1);
    }

    // ---- Seção 5: Controle de prazos ----

    private String renderSecao5(String source, AnalysisResponseDto.RelatorioEstruturadoDto relatorio) {
        AnalysisResponseDto.ControleDePrazos prazos = relatorio.getControleDePrazos();
        return source
                .replace("{{DATA_FATO}}", prazos == null ? NOT_AVAILABLE_V1 : value(prazos.getDataFato()))
                .replace("{{DATA_INTIMACAO}}", prazos == null ? NOT_AVAILABLE_V1 : value(prazos.getDataIntimacao()))
                .replace("{{PRAZO_FATAL_ESTIMADO}}", prazos == null ? NOT_AVAILABLE_V1 : value(prazos.getPrazoFatalEstimado()))
                .replace("{{TIPO_PRAZO}}", prazos == null ? NOT_AVAILABLE_V1 : value(prazos.getTipoPrazo()))
                .replace("{{RISCO_PRESCRICAO}}", prazos == null ? NOT_AVAILABLE_V1 : label(RISCO_PRESCRICAO_LABELS, prazos.getRiscoPrescricao()));
    }

    // ---- Seção 6: Resumo estruturado + 6.4 (Recomendação, aditiva) ----

    private String renderSecao6(String source, AnalysisResponseDto.RelatorioEstruturadoDto relatorio) {
        String result = source.replace("{{RESUMO_FATOS}}", relatorio.getResumoEstruturado() == null
                ? NOT_AVAILABLE_V1 : value(relatorio.getResumoEstruturado().getSinteseFatos()));

        String pedidosBlock = renderListaNumerada(
                relatorio.getResumoEstruturado() == null ? null : relatorio.getResumoEstruturado().getPedidos(),
                "Pedido");
        result = replaceBetween(result, "### 6.2. Principais Alegações e Pedidos\n", "\n\n### 6.3.", pedidosBlock);

        String pontosCriticosBlock = renderListaSimples(
                relatorio.getResumoEstruturado() == null ? null : relatorio.getResumoEstruturado().getPontosCriticos());
        String recomendacaoBlock = renderRecomendacao(relatorio.getRecomendacao());
        String secao63E64 = pontosCriticosBlock + "\n\n### 6.4. Recomendação Preliminar da IA\n" + recomendacaoBlock;
        result = replaceBetween(result, "### 6.3. Pontos Críticos e Riscos Jurídicos\n", "\n\n---\n\n## 7.", secao63E64);

        return result;
    }

    private String renderListaNumerada(List<String> itens, String rotulo) {
        if (itens == null || itens.isEmpty()) {
            return "* " + NOT_AVAILABLE_V1;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < itens.size(); i++) {
            sb.append("* **").append(rotulo).append(" ").append(i + 1).append(":** ").append(value(itens.get(i))).append("\n");
        }
        return sb.substring(0, sb.length() - 1);
    }

    private String renderListaSimples(List<String> itens) {
        if (itens == null || itens.isEmpty()) {
            return "* " + NOT_AVAILABLE_V1;
        }
        StringBuilder sb = new StringBuilder();
        for (String item : itens) {
            sb.append("* ").append(value(item)).append("\n");
        }
        return sb.substring(0, sb.length() - 1);
    }

    private String renderRecomendacao(AnalysisResponseDto.Recomendacao recomendacao) {
        if (recomendacao == null) {
            return "* " + NOT_AVAILABLE_V1;
        }
        return "* **Classificação:** " + label(CLASSIFICACAO_LABELS, recomendacao.getClassificacao()) + "\n"
                + "* **Justificativa:** " + value(recomendacao.getJustificativa());
    }

    // ---- Seção 7: Jurisprudência e precedentes ----

    private String renderSecao7(String source, AnalysisResponseDto.RelatorioEstruturadoDto relatorio) {
        String precedentesBlock = renderPrecedentes(relatorio.getJurisprudenciaCitada());
        return replaceBetween(source, "*(Recuperação Semântica por Vetores via BERT / pgvector)*\n\n", "\n\n---\n\n## 8.", precedentesBlock);
    }

    private String renderPrecedentes(List<AnalysisResponseDto.JurisprudenciaCitada> precedentes) {
        if (precedentes == null || precedentes.isEmpty()) {
            return "_Nenhum precedente retornado nesta análise._";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < precedentes.size(); i++) {
            AnalysisResponseDto.JurisprudenciaCitada p = precedentes.get(i);
            String fonteETitulo = value(p.getFonte()) + (p.getTitulo() == null || p.getTitulo().isBlank() ? "" : " — " + p.getTitulo());
            sb.append(i + 1).append(". **Precedente ").append(i + 1).append(":**\n")
                    .append("   * **Tribunal / Súmula / Tema:** `").append(fonteETitulo).append("`\n")
                    .append("   * **Grau de Similaridade Semântica:** ").append(label(NIVEL_LABELS, p.getAderencia())).append("\n")
                    .append("   * **Ementa / Síntese Aplicável:** ").append(value(p.getEmenta())).append("\n\n");
        }
        return sb.substring(0, sb.length() - 2);
    }

    // ---- Helpers ----

    private String value(String v) {
        return v == null ? NOT_AVAILABLE_V1 : v;
    }

    private String label(Map<String, String> labels, String rawValue) {
        if (rawValue == null) {
            return NOT_AVAILABLE_V1;
        }
        return labels.getOrDefault(rawValue, rawValue);
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime == null ? NOT_AVAILABLE_V1 : dateTime.format(DATE_FORMAT);
    }

    /** Substitui o conteúdo estritamente entre {@code startMarker} e {@code endMarker} (ambos preservados). */
    private String replaceBetween(String source, String startMarker, String endMarker, String replacement) {
        int startIdx = source.indexOf(startMarker);
        if (startIdx < 0) {
            throw new TemplateRenderException("Marcador de template não encontrado: \"" + startMarker + "\"");
        }
        int contentStart = startIdx + startMarker.length();
        int endIdx = source.indexOf(endMarker, contentStart);
        if (endIdx < 0) {
            throw new TemplateRenderException("Marcador de fim de template não encontrado: \"" + endMarker + "\"");
        }
        return source.substring(0, contentStart) + replacement + source.substring(endIdx);
    }
}
