# 0002 — Callback HTTP do mcp-ai em vez de fila de retorno

- Status: aceita
- Data: 2026-06

## Contexto
Depois de pré-processar uma sessão, o `triaige-srv-mcp-ai` precisa avisar o orchestrator para que a análise de IA seja disparada. O pedido de ida já é assíncrono (fila `triaige-docs-preprocessing`). Desde a primeira versão, com a Lambda `triaige-fn-ocr-normalizer`, o retorno era uma chamada REST ao orchestrator.

## Decisão
O mcp-ai devolve o resultado por `POST /api/orchestrator/v1/sessions/{id}/mcp-result`, autenticado por `X-Internal-Token`. O orchestrator aplica o resultado na mesma requisição e dispara a análise depois do commit. Se o callback esgotar os retries do lado do mcp-ai, o payload vai para a fila `triaige-mcp-callback-dlq`, para não se perder.

## Alternativas consideradas
- Fila de resultados do pipeline: desacoplaria a disponibilidade do orchestrator, mas exigiria mais um consumidor em polling, adicionaria a latência do polling ao fluxo e não daria ao mcp-ai a confirmação de que o resultado foi aceito.

## Consequências
- O mcp-ai sabe na hora se o resultado foi aplicado (resposta 2xx), e o fluxo não espera um ciclo de polling.
- Fica uma fila a menos no caminho principal.
- Em troca, o orchestrator precisa estar no ar no momento do callback, e o endpoint fica atrás do ALB (`/api/orchestrator/*`), alcançável pela internet com o token como única proteção.
- A `triaige-mcp-callback-dlq` só é reprocessada manualmente.
