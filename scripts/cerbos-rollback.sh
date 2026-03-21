#!/bin/bash

set -euo pipefail

TARGET="${TARGET:-local}" # local | infra
MODE="${MODE:-shadow}" # shadow | off
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
  local args=""
  if [ "$MODE" = "off" ]; then
    args="$args --set cerbos.enabled=false"
    args="$args --set cerbos.deploy=false"
    args="$args --set-string cerbos.mode=DISABLED"
    args="$args --set authz.cerbosShadowEnabled=false"
    args="$args --set authz.cerbosEnforceEnabled=false"
  else
    args="$args --set cerbos.enabled=true"
    args="$args --set-string cerbos.mode=SHADOW"
    args="$args --set authz.cerbosShadowEnabled=true"
    args="$args --set authz.cerbosEnforceEnabled=false"
    if [ -n "$BUNDLE_VERSION" ]; then
      args="$args --set-string cerbos.bundleVersion=$BUNDLE_VERSION"
    fi
  fi
  printf "%s" "$args"
}

if [ "$TARGET" != "local" ] && [ "$TARGET" != "infra" ]; then
  log_error "TARGET должен быть local|infra"
  exit 1
fi

if [ "$MODE" != "shadow" ] && [ "$MODE" != "off" ]; then
  log_error "MODE должен быть shadow|off"
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
