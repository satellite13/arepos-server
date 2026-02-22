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
KEEP_POSTGRES_VOLUME="${KEEP_POSTGRES_VOLUME:-false}"
AUTH_SECRET_NAME="${AUTH_SECRET_NAME:-arepos-server-auth-secret}"
DEPLOY_IMAGE_PULL_POLICY="${DEPLOY_IMAGE_PULL_POLICY:-IfNotPresent}"

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
check_command curl
check_command jq

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

# Создание секрета для JWT и Admin Secret
log_info "Проверка секрета аутентификации '$AUTH_SECRET_NAME'..."
if ! kubectl get secret "$AUTH_SECRET_NAME" -n "$NAMESPACE" &> /dev/null; then
    if [ -z "$JWT_SECRET" ]; then
        JWT_SECRET=$(openssl rand -base64 48)
        log_warn "JWT_SECRET не задан, сгенерирован случайный ключ"
    fi
    if [ -z "$ADMIN_SECRET" ]; then
        ADMIN_SECRET=$(openssl rand -base64 32)
        log_warn "ADMIN_SECRET не задан, сгенерирован случайный ключ"
        log_warn "Сохраните ADMIN_SECRET для создания администраторов: $ADMIN_SECRET"
    fi
    log_info "Создание секрета '$AUTH_SECRET_NAME'..."
    kubectl create secret generic "$AUTH_SECRET_NAME" \
        -n "$NAMESPACE" \
        --from-literal=jwt-secret="$JWT_SECRET" \
        --from-literal=admin-secret="$ADMIN_SECRET"
else
    log_info "Секрет '$AUTH_SECRET_NAME' уже существует"
fi

# Определение тега образа
GIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "latest")
APP_VERSION=$(grep '^version' build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/' || echo "0.0.1-SNAPSHOT")
IMAGE_TAG="${APP_VERSION}-${GIT_HASH}"
IMAGE_NAME="arch/arepos-server"
EXPECTED_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
DEPLOY_BUILD_ID="${IMAGE_TAG}-$(date +%s)"
log_info "Тег образа: ${IMAGE_TAG}"
log_info "Build ID деплоя: ${DEPLOY_BUILD_ID}"
log_info "Image pull policy: ${DEPLOY_IMAGE_PULL_POLICY}"

# Сборка Docker образа
if [ "$BUILD_IMAGE" = "true" ]; then
    log_info "Сборка Docker образа..."
    ./gradlew bootBuildImage
    if [ $? -ne 0 ]; then
        log_error "Ошибка при сборке Docker образа"
        exit 1
    fi
    # Дополнительный тег с git hash
    docker tag "${IMAGE_NAME}:${APP_VERSION}" "${EXPECTED_IMAGE}"
    if ! docker image inspect "${EXPECTED_IMAGE}" > /dev/null 2>&1; then
        log_error "Не найден локальный образ ${EXPECTED_IMAGE} после тегирования"
        exit 1
    fi
    log_info "Docker образ собран и помечен как ${EXPECTED_IMAGE}"
else
    log_warn "Пропуск сборки Docker образа (BUILD_IMAGE=false)"
fi

# Удаление предыдущего развертывания, если существует
if helm list -n "$NAMESPACE" | grep -q "$RELEASE_NAME"; then
    if [ "$KEEP_POSTGRES_VOLUME" = "true" ]; then
        log_warn "Найдено существующее развертывание '$RELEASE_NAME'. Используем upgrade без удаления PVC (KEEP_POSTGRES_VOLUME=true)"
    else
        log_warn "Найдено существующее развертывание '$RELEASE_NAME'. Удаление..."
        helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" || true
        sleep 5
    fi
fi

# Удаление PVC, если существует
if [ "$KEEP_POSTGRES_VOLUME" != "true" ] && kubectl get pvc -n "$NAMESPACE" | grep -q "postgresql-data"; then
    log_warn "Удаление существующего PVC..."
    kubectl delete pvc -n "$NAMESPACE" arepos-server-postgresql-data || true
    sleep 3
fi

# Развертывание через Helm
log_info "Развертывание приложения через Helm..."
HELM_ACTION="install"
if [ "$KEEP_POSTGRES_VOLUME" = "true" ]; then
    HELM_ACTION="upgrade --install"
fi
HELM_CMD="helm $HELM_ACTION $RELEASE_NAME charts/arepos-server -n $NAMESPACE"

if [ -f "$VALUES_FILE" ]; then
    HELM_CMD="$HELM_CMD -f $VALUES_FILE"
    log_info "Использование файла значений: $VALUES_FILE"
fi

if [ "$POSTGRESQL_ENABLED" = "true" ]; then
    HELM_CMD="$HELM_CMD --set postgresql.enabled=true"
    log_info "PostgreSQL будет развернут вместе с приложением"
fi

# Передаем уникальный тег образа
HELM_CMD="$HELM_CMD --set-string image.tag=$IMAGE_TAG"
HELM_CMD="$HELM_CMD --set image.pullPolicy=$DEPLOY_IMAGE_PULL_POLICY"
HELM_CMD="$HELM_CMD --set-string deployMetadata.buildId=$DEPLOY_BUILD_ID"

eval $HELM_CMD

if [ $? -ne 0 ]; then
    log_error "Ошибка при развертывании через Helm"
    exit 1
fi

log_info "Ожидание rollout deployment/${RELEASE_NAME} (таймаут: ${WAIT_TIMEOUT}с)..."
if ! kubectl rollout status "deployment/${RELEASE_NAME}" -n "$NAMESPACE" --timeout="${WAIT_TIMEOUT}s"; then
    log_error "Таймаут/ошибка rollout deployment/${RELEASE_NAME}"
    log_info "Текущий статус подов:"
    kubectl get pods -n "$NAMESPACE"
    exit 1
fi
log_info "Rollout завершен"

# Проверка статуса подов
log_info "Статус подов:"
kubectl get pods -n "$NAMESPACE"

# Проверка образа deployment (источник истины для rollout)
DEPLOY_IMAGE=$(kubectl get deployment "$RELEASE_NAME" -n "$NAMESPACE" -o jsonpath="{.spec.template.spec.containers[0].image}")
if [ "$DEPLOY_IMAGE" != "$EXPECTED_IMAGE" ]; then
    log_error "В deployment указан неожиданный образ: $DEPLOY_IMAGE (ожидался $EXPECTED_IMAGE)"
    exit 1
fi

# Проверка, что хотя бы один Running+Ready pod уже поднят с ожидаемым образом
READY_EXPECTED_COUNT=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=arepos-server -o json | jq -r --arg image "$EXPECTED_IMAGE" '
    [ .items[]
      | select(.status.phase == "Running")
      | select(.spec.containers[0].image == $image)
      | select(any(.status.containerStatuses[]?; .ready == true))
    ] | length
')
if [ "${READY_EXPECTED_COUNT}" -lt 1 ]; then
    log_error "Нет Running+Ready pod с ожидаемым образом: $EXPECTED_IMAGE"
    kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=arepos-server -o wide
    exit 1
fi
log_info "Запущен ожидаемый образ: $EXPECTED_IMAGE (ready pods: $READY_EXPECTED_COUNT)"

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

