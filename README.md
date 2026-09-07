# triaige-srv-orchestrator

Serviço de ingestão de documentos do TriAIge — Fase 1 (ver
[`spec-fase1-orchestrator-ingestao.md`](./spec-fase1-orchestrator-ingestao.md)) — **e**, a
partir da Fase 4 (`spec-fase4-orchestrator-triagem-ia.md`), orquestrador do disparo da
análise de IA pós-pipeline.

Recebe documentos enviados pelo sistema do escritório, persiste metadados no
MySQL, grava os arquivos originais no S3 raw e publica o "documento pronto"
na fila de pré-processamento (Q2) para o MCP consumir. Esta fase 1 **não**
inclui OCR, anonimização, IA ou notificação. A partir da Fase 4, ao receber de volta
o `mcp-result` do `triaige-srv-mcp-ai` com `status = COMPLETED`/`PARTIALLY_COMPLETED`,
o Orchestrator também dispara a análise de IA, renderiza o relatório final no template
canônico e o publica para a fila de notificação (Q3) — ver seção dedicada abaixo.

## Arquitetura

Hexagonal, em pacotes por responsabilidade:

```
api/            controllers, DTOs de request/response
application/    use cases (um por endpoint) e services de domínio
                (ProtocolGeneratorService, AuditService, IdempotencyService,
                ApiCredentialAuthService, DocumentReceivedFinalizationService,
                SessionFinalizationService, TriggerAiAnalysisUseCase — Fase 4) e
                listeners (AiAnalysisEventListener — Fase 4, dispara a análise após
                commit da transação do callback)
domain/         entidades JPA, enums, exceções de negócio, eventos de aplicação
                (AiAnalysisTriggeredEvent — Fase 4)
infrastructure/ config (AWS, Jackson, propriedades, async — Fase 4), persistence
                (repos JPA), s3 (presign/HeadObject), sqs (publisher, mensagens,
                consumidor interno de Q1), security (filtro de autenticação),
                http (cliente do triaige-srv-mcp-ai — Fase 4), render (preenche o
                template canônico do relatório final por âncoras de texto, sem
                template engine — Fase 4)
shared/         tratamento de erro, logging estruturado, métricas (incluindo
                AiAnalysisMetrics — Fase 4), utils
```

## Subindo o ambiente local

Copie/ajuste o `.env` na raiz do projeto (já vem preenchido com defaults de
desenvolvimento) e suba:

```bash
docker compose up --build
```

Um único `docker-compose.yml` sobe MySQL (porta `MYSQL_PORT`, padrão 3316) e
o próprio serviço (porta `SRV_ORCHESTRATOR_PORT`, padrão 8080). Não há mais
LocalStack: os endpoints S3/SQS apontam para recursos AWS reais (provisionados
via Terraform em `triaige-infra`) — deixe `AWS_SQS_ENDPOINT`/`AWS_S3_ENDPOINT`
em branco no `.env` para usar os endpoints reais da região, com
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_SESSION_TOKEN` válidos.
`application.yml` já reflete o único cenário suportado (MySQL real via
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, bucket real via
`RAW_DOCUMENTS_BUCKET`), todos com defaults sobrescrevíveis por variável de
ambiente (ver `.env`).

Uma credencial de teste já ativa é criada pelo seed em `script.sql` (raiz do
workspace, aplicado externamente via Ansible):

```
Authorization: Bearer dev-local-token
```

> **Fase 4 exige o `triaige-srv-mcp-ai` no ar e um `MCP_ANALYZE_TOKEN` combinado entre os
> dois `.env`.** `MCP_AI_BASE_URL` (padrão `http://localhost:8084`, use
> `http://host.docker.internal:8084` rodando via Docker Compose) e `MCP_ANALYZE_TOKEN`
> autenticam a chamada `POST {mcp-ai}/api/ai/v1/analyze`; `CURATED_DOCUMENTS_BUCKET` e
> `RESULTS_READY_QUEUE_URL` (Q3) precisam apontar para recursos reais provisionados via
> Terraform em `triaige-infra`. Sem isso, o disparo da análise falha com
> `MCP_AI_UNAVAILABLE`/`MCP_AI_HTTP_ERROR` e a sessão fica em `ANALYSIS_FAILED` — o
> pipeline da Fase 1/2 (`createSession` até `finalize`) continua funcionando normalmente.

### Rodando sem Docker

```bash
mvn spring-boot:run
```

Usa a mesma configuração de `application.yml` (MySQL real via `DB_URL`), então exige MySQL
acessível (ex: `docker compose up mysql` deste repositório). Endpoints que chamam S3/SQS
exigem credenciais AWS reais configuradas via `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/
`AWS_S3_ENDPOINT`/`AWS_SQS_ENDPOINT`.

## API

Base path: `/api/orchestrator/v1`. Todos os endpoints exigem
`Authorization: Bearer <token>`; os três POSTs que causam efeito colateral
(`/sessions`, `.../complete`, `.../finalize`) também exigem um header
`Idempotency-Key` (UUID).

| Método | Path | Descrição |
|---|---|---|
| POST | `/sessions` | Cria sessão de triagem + caso |
| POST | `/sessions/{sessionId}/documents/presign` | Solicita URL pré-assinada de upload |
| POST | `/sessions/{sessionId}/documents/{documentId}/complete` | Confirma upload concluído |
| POST | `/sessions/{sessionId}/finalize` | Encerra recebimento e publica para o MCP (Q2) |
| GET | `/sessions/{sessionId}` | Consulta status da sessão e documentos |
| POST | `/sessions/{sessionId}/mcp-result` | Callback do `triaige-srv-mcp-ai` (Fase 2) com o resultado do pipeline |

Contrato completo: [`docs/openapi.yaml`](./docs/openapi.yaml) e a spec.

O endpoint `mcp-result` é a única exceção ao esquema de autenticação acima: não usa
`Authorization: Bearer`, e sim um header `X-Internal-Token` (shared secret,
`MCP_INTERNAL_TOKEN`/`orchestrator.internal.mcp-callback-token`) — pensado para chamada
servidor-a-servidor em rede interna, não exposição via ALB/API Gateway (spec da Fase 2,
seção 6). Ver [`triaige-srv-mcp-ai`](../triaige-srv-mcp-ai) para o lado que o chama.

## Fluxo de análise de IA (Fase 4)

Ao aplicar um `mcp-result` com `status = COMPLETED`/`PARTIALLY_COMPLETED`,
`ApplyMcpResultUseCase` publica um `AiAnalysisTriggeredEvent` de aplicação (Spring), só
efetivamente consumido **após o commit** da transação do callback
(`AiAnalysisEventListener`, `@TransactionalEventListener(phase = AFTER_COMMIT)`), em uma
thread separada (`analysisExecutor`, pool dedicado) — para não segurar a resposta HTTP do
callback pela duração da análise. `TriggerAiAnalysisUseCase` então:

1. marca a sessão como `ANALYZING` (idempotência preventiva: evento duplicado numa sessão
   já além desse ponto é ignorado);
2. chama `POST {mcp-ai}/api/ai/v1/analyze` (1 retry com backoff fixo de 5s só para falha de
   rede/conexão; timeout de leitura configurável, `orchestrator.mcp-ai.read-timeout-ms`,
   folgado acima do timeout de análise do próprio mcp-ai);
3. renderiza o relatório estruturado devolvido no template canônico
   (`template_triagem_triaige.md`) e grava JSON + Markdown no S3 curated
   (`AwsProperties.s3.curated-documents-bucket`);
4. persiste `triage_results` (com stub local em `ai_tool_calls` para satisfazer a FK de
   `jurisprudence_call_id`, gerado no banco do mcp-ai) e marca a sessão como
   `ANALYSIS_COMPLETED`;
5. publica em **Q3** para a fila de notificação.

Qualquer falha em qualquer passo (rede, HTTP do mcp-ai, renderização, escrita S3) marca a
sessão como `ANALYSIS_FAILED` e audita o `errorCode` — nunca deixa uma sessão presa em
`ANALYZING`. `SessionStatus.COMPLETED`/`PARTIALLY_COMPLETED` deixam de ser terminais a
partir desta fase: passam a significar "pipeline do MCP concluído, aguardando análise de
IA"; só `FAILED` continua terminal.

## Filas

- **Q1** `triaige-docs-received`: log de eventos de recebimento, publicado
  de forma síncrona pelo próprio Orchestrator antes da escrita transacional
  no MySQL, e consumido internamente (mesma instância) como mecanismo de
  recuperação caso a escrita falhe. Não é consumida por outro serviço nesta
  fase.
- **Q2** `triaige-docs-preprocessing`: contrato estável Orchestrator ↔ MCP ↔
  IA (schema versionado, `schemaVersion`).
- **Q3** (Fase 4, `aws.sqs.results-ready-queue-url`): publicada só em caso de
  sucesso da análise de IA, com as referências ao relatório já gravado em S3 curated —
  consumida pelo serviço de notificação (fora do escopo deste repositório).

## Testes

```bash
mvn test
```

Apenas testes unitários por enquanto — os testes de integração (Testcontainers:
MySQL + LocalStack) cobrindo o fluxo ponta a ponta `createSession -> presign ->
upload S3 -> complete -> finalize` e a concorrência da geração de protocolo
foram removidos junto com o Flyway; serão reintroduzidos quando o provisionamento
de schema via Ansible estiver pronto.

Cobertura da Fase 4: `TriggerAiAnalysisUseCaseTest` (fluxo feliz e cada ramo de falha do
use case, mockando o cliente HTTP/S3/repositórios), `McpAiAnalysisClientTest` (contrato
HTTP do cliente do mcp-ai via `MockRestServiceServer`) e `TriageReportRendererTest`
(preenchimento do template com 0/1/N itens em cada bloco de tamanho variável).

## Observabilidade

- Logs estruturados em JSON (logstash-logback-encoder), com `correlationId`,
  `sessionId`, `endpoint`, `method`, `statusCode`, `latencyMs` via MDC —
  nunca conteúdo de documento ou dado pessoal.
- Métricas via Micrometer (`IngestionLatencyMs`, `SessionsCreatedCount`,
  `DocumentsReceivedCount`, `SessionsFinalizedCount`, `FinalizeErrorCount`,
  `Q1PublishLatencyMs`, `Q2PublishLatencyMs`, `S3HeadObjectLatencyMs`, e da Fase 4:
  `AiAnalysisTriggerLatencyMs`, `AiAnalysisSuccessCount`, `AiAnalysisFailureCount`
  (por `errorCode`), `AiAnalysisRetryCount`, `CuratedReportRenderFailureCount`),
  exportação para CloudWatch desabilitada por padrão
  (`CLOUDWATCH_METRICS_ENABLED=true` para habilitar; namespace
  `Triaige/Orchestrator`). Alarmes CloudWatch são infraestrutura (Terraform/
  console), fora do escopo deste repositório.

## Suposições assumidas (não cobertas explicitamente pela spec)

- `api_credentials.status` e `law_firms.status`: valores não enumerados pela
  spec; adotado `ACTIVE/INACTIVE/REVOKED` para credenciais.
- `caso.areaJuridica`/`caso.tipoCaso`: a spec menciona "fora de domínio
  conhecido" mas não fornece a lista de valores válidos; validados apenas
  como texto obrigatório com limite de tamanho.
- `contentType`: a spec pede uma lista de MIME types permitidos "a definir
  com o time jurídico"; não implementada nesta fase (aceito qualquer valor
  não vazio até 100 caracteres).
