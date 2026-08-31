# triaige-srv-orchestrator

Serviço de ingestão de documentos do TriAIge — Fase 1 (ver
[`spec-fase1-orchestrator-ingestao.md`](./spec-fase1-orchestrator-ingestao.md)).

Recebe documentos enviados pelo sistema do escritório, persiste metadados no
MySQL, grava os arquivos originais no S3 raw e publica o "documento pronto"
na fila de pré-processamento (Q2) para o MCP consumir. Esta fase **não**
inclui OCR, anonimização, IA ou notificação.

## Arquitetura

Hexagonal, em pacotes por responsabilidade:

```
api/            controllers, DTOs de request/response
application/    use cases (um por endpoint) e services de domínio
                (ProtocolGeneratorService, AuditService, IdempotencyService,
                ApiCredentialAuthService, DocumentReceivedFinalizationService,
                SessionFinalizationService)
domain/         entidades JPA, enums, exceções de negócio
infrastructure/ config (AWS, Jackson, propriedades), persistence (repos JPA),
                s3 (presign/HeadObject), sqs (publisher, mensagens, consumidor
                interno de Q1), security (filtro de autenticação)
shared/         tratamento de erro, logging estruturado, métricas, utils
```

## Subindo o ambiente local

```bash
docker compose up --build
```

Sobe MySQL (porta 3306), LocalStack com SQS+S3 (porta 4566, filas e bucket
criados por `scripts/localstack/01-create-queues.sh`) e o próprio serviço
(porta 8080). Não há mais perfis Spring separados: `application.yml` já
reflete o único cenário suportado (MySQL real via `DB_URL`/`DB_USERNAME`/
`DB_PASSWORD`, bucket real via `RAW_DOCUMENTS_BUCKET`), todos com defaults
locais sobrescrevíveis por variável de ambiente.

Uma credencial de teste já ativa é criada pela migração
`V3__seed_dev_data.sql`:

```
Authorization: Bearer dev-local-token
```

> **Atenção:** URLs pré-assinadas geradas pelo container usam o endpoint
> interno do compose (`http://localstack:4566`) e não são resolvíveis por um
> cliente rodando no host. Para testar o fluxo `presign -> upload direto ao
> S3` a partir de fora do compose, rode o orchestrator localmente (fora do
> container) com `AWS_S3_ENDPOINT=http://localhost:4566`.

### Rodando sem Docker

```bash
mvn spring-boot:run
```

Usa a mesma configuração de `application.yml` (MySQL real via `DB_URL`), então exige MySQL
acessível (ex: `docker compose up mysql` deste repositório). Endpoints que chamam S3/SQS
continuam exigindo LocalStack ou credenciais AWS reais configuradas via
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

## Filas

- **Q1** `triaige-docs-received`: log de eventos de recebimento, publicado
  de forma síncrona pelo próprio Orchestrator antes da escrita transacional
  no MySQL, e consumido internamente (mesma instância) como mecanismo de
  recuperação caso a escrita falhe. Não é consumida por outro serviço nesta
  fase.
- **Q2** `triaige-docs-preprocessing`: contrato estável Orchestrator ↔ MCP ↔
  IA (schema versionado, `schemaVersion`).

## Testes

```bash
mvn test
```

Inclui teste de integração (Testcontainers: MySQL + LocalStack) cobrindo o
fluxo ponta a ponta `createSession -> presign -> upload S3 -> complete ->
finalize`, e teste de concorrência da geração de protocolo.

## Observabilidade

- Logs estruturados em JSON (logstash-logback-encoder), com `correlationId`,
  `sessionId`, `endpoint`, `method`, `statusCode`, `latencyMs` via MDC —
  nunca conteúdo de documento ou dado pessoal.
- Métricas via Micrometer (`IngestionLatencyMs`, `SessionsCreatedCount`,
  `DocumentsReceivedCount`, `SessionsFinalizedCount`, `FinalizeErrorCount`,
  `Q1PublishLatencyMs`, `Q2PublishLatencyMs`, `S3HeadObjectLatencyMs`),
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
