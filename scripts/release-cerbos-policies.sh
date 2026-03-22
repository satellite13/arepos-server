#!/bin/bash

set -euo pipefail

POLICY_DIR="${POLICY_DIR:-authz/cerbos/policies}"
RELEASE_DIR="${RELEASE_DIR:-authz/cerbos/releases}"
BUNDLE_VERSION="${BUNDLE_VERSION:-policy-$(git rev-parse --short HEAD)}"
DRY_RUN="${DRY_RUN:-false}"
ALLOW_DIRTY="${ALLOW_DIRTY:-false}"
DEPLOY_TARGET="${DEPLOY_TARGET:-none}" # none | local | prod
SKIP_POLICY_TESTS="${SKIP_POLICY_TESTS:-true}"
CERBOS_IMAGE="${CERBOS_IMAGE:-ghcr.io/cerbos/cerbos:latest}"

# Used only when DEPLOY_TARGET=local
NAMESPACE="${NAMESPACE:-arch}"
RELEASE_NAME="${RELEASE_NAME:-arepos-server}"
VALUES_FILE="${VALUES_FILE:-deploy-values.yaml}"

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

check_git_clean() {
  if [ "$ALLOW_DIRTY" = "true" ]; then
    return
  fi
  if [ -n "$(git status --porcelain)" ]; then
    log_error "Рабочее дерево не чистое. Зафиксируйте изменения или запустите с ALLOW_DIRTY=true"
    exit 1
  fi
}

compile_policies() {
  log_info "Валидация policy через cerbos compile..."
  local compile_args
  if [ "$SKIP_POLICY_TESTS" = "true" ]; then
    compile_args="--skip-tests"
  else
    compile_args=""
  fi

  if command -v cerbos >/dev/null 2>&1; then
    cerbos compile "$POLICY_DIR" $compile_args
    return
  fi

  if command -v docker >/dev/null 2>&1; then
    log_warn "cerbos CLI не найден, использую Docker image ${CERBOS_IMAGE}"
    docker run --rm -v "$PWD":/work -w /work "$CERBOS_IMAGE" compile "$POLICY_DIR" $compile_args
    return
  fi

  log_error "Не найден ни cerbos CLI, ни docker для валидации policy"
  exit 1
}

build_bundle() {
  mkdir -p "$RELEASE_DIR"
  local archive="$RELEASE_DIR/${BUNDLE_VERSION}.tar.gz"
  tar -czf "$archive" -C "$(dirname "$POLICY_DIR")" "$(basename "$POLICY_DIR")"
  shasum -a 256 "$archive" > "${archive}.sha256"
  log_info "Собран bundle: $archive"
  log_info "SHA256 записан: ${archive}.sha256"
}

sync_chart_policies() {
  local chart_policy_dir="charts/arepos-server/cerbos/policies"
  mkdir -p "$chart_policy_dir"
  cp "$POLICY_DIR"/*.yaml "$chart_policy_dir"/
  log_info "Синхронизированы policy в Helm chart: $chart_policy_dir"
}

deploy_local() {
  local extra_args="--set cerbos.enabled=true --set cerbos.deploy=true --set-string cerbos.bundleVersion=${BUNDLE_VERSION}"
  log_info "Локальный deploy через deploy.sh (enforce-only)..."
  if [ "$DRY_RUN" = "true" ]; then
    log_warn "DRY_RUN=true, команда не выполнена:"
    echo "HELM_EXTRA_ARGS=\"$extra_args\" NAMESPACE=$NAMESPACE RELEASE_NAME=$RELEASE_NAME VALUES_FILE=$VALUES_FILE ./deploy.sh"
    return
  fi
  HELM_EXTRA_ARGS="$extra_args" NAMESPACE="$NAMESPACE" RELEASE_NAME="$RELEASE_NAME" VALUES_FILE="$VALUES_FILE" ./deploy.sh
}

print_prod_instructions() {
  log_info "Production (infra / Yandex Cloud): используйте bundleVersion=${BUNDLE_VERSION}"
  echo
  echo "Рекомендуемые параметры для infra:"
  echo "  cerbos.enabled=true"
  echo "  cerbos.deploy=true"
  echo "  cerbos.bundleVersion=${BUNDLE_VERSION}"
}

main() {
  require_command git
  require_command tar
  require_command shasum

  if [ ! -d "$POLICY_DIR" ]; then
    log_error "Каталог policy не найден: $POLICY_DIR"
    exit 1
  fi

  check_git_clean
  compile_policies
  build_bundle
  sync_chart_policies

  case "$DEPLOY_TARGET" in
    none)
      log_info "DEPLOY_TARGET=none, деплой пропущен"
      ;;
    local)
      deploy_local
      ;;
    prod)
      print_prod_instructions
      ;;
    *)
      log_error "Неизвестный DEPLOY_TARGET: $DEPLOY_TARGET (ожидается none|local|prod)"
      exit 1
      ;;
  esac
}

main "$@"
