#!/bin/bash

set -euo pipefail

NAMESPACE="${NAMESPACE:-arch}"
SERVICE_NAME="${SERVICE_NAME:-arepos-server}"
AUTH_TOKEN="${AUTH_TOKEN:-}"
MODE="${MODE:-pod}" # pod | baseline
BASELINE_FILE="${BASELINE_FILE:-.cerbos-shadow-baseline.env}"
WRITE_BASELINE="${WRITE_BASELINE:-false}"
MAX_MISMATCH="${MAX_MISMATCH:-0}"
MAX_ERRORS="${MAX_ERRORS:-0}"
MIN_MATCH_RATE="${MIN_MATCH_RATE:-99.9}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

get_app_pod() {
  kubectl get pods -n "$NAMESPACE" \
    | rg "^${SERVICE_NAME}-" \
    | rg -v "minio|postgresql|cerbos|Completed|Terminating" \
    | awk '{print $1}' \
    | head -n 1
}

fetch_token_if_needed() {
  if [ -n "$AUTH_TOKEN" ]; then
    return
  fi
  local ts
  ts="$(date +%s)"
  local email="shadow-check-${ts}@example.com"
  log_info "AUTH_TOKEN не передан, создаю временного пользователя: ${email}"
  AUTH_TOKEN="$(kubectl run -n "$NAMESPACE" shadow-token --rm -i --restart=Never --image=curlimages/curl:8.7.1 --command -- sh -c "curl -sS -X POST http://${SERVICE_NAME}:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{\"email\":\"${email}\",\"password\":\"Passw0rd!\",\"firstName\":\"Shadow\",\"lastName\":\"Check\"}'" | sed -n 's/.*\"accessToken\":\"\([^\"]*\)\".*/\1/p')"
  if [ -z "$AUTH_TOKEN" ]; then
    log_error "Не удалось получить AUTH_TOKEN"
    exit 1
  fi
}

fetch_metrics() {
  kubectl run -n "$NAMESPACE" shadow-metrics --rm -i --restart=Never --image=curlimages/curl:8.7.1 --command -- sh -c \
    "curl -sS -H 'Authorization: Bearer ${AUTH_TOKEN}' http://${SERVICE_NAME}:8080/actuator/prometheus"
}

sum_metric_values_from_stream() {
  local pattern="$1"
  awk -v p="$pattern" '$0 ~ p {sum += $NF} END {printf "%.0f", sum+0}'
}

metrics_snapshot() {
  local metrics="$1"
  local matches mismatches ok err
  matches="$(echo "$metrics" | sum_metric_values_from_stream "^arepos_authz_shadow_compare_total\\{.*match=\"true\"")"
  mismatches="$(echo "$metrics" | sum_metric_values_from_stream "^arepos_authz_shadow_compare_total\\{.*match=\"false\"")"
  ok="$(echo "$metrics" | sum_metric_values_from_stream "^arepos_authz_cerbos_request_seconds_count\\{.*outcome=\"ok\"")"
  err="$(echo "$metrics" | sum_metric_values_from_stream "^arepos_authz_cerbos_request_seconds_count\\{.*outcome=\"error\"")"
  cat <<EOF
MATCHES=$matches
MISMATCHES=$mismatches
OK=$ok
ERRORS=$err
EOF
}

count_log_events_since() {
  local app_pod="$1"
  local since_time="$2"
  local kind="$3"

  if [ "$kind" = "error" ]; then
    kubectl logs -n "$NAMESPACE" "$app_pod" --since-time "$since_time" 2>/dev/null \
      | rg -c "Cerbos check failed" || true
    return
  fi

  if [ "$kind" = "mismatch" ]; then
    kubectl logs -n "$NAMESPACE" "$app_pod" --since-time "$since_time" 2>/dev/null \
      | rg -c "Cerbos shadow mismatch" || true
    return
  fi

  echo "0"
}

main() {
  require_command kubectl
  require_command awk
  require_command sed
  require_command rg

  local app_pod pod_start
  app_pod="$(get_app_pod)"
  if [ -z "$app_pod" ]; then
    log_error "Не найден pod приложения ${SERVICE_NAME} в namespace ${NAMESPACE}"
    exit 1
  fi
  pod_start="$(kubectl get pod -n "$NAMESPACE" "$app_pod" -o jsonpath='{.status.startTime}')"

  fetch_token_if_needed
  local metrics
  metrics="$(fetch_metrics)"
  local snapshot
  snapshot="$(metrics_snapshot "$metrics")"
  eval "$snapshot"

  local match_count mismatch_count error_count ok_count window_label
  match_count="$MATCHES"
  mismatch_count="$MISMATCHES"
  error_count="$ERRORS"
  ok_count="$OK"
  window_label="since pod start ($pod_start)"

  if [ "$MODE" = "baseline" ]; then
    if [ ! -f "$BASELINE_FILE" ]; then
      log_error "MODE=baseline, но файл бейзлайна не найден: $BASELINE_FILE"
      log_warn "Создайте его командой: WRITE_BASELINE=true ./scripts/check-cerbos-shadow.sh"
      exit 1
    fi
    # shellcheck disable=SC1090
    source "$BASELINE_FILE"
    match_count=$(( MATCHES - MATCHES_BASELINE ))
    mismatch_count=$(( MISMATCHES - MISMATCHES_BASELINE ))
    ok_count=$(( OK - OK_BASELINE ))
    error_count=$(( ERRORS - ERRORS_BASELINE ))
    if [ "$match_count" -lt 0 ]; then match_count=0; fi
    if [ "$mismatch_count" -lt 0 ]; then mismatch_count=0; fi
    if [ "$ok_count" -lt 0 ]; then ok_count=0; fi
    if [ "$error_count" -lt 0 ]; then error_count=0; fi
    window_label="since baseline (${BASELINE_TS:-unknown})"
  fi

  # Log-based window diagnostics (useful when counters contain historical noise).
  local log_error_count log_mismatch_count
  log_error_count="$(count_log_events_since "$app_pod" "$pod_start" "error")"
  log_mismatch_count="$(count_log_events_since "$app_pod" "$pod_start" "mismatch")"
  log_error_count="${log_error_count:-0}"
  log_mismatch_count="${log_mismatch_count:-0}"

  local total_checks
  total_checks=$((match_count + mismatch_count))
  local match_rate="0.00"
  if [ "$total_checks" -gt 0 ]; then
    match_rate="$(awk -v m="$match_count" -v t="$total_checks" 'BEGIN { printf "%.2f", (m/t)*100 }')"
  fi

  echo "=== Cerbos Shadow Report ==="
  echo "namespace: $NAMESPACE"
  echo "service:   $SERVICE_NAME"
  echo "pod:       $app_pod"
  echo "window:    $window_label"
  echo "matches:   $match_count"
  echo "mismatch:  $mismatch_count"
  echo "cerbos ok: $ok_count"
  echo "cerbos err:$error_count"
  echo "match rate: ${match_rate}%"
  echo "logs since pod start: errors=$log_error_count mismatch=$log_mismatch_count"

  if [ "$total_checks" -eq 0 ]; then
    log_warn "Нет данных shadow_compare (еще не было релевантного трафика)"
  fi

  if [ "$WRITE_BASELINE" = "true" ]; then
    cat > "$BASELINE_FILE" <<EOF
BASELINE_TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
BASELINE_POD=$app_pod
BASELINE_POD_START=$pod_start
MATCHES_BASELINE=$MATCHES
MISMATCHES_BASELINE=$MISMATCHES
OK_BASELINE=$OK
ERRORS_BASELINE=$ERRORS
EOF
    log_info "Бейзлайн сохранён в $BASELINE_FILE"
  fi

  local gate_failed="false"
  if [ "$mismatch_count" -gt "$MAX_MISMATCH" ]; then
    log_warn "Порог mismatch превышен: ${mismatch_count} > ${MAX_MISMATCH}"
    gate_failed="true"
  fi
  if [ "$error_count" -gt "$MAX_ERRORS" ]; then
    log_warn "Порог errors превышен: ${error_count} > ${MAX_ERRORS}"
    gate_failed="true"
  fi
  if [ "$total_checks" -gt 0 ] && awk -v rate="$match_rate" -v min="$MIN_MATCH_RATE" 'BEGIN { exit !(rate < min) }'; then
    log_warn "Match rate ниже порога: ${match_rate}% < ${MIN_MATCH_RATE}%"
    gate_failed="true"
  fi

  if [ "$gate_failed" = "true" ]; then
    log_warn "Gate не пройден — enforce включать рано"
    exit 2
  fi

  log_info "Gate пройден — по текущему окну можно рассматривать этап enforce"
}

main "$@"
