#!/bin/bash
set -euo pipefail

BROKERS="${REDPANDA_BROKERS:-redpanda:9092}"
TOPIC_NAME="${GREETING_TEMPLATES_TOPIC:-greeting-templates}"
SEED_FILE="/redpanda-seed/greeting-templates-seed.jsonl"
TRANSACTIONS_TOPIC="${TRANSACTIONS_TOPIC:-transacoes-financeiras-processadas}"
TRANSACTIONS_DLT_TOPIC="${TRANSACTIONS_DLT_TOPIC:-${TRANSACTIONS_TOPIC}.DLT}"
TRANSACTIONS_PARTITIONS="${TRANSACTIONS_PARTITIONS:-6}"

create_topic() {
  local name="$1"
  local partitions="$2"

  if rpk topic describe "${name}" --brokers "${BROKERS}" >/dev/null 2>&1; then
    echo "Topic '${name}' already exists, skipping creation."
  else
    echo "Creating topic '${name}' with ${partitions} partition(s)..."
    rpk topic create "${name}" --brokers "${BROKERS}" --partitions "${partitions}" --replicas 1
  fi
}

echo "Waiting for Redpanda broker at ${BROKERS}..."
until rpk cluster info --brokers "${BROKERS}" >/dev/null 2>&1; do
  echo "  not ready yet, retrying in 2s..."
  sleep 2
done
echo "Redpanda broker is ready."

create_topic "${TOPIC_NAME}" 1
create_topic "${TRANSACTIONS_TOPIC}" "${TRANSACTIONS_PARTITIONS}"
create_topic "${TRANSACTIONS_DLT_TOPIC}" "${TRANSACTIONS_PARTITIONS}"

echo "Publishing seed messages from ${SEED_FILE}..."
rpk topic produce "${TOPIC_NAME}" --brokers "${BROKERS}" -f '%v\n' < "${SEED_FILE}"

COUNT=$(wc -l < "${SEED_FILE}" | tr -d ' ')
echo "Seed complete. Published ${COUNT} message(s) to '${TOPIC_NAME}'."
