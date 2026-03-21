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
KEEP_POSTGRES_VOLUME="${KEEP_POSTGRES_VOLUME:-true}"
KEEP_MINIO_VOLUME="${KEEP_MINIO_VOLUME:-true}"
AUTH_SECRET_NAME="${AUTH_SECRET_NAME:-arepos-server-auth-secret}"
DEPLOY_IMAGE_PULL_POLICY="${DEPLOY_IMAGE_PULL_POLICY:-IfNotPresent}"
CHART_PATH="${CHART_PATH:-charts/arepos-server}"
BLUE_GREEN="${BLUE_GREEN:-false}"
BG_SWITCH="${BG_SWITCH:-true}"
SERVICE_NAME="${SERVICE_NAME:-$RELEASE_NAME}"
HELM_EXTRA_ARGS="${HELM_EXTRA_ARGS:-}"
CHECK_SHADOW_GATE="${CHECK_SHADOW_GATE:-false}"
SHADOW_GATE_SCRIPT="${SHADOW_GATE_SCRIPT:-./scripts/check-cerbos-shadow.sh}"
SHADOW_GATE_MODE="${SHADOW_GATE_MODE:-baseline}"
SHADOW_GATE_BASELINE_FILE="${SHADOW_GATE_BASELINE_FILE:-.cerbos-shadow-baseline.env}"
SHADOW_GATE_MAX_MISMATCH="${SHADOW_GATE_MAX_MISMATCH:-0}"
SHADOW_GATE_MAX_ERRORS="${SHADOW_GATE_MAX_ERRORS:-0}"
SHADOW_GATE_MIN_MATCH_RATE="${SHADOW_GATE_MIN_MATCH_RATE:-99.9}"
SHADOW_GATE_ON_ANY_DEPLOY="${SHADOW_GATE_ON_ANY_DEPLOY:-false}"
SHADOW_GATE_WARN_ONLY="${SHADOW_GATE_WARN_ONLY:-false}"
CERBOS_SHADOW="${CERBOS_SHADOW:-false}"
CERBOS_ENFORCE="${CERBOS_ENFORCE:-false}"
CERBOS_OFF="${CERBOS_OFF:-false}"
CERBOS_DEPLOY="${CERBOS_DEPLOY:-true}"
CERBOS_BUNDLE_VERSION="${CERBOS_BUNDLE_VERSION:-policy-$(git rev-parse --short HEAD 2>/dev/null || echo latest)}"
CERBOS_FAIL_OPEN="${CERBOS_FAIL_OPEN:-true}"

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

is_valid_color() {
    [ "$1" = "blue" ] || [ "$1" = "green" ]
}

opposite_color() {
    if [ "$1" = "blue" ]; then
        echo "green"
    else
        echo "blue"
    fi
}

check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "$1 не установлен"
        exit 1
    fi
}

append_helm_extra_arg() {
    if [ -n "$HELM_EXTRA_ARGS" ]; then
        HELM_EXTRA_ARGS="$HELM_EXTRA_ARGS $1"
    else
        HELM_EXTRA_ARGS="$1"
    fi
}

configure_cerbos_shorthand() {
    if [ "$CERBOS_OFF" = "true" ] && { [ "$CERBOS_SHADOW" = "true" ] || [ "$CERBOS_ENFORCE" = "true" ]; }; then
        log_error "CERBOS_OFF=true нельзя совмещать с CERBOS_SHADOW=true или CERBOS_ENFORCE=true"
        exit 1
    fi

    if [ "$CERBOS_SHADOW" = "true" ] && [ "$CERBOS_ENFORCE" = "true" ]; then
        log_error "Нельзя одновременно включать CERBOS_SHADOW=true и CERBOS_ENFORCE=true"
        exit 1
    fi

    if [ "$CERBOS_OFF" = "true" ]; then
        append_helm_extra_arg "--set cerbos.enabled=false"
        append_helm_extra_arg "--set cerbos.deploy=false"
        append_helm_extra_arg "--set-string cerbos.mode=DISABLED"
        append_helm_extra_arg "--set authz.cerbosShadowEnabled=false"
        append_helm_extra_arg "--set authz.cerbosEnforceEnabled=false"
        log_info "Включен shorthand режим CERBOS_OFF=true"
        return
    fi

    if [ "$CERBOS_SHADOW" != "true" ] && [ "$CERBOS_ENFORCE" != "true" ]; then
        return
    fi

    append_helm_extra_arg "--set cerbos.enabled=true"
    append_helm_extra_arg "--set cerbos.deploy=${CERBOS_DEPLOY}"
    append_helm_extra_arg "--set-string cerbos.bundleVersion=${CERBOS_BUNDLE_VERSION}"
    append_helm_extra_arg "--set authz.cerbosFailOpen=${CERBOS_FAIL_OPEN}"

    if [ "$CERBOS_SHADOW" = "true" ]; then
        append_helm_extra_arg "--set-string cerbos.mode=SHADOW"
        append_helm_extra_arg "--set authz.cerbosShadowEnabled=true"
        append_helm_extra_arg "--set authz.cerbosEnforceEnabled=false"
        log_info "Включен shorthand режим CERBOS_SHADOW=true"
        return
    fi

    if [ "$CERBOS_ENFORCE" = "true" ]; then
        append_helm_extra_arg "--set-string cerbos.mode=ENFORCE"
        append_helm_extra_arg "--set authz.cerbosShadowEnabled=false"
        append_helm_extra_arg "--set authz.cerbosEnforceEnabled=true"
        log_info "Включен shorthand режим CERBOS_ENFORCE=true"
    fi
}

should_run_shadow_gate() {
    if [ "$CHECK_SHADOW_GATE" != "true" ]; then
        return 1
    fi
    if [ "$SHADOW_GATE_ON_ANY_DEPLOY" = "true" ]; then
        return 0
    fi
    if [ "$CERBOS_ENFORCE" = "true" ]; then
        return 0
    fi
    case "$HELM_EXTRA_ARGS" in
        *"cerbosEnforceEnabled=true"*|*"cerbos.mode=ENFORCE"*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
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

# Подтверждение kubectl context
CURRENT_CONTEXT=$(kubectl config current-context)
CLUSTER_NAME=$(kubectl config view -o jsonpath="{.contexts[?(@.name=='$CURRENT_CONTEXT')].context.cluster}")
log_warn "Текущий kubectl context: $CURRENT_CONTEXT (кластер: $CLUSTER_NAME)"
if [ "${SKIP_CONFIRM:-false}" != "true" ]; then
    read -p "Деплоить в этот кластер? (y/N) " CONFIRM
    if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
        log_info "Деплой отменён"
        exit 0
    fi
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

# Определение тега образа (можно переопределить: IMAGE_TAG=0.2.5 ./deploy.sh)
GIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "latest")
APP_VERSION=$(grep '^version' build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/' || echo "0.1.0")
IMAGE_TAG="${IMAGE_TAG:-${APP_VERSION}-${GIT_HASH}}"
IMAGE_NAME="arch/arepos-server"
EXPECTED_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
DEPLOY_BUILD_ID="${IMAGE_TAG}-$(date +%s)"
VERSION_CHECK_ATTEMPTS="${VERSION_CHECK_ATTEMPTS:-10}"
VERSION_CHECK_DELAY="${VERSION_CHECK_DELAY:-2}"
log_info "Тег образа: ${IMAGE_TAG}"
log_info "Build ID деплоя: ${DEPLOY_BUILD_ID}"
log_info "Image pull policy: ${DEPLOY_IMAGE_PULL_POLICY}"

configure_cerbos_shorthand

if should_run_shadow_gate; then
    log_info "Проверка Cerbos shadow gate перед deploy..."
    if [ ! -x "$SHADOW_GATE_SCRIPT" ]; then
        log_error "Скрипт shadow gate не найден или не исполняемый: $SHADOW_GATE_SCRIPT"
        exit 1
    fi
    if ! MODE="$SHADOW_GATE_MODE" \
         BASELINE_FILE="$SHADOW_GATE_BASELINE_FILE" \
         MAX_MISMATCH="$SHADOW_GATE_MAX_MISMATCH" \
         MAX_ERRORS="$SHADOW_GATE_MAX_ERRORS" \
         MIN_MATCH_RATE="$SHADOW_GATE_MIN_MATCH_RATE" \
         NAMESPACE="$NAMESPACE" \
         SERVICE_NAME="$SERVICE_NAME" \
         "$SHADOW_GATE_SCRIPT"; then
        if [ "$SHADOW_GATE_WARN_ONLY" = "true" ]; then
            log_warn "Shadow gate не пройден, но включен SHADOW_GATE_WARN_ONLY=true. Продолжаем деплой."
        else
            log_error "Shadow gate не пройден. Деплой прерван."
            exit 1
        fi
    fi
fi

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

DEPLOYMENT_TO_CHECK="$RELEASE_NAME"
POD_SELECTOR="app.kubernetes.io/name=arepos-server"

if [ "$BLUE_GREEN" = "true" ]; then
    log_info "Blue/Green режим включён"
    CURRENT_COLOR=$(kubectl get service "$SERVICE_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.selector.app\.kubernetes\.io/color}' 2>/dev/null || true)
    if ! is_valid_color "$CURRENT_COLOR"; then
        CURRENT_COLOR="blue"
    fi
    TARGET_COLOR=$(opposite_color "$CURRENT_COLOR")
    log_info "Текущий активный цвет: $CURRENT_COLOR, деплой в неактивный: $TARGET_COLOR"

    CURRENT_COLOR_IMAGE=$(kubectl get deployment "${SERVICE_NAME}-${CURRENT_COLOR}" -n "$NAMESPACE" -o jsonpath="{.spec.template.spec.containers[0].image}" 2>/dev/null || true)
    CURRENT_COLOR_TAG="${CURRENT_COLOR_IMAGE##*:}"
    if [ -z "$CURRENT_COLOR_TAG" ] || [ "$CURRENT_COLOR_TAG" = "$CURRENT_COLOR_IMAGE" ]; then
        CURRENT_COLOR_TAG="$IMAGE_TAG"
    fi

    HELM_CMD="helm upgrade --install $RELEASE_NAME $CHART_PATH -n $NAMESPACE --set blueGreen.enabled=true --set blueGreen.activeColor=$CURRENT_COLOR"
    if [ -f "$VALUES_FILE" ]; then
        HELM_CMD="$HELM_CMD -f $VALUES_FILE"
        log_info "Использование файла значений: $VALUES_FILE"
    fi
    if [ "$POSTGRESQL_ENABLED" = "true" ]; then
        HELM_CMD="$HELM_CMD --set postgresql.enabled=true"
        log_info "PostgreSQL будет развернут вместе с приложением"
    fi
    HELM_CMD="$HELM_CMD --set-string blueGreen.image.${CURRENT_COLOR}Tag=$CURRENT_COLOR_TAG"
    HELM_CMD="$HELM_CMD --set-string blueGreen.image.${TARGET_COLOR}Tag=$IMAGE_TAG"
    HELM_CMD="$HELM_CMD --set image.pullPolicy=$DEPLOY_IMAGE_PULL_POLICY"
    HELM_CMD="$HELM_CMD --set-string deployMetadata.buildId=$DEPLOY_BUILD_ID"
    if [ -n "$HELM_EXTRA_ARGS" ]; then
        HELM_CMD="$HELM_CMD $HELM_EXTRA_ARGS"
    fi

    eval $HELM_CMD

    DEPLOYMENT_TO_CHECK="${SERVICE_NAME}-${TARGET_COLOR}"
    POD_SELECTOR="$POD_SELECTOR,app.kubernetes.io/color=$TARGET_COLOR"

    log_info "Ожидание rollout deployment/${DEPLOYMENT_TO_CHECK} (таймаут: ${WAIT_TIMEOUT}с)..."
    if ! kubectl rollout status "deployment/${DEPLOYMENT_TO_CHECK}" -n "$NAMESPACE" --timeout="${WAIT_TIMEOUT}s"; then
        log_error "Таймаут/ошибка rollout deployment/${DEPLOYMENT_TO_CHECK}"
        log_info "Текущий статус подов:"
        kubectl get pods -n "$NAMESPACE"
        exit 1
    fi
    log_info "Rollout завершен"

    if [ "$BG_SWITCH" = "true" ]; then
        log_info "Переключение трафика на цвет '$TARGET_COLOR'..."
        HELM_SWITCH_CMD="helm upgrade --install $RELEASE_NAME $CHART_PATH -n $NAMESPACE --reuse-values --set blueGreen.enabled=true --set blueGreen.activeColor=$TARGET_COLOR --set-string deployMetadata.buildId=${DEPLOY_BUILD_ID}-switch"
        if [ -n "$HELM_EXTRA_ARGS" ]; then
            HELM_SWITCH_CMD="$HELM_SWITCH_CMD $HELM_EXTRA_ARGS"
        fi
        eval $HELM_SWITCH_CMD
        log_info "Трафик переключен на '$TARGET_COLOR'"
    else
        log_warn "Переключение трафика пропущено (BG_SWITCH=false). Активным остаётся '$CURRENT_COLOR'"
    fi
else
    # Удаление предыдущего развертывания, если существует
    if helm list -n "$NAMESPACE" | grep -q "$RELEASE_NAME"; then
        if [ "$KEEP_POSTGRES_VOLUME" = "true" ] || [ "$KEEP_MINIO_VOLUME" = "true" ]; then
            log_warn "Найдено существующее развертывание '$RELEASE_NAME'. Используем upgrade без удаления PVC (KEEP_POSTGRES_VOLUME=$KEEP_POSTGRES_VOLUME, KEEP_MINIO_VOLUME=$KEEP_MINIO_VOLUME)"
        else
            log_warn "Найдено существующее развертывание '$RELEASE_NAME'. Удаление..."
            helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" || true
            sleep 5
        fi
    fi

    # Удаление PostgreSQL PVC, если существует
    if [ "$KEEP_POSTGRES_VOLUME" != "true" ] && kubectl get pvc -n "$NAMESPACE" | grep -q "postgresql-data"; then
        log_warn "Удаление существующего PostgreSQL PVC..."
        kubectl delete pvc -n "$NAMESPACE" arepos-server-postgresql-data --wait=false || true
        sleep 3
    fi

    # Удаление MinIO PVC, если существует
    if [ "$KEEP_MINIO_VOLUME" != "true" ] && kubectl get pvc -n "$NAMESPACE" | grep -q "minio-data"; then
        log_warn "Удаление существующего MinIO PVC..."
        kubectl delete pvc -n "$NAMESPACE" arepos-server-minio-data --wait=false || true
        sleep 3
    fi

    # Развертывание через Helm
    log_info "Развертывание приложения через Helm..."
    HELM_ACTION="install"
    if [ "$KEEP_POSTGRES_VOLUME" = "true" ] || [ "$KEEP_MINIO_VOLUME" = "true" ]; then
        HELM_ACTION="upgrade --install"
    fi
    HELM_CMD="helm $HELM_ACTION $RELEASE_NAME $CHART_PATH -n $NAMESPACE"

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
    if [ -n "$HELM_EXTRA_ARGS" ]; then
        HELM_CMD="$HELM_CMD $HELM_EXTRA_ARGS"
    fi

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
fi

# Проверка статуса подов
log_info "Статус подов:"
kubectl get pods -n "$NAMESPACE"

# Проверка образа deployment (источник истины для rollout)
DEPLOY_IMAGE=$(kubectl get deployment "$DEPLOYMENT_TO_CHECK" -n "$NAMESPACE" -o jsonpath="{.spec.template.spec.containers[0].image}")
if [ "$DEPLOY_IMAGE" != "$EXPECTED_IMAGE" ]; then
    log_error "В deployment '$DEPLOYMENT_TO_CHECK' указан неожиданный образ: $DEPLOY_IMAGE (ожидался $EXPECTED_IMAGE)"
    exit 1
fi

# Проверка, что хотя бы один Running+Ready pod уже поднят с ожидаемым образом
READY_EXPECTED_COUNT=$(kubectl get pods -n "$NAMESPACE" -l "$POD_SELECTOR" -o json | jq -r --arg image "$EXPECTED_IMAGE" '
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
HEALTH=$(curl -s "http://$SERVICE_NAME.$NAMESPACE.svc.cluster.local:8080/actuator/health" 2>/dev/null || echo "")

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

# Проверка версии развернутого приложения через REST endpoint
log_info "Проверка версии приложения..."
VERSION_URL="http://$SERVICE_NAME.$NAMESPACE.svc.cluster.local:8080/api/v1/system/version"
DEPLOYED_VERSION=""

for ATTEMPT in $(seq 1 "$VERSION_CHECK_ATTEMPTS"); do
    VERSION_RESPONSE=$(curl -s "$VERSION_URL" 2>/dev/null || echo "")
    DEPLOYED_VERSION=$(echo "$VERSION_RESPONSE" | jq -r '.version // empty' 2>/dev/null || echo "")

    if [ -n "$DEPLOYED_VERSION" ]; then
        break
    fi

    if [ "$ATTEMPT" -lt "$VERSION_CHECK_ATTEMPTS" ]; then
        log_warn "Не удалось получить версию (попытка $ATTEMPT/$VERSION_CHECK_ATTEMPTS), повтор через ${VERSION_CHECK_DELAY}с..."
        sleep "$VERSION_CHECK_DELAY"
    fi
done

if [ -z "$DEPLOYED_VERSION" ]; then
    log_error "Не удалось получить версию приложения с endpoint: $VERSION_URL"
    exit 1
fi

if [ "$DEPLOYED_VERSION" != "$APP_VERSION" ]; then
    log_error "Версия развернутого приложения не совпадает с ожидаемой: получено '$DEPLOYED_VERSION', ожидалось '$APP_VERSION'"
    exit 1
fi

log_info "Версия приложения совпадает: $DEPLOYED_VERSION"

# Вывод информации о доступе
log_info "Приложение развернуто!"
echo ""
echo "Доступ к API:"
echo "  http://$SERVICE_NAME.$NAMESPACE.svc.cluster.local:8080"
echo ""
echo "Просмотр логов:"
echo "  kubectl logs -n $NAMESPACE -l app.kubernetes.io/name=arepos-server"
echo ""
echo "Порт-форвард для локального доступа:"
echo "  arepos-server: kubectl port-forward -n $NAMESPACE svc/$SERVICE_NAME 8080:8080"
echo "  MinIO (API+консоль): kubectl port-forward -n $NAMESPACE svc/${SERVICE_NAME}-minio 9000:9000 9001:9001"
echo "  (MinIO консоль: http://localhost:9001, API: http://localhost:9000)"
echo ""
echo "Если консоль на :9001 недоступна — MinIO мог запуститься со случайным портом."
echo "Проверьте порт в логах: kubectl logs -n $NAMESPACE -l app.kubernetes.io/component=minio | grep WebUI"
echo "Затем: kubectl port-forward -n $NAMESPACE \$(kubectl get pod -n $NAMESPACE -l app.kubernetes.io/component=minio -o jsonpath='{.items[0].metadata.name}') 9001:<ПОРТ_ИЗ_ЛОГОВ>"
echo ""

