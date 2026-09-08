# TriAIge / triaige-srv-orchestrator

> Serviço de ingestão de documentos e orquestração da triagem jurídica: recebe os documentos do escritório, coordena o pipeline de IA e devolve o relatório final.

## Sobre o projeto

O **TriAIge** é uma plataforma de triagem jurídica assistida por IA: escritórios de advocacia enviam os documentos de um caso e recebem de volta um relatório estruturado (classificação, avaliação de criticidade, partes envolvidas, prazos e resumo fundamentado), com o texto sensível anonimizado antes de qualquer processamento por IA.

Este repositório contém o serviço responsável por:

- Receber os documentos enviados pelo escritório e persistir metadados da sessão/caso no MySQL.
- Armazenar os arquivos originais no S3 (bucket *raw*) via upload direto com URL pré-assinada.
- Publicar o "documento pronto" na fila de pré-processamento para o `triaige-srv-mcp-ai` consumir.
- Receber de volta o resultado do pipeline (callback) e, quando concluído com sucesso, disparar a análise de IA, renderizar o relatório final e publicá-lo para a fila de notificação.

## Papel deste serviço na arquitetura

O Orchestrator é o ponto de entrada do fluxo de triagem e também quem fecha o ciclo, aplicando o resultado da IA ao caso do escritório.

```text
Sistema do escritório
   ↓ (upload de documentos)
triaige-srv-orchestrator  ←──────────────┐
   ↓ (Q2: documento pronto)              │ (callback: mcp-result)
triaige-srv-mcp-ai (pipeline T1-T4)  ─────┘
   ↓ (mcp-ai dispara a análise; orchestrator recebe o relatório e publica)
Q3 (fila de notificação) → serviço de notificação (fora deste repositório)
```

## Responsabilidades

Este serviço é responsável por:

- Criar sessões de triagem e o caso associado, com geração de protocolo único.
- Emitir URLs pré-assinadas de upload para o S3 (bucket *raw*) e confirmar a conclusão de cada upload.
- Finalizar o recebimento de uma sessão e publicar o evento de "documento pronto" (Q2) para o `triaige-srv-mcp-ai`.
- Receber o callback com o resultado do pipeline (`mcp-result`) e aplicá-lo ao estado da sessão.
- A partir da conclusão do pipeline (`COMPLETED`/`PARTIALLY_COMPLETED`), disparar a chamada de análise de IA ao `triaige-srv-mcp-ai`, renderizar o relatório estruturado retornado no template canônico e gravá-lo no S3 (bucket *curated*).
- Publicar o resultado final na fila de notificação (Q3).
- Autenticação por credencial de API (escritórios) e por token interno (chamadas servidor-a-servidor), idempotência das operações com efeito colateral, auditoria e métricas.

### Fora do escopo

Este serviço não é responsável por:

- OCR, anonimização, agrupamento de documentos ou raciocínio de IA — isso é feito pelo `triaige-srv-mcp-ai`.
- Envio de notificações ao escritório — isso é responsabilidade do serviço de notificação, que consome a fila publicada por este serviço.
- Provisionamento de infraestrutura (rede, filas, buckets, banco) — isso é feito pelo `triaige-infra`.

## Arquitetura

Organização em pacotes por responsabilidade (estilo hexagonal):

```text
api/            controllers e DTOs de request/response
application/    use cases (um por endpoint), services de domínio
                (ProtocolGeneratorService, AuditService, IdempotencyService,
                ApiCredentialAuthService, DocumentReceivedFinalizationService,
                SessionFinalizationService, TriggerAiAnalysisUseCase) e o
                listener que dispara a análise de IA após o commit do callback
                (AiAnalysisEventListener)
domain/         entidades JPA, enums, exceções de negócio e eventos de aplicação
                (AiAnalysisTriggeredEvent)
infrastructure/ config (AWS, Jackson, propriedades, execução assíncrona),
                persistence (repositórios JPA), s3 (presign / head object),
                sqs (publisher, mensagens, consumidor interno de Q1), security
                (filtros de autenticação), http (cliente do triaige-srv-mcp-ai),
                render (preenchimento do template canônico do relatório final)
shared/         tratamento de erro, logging estruturado, métricas, utilitários
```

### Comunicação com outros serviços

| Serviço / Recurso | Tipo | Finalidade |
|---|---|---|
| `triaige-srv-mcp-ai` | HTTP / REST | Dispara a análise de IA (`POST /api/ai/v1/analyze`) e recebe o callback do pipeline |
| Fila `triaige-docs-preprocessing` (Q2) | SQS | Publica o "documento pronto" para o `triaige-srv-mcp-ai` consumir |
| Fila de resultados (Q3) | SQS | Publica o relatório final para o serviço de notificação |
| MySQL | Banco de dados | Persistência de sessões, casos, documentos e resultados de triagem |
| S3 (buckets *raw* e *curated*) | Storage | Documentos originais e relatório final gerado |

## Tecnologias utilizadas

- Java 21 + Spring Boot 3.5 (Web, Validation, Data JPA, Actuator)
- MySQL (via `mysql-connector-j`)
- AWS SDK v2 — SQS, S3, STS
- Lombok, MapStruct
- Jackson (com suporte a `java.time`)
- Logback com `logstash-logback-encoder` (logs estruturados em JSON)
- Micrometer + CloudWatch (métricas)
- Docker / Docker Compose
- JUnit 5, Awaitility (testes)

## Estrutura do projeto

```text
src/
├── main/
│   ├── java/br/com/triaige/orchestrator/
│   │   ├── api/            controllers, DTOs de request/response
│   │   ├── application/    use cases, services e listeners
│   │   ├── domain/         entidades, enums, exceções, eventos
│   │   ├── infrastructure/ config, persistence, s3, sqs, security, http, render
│   │   └── shared/         erro, logging, métricas, utils
│   └── resources/
│       ├── application.yml
│       └── templates/template_triagem_triaige.md
└── test/
    └── ...
```

## Pré-requisitos

- Java 21+
- Maven (ou o wrapper `mvnw`)
- Docker e Docker Compose
- Credenciais AWS válidas (para os endpoints S3/SQS reais) — provisionados via Terraform em `triaige-infra`

## Configuração

### Variáveis de ambiente

Copie/ajuste o `.env` na raiz do projeto (já vem preenchido com defaults de desenvolvimento):

```env
MYSQL_DATABASE=
MYSQL_USER=
MYSQL_PASSWORD=
MYSQL_PORT=

AWS_REGION=
AWS_SQS_ENDPOINT=
AWS_S3_ENDPOINT=
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_SESSION_TOKEN=

RAW_DOCUMENTS_BUCKET=
CURATED_DOCUMENTS_BUCKET=
DOCS_RECEIVED_QUEUE_URL=
DOCS_PREPROCESSING_QUEUE_URL=
RESULTS_READY_QUEUE_URL=

MCP_INTERNAL_TOKEN=
MCP_AI_BASE_URL=
MCP_ANALYZE_TOKEN=

SRV_ORCHESTRATOR_PORT=
```

> Nunca versione credenciais, tokens, senhas ou outros secrets reais no repositório.

### Configuração local

`application.yml` já reflete o cenário suportado (MySQL real via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, buckets/filas reais), com defaults sobrescrevíveis por variável de ambiente (ver `.env`). Não há LocalStack: os endpoints S3/SQS apontam para recursos reais provisionados via Terraform em `triaige-infra` — deixe `AWS_SQS_ENDPOINT`/`AWS_S3_ENDPOINT` em branco para usar os endpoints reais da região.

Uma credencial de teste ativa é provisionada pelo seed do banco, disponibilizado via `triaige-infra` (Ansible), usada como:

```
Authorization: Bearer dev-local-token
```

> A chamada de análise de IA (`TriggerAiAnalysisUseCase`) exige o `triaige-srv-mcp-ai` no ar e um `MCP_ANALYZE_TOKEN` combinado entre os dois `.env`. Sem isso, o disparo falha com `MCP_AI_UNAVAILABLE`/`MCP_AI_HTTP_ERROR` e a sessão fica em `ANALYSIS_FAILED` — o pipeline de ingestão (`createSession` até `finalize`) continua funcionando normalmente.

## Executando localmente

### Executando com Docker

```bash
docker compose up --build
```

Sobe o MySQL (porta `MYSQL_PORT`, padrão `3316`) e o próprio serviço (porta `SRV_ORCHESTRATOR_PORT`, padrão `8080`).

Para encerrar:

```bash
docker compose down
```

### Executando sem Docker

```bash
mvn spring-boot:run
```

Exige MySQL acessível (ex: `docker compose up mysql` deste repositório) e credenciais AWS reais para os endpoints S3/SQS.

## Testes

```bash
mvn test
```

Cobertura: testes unitários dos use cases e services, incluindo o fluxo de disparo de análise de IA (`TriggerAiAnalysisUseCaseTest`, feliz e cada ramo de falha), o contrato HTTP do cliente do `mcp-ai` (`McpAiAnalysisClientTest`) e o preenchimento do template do relatório (`TriageReportRendererTest`).

## API

Base path: `/api/orchestrator/v1`. Contrato completo: [`docs/openapi.yaml`](./docs/openapi.yaml).

Todos os endpoints exigem `Authorization: Bearer <token>`; os POSTs com efeito colateral (`/sessions`, `.../complete`, `.../finalize`) também exigem o header `Idempotency-Key` (UUID).

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/sessions` | Cria sessão de triagem + caso |
| `POST` | `/sessions/{sessionId}/documents/presign` | Solicita URL pré-assinada de upload |
| `POST` | `/sessions/{sessionId}/documents/{documentId}/complete` | Confirma upload concluído |
| `POST` | `/sessions/{sessionId}/finalize` | Encerra recebimento e publica para o `mcp-ai` (Q2) |
| `GET` | `/sessions/{sessionId}` | Consulta status da sessão e documentos |
| `POST` | `/sessions/{sessionId}/mcp-result` | Callback do `triaige-srv-mcp-ai` com o resultado do pipeline |

O endpoint `mcp-result` é a exceção ao esquema acima: usa `X-Internal-Token` (shared secret) em vez de `Authorization: Bearer` — chamada servidor-a-servidor em rede interna, não exposta via API Gateway.

## Fluxo principal

```text
Documentos enviados pelo escritório
   ↓
Sessão criada + upload via URL pré-assinada (S3 raw)
   ↓
Finalização da sessão → publica na fila de pré-processamento (Q2)
   ↓
triaige-srv-mcp-ai processa (OCR, anonimização, agrupamento) e devolve callback
   ↓
Análise de IA disparada (POST /analyze no mcp-ai)
   ↓
Relatório renderizado no template canônico e gravado no S3 curated
   ↓
Publicação na fila de notificação (Q3)
```

### Fluxo de análise de IA

Ao aplicar um `mcp-result` com `status = COMPLETED`/`PARTIALLY_COMPLETED`, o use case publica um evento de aplicação, consumido **após o commit** da transação do callback, em uma thread separada (para não segurar a resposta HTTP). A partir daí:

1. a sessão é marcada como `ANALYZING`;
2. o serviço chama `POST {mcp-ai}/api/ai/v1/analyze` (1 retry com backoff fixo para falha de rede/conexão);
3. o relatório estruturado devolvido é renderizado no template canônico e gravado (JSON + Markdown) no S3 *curated*;
4. o resultado é persistido (`triage_results`) e a sessão marcada como `ANALYSIS_COMPLETED`;
5. o resultado é publicado na fila de notificação (Q3).

Qualquer falha em qualquer passo marca a sessão como `ANALYSIS_FAILED` e audita o `errorCode` — nunca deixa uma sessão presa em `ANALYZING`.

## Mensageria

| Fila | Tipo | Uso |
|---|---|---|
| `triaige-docs-received` (Q1) | Producer/Consumer interno | Log de eventos de recebimento; mecanismo de recuperação caso a escrita no MySQL falhe |
| `triaige-docs-preprocessing` (Q2) | Producer | Contrato Orchestrator ↔ `mcp-ai` (schema versionado) |
| Fila de resultados (Q3) | Producer | Publicada só em caso de sucesso da análise de IA, consumida pelo serviço de notificação |

## Banco de dados

### Banco utilizado

`MySQL`

### Principais entidades

- `TriageSession`: sessão de triagem em andamento, com status do ciclo (recebimento → pipeline → análise de IA).
- `LegalCase` / `LawFirm`: caso jurídico e escritório associado.
- `LegalDocument`: documento enviado, com status de upload/processamento.
- `AiToolCall`: auditoria de chamadas a ferramentas de IA.
- `AuditEvent`: trilha de auditoria das operações do serviço.
- `TriageResult`: resultado final da triagem, referenciando o relatório gravado no S3.
- `IdempotencyRecord` / `ProtocolSequence`: suporte a idempotência e geração de protocolo único.
- `ApiCredential`: credenciais de API dos escritórios.

## Integrações externas

### triaige-srv-mcp-ai

**Finalidade:** processamento do pipeline de documentos (OCR, anonimização, agrupamento) e raciocínio de IA sobre o conteúdo anonimizado.

**Tipo de comunicação:** HTTP/REST (chamada de análise) + SQS (fila de pré-processamento) + callback HTTP (retorno do pipeline).

## Docker

### Build da imagem

```bash
docker build -t triaige-srv-orchestrator .
```

### Executando a imagem

```bash
docker run \
  -p 8080:8080 \
  --env-file .env \
  triaige-srv-orchestrator
```

## Observabilidade

- Logs estruturados em JSON (`logstash-logback-encoder`), com `correlationId`, `sessionId`, `endpoint`, `method`, `statusCode`, `latencyMs` via MDC — nunca conteúdo de documento ou dado pessoal.
- Métricas via Micrometer (`IngestionLatencyMs`, `SessionsCreatedCount`, `DocumentsReceivedCount`, `SessionsFinalizedCount`, `FinalizeErrorCount`, `Q1PublishLatencyMs`, `Q2PublishLatencyMs`, `S3HeadObjectLatencyMs`, `AiAnalysisTriggerLatencyMs`, `AiAnalysisSuccessCount`, `AiAnalysisFailureCount`, `AiAnalysisRetryCount`, `CuratedReportRenderFailureCount`), exportação para CloudWatch desabilitada por padrão (`CLOUDWATCH_METRICS_ENABLED=true` para habilitar; namespace `Triaige/Orchestrator`).
- Health Check: `GET /actuator/health`

## Segurança

- Autenticação por credencial de API (`Authorization: Bearer`) para chamadas do escritório e por token interno (`X-Internal-Token`) para chamadas servidor-a-servidor.
- Idempotência obrigatória (header `Idempotency-Key`) nas operações com efeito colateral.
- Secrets (credenciais AWS, tokens, senha do banco) configurados via variáveis de ambiente, fora do código.
- Nenhum conteúdo de documento ou dado pessoal é gravado nos logs estruturados.

## Repositórios relacionados

Este repositório faz parte do ecossistema **TriAIge**.

| Repositório | Responsabilidade |
|---|---|
| `triaige-front-nextjs` | Site institucional e mockup de dashboard (Next.js / Tailwind) |
| `triaige-srv-mcp-ai` | Pipeline de pré-processamento (OCR, anonimização, agrupamento) e raciocínio de IA |
| `triaige-infra` | Provisionamento de infraestrutura AWS (Terraform + Ansible) |

## Projeto acadêmico

Projeto desenvolvido como Trabalho de Conclusão de Curso em:

**Curso:** Sistemas de Informação
**Instituição:** São Paulo Tech School
**Ano:** 2026

### Objetivo

Aplicar IA generativa e engenharia de software para automatizar a triagem inicial de casos jurídicos, reduzindo o tempo de análise manual de documentos por escritórios de advocacia, com anonimização de dados sensíveis antes de qualquer processamento por IA.

## Equipe

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/GabrielNunees063">
        <img src="https://avatars.githubusercontent.com/u/125298578?v=4" width="100px;"><br>
        <sub><b>Gabriel Nunes</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/Bielzinschiavo">
        <img src="https://avatars.githubusercontent.com/u/125298078?v=4" width="100px;"><br>
        <sub><b>Gabriel Schiavo</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/gyuliapiqueira">
        <img src="https://avatars.githubusercontent.com/u/125298346?v=4" width="100px;"><br>
        <sub><b>Gyulia Piqueira</b></sub>
      </a>
    </td>
  </tr>

  <tr>
    <td align="center" colspan="3">
      <table>
        <tr>
          <td align="center">
            <a href="https://github.com/Kaori2">
              <img src="https://avatars.githubusercontent.com/u/125297000?v=4" width="100px;"><br>
              <sub><b>Kaori Katayama</b></sub>
            </a>
          </td>
          <td align="center">
            <a href="https://github.com/Miguel-Araujo325">
              <img src="https://avatars.githubusercontent.com/u/125296970?v=4" width="100px;"><br>
              <sub><b>Miguel Araujo</b></sub>
            </a>
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>

## Licença

> Este projeto foi desenvolvido para fins acadêmicos.
