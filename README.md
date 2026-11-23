# TriAIge / triaige-srv-orchestrator

> Serviço de ingestão de documentos e orquestração da triagem jurídica.

![Java](https://img.shields.io/badge/Java_21-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=flat-square&logo=mysql&logoColor=white)
![AWS](https://img.shields.io/badge/AWS_S3_%7C_SQS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white)
![Licença](https://img.shields.io/badge/licen%C3%A7a-MIT-blue?style=flat-square)

Parte da plataforma [TriAIge](https://github.com/TriAIge) — triagem jurídica assistida por IA.

## Sobre o projeto

Este serviço é a porta de entrada e o ponto de fechamento do fluxo de triagem. Recebe os documentos do escritório (upload direto no S3 por URL pré-assinada), publica cada sessão para o pipeline do `triaige-srv-mcp-ai`, dispara a análise de IA quando o pipeline termina e grava o relatório final no S3 *curated*, publicando-o para o `triaige-srv-notification`.

## Papel na arquitetura

```text
Sistema do escritório / portal
   ↓ 1. cria sessão, pede URL pré-assinada e envia o arquivo direto ao S3 raw
triaige-srv-orchestrator  ←──────────────────────────────┐
   ↓ 2. finalize → publica "documento pronto" (Q2)        │ 3. callback mcp-result
triaige-srv-mcp-ai (T1 OCR → T2 → T3 → T4) ───────────────┘
   ↑ 4. POST /api/ai/v1/analyze (após o callback)
triaige-srv-orchestrator
   ↓ 5. renderiza o relatório no template canônico e grava no S3 curated
   ↓ 6. publica "resultado pronto" (Q3 triaige-results-ready)
triaige-srv-notification ── 7. callback notification-result ──→ triaige-srv-orchestrator
```

A análise de IA é disparada por um evento de aplicação consumido **após o commit** do callback `mcp-result` (`COMPLETED`/`PARTIALLY_COMPLETED`), em thread separada. A sessão passa por `ANALYZING` → `ANALYSIS_COMPLETED` e, com o retorno do notification, `NOTIFIED`/`NOTIFICATION_FAILED`. Qualquer falha na análise marca `ANALYSIS_FAILED` e audita o `errorCode`, nunca deixando uma sessão presa em `ANALYZING`.

## Responsabilidades

- Criar sessões de triagem e o caso associado, com protocolo único, e vincular os contatos ativos do escritório como destinatários (`notification_recipients`).
- Emitir URLs pré-assinadas de upload para o S3 *raw* e confirmar a conclusão de cada upload.
- Finalizar o recebimento de uma sessão e publicar o "documento pronto" (Q2) para o `triaige-srv-mcp-ai`.
- Receber o callback do pipeline (`mcp-result`) e, em caso de sucesso, chamar a análise de IA no `triaige-srv-mcp-ai` (1 retry com backoff fixo em falha de rede).
- Renderizar o relatório no template canônico, gravá-lo (JSON + Markdown) no S3 *curated*, persistir `triage_results` e publicar na Q3.
- Receber o callback do `triaige-srv-notification` (`notification-result`) e fechar o ciclo da sessão.
- Autenticação por credencial de API e por token interno, idempotência, auditoria e métricas.

### Fora do escopo

- OCR, anonimização, agrupamento e raciocínio de IA — `triaige-srv-mcp-ai`.
- Envio de e-mails ao escritório — `triaige-srv-notification`.
- Cadastro de escritórios, contatos e credenciais — `triaige-front-nextjs`.
- Provisionamento de rede, filas, buckets e banco — `triaige-infra`.

## Arquitetura interna

Organização em pacotes por responsabilidade (estilo hexagonal):

```text
src/main/java/br/com/triaige/orchestrator/
├── api/            controllers e DTOs de request/response
├── application/    use cases (um por endpoint), services de domínio (ProtocolGeneratorService,
│                   AuditService, IdempotencyService, ApiCredentialAuthService,
│                   SessionFinalizationService, TriggerAiAnalysisUseCase) e o listener
│                   AiAnalysisEventListener, que dispara a análise após o commit do callback
├── domain/         entidades JPA, enums, exceções de negócio e eventos de aplicação
├── infrastructure/ config, persistence (JPA), s3 (presign / head object), sqs (publisher e
│                   consumidor interno da Q1), security (filtros), http (cliente do mcp-ai),
│                   render (template canônico do relatório)
└── shared/         tratamento de erro, logging estruturado, métricas, utilitários
src/main/resources/
├── application.yml
└── templates/template_triagem_triaige.md
```

### Comunicação com outros serviços

| Serviço / Recurso | Tipo | Finalidade |
|---|---|---|
| `triaige-srv-mcp-ai` | HTTP (chamada) | `POST /api/ai/v1/analyze` para a análise de IA |
| `triaige-srv-mcp-ai` | HTTP (callback recebido) | `POST /sessions/{id}/mcp-result` com o resultado do pipeline |
| `triaige-srv-notification` | HTTP (callback recebido) | `POST /sessions/{id}/notification-result` com o resultado do envio |
| `triaige-docs-received` (Q1) | SQS (producer/consumer interno) | Log de eventos de recebimento |
| `triaige-docs-preprocessing` (Q2) | SQS (producer) | "Documento pronto" para o `triaige-srv-mcp-ai` |
| `triaige-results-ready` (Q3) | SQS (producer) | "Resultado pronto" para o `triaige-srv-notification` |
| S3 *raw* e *curated* | Storage | Documentos originais e relatório final |
| MySQL (schema `triaige`) | Banco de dados | Compartilhado com os demais serviços e com o front |

## Tecnologias

- Java 21, Spring Boot 3.5.0 (Web, Validation, Data JPA, Actuator)
- MySQL 8 (`mysql-connector-j`), Hibernate com `ddl-auto: validate`
- AWS SDK v2 (SQS, S3, STS)
- Lombok, MapStruct, Jackson (`java.time`)
- Logback com `logstash-logback-encoder` (logs JSON)
- Micrometer com exportação para CloudWatch
- JUnit 5, Awaitility
- Docker / Docker Compose

## Pré-requisitos

- Java 21 e Maven
- Docker e Docker Compose
- MySQL acessível na porta `3306` com o schema `triaige` (o seed está em [`triaige-infra`](https://github.com/TriAIge/triaige-infra), `ansible/files/triaige-schema-seed.sql`)
- Credenciais AWS válidas (variáveis `AWS_*` ou perfil `default` em `~/.aws/credentials`) e as filas e buckets já existentes na região

## Configuração

Crie um `.env` na raiz (não versionado). O `docker-compose.yml` monta `DB_URL` a partir de `MYSQL_DATABASE`, e passa `MYSQL_USER`/`MYSQL_PASSWORD` como `DB_USERNAME`/`DB_PASSWORD`; sem Docker, use diretamente as variáveis `DB_*`.

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `MYSQL_DATABASE` | Sim (Compose) | — | Nome do schema no MySQL do host (`triaige`) |
| `MYSQL_USER` / `MYSQL_PASSWORD` | Sim (Compose) | — | Credenciais do MySQL |
| `DB_URL` | Não | `jdbc:mysql://localhost:3306/triaige_srv_orchestrator?...` | URL JDBC (execução sem Docker) |
| `DB_USERNAME` / `DB_PASSWORD` | Não | `triaige` / `triaige` | Credenciais JDBC (execução sem Docker) |
| `SRV_ORCHESTRATOR_PORT` | Sim (Compose) | — | Porta publicada no host (o container escuta em `8080`) |
| `SERVER_PORT` | Não | `8080` | Porta HTTP do serviço |
| `AWS_REGION` | Não | `us-east-1` | Região AWS |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN` | Não | — | Credenciais AWS; se vazias, usa o perfil montado de `~/.aws` |
| `AWS_SQS_ENDPOINT` / `AWS_S3_ENDPOINT` | Não | vazio | Endpoint alternativo; deixe em branco para a AWS real |
| `RAW_DOCUMENTS_BUCKET` | Sim | `bucket-triaige-raw-certificacoes` | Bucket S3 *raw* |
| `CURATED_DOCUMENTS_BUCKET` | Sim | `bucket-triaige-curated` | Bucket S3 *curated* |
| `DOCS_RECEIVED_QUEUE_URL` | Sim | URL local de `triaige-docs-received` | Fila Q1 |
| `DOCS_PREPROCESSING_QUEUE_URL` | Sim | URL local de `triaige-docs-preprocessing` | Fila Q2 |
| `RESULTS_READY_QUEUE_URL` | Sim | URL local de `triaige-results-ready` | Fila Q3 |
| `MCP_AI_BASE_URL` | Sim | `http://localhost:8084` | URL base do `triaige-srv-mcp-ai` |
| `MCP_ANALYZE_TOKEN` | Sim | `dev-local-analyze-token` | Token enviado em `X-Internal-Token` ao `/analyze`; igual ao do mcp-ai |
| `MCP_INTERNAL_TOKEN` | Sim | `dev-local-internal-token` | Token esperado no callback `mcp-result`; igual ao do mcp-ai |
| `NOTIFICATION_INTERNAL_TOKEN` | Sim | `dev-local-internal-token` | Token esperado no callback `notification-result`; igual ao do notification |
| `Q1_CONSUMER_ENABLED` | Não | `true` | Liga o consumidor interno da Q1 |
| `CLOUDWATCH_METRICS_ENABLED` | Não | `false` | Exporta métricas para o CloudWatch |
| `LOG_LEVEL` | Não | `INFO` | Nível de log de `br.com.triaige` |

Os padrões existem só para desenvolvimento. Nunca versione credenciais, tokens ou senhas reais. Sem o `triaige-srv-mcp-ai` no ar e com `MCP_ANALYZE_TOKEN` combinado, a análise falha com `MCP_AI_UNAVAILABLE`/`MCP_AI_HTTP_ERROR` e a sessão fica em `ANALYSIS_FAILED`; a ingestão (`createSession` até `finalize`) continua funcionando.

## Executando localmente

### Docker Compose

```bash
docker compose up --build
```

Sobe só o serviço, na porta `SRV_ORCHESTRATOR_PORT`. O MySQL não faz parte do Compose: o container acessa o banco do host em `host.docker.internal:3306`. Para encerrar, `docker compose down`.

### Sem Docker

```bash
mvn spring-boot:run
```

Exige o MySQL acessível em `DB_URL` e credenciais AWS no ambiente.

### Build e run da imagem

```bash
docker build -t triaige-srv-orchestrator .
docker run -p 8080:8080 --env-file .env triaige-srv-orchestrator
```

Nesse modo, defina `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` no `.env`.

## Testes

```bash
mvn test
```

Cobrem os use cases e services, incluindo o disparo da análise de IA (`TriggerAiAnalysisUseCaseTest`, caminho feliz e cada ramo de falha), o contrato HTTP do cliente do mcp-ai (`McpAiAnalysisClientTest`), o template do relatório (`TriageReportRendererTest`), a idempotência (`IdempotencyServiceTest`) e a configuração de métricas (`CloudWatchMetricsConfigTest`).

## API

Base path: `/api/orchestrator/v1`. Contrato completo em [`docs/openapi.yaml`](./docs/openapi.yaml).

Os endpoints de escritório exigem `Authorization: Bearer <token>`. O token é a credencial de integração emitida no painel do [`triaige-front-nextjs`](https://github.com/TriAIge/triaige-front-nextjs) (`/admin/integracoes`), e o serviço compara o SHA-256 dele com `api_credentials.token_hash`. Os POSTs com efeito colateral (`/sessions`, `.../complete`, `.../finalize`) também exigem `Idempotency-Key` (UUID). O seed de desenvolvimento traz a credencial `dev-local-token`.

| Método | Endpoint | Autenticação | Descrição |
|---|---|---|---|
| `POST` | `/sessions` | Bearer | Cria sessão de triagem e caso |
| `POST` | `/sessions/{sessionId}/documents/presign` | Bearer | Solicita URL pré-assinada de upload |
| `POST` | `/sessions/{sessionId}/documents/{documentId}/complete` | Bearer | Confirma upload concluído |
| `POST` | `/sessions/{sessionId}/finalize` | Bearer | Encerra o recebimento e publica na Q2 |
| `GET` | `/sessions/{sessionId}` | Bearer | Consulta status da sessão e documentos |
| `POST` | `/sessions/{sessionId}/mcp-result` | `X-Internal-Token` | Callback do `triaige-srv-mcp-ai` |
| `POST` | `/sessions/{sessionId}/notification-result` | `X-Internal-Token` | Callback do `triaige-srv-notification` |

Os dois callbacks são chamadas servidor-a-servidor autenticadas por `X-Internal-Token`. A rota fica atrás do ALB (`/api/orchestrator/*`), portanto é alcançável pela internet; o token é a única proteção e precisa ser forte e mantido em segredo.

## Mensageria

| Fila | Papel | Uso |
|---|---|---|
| `triaige-docs-received` (Q1) | Producer e consumer interno | Log de eventos de recebimento; recuperação caso a escrita no MySQL falhe |
| `triaige-docs-preprocessing` (Q2) | Producer | Contrato orchestrator → mcp-ai (schema versionado) |
| `triaige-results-ready` (Q3) | Producer | Publicada só quando a análise de IA termina com sucesso |

Cada fila tem uma DLQ (`<fila>-dlq`) criada pelo `triaige-infra`.

## Banco de dados

MySQL, schema `triaige`, compartilhado com `triaige-srv-mcp-ai`, `triaige-srv-notification` e `triaige-front-nextjs`. O schema é aplicado pelo `triaige-infra` e o Hibernate só valida (`ddl-auto: validate`).

| Tabela | Acesso | Uso |
|---|---|---|
| `triage_sessions`, `legal_cases`, `legal_documents` | Leitura e escrita | Sessão, caso e documentos enviados |
| `processing_steps`, `ai_tool_calls`, `audit_events` | Leitura e escrita | Etapas, chamadas de ferramentas de IA e auditoria |
| `triage_results` | Escrita | Referência ao relatório gravado no S3 *curated* |
| `notification_recipients` | Escrita | Destinatários da sessão (snapshot dos contatos ativos) |
| `idempotency_records`, `protocol_sequences` | Leitura e escrita | Idempotência e geração de protocolo |
| `law_firms`, `law_firm_contacts`, `api_credentials` | Leitura | Escritório, contatos e credenciais de API |

`law_firms`, `law_firm_contacts` e `api_credentials` são gravadas pelo `triaige-front-nextjs` no mesmo banco. O `token_hash` emitido pelo front é o mesmo validado no `Authorization: Bearer`.

## Integrações externas

- **AWS S3:** presign de upload no *raw*, `HeadObject` na confirmação e gravação do relatório no *curated*.
- **AWS SQS:** filas Q1, Q2 e Q3.

As integrações com os demais serviços estão em [Comunicação com outros serviços](#comunicação-com-outros-serviços).

## Observabilidade

- Logs JSON (`logstash-logback-encoder`) com `correlationId`, `sessionId`, `endpoint`, `method`, `statusCode` e `latencyMs` via MDC, sem conteúdo de documento nem dado pessoal. Em produção vão para o log group `/triaige/triaige-srv-orchestrator`.
- Métricas Micrometer no namespace `Triaige/Orchestrator` (`IngestionLatencyMs`, `SessionsCreatedCount`, `DocumentsReceivedCount`, `SessionsFinalizedCount`, `FinalizeErrorCount`, `Q1PublishLatencyMs`, `Q2PublishLatencyMs`, `S3HeadObjectLatencyMs`, `AiAnalysisTriggerLatencyMs`, `AiAnalysisSuccessCount`, `AiAnalysisFailureCount`, `AiAnalysisRetryCount`, `CuratedReportRenderFailureCount`), exportadas com `CLOUDWATCH_METRICS_ENABLED=true`.
- Health check: `GET /actuator/health`.

## Segurança

- `Authorization: Bearer` para escritórios; `X-Internal-Token` para os callbacks servidor-a-servidor (ver [API](#api) sobre a exposição pelo ALB).
- `Idempotency-Key` obrigatória nas operações com efeito colateral.
- Secrets via variáveis de ambiente, fora do código.
- Nenhum conteúdo de documento ou dado pessoal nos logs.

## Repositórios relacionados

| Repositório | Responsabilidade |
|---|---|
| [`triaige-front-nextjs`](https://github.com/TriAIge/triaige-front-nextjs) | Portal dos escritórios e painel da equipe TriAIge (Next.js), com API própria sobre o banco compartilhado |
| [`triaige-srv-mcp-ai`](https://github.com/TriAIge/triaige-srv-mcp-ai) | Pré-processamento (OCR, anonimização, agrupamento, resumo) e análise com IA |
| [`triaige-srv-notification`](https://github.com/TriAIge/triaige-srv-notification) | Envio do relatório por e-mail, com retry e callback ao orchestrator |
| [`triaige-infra`](https://github.com/TriAIge/triaige-infra) | Infraestrutura AWS (Terraform, Ansible e GitHub Actions) |

## Projeto acadêmico

Trabalho de Conclusão de Curso de Sistemas de Informação na São Paulo Tech School (SPTech), 2026. Equipe e visão geral no [perfil da organização](https://github.com/TriAIge).

## Licença

Distribuído sob a licença MIT — ver [LICENSE](LICENSE).
