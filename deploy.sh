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
VALUES_FILE="${VALUES_FILE:-deploy-values.yaml}"
POSTGRESQL_ENABLED="${POSTGRESQL_ENABLED:-true}"
BUILD_IMAGE="${BUILD_IMAGE:-true}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-300}"

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

check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "$1 не установлен"
        exit 1
    fi
}

# Проверка необходимых команд
log_info "Проверка необходимых команд..."
check_command kubectl
check_command helm
check_command docker

# Проверка подключения к кластеру
log_info "Проверка подключения к Kubernetes кластеру..."
if ! kubectl cluster-info &> /dev/null; then
    log_error "Не удалось подключиться к Kubernetes кластеру"
    exit 1
fi

# Создание namespace, если не существует
log_info "Проверка namespace '$NAMESPACE'..."
if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
    log_info "Создание namespace '$NAMESPACE'..."
    kubectl create namespace "$NAMESPACE"
fi

# Сборка Docker образа
if [ "$BUILD_IMAGE" = "true" ]; then
    log_info "Сборка Docker образа..."
    ./gradlew bootBuildImage
    if [ $? -ne 0 ]; then
        log_error "Ошибка при сборке Docker образа"
        exit 1
    fi
    log_info "Docker образ успешно собран"
else
    log_warn "Пропуск сборки Docker образа (BUILD_IMAGE=false)"
fi

# Удаление предыдущего развертывания, если существует
if helm list -n "$NAMESPACE" | grep -q "$RELEASE_NAME"; then
    log_warn "Найдено существующее развертывание '$RELEASE_NAME'. Удаление..."
    helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" || true
    sleep 5
fi

# Удаление PVC, если существует
if kubectl get pvc -n "$NAMESPACE" | grep -q "postgresql-data"; then
    log_warn "Удаление существующего PVC..."
    kubectl delete pvc -n "$NAMESPACE" arepos-server-postgresql-data || true
    sleep 3
fi

# Развертывание через Helm
log_info "Развертывание приложения через Helm..."
HELM_CMD="helm install $RELEASE_NAME charts/arepos-server -n $NAMESPACE"

if [ -f "$VALUES_FILE" ]; then
    HELM_CMD="$HELM_CMD -f $VALUES_FILE"
    log_info "Использование файла значений: $VALUES_FILE"
fi

if [ "$POSTGRESQL_ENABLED" = "true" ]; then
    HELM_CMD="$HELM_CMD --set postgresql.enabled=true"
    log_info "PostgreSQL будет развернут вместе с приложением"
fi

eval $HELM_CMD

if [ $? -ne 0 ]; then
    log_error "Ошибка при развертывании через Helm"
    exit 1
fi

log_info "Ожидание запуска подов..."
sleep 10

# Ожидание готовности подов
log_info "Ожидание готовности приложения (таймаут: ${WAIT_TIMEOUT}с)..."
TIMEOUT=$WAIT_TIMEOUT
ELAPSED=0
INTERVAL=5

while [ $ELAPSED -lt $TIMEOUT ]; do
    READY=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=arepos-server -o jsonpath='{.items[0].status.containerStatuses[0].ready}' 2>/dev/null || echo "false")
    
    if [ "$READY" = "true" ]; then
        log_info "Приложение готово!"
        break
    fi
    
    ELAPSED=$((ELAPSED + INTERVAL))
    REMAINING=$((TIMEOUT - ELAPSED))
    if [ $REMAINING -gt 0 ]; then
        echo -n "."
        sleep $INTERVAL
    fi
done

echo ""

if [ "$READY" != "true" ]; then
    log_error "Таймаут ожидания готовности приложения"
    log_info "Текущий статус подов:"
    kubectl get pods -n "$NAMESPACE"
    exit 1
fi

# Проверка статуса подов
log_info "Статус подов:"
kubectl get pods -n "$NAMESPACE"

# Проверка Health Check
log_info "Проверка Health Check..."
sleep 5
HEALTH=$(curl -s http://arepos-server.$NAMESPACE.svc.cluster.local:8080/actuator/health 2>/dev/null || echo "")

if [ -n "$HEALTH" ]; then
    STATUS=$(echo "$HEALTH" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
    if [ "$STATUS" = "UP" ]; then
        log_info "Health Check: ✅ UP"
    else
        log_warn "Health Check: ⚠️  $STATUS"
    fi
else
    log_warn "Не удалось проверить Health Check"
fi

# Вывод информации о доступе
log_info "Приложение развернуто!"
echo ""
echo "Доступ к API:"
echo "  http://arepos-server.$NAMESPACE.svc.cluster.local:8080"
echo ""
echo "Просмотр логов:"
echo "  kubectl logs -n $NAMESPACE -l app.kubernetes.io/name=arepos-server"
echo ""
echo "Порт-форвард для локального доступа:"
echo "  kubectl port-forward -n $NAMESPACE svc/arepos-server 8080:8080"
echo ""

