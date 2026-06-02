#!/bin/bash
#
# Создание пользователя через REST API arepos-server (POST /api/v1/users).
# По умолчанию обращается к in-cluster URL; для локального доступа задайте BASE_URL.
#
# Примеры:
#   ./scripts/create-user.sh user@example.com
#   BASE_URL=http://localhost:8080 ./scripts/create-user.sh admin@example.com
#
# Переменные: BASE_URL (по умолчанию http://arepos-server.arch.svc.cluster.local:8080).

BASE_URL="${BASE_URL:-http://arepos-server.arch.svc.cluster.local:8080}"

# Получаем email как обязательный параметр
EMAIL="$1"

if [ -z "$EMAIL" ]; then
  echo "Ошибка: не указан email пользователя"
  echo "Использование: $0 <email>"
  exit 1
fi

echo "Создание пользователя с email $EMAIL..."
echo "URL: $BASE_URL/api/v1/users"
echo ""

curl -X POST "$BASE_URL/api/v1/users" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\"
  }" \
  -w "\n\nHTTP Status: %{http_code}\n" \
  -s | jq '.' 2>/dev/null
