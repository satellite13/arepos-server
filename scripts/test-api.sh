#!/bin/bash
#
# Smoke-тесты REST API arepos-server: health check и CRUD по основным сущностям
# (users, models, notations, nodes, links, relations и др.) внутри кластера.
#
# Примеры:
#   ./scripts/test-api.sh
#   NAMESPACE=arch ./scripts/test-api.sh
#
# Переменные: NAMESPACE (по умолчанию arch); BASE_URL формируется как
# http://arepos-server.<NAMESPACE>.svc.cluster.local:8080.

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
# shellcheck disable=SC2034
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Параметры по умолчанию
NAMESPACE="${NAMESPACE:-arch}"
BASE_URL="http://arepos-server.$NAMESPACE.svc.cluster.local:8080"

# Функции
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

test_endpoint() {
    local method=$1
    local endpoint=$2
    local data=$3
    local description=$4
    
    echo -n "  $description: "
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" "$BASE_URL$endpoint" 2>/dev/null)
    elif [ "$method" = "POST" ]; then
        response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null)
    elif [ "$method" = "PUT" ]; then
        response=$(curl -s -w "\n%{http_code}" -X PUT "$BASE_URL$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null)
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo -e "${GREEN}✅ OK${NC} (HTTP $http_code)"
        echo "$body" | jq -r '.id // .totalElements // "OK"' 2>/dev/null || echo "OK"
        return 0
    else
        echo -e "${RED}❌ FAILED${NC} (HTTP $http_code)"
        echo "$body" | jq -r '.error // .message // "Error"' 2>/dev/null || echo "Error"
        return 1
    fi
}

# Проверка Health Check
log_info "Проверка Health Check..."
health=$(curl -s "$BASE_URL/actuator/health" 2>/dev/null || echo "")
if [ -n "$health" ]; then
    status=$(echo "$health" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
    if [ "$status" = "UP" ]; then
        log_info "Health Check: ✅ UP"
    else
        log_error "Health Check: ⚠️  $status"
        exit 1
    fi
else
    log_error "Не удалось подключиться к приложению"
    exit 1
fi

echo ""
log_info "Запуск тестов API..."
echo ""

# Тесты
failed=0
total=0

# 1. Users
echo "1. USERS API:"
total=$((total + 1))
if test_endpoint "POST" "/api/v1/users" '{"email":"test@example.com","attrs":"{\"role\":\"user\"}"}' "POST /api/v1/users"; then
    USER_ID=$(curl -s "$BASE_URL/api/v1/users?page=0&size=1" | jq -r '.content[0].id' 2>/dev/null)
    total=$((total + 1))
    test_endpoint "GET" "/api/v1/users?page=0&size=5" "" "GET /api/v1/users" || failed=$((failed + 1))
    if [ -n "$USER_ID" ] && [ "$USER_ID" != "null" ]; then
        total=$((total + 1))
        test_endpoint "GET" "/api/v1/users/$USER_ID" "" "GET /api/v1/users/{id}" || failed=$((failed + 1))
        total=$((total + 1))
        test_endpoint "PUT" "/api/v1/users/$USER_ID" "{\"email\":\"updated@example.com\"}" "PUT /api/v1/users/{id}" || failed=$((failed + 1))
    fi
else
    failed=$((failed + 1))
fi
echo ""

# 2. Models
echo "2. MODELS API:"
if [ -n "$USER_ID" ] && [ "$USER_ID" != "null" ]; then
    total=$((total + 1))
    if test_endpoint "POST" "/api/v1/models" "{\"name\":\"Test Model\",\"version\":\"1.0.0\",\"ownerId\":\"$USER_ID\"}" "POST /api/v1/models"; then
        MODEL_ID=$(curl -s "$BASE_URL/api/v1/models?page=0&size=1" | jq -r '.content[0].id' 2>/dev/null)
        total=$((total + 1))
        test_endpoint "GET" "/api/v1/models?page=0&size=5" "" "GET /api/v1/models" || failed=$((failed + 1))
    else
        failed=$((failed + 1))
    fi
else
    log_error "Не удалось получить USER_ID для тестов Models"
    failed=$((failed + 1))
fi
echo ""

# 3. Notations
echo "3. NOTATIONS API:"
if [ -n "$MODEL_ID" ] && [ "$MODEL_ID" != "null" ]; then
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/notations" "{\"name\":\"Test Notation\",\"version\":\"1.0.0\",\"modelId\":\"$MODEL_ID\",\"ownerId\":\"$USER_ID\"}" "POST /api/v1/notations" || failed=$((failed + 1))
else
    log_error "Не удалось получить MODEL_ID для тестов Notations"
    failed=$((failed + 1))
fi
echo ""

# 4. Node Types
echo "4. NODE TYPES API:"
if [ -n "$USER_ID" ] && [ "$USER_ID" != "null" ]; then
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/node-types" "{\"name\":\"Test Node Type\",\"ownerId\":\"$USER_ID\"}" "POST /api/v1/node-types" || failed=$((failed + 1))
    NODE_TYPE_ID=$(curl -s "$BASE_URL/api/v1/node-types?page=0&size=1" | jq -r '.content[0].id' 2>/dev/null)
else
    log_error "Не удалось получить USER_ID для тестов Node Types"
    failed=$((failed + 1))
fi
echo ""

# 5. Nodes
echo "5. NODES API:"
if [ -n "$MODEL_ID" ] && [ "$MODEL_ID" != "null" ] && [ -n "$NODE_TYPE_ID" ] && [ "$NODE_TYPE_ID" != "null" ]; then
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/nodes" "{\"name\":\"Node 1\",\"modelId\":\"$MODEL_ID\",\"ownerId\":\"$USER_ID\",\"nodeTypeId\":\"$NODE_TYPE_ID\"}" "POST /api/v1/nodes" || failed=$((failed + 1))
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/nodes" "{\"name\":\"Node 2\",\"modelId\":\"$MODEL_ID\",\"ownerId\":\"$USER_ID\",\"nodeTypeId\":\"$NODE_TYPE_ID\"}" "POST /api/v1/nodes (второй)" || failed=$((failed + 1))
else
    log_error "Не удалось получить необходимые ID для тестов Nodes"
    failed=$((failed + 1))
fi
echo ""

# 6. Components
echo "6. COMPONENTS API:"
NOTATION_ID=$(curl -s "$BASE_URL/api/v1/notations?page=0&size=1" | jq -r '.content[0].id' 2>/dev/null)
if [ -n "$NOTATION_ID" ] && [ "$NOTATION_ID" != "null" ] && [ -n "$NODE_TYPE_ID" ] && [ "$NODE_TYPE_ID" != "null" ]; then
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/components" "{\"name\":\"Component 1\",\"version\":\"1.0.0\",\"notationId\":\"$NOTATION_ID\",\"ownerId\":\"$USER_ID\",\"nodeTypeId\":\"$NODE_TYPE_ID\"}" "POST /api/v1/components" || failed=$((failed + 1))
else
    log_error "Не удалось получить необходимые ID для тестов Components"
    failed=$((failed + 1))
fi
echo ""

# 7. Link Types
echo "7. LINK TYPES API:"
if [ -n "$USER_ID" ] && [ "$USER_ID" != "null" ]; then
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/link-types" "{\"name\":\"Test Link Type\",\"ownerId\":\"$USER_ID\"}" "POST /api/v1/link-types" || failed=$((failed + 1))
    LINK_TYPE_ID=$(curl -s "$BASE_URL/api/v1/link-types?page=0&size=1" | jq -r '.content[0].id' 2>/dev/null)
else
    log_error "Не удалось получить USER_ID для тестов Link Types"
    failed=$((failed + 1))
fi
echo ""

# 8. Links
echo "8. LINKS API:"
SOURCE_ID=$(curl -s "$BASE_URL/api/v1/nodes?page=0&size=1" | jq -r '.content[0].id' 2>/dev/null)
TARGET_ID=$(curl -s "$BASE_URL/api/v1/nodes?page=1&size=1" | jq -r '.content[0].id' 2>/dev/null)
if [ -n "$MODEL_ID" ] && [ "$MODEL_ID" != "null" ] && [ -n "$LINK_TYPE_ID" ] && [ "$LINK_TYPE_ID" != "null" ] && [ -n "$SOURCE_ID" ] && [ "$SOURCE_ID" != "null" ] && [ -n "$TARGET_ID" ] && [ "$TARGET_ID" != "null" ]; then
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/links" "{\"modelId\":\"$MODEL_ID\",\"ownerId\":\"$USER_ID\",\"linkTypeId\":\"$LINK_TYPE_ID\",\"sourceId\":\"$SOURCE_ID\",\"targetId\":\"$TARGET_ID\"}" "POST /api/v1/links" || failed=$((failed + 1))
else
    log_error "Не удалось получить необходимые ID для тестов Links"
    failed=$((failed + 1))
fi
echo ""

# 9. Relations
echo "9. RELATIONS API:"
if [ -n "$NOTATION_ID" ] && [ "$NOTATION_ID" != "null" ] && [ -n "$LINK_TYPE_ID" ] && [ "$LINK_TYPE_ID" != "null" ]; then
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/relations" "{\"name\":\"Test Relation\",\"notationId\":\"$NOTATION_ID\",\"linkTypeId\":\"$LINK_TYPE_ID\",\"ownerId\":\"$USER_ID\",\"version\":\"1.0.0\"}" "POST /api/v1/relations" || failed=$((failed + 1))
    RELATION_ID=$(curl -s "$BASE_URL/api/v1/relations?page=0&size=1" | jq -r '.content[0].id' 2>/dev/null)
else
    log_error "Не удалось получить необходимые ID для тестов Relations"
    failed=$((failed + 1))
fi
echo ""

# 10. Relation Rules
echo "10. RELATION RULES API:"
COMPONENT_ID=$(curl -s "$BASE_URL/api/v1/components?page=0&size=1" | jq -r '.content[0].id' 2>/dev/null)
if [ -n "$RELATION_ID" ] && [ "$RELATION_ID" != "null" ] && [ -n "$COMPONENT_ID" ] && [ "$COMPONENT_ID" != "null" ]; then
    total=$((total + 1))
    test_endpoint "POST" "/api/v1/relation-rules" "{\"relationId\":\"$RELATION_ID\",\"fromComponentId\":\"$COMPONENT_ID\",\"toComponentId\":\"$COMPONENT_ID\",\"ownerId\":\"$USER_ID\"}" "POST /api/v1/relation-rules" || failed=$((failed + 1))
else
    log_error "Не удалось получить необходимые ID для тестов Relation Rules"
    failed=$((failed + 1))
fi
echo ""

# 11. Audit Log
echo "11. AUDIT LOG API:"
total=$((total + 1))
test_endpoint "GET" "/api/v1/audit-log?page=0&size=5" "" "GET /api/v1/audit-log" || failed=$((failed + 1))
echo ""

# Итоговая статистика
echo "=== ИТОГОВАЯ СТАТИСТИКА ==="
echo ""
for endpoint in users models notations node-types nodes components link-types links relations relation-rules audit-log; do
    count=$(curl -s "$BASE_URL/api/v1/$endpoint?page=0&size=1" 2>/dev/null | jq -r '.totalElements // 0' 2>/dev/null || echo "0")
    printf "  %-20s %3d\n" "$endpoint:" "$count"
done
echo ""

# Результаты тестов
echo "=== РЕЗУЛЬТАТЫ ТЕСТОВ ==="
passed=$((total - failed))
echo "Всего тестов: $total"
echo -e "Пройдено: ${GREEN}$passed${NC}"
echo -e "Провалено: ${RED}$failed${NC}"
echo ""

if [ $failed -eq 0 ]; then
    log_info "✅ Все тесты пройдены успешно!"
    exit 0
else
    log_error "❌ Некоторые тесты провалились"
    exit 1
fi

