#!/bin/bash

set -euo pipefail

NAMESPACE="${NAMESPACE:-arch}"
LOCAL_VALUES_FILE="${LOCAL_VALUES_FILE:-deploy-values.yaml}"
INFRA_VALUES_FILE="${INFRA_VALUES_FILE:-}"
INCLUDE_ENDPOINT="${INCLUDE_ENDPOINT:-false}"

PARITY_SCRIPT="${PARITY_SCRIPT:-./scripts/check-cerbos-config-parity.sh}"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

if [ -z "$INFRA_VALUES_FILE" ]; then
  log_error "Укажите INFRA_VALUES_FILE=<path-to-infra-values.yaml>"
  exit 1
fi

if [ ! -x "$PARITY_SCRIPT" ]; then
  log_error "Не найден исполняемый parity script: $PARITY_SCRIPT"
  exit 1
fi

log_info "Шаг 1/1: config parity precheck"
LOCAL_VALUES_FILE="$LOCAL_VALUES_FILE" \
INFRA_VALUES_FILE="$INFRA_VALUES_FILE" \
NAMESPACE="$NAMESPACE" \
INCLUDE_ENDPOINT="$INCLUDE_ENDPOINT" \
"$PARITY_SCRIPT"

log_info "Promote precheck пройден: parity check OK"
