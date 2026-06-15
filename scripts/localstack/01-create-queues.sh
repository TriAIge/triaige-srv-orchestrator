#!/bin/bash
# Script de inicialização do LocalStack — cria as filas SQS (com DLQ) na inicialização
set -e

echo "Creating SQS DLQs..."

awslocal sqs create-queue --queue-name triaige-docs-preprocessing-dlq \
  --attributes '{"MessageRetentionPeriod":"604800"}'

awslocal sqs create-queue --queue-name triaige-results-ready-dlq \
  --attributes '{"MessageRetentionPeriod":"604800"}'

echo "Creating SQS queues..."

DOCS_PREPROCESSING_DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url "$(awslocal sqs get-queue-url --queue-name triaige-docs-preprocessing-dlq --query QueueUrl --output text)" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

RESULTS_READY_DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url "$(awslocal sqs get-queue-url --queue-name triaige-results-ready-dlq --query QueueUrl --output text)" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

awslocal sqs create-queue --queue-name triaige-docs-preprocessing \
  --attributes "{\"VisibilityTimeout\":\"300\",\"MessageRetentionPeriod\":\"604800\",\"ReceiveMessageWaitTimeSeconds\":\"20\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DOCS_PREPROCESSING_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

awslocal sqs create-queue --queue-name triaige-results-ready \
  --attributes "{\"VisibilityTimeout\":\"120\",\"MessageRetentionPeriod\":\"604800\",\"ReceiveMessageWaitTimeSeconds\":\"20\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${RESULTS_READY_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}"

echo "Creating S3 buckets..."

awslocal s3 mb s3://triaige-raw-documents || true
awslocal s3 mb s3://triaige-processed-documents || true
awslocal s3 mb s3://triaige-results || true

echo "Seeding AI test document..."

cat > /tmp/normalized.txt <<'EOF'
Contrato de prestacao de servicos entre as partes Joao Silva (Contratante) e
Empresa ABC Ltda (Contratada). O contratante alega descumprimento contratual,
com falta de pagamento de 3 parcelas no valor de R$ 5.000,00 cada.
Data do contrato: 01/01/2025. Vencimento das parcelas: 01/02/2025, 01/03/2025, 01/04/2025.
EOF

awslocal s3 cp /tmp/normalized.txt \
  s3://triaige-processed-documents/tenant-001/sess-2026-000001/doc-001/normalized.txt

echo "LocalStack setup complete."
