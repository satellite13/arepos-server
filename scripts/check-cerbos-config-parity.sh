#!/bin/bash

set -euo pipefail

LOCAL_VALUES_FILE="${LOCAL_VALUES_FILE:-deploy-values.yaml}"
INFRA_VALUES_FILE="${INFRA_VALUES_FILE:-}"
CHART_PATH="${CHART_PATH:-charts/arepos-server}"
NAMESPACE="${NAMESPACE:-arch}"
INCLUDE_ENDPOINT="${INCLUDE_ENDPOINT:-false}"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_error "Команда '$1' не найдена"
    exit 1
  fi
}

if [ -z "$INFRA_VALUES_FILE" ]; then
  log_error "Нужно указать INFRA_VALUES_FILE=<path-to-infra-values.yaml>"
  exit 1
fi

if [ ! -f "$LOCAL_VALUES_FILE" ]; then
  log_error "Локальный values-файл не найден: $LOCAL_VALUES_FILE"
  exit 1
fi

if [ ! -f "$INFRA_VALUES_FILE" ]; then
  log_error "Infra values-файл не найден: $INFRA_VALUES_FILE"
  exit 1
fi

require_command helm
require_command awk
require_command sed

render_chart() {
  local values_file="$1"
  helm template parity-check "$CHART_PATH" -n "$NAMESPACE" -f "$values_file"
}

extract_env_value() {
  local rendered="$1"
  local key="$2"

  local raw
  raw="$(printf "%s\n" "$rendered" | awk -v k="$key" '
    $0 ~ "name: "k"$" { found=1; next }
    found == 1 && $0 ~ "value:" {
      sub(/^[[:space:]]*value:[[:space:]]*/, "", $0)
      gsub(/^"|"$/, "", $0)
      if (length($0) == 0) {
        print "__EMPTY__"
      } else {
        print $0
      }
      exit
    }
  ')"

  printf "%s" "$raw"
}

LOCAL_RENDERED="$(render_chart "$LOCAL_VALUES_FILE")"
INFRA_RENDERED="$(render_chart "$INFRA_VALUES_FILE")"

PARITY_KEYS=(
  "AREPOS_AUTHZ_CERBOS_REQUEST_TIMEOUT"
  "AREPOS_AUTHZ_CERBOS_BUNDLE_VERSION"
)

if [ "$INCLUDE_ENDPOINT" = "true" ]; then
  PARITY_KEYS+=("AREPOS_AUTHZ_CERBOS_ENDPOINT")
fi

echo "=== Cerbos Config Parity Check ==="
echo "local values: $LOCAL_VALUES_FILE"
echo "infra values: $INFRA_VALUES_FILE"
echo "chart:        $CHART_PATH"
echo ""

MISMATCHES=0
for key in "${PARITY_KEYS[@]}"; do
  local_value="$(extract_env_value "$LOCAL_RENDERED" "$key")"
  infra_value="$(extract_env_value "$INFRA_RENDERED" "$key")"

  if [ "$local_value" = "__EMPTY__" ]; then
    local_value=""
  fi
  if [ "$infra_value" = "__EMPTY__" ]; then
    infra_value=""
  fi

  if [ -z "$local_value" ] && [ -z "$infra_value" ]; then
    echo "OK   $key = \"\""
    continue
  fi

  if [ -z "$local_value" ] || [ -z "$infra_value" ]; then
    log_warn "$key: значение не найдено в одном из rendered manifests"
    MISMATCHES=$((MISMATCHES + 1))
    continue
  fi

  if [ "$local_value" = "$infra_value" ]; then
    echo "OK   $key = $local_value"
  else
    echo "DIFF $key"
    echo "     local: $local_value"
    echo "     infra: $infra_value"
    MISMATCHES=$((MISMATCHES + 1))
  fi
done

echo ""
if [ "$MISMATCHES" -gt 0 ]; then
  log_error "Parity check не пройден: несовпадений = $MISMATCHES"
  exit 2
fi

log_info "Parity check пройден: ключевые Cerbos-параметры совпадают"
