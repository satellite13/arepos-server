#!/bin/bash

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
