#!/bin/bash
#
# Откат Cerbos на указанную версию policy bundle: печатает или выполняет команду
# deploy/helm upgrade с cerbos.enabled=true и cerbos.bundleVersion.
#
# Примеры:
#   BUNDLE_VERSION=policy-abc1234 ./scripts/cerbos-rollback.sh
#   TARGET=local BUNDLE_VERSION=policy-abc1234 PRINT_ONLY=false ./scripts/cerbos-rollback.sh
#   TARGET=infra BUNDLE_VERSION=policy-abc1234 APPLY=true VALUES_FILE=../infra/values.yaml ./scripts/cerbos-rollback.sh
#
# Переменные: TARGET (local|infra), BUNDLE_VERSION, PRINT_ONLY (true), APPLY (false),
# NAMESPACE, RELEASE_NAME, VALUES_FILE, CHART_PATH.

set -euo pipefail

TARGET="${TARGET:-local}" # local | infra
BUNDLE_VERSION="${BUNDLE_VERSION:-}"

PRINT_ONLY="${PRINT_ONLY:-true}"
APPLY="${APPLY:-false}"

NAMESPACE="${NAMESPACE:-arch}"
RELEASE_NAME="${RELEASE_NAME:-arepos-server}"
VALUES_FILE="${VALUES_FILE:-deploy-values.yaml}"
CHART_PATH="${CHART_PATH:-charts/arepos-server}"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

build_helm_set_args() {
  local args=" --set cerbos.enabled=true --set cerbos.deploy=true"
  if [ -n "$BUNDLE_VERSION" ]; then
    args="$args --set-string cerbos.bundleVersion=$BUNDLE_VERSION"
  fi
  printf "%s" "$args"
}

if [ "$TARGET" != "local" ] && [ "$TARGET" != "infra" ]; then
  log_error "TARGET должен быть local|infra"
  exit 1
fi

SET_ARGS="$(build_helm_set_args)"

if [ "$TARGET" = "local" ]; then
  CMD="NAMESPACE=$NAMESPACE RELEASE_NAME=$RELEASE_NAME VALUES_FILE=$VALUES_FILE HELM_EXTRA_ARGS=\"$SET_ARGS\" ./deploy.sh"
  log_info "Local rollback command:"
  echo "$CMD"
  if [ "$PRINT_ONLY" = "false" ]; then
    eval "$CMD"
  fi
  exit 0
fi

CMD="helm upgrade --install $RELEASE_NAME $CHART_PATH -n $NAMESPACE -f $VALUES_FILE $SET_ARGS"
log_info "Infra rollback command:"
echo "$CMD"
if [ "$APPLY" = "true" ]; then
  eval "$CMD"
fi
