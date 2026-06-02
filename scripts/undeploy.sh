#!/bin/bash

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Параметры по умолчанию
NAMESPACE="${NAMESPACE:-arch}"
RELEASE_NAME="${RELEASE_NAME:-arepos-server}"
DELETE_PVC="${DELETE_PVC:-false}"

# Функции
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Проверка подключения к кластеру
log_info "Проверка подключения к Kubernetes кластеру..."
if ! kubectl cluster-info &> /dev/null; then
    log_error "Не удалось подключиться к Kubernetes кластеру"
    exit 1
fi

# Удаление Helm release
if helm list -n "$NAMESPACE" | grep -q "$RELEASE_NAME"; then
    log_info "Удаление Helm release '$RELEASE_NAME'..."
    helm uninstall "$RELEASE_NAME" -n "$NAMESPACE"
    log_info "Helm release удален"
else
    log_warn "Helm release '$RELEASE_NAME' не найден"
fi

# Удаление PVC, если указано
if [ "$DELETE_PVC" = "true" ]; then
    log_info "Удаление PVC..."
    kubectl delete pvc -n "$NAMESPACE" arepos-server-postgresql-data 2>/dev/null || log_warn "PVC не найден или уже удален"
fi

log_info "Удаление завершено!"

