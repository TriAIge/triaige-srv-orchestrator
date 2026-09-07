# RELATÓRIO DE TRIAGEM JURÍDICA INTELIGENTE — TriAIge
*Governança, Inteligência Artificial e Rastreabilidade Documental*

---

## 1. METADADOS E AUDITORIA DO PROCESSO
> **Aviso de Conformidade LGPD:** Os dados pessoais e sensíveis originais foram anonimizados conforme a Lei Geral de Proteção de Dados (Lei nº 13.709/2018). Este relatório é gerado automaticamente para suporte à decisão e **exige validação humana obrigatória**.

* **ID do Caso (Protocolo TriAIge):** `CASE-{{CASE_ID}}`
* **Escritório Solicitante / Cliente:** `{{NOME_ESCRITORIO}}` (ID: `{{ESCRITORIO_ID}}`)
* **Canal de Entrada:** `{{CANAL_ENTRADA}}` *(Ex: API Gateway / CRM / Web / WhatsApp)*
* **Data e Hora de Recebimento:** `{{DATA_HORA_RECEBIMENTO}}`
* **Data e Hora de Conclusão da Triagem:** `{{DATA_HORA_PROCESSAMENTO}}`
* **Status do Processamento:** `{{STATUS_PROCESSAMENTO}}` *(Ex: Relatório Gerado / Em Validação)*
* **Hash do Log de Auditoria (Rastreabilidade):** `{{HASH_AUDITORIA_LOG}}`

---

## 2. CLASSIFICAÇÃO E DIRECIONAMENTO INICIAL
* **Ramo do Direito:** `{{RAMO_DIREITO}}` *(Ex: Direito Civil / Trabalhista / Tributário / Consumidor / Empresarial)*
* **Tipo / Assunto da Ação:** `{{TIPO_ACAO}}` *(Ex: Ação de Cobrança / Reclamatória Trabalhista / Execução Fiscal / Indenizatória)*
* **Vara / Foro Competente (Estimado):** `{{FORO_COMPETENTE}}`
* **Sugestão de Nível de Atendimento:** `{{NIVEL_ATENDIMENTO}}` *(Ex: Triagem Nível 1 - Júnior / Nível 2 - Pleno / Nível 3 - Sócio)*
* **Advogado / Equipe Sugerida:** `{{EQUIPE_RESPONSAVEL}}`

---

## 3. AVALIAÇÃO DE CRITICIDADE E COMPLEXIDADE

| Dimensão | Classificação | Nível de Confiança IA | Justificativa Técnica |
| :--- | :---: | :---: | :--- |
| **Criticidade (Urgência)** | `{{NIVEL_CRITICIDADE}}` *(Alta / Média / Baixa)* | `{{CONF_CRITICIDADE}}%` | `{{MOTIVO_CRITICIDADE}}` |
| **Complexidade da Causa** | `{{NIVEL_COMPLEXIDADE}}` *(Alta / Média / Baixa)* | `{{CONF_COMPLEXIDADE}}%` | `{{MOTIVO_COMPLEXIDADE}}` |
| **Impacto Financeiro Estimado** | `{{VALOR_CAUSA_ESTIMADO}}` | `{{CONF_VALOR}}%` | `{{MOTIVO_IMPACTO_FIN}}` |

---

## 4. PARTES E ENTIDADES EXTRAÍDAS (ANONIMIZADAS)
* **Polo Ativo (Requerente):** `{{POLO_ATIVO_ANONIMIZADO}}` *(Ex: [REQUERENTE_01] - Pessoa Física)*
* **Polo Passivo (Requerido):** `{{POLO_PASSIVO_ANONIMIZADO}}` *(Ex: [REQUERIDO_01] - Instituição Financeira)*
* **Terceiros / Órgãos Envolvidos:** `{{TERCEIROS_INTERESSADOS}}`
* **Documentos Processados (via OCR/Pipeline):**
  - `{{NOME_ARQUIVO_01}}` — *(Status OCR: Concluído com Sucesso)*
  - `{{NOME_ARQUIVO_02}}` — *(Status OCR: Concluído com Sucesso)*

---

## 5. CONTROLE DE PRAZOS E FATOS TEMPORAIS
> ⚠️ **Atenção aos Prazos:** Verificação automatizada preliminar. Requer conferência imediata das publicações e intimações oficiais.

* **Data da Ocorrência dos Fatos:** `{{DATA_FATO}}`
* **Data de Notificação / Citação / Intimação:** `{{DATA_INTIMACAO}}`
* **Prazo Fatal / Limite Identificado:** `{{PRAZO_FATAL_ESTIMADO}}`
* **Tipo de Prazo:** `{{TIPO_PRAZO}}` *(Ex: Contestação (15 dias úteis) / Recurso Ordinário (8 dias úteis) / Réplica)*
* **Risco de Decadência / Prescrição:** `{{RISCO_PRESCRICAO}}` *(Ex: Baixo / Alerta de Prescrição Iminente)*

---

## 6. RESUMO PRELIMINAR ESTRUTURADO DO CASO
### 6.1. Síntese dos Fatos
{{RESUMO_FATOS}}

### 6.2. Principais Alegações e Pedidos
* **Pedido 1:** {{PEDIDO_01}}
* **Pedido 2:** {{PEDIDO_02}}
* **Pedido 3:** {{PEDIDO_03}}

### 6.3. Pontos Críticos e Riscos Jurídicos
* {{PONTO_CRITICO_01}}
* {{PONTO_CRITICO_02}}

---

## 7. SUGESTÕES DE JURISPRUDÊNCIA E PRECEDENTES SIMILARES
*(Recuperação Semântica por Vetores via BERT / pgvector)*

1. **Precedente 1:**
   * **Tribunal / Súmula / Tema:** `{{PRECEDENTE_01_FONTE}}`
   * **Grau de Similaridade Semântica:** `{{PRECEDENTE_01_SIMILARIDADE}}%`
   * **Ementa / Síntese Aplicável:** {{PRECEDENTE_01_EMENTA}}

2. **Precedente 2:**
   * **Tribunal / Súmula / Tema:** `{{PRECEDENTE_02_FONTE}}`
   * **Grau de Similaridade Semântica:** `{{PRECEDENTE_02_SIMILARIDADE}}%`
   * **Ementa / Síntese Aplicável:** {{PRECEDENTE_02_EMENTA}}

---

## 8. PARECER DE REVISÃO E VALIDAÇÃO HUMANA (HITL)
> **Campo a ser preenchido pelo Advogado Responsável (Art. 1.1 / US04 / AS IS - TO BE)**

* **Parecer da Triagem:** [ ] Aprovado integralmente  |  [ ] Aprovado com ajustes  |  [ ] Rejeitado / Reclassificar
* **Ajustes na Classificação / Criticidade:** `________________________________________________`
* **Tratativa Definida:** 
  - [ ] Elaborar Peça / Contestação
  - [ ] Agendar Reunião com Cliente
  - [ ] Recusar Demanda / Inviabilidade Jurídica
  - [ ] Enviar Proposta de Honorários
* **Advogado(a) Validador(a):** `________________________________________________`
* **OAB nº:** `________________________`  |  **Data de Validação:** `____/____/________`
* **Assinatura:** `________________________________________________`
