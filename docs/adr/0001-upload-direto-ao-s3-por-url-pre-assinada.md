# 0001 — Upload direto ao S3 por URL pré-assinada

- Status: aceita
- Data: 2026-08

## Contexto
Na primeira versão (junho de 2026) o escritório enviava cada arquivo para `POST /sessions/{id}/documents` em `multipart/form-data`, e o orchestrator repassava o conteúdo ao S3. Processos jurídicos chegam a dezenas de PDFs grandes; cada upload ocupava memória e uma thread do serviço durante toda a transferência, e passava pelo ALB e pelas EC2 de aplicação.

## Decisão
Na reescrita de agosto de 2026, o orchestrator deixou de receber o arquivo. O cliente pede uma URL pré-assinada (`POST .../documents/presign`, válida por 15 minutos), envia o arquivo direto ao bucket *raw* e confirma com `POST .../documents/{id}/complete`; o serviço verifica o objeto com `HeadObject` antes de aceitar.

## Alternativas consideradas
- Upload passando pela API (abordagem anterior): mais simples para o cliente, numa chamada só, mas fazia o serviço carregar o tráfego dos arquivos e limitava o tamanho do envio aos limites do servidor e do ALB.

## Consequências
- O tráfego dos arquivos não passa pelas nossas instâncias.
- Uploads grandes ou paralelos não afetam a latência da API, e o S3 cuida da transferência.
- Em troca, o cliente faz três chamadas por documento (presign, PUT no S3, complete).
- Um upload pode ficar órfão se o cliente não confirmar; por isso o `complete` confere a existência do objeto e só documentos confirmados entram no `finalize`.
