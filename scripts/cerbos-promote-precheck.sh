#!/bin/bash

set -euo pipefail

NAMESPACE="${NAMESPACE:-arch}"
SERVICE_NAME="${SERVICE_NAME:-arepos-server}"
LOCAL_VALUES_FILE="${LOCAL_VALUES_FILE:-deploy-values.yaml}"
INFRA_VALUES_FILE="${INFRA_VALUES_FILE:-}"
MODE="${MODE:-baseline}"
BASELINE_FILE="${BASELINE_FILE:-.cerbos-shadow-baseline.env}"
MAX_MISMATCH="${MAX_MISMATCH:-0}"
MAX_ERRORS="${MAX_ERRORS:-0}"
MIN_MATCH_RATE="${MIN_MATCH_RATE:-99.9}"
INCLUDE_ENDPOINT="${INCLUDE_ENDPOINT:-false}"

SHADOW_SCRIPT="${SHADOW_SCRIPT:-./scripts/check-cerbos-shadow.sh}"
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

if [ ! -x "$SHADOW_SCRIPT" ]; then
  log_error "Не найден исполняемый shadow script: $SHADOW_SCRIPT"
  exit 1
fi

if [ ! -x "$PARITY_SCRIPT" ]; then
  log_error "Не найден исполняемый parity script: $PARITY_SCRIPT"
  exit 1
fi

log_info "Шаг 1/2: shadow gate precheck"
MODE="$MODE" \
BASELINE_FILE="$BASELINE_FILE" \
MAX_MISMATCH="$MAX_MISMATCH" \
MAX_ERRORS="$MAX_ERRORS" \
MIN_MATCH_RATE="$MIN_MATCH_RATE" \
NAMESPACE="$NAMESPACE" \
SERVICE_NAME="$SERVICE_NAME" \
"$SHADOW_SCRIPT"

log_info "Шаг 2/2: config parity precheck"
LOCAL_VALUES_FILE="$LOCAL_VALUES_FILE" \
INFRA_VALUES_FILE="$INFRA_VALUES_FILE" \
NAMESPACE="$NAMESPACE" \
INCLUDE_ENDPOINT="$INCLUDE_ENDPOINT" \
"$PARITY_SCRIPT"

log_info "Promote precheck пройден: shadow gate + parity check OK"
