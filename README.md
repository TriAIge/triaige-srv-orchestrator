# triaige-srv-orchestrator

Serviço orquestrador do projeto **TriAIge** — controla o ciclo de vida de sessões jurídicas, coordena o fluxo entre pré-processamento (via Amazon SQS), análise de IA (via REST síncrono com o `triaige-srv-ai`) e notificação (via Amazon SQS), e persiste rastreabilidade em MySQL.

---

## Sumário

- [Arquitetura](#arquitetura)
- [Pré-requisitos](#pré-requisitos)
- [Perfis e persistência](#perfis-e-persistência)
- [Configuração externa (segredos)](#configuração-externa-segredos)
- [Rodando localmente](#rodando-localmente)
- [Documentação da API](#documentação-da-api)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Fluxo de status](#fluxo-de-status)
- [Estrutura de pacotes](#estrutura-de-pacotes)

---

## Arquitetura

```
Front-end / API Gateway
         │
         ▼
┌───────────────────────────────┐
│  triaige-srv-orchestrator     │  ← este serviço
│  POST /sessions               │
│  POST /sessions/{}/docs ───┐  │
│  POST /sessions/{}/proc    │  │
│  GET  /sessions/{}         │  │
└────────┬───────────────────┼──┘
         │                    │ S3 PutObject
         │                    ▼
         │           bucket triaige-raw-documents
         ▼
                    SQS triaige-docs-preprocessing
                              │
                              ▼
                    pré-processamento (Lambda, ainda não implementada)
                              │
                              │ callback HTTP (PreProcessingCompletedUseCase)
                              ▼
                  REST síncrono ──► triaige-srv-ai (POST /internal/api/v1/analysis)
                              │
                              ▼
                  S3 bucket curated + SQS triaige-results-ready
                              │
                              ▼
                  triaige-srv-notification-sender
```

O Orquestrador **não lê PDF**, **não faz OCR**, **não chama Gemini diretamente** e **não envia e-mail/SMS**. Ele recebe o documento, envia para o bucket S3, dispara o pré-processamento via SQS, chama o `triaige-srv-ai` via REST quando o pré-processamento termina, grava o resultado no bucket curated e publica o evento de resultado pronto na fila SQS para o serviço de notificação.

---

## Pré-requisitos

- Java 21+
- Maven 3.9+
- Docker e Docker Compose (LocalStack para S3/SQS)

---

## Perfis e persistência

| Perfil | Persistência | Quando usar |
|--------|---------------|-------------|
| `dev` (padrão) | H2 em arquivo (`./data/triaige-srv-orchestrator`), schema gerado automaticamente (`ddl-auto: update`), Flyway desabilitado | Desenvolvimento local, sem precisar subir MySQL |
| `ho` | MySQL (`triaige_srv_orchestrator`) local via Docker Compose, Flyway habilitado | Homologação / teste local do schema com MySQL real, sem depender de infraestrutura externa |
| `prod` | MySQL (`triaige_srv_orchestrator`) na AWS, Flyway habilitado | Ambiente de produção (infraestrutura provisionada via Terraform/Ansible) |

O perfil ativo é definido pela variável `SPRING_PROFILES_ACTIVE` (padrão `dev`).

```bash
# dev (H2 local)
mvn spring-boot:run

# ho (MySQL local, ex: via docker compose up mysql)
SPRING_PROFILES_ACTIVE=ho mvn spring-boot:run

# prod (MySQL na AWS — requer DB_URL/DB_USERNAME/DB_PASSWORD via config externa)
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

Console do H2 disponível em `http://localhost:8080/h2-console` no perfil `dev`.

---

## Configuração externa (segredos)

Valores sensíveis e específicos do ambiente de produção (URLs de buckets S3, filas
SQS, credenciais do MySQL etc.) **não ficam neste repositório**. Eles são carregados
em tempo de execução a partir do projeto irmão
[`triaige-srv-orchestrator-config`](../triaige-srv-orchestrator-config), via:

```yaml
spring:
  config:
    import: "optional:file:${CONFIG_REPO_PATH:../triaige-srv-orchestrator-config/}"
```

- O `triaige-srv-orchestrator-config` contém **um arquivo por ambiente**
  (`application-ho.yml`, `application-prod.yml`), seguindo a mesma convenção
  de profiles do Spring Boot — não um único arquivo combinado.
- O import é **opcional** — em `dev` e `ho`, sem o arquivo do profile presente,
  os defaults de `application-dev.yml` (H2 + LocalStack) ou `application-ho.yml`
  (MySQL local via docker compose) continuam valendo normalmente.
- Em `prod`, o Ansible gera `application-prod.yml` a partir do template
  `application-prod.yml.example` (com os valores reais de saída do Terraform) e
  o copia para o caminho indicado por `CONFIG_REPO_PATH` no servidor. O perfil
  `prod` não tem credenciais default — sem esse arquivo (ou as variáveis
  `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` equivalentes), a aplicação não sobe.

Veja detalhes no README do [`triaige-srv-orchestrator-config`](../triaige-srv-orchestrator-config/README.md).

---

## Rodando localmente

### 1. Subir LocalStack (S3 + SQS)

```bash
docker compose up localstack -d
```

O script `scripts/localstack/01-create-queues.sh` é executado automaticamente pelo LocalStack na inicialização e cria:
- Filas SQS (com DLQ): `triaige-docs-preprocessing`, `triaige-results-ready`
- Buckets S3: `triaige-raw-documents`, `triaige-processed-documents`, `triaige-results`
- Documento TXT de teste usado pelo `triaige-srv-ai`:
  `s3://triaige-processed-documents/tenant-001/sess-2026-000001/doc-001/normalized.txt`

### 2. Rodar o serviço

```bash
# Desenvolvimento
export AWS_SQS_ENDPOINT=http://localhost:4566
export AWS_S3_ENDPOINT=http://localhost:4566
export RAW_DOCUMENTS_BUCKET=triaige-raw-documents
export CURATED_RESULTS_BUCKET=triaige-results
export DOCS_PREPROCESSING_QUEUE_URL=http://localhost:4566/000000000000/triaige-docs-preprocessing
export RESULTS_READY_QUEUE_URL=http://localhost:4566/000000000000/triaige-results-ready
export AI_SERVICE_BASE_URL=http://localhost:8082

mvn spring-boot:run

# Opção B — Docker Compose completo (build + run, perfil ho com MySQL)
docker compose up --build
```

O serviço estará disponível em `http://localhost:8080`.

Health check: `GET http://localhost:8080/actuator/health`

---

## Documentação da API

A especificação completa dos endpoints (públicos e internos), incluindo schemas de
request/response, enums e respostas de erro, está em
[`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3.0).

Para visualizar de forma interativa, abra o arquivo em uma ferramenta como
[Swagger Editor](https://editor.swagger.io/) ou Redoc.

### Testando localmente (Postman/Insomnia)

Uma coleção pronta para uso está em
[`docs/postman/triaige-srv-orchestrator.postman_collection.json`](docs/postman/triaige-srv-orchestrator.postman_collection.json).
Importe no Postman ou Insomnia (ambos suportam o formato Postman Collection v2.1).

A coleção já inclui as variáveis `baseUrl`, `lawFirmId`, `sessionId`, `documentId` e
`correlationId`. Os requests de "Criar sessão" e "Registrar documento" preenchem
automaticamente `sessionId`, `correlationId` e `documentId` a partir da resposta,
para encadear as próximas chamadas sem copiar/colar valores manualmente.

### Headers opcionais

| Header | Descrição |
|--------|-----------|
| `X-Correlation-Id` | UUID de rastreabilidade. Gerado automaticamente se ausente |
| `X-Law-Firm-Id` | Isolamento por escritório jurídico. Sobrescreve o `lawFirmId` do body quando presente |

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil ativo: `dev` (H2), `ho` (MySQL local) ou `prod` (MySQL na AWS) |
| `CONFIG_REPO_PATH` | `../triaige-srv-orchestrator-config/` | Caminho do repositório de [configuração externa](#configuração-externa-segredos) com os segredos do ambiente (usado em `prod`) |
| `DB_URL` | depende do perfil | JDBC URL do banco (H2 em `dev`, MySQL em `ho`/`prod`) |
| `DB_USERNAME` | `sa` (dev) / `triaige` (ho) / _sem default_ (prod) | Usuário do banco |
| `DB_PASSWORD` | _(vazio)_ (dev) / `triaige` (ho) / _sem default_ (prod) | Senha do banco |
| `AWS_REGION` | `us-east-1` | Região AWS |
| `AWS_SQS_ENDPOINT` | _(vazio = AWS real)_ | Endpoint customizado do SQS (LocalStack) |
| `AWS_S3_ENDPOINT` | _(vazio = AWS real)_ | Endpoint customizado do S3 (LocalStack) |
| `RAW_DOCUMENTS_BUCKET` | `triaige-raw-documents` | Bucket de destino do upload de documentos |
| `CURATED_RESULTS_BUCKET` | `triaige-results` | Bucket de destino do resultado da análise de IA (curated) |
| `DOCS_PREPROCESSING_QUEUE_URL` | URL LocalStack local | URL da fila `triaige-docs-preprocessing` (pré-processamento) |
| `RESULTS_READY_QUEUE_URL` | URL LocalStack local | URL da fila `triaige-results-ready` (resultado pronto para notificação) |
| `AI_SERVICE_BASE_URL` | `http://localhost:8082` | URL base do `triaige-srv-ai` (chamada REST síncrona de análise) |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `LOG_LEVEL` | `INFO` | Nível de log (`DEBUG`, `INFO`, `WARN`) |

---

## Fluxo de status

```
ABERTA
  │
  ├── [enviar documentos para o bucket] ────────────────────────────────┐
  │                                                                     │
  └── POST /process                                                     │
        │                                                               │
        ▼                                                               │
AGUARDANDO_PRE_PROCESSAMENTO ── SQS triaige-docs-preprocessing ──►     │
        │                            pré-processamento (Lambda,         │
        │                            ainda não implementada)            │
        │                                      │                        │
        │                       callback HTTP  │                        │
        ▼◄─────────────────────────────────────┘                        │
AGUARDANDO_PROCESSAMENTO_IA                                            │
        │                                                               │
        │ chamada REST síncrona ──► triaige-srv-ai                     │
        │ (POST /internal/api/v1/analysis)                              │
        ▼                                                               │
CONCLUIDA ── SQS triaige-results-ready ──► triaige-srv-notification-sender
                                                                        │
FALHA ◄─────────────────────────────── erro em qualquer etapa ─────────┘
CANCELADA
```

Ao receber o callback de pré-processamento, o orquestrador chama o `triaige-srv-ai`
de forma síncrona (mesma transação) e já completa o fluxo até `CONCLUIDA`/`FALHA`,
sem etapas intermediárias de fila para a análise de IA.

---

## Estrutura de pacotes

```
br.com.triaige.orchestrator
├── OrchestratorApplication.java            ← classe main (Spring Boot)
├── api
│   ├── controller
│   │   ├── SessionController.java          ← endpoints públicos
│   │   └── InternalCallbackController.java ← endpoints /internal
│   └── dto
│       ├── request/                        ← CreateSessionRequest, ...
│       └── response/                       ← CreateSessionResponse, RegisterDocumentResponse, ...
├── application
│   ├── service
│   │   ├── AuditService.java
│   │   └── ProtocolGeneratorService.java
│   └── usecase
│       ├── CreateSessionUseCase.java
│       ├── RegisterDocumentUseCase.java    ← faz upload via DocumentStoragePort
│       ├── TriggerProcessingUseCase.java
│       ├── PreProcessingCompletedUseCase.java
│       ├── AiProcessingCompletedUseCase.java
│       └── GetSessionStatusUseCase.java
├── domain
│   ├── entity/                             ← TriageSession, LegalCase, LegalDocument, ...
│   ├── enums/                              ← SessionStatus, DocumentStatus, LegalArea, ...
│   └── exception/                          ← SessionNotFoundException, DocumentStorageException, ...
├── infrastructure
│   ├── ai
│   │   ├── AiAnalysisClient.java           ← interface
│   │   ├── RestAiAnalysisClient.java       ← implementação via RestClient (REST síncrono)
│   │   ├── AiAnalysisRequest.java
│   │   └── AiAnalysisResponse.java
│   ├── config
│   │   ├── AwsConfig.java                  ← beans SqsClient e S3Client
│   │   ├── AwsProperties.java
│   │   ├── AiServiceProperties.java        ← triaige.ai-service.base-url
│   │   ├── AiServiceConfig.java            ← bean RestClient para o triaige-srv-ai
│   │   └── JacksonConfig.java
│   ├── persistence/                        ← JPA Repositories
│   ├── s3
│   │   ├── DocumentStoragePort.java        ← interface
│   │   ├── S3DocumentStorageService.java   ← implementação AWS SDK v2 (PutObject)
│   │   └── StoredDocument.java
│   └── sqs
│       ├── QueuePublisher.java             ← interface
│       ├── SqsQueuePublisher.java          ← implementação AWS SDK v2
│       └── *Message.java                   ← DTOs das mensagens SQS (PreProcessingRequestedMessage, TriageResultReadyMessage)
└── shared
    ├── error
    │   ├── ErrorResponse.java
    │   └── GlobalExceptionHandler.java
    └── util
        └── CorrelationIdUtil.java
```
