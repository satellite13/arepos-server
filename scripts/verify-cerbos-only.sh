#!/bin/bash
#
# Статическая проверка Cerbos-only авторизации: отсутствие role-bypass в контроллерах,
# legacy-флагов Cerbos, наличие policy-файлов; компиляция и Cerbos-фокусные unit-тесты.
#
# Примеры:
#   ./scripts/verify-cerbos-only.sh
#
# Требования: rg, bash, ./gradlew.

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_error "Command '$1' is required"
    exit 1
  fi
}

assert_no_matches() {
  local pattern="$1"
  local path="$2"
  local description="$3"
  if rg -n "$pattern" "$path" >/dev/null; then
    log_error "Failed: $description"
    rg -n "$pattern" "$path"
    exit 1
  fi
  log_info "OK: $description"
}

assert_file_exists() {
  local path="$1"
  if [ ! -f "$path" ]; then
    log_error "Missing file: $path"
    exit 1
  fi
  log_info "OK: file exists $path"
}

main() {
  require_command rg
  require_command bash

  log_info "Checking role-based bypass in controllers..."
  assert_no_matches "CurrentUser\\.isAdmin\\(|hasRole\\('ADMIN'\\)|@PreAuthorize\\(\"hasRole\\('ADMIN'\\)\"" \
    "src/main/kotlin/ru/kavader/arepos/controller" \
    "no admin role bypass in controllers"

  log_info "Checking deprecated Cerbos runtime flags..."
  assert_no_matches "cerbos\\.mode|AREPOS_AUTHZ_CERBOS_MODE|cerbosShadowEnabled|cerbosEnforceEnabled|cerbosFailOpen|fail-open|CerbosMode" \
    "src/main" \
    "no legacy cerbos mode/shadow/fail-open flags in runtime code"
  assert_no_matches "cerbos\\.mode|AREPOS_AUTHZ_CERBOS_MODE|cerbosShadowEnabled|cerbosEnforceEnabled|cerbosFailOpen|fail-open" \
    "charts" \
    "no legacy cerbos mode/shadow/fail-open flags in charts"
  assert_no_matches "cerbos\\.mode|cerbosShadowEnabled|cerbosEnforceEnabled|cerbosFailOpen|fail-open|CERBOS_SHADOW|CERBOS_ENFORCE|CERBOS_OFF" \
    "deploy.sh" \
    "no legacy cerbos mode/shadow/fail-open flags in deploy.sh"
  assert_no_matches "cerbos\\.mode|cerbosShadowEnabled|cerbosEnforceEnabled|cerbosFailOpen|fail-open|CERBOS_SHADOW|CERBOS_ENFORCE|CERBOS_OFF" \
    "scripts/release-cerbos-policies.sh" \
    "no legacy cerbos mode/shadow/fail-open flags in release script"
  assert_no_matches "cerbos\\.mode|cerbosShadowEnabled|cerbosEnforceEnabled|cerbosFailOpen|fail-open|CERBOS_SHADOW|CERBOS_ENFORCE|CERBOS_OFF" \
    "scripts/cerbos-promote-precheck.sh" \
    "no legacy cerbos mode/shadow/fail-open flags in promote precheck script"
  assert_no_matches "cerbos\\.mode|cerbosShadowEnabled|cerbosEnforceEnabled|cerbosFailOpen|fail-open|CERBOS_SHADOW|CERBOS_ENFORCE|CERBOS_OFF" \
    "scripts/check-cerbos-config-parity.sh" \
    "no legacy cerbos mode/shadow/fail-open flags in parity script"
  assert_no_matches "cerbos\\.mode|cerbosShadowEnabled|cerbosEnforceEnabled|cerbosFailOpen|fail-open|CERBOS_SHADOW|CERBOS_ENFORCE|CERBOS_OFF" \
    "scripts/cerbos-rollback.sh" \
    "no legacy cerbos mode/shadow/fail-open flags in rollback script"

  log_info "Checking required Cerbos policy files..."
  assert_file_exists "authz/cerbos/policies/resource.model.yaml"
  assert_file_exists "authz/cerbos/policies/resource.notation.yaml"
  assert_file_exists "authz/cerbos/policies/resource.node_type.yaml"
  assert_file_exists "authz/cerbos/policies/resource.link_type.yaml"
  assert_file_exists "authz/cerbos/policies/resource.node_shape.yaml"
  assert_file_exists "authz/cerbos/policies/resource.file.yaml"
  assert_file_exists "authz/cerbos/policies/resource.share.yaml"
  assert_file_exists "authz/cerbos/policies/resource.admin_panel.yaml"
  assert_file_exists "authz/cerbos/policies/resource.user_admin.yaml"

  log_info "Compiling Kotlin sources..."
  ./gradlew compileKotlin compileTestKotlin

  log_info "Running Cerbos-focused tests..."
  ./gradlew test --tests "*CerbosAuthzModelTest" --tests "*CerbosDecisionServiceTest"

  log_info "Cerbos-only verification passed."
}

main "$@"
