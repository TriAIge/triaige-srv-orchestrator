#!/bin/bash
# Script de inicialização do LocalStack — cria as filas SQS (com DLQ) e o bucket S3 raw.
set -e

echo "Creating SQS DLQs..."

awslocal sqs create-queue --queue-name triaige-docs-received-dlq \
  --attributes '{"MessageRetentionPeriod":"604800"}'

awslocal sqs create-queue --queue-name triaige-docs-preprocessing-dlq \
  --attributes '{"MessageRetentionPeriod":"604800"}'

echo "Creating SQS queues..."

DOCS_RECEIVED_DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url "$(awslocal sqs get-queue-url --queue-name triaige-docs-received-dlq --query QueueUrl --output text)" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

DOCS_PREPROCESSING_DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url "$(awslocal sqs get-queue-url --queue-name triaige-docs-preprocessing-dlq --query QueueUrl --output text)" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

# Q1 (triaige-docs-received): buffer de resiliência consumido internamente pelo próprio
# Orchestrator (spec seção 2) — não pelo MCP.
awslocal sqs create-queue --queue-name triaige-docs-received \
  --attributes "{\"VisibilityTimeout\":\"60\",\"MessageRetentionPeriod\":\"604800\",\"ReceiveMessageWaitTimeSeconds\":\"10\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DOCS_RECEIVED_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}"

# Q2 (triaige-docs-preprocessing): contrato Orchestrator -> MCP (fora do escopo desta fase).
awslocal sqs create-queue --queue-name triaige-docs-preprocessing \
  --attributes "{\"VisibilityTimeout\":\"300\",\"MessageRetentionPeriod\":\"604800\",\"ReceiveMessageWaitTimeSeconds\":\"20\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DOCS_PREPROCESSING_DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

echo "Creating S3 buckets..."

awslocal s3 mb s3://bucket-triaige-raw-certificacoes || true

echo "LocalStack setup complete."
