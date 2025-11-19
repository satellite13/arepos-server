# Развертывание в Kubernetes

## Скрипты

### 1. `deploy.sh` - Развертывание приложения

Автоматизирует процесс развертывания приложения в Kubernetes:
- Проверяет наличие необходимых инструментов (kubectl, helm, docker)
- Собирает Docker образ (опционально)
- Создает namespace (если не существует)
- Удаляет предыдущее развертывание (если есть)
- Разворачивает приложение через Helm
- Ожидает готовности приложения
- Проверяет Health Check

**Использование:**
```bash
./deploy.sh
```

**Переменные окружения:**
- `NAMESPACE` - namespace для развертывания (по умолчанию: `arch`)
- `RELEASE_NAME` - имя Helm release (по умолчанию: `arepos-server`)
- `VALUES_FILE` - файл значений Helm (по умолчанию: `deploy-values.yaml`)
- `POSTGRESQL_ENABLED` - включить PostgreSQL (по умолчанию: `true`)
- `BUILD_IMAGE` - собрать Docker образ (по умолчанию: `true`)
- `WAIT_TIMEOUT` - таймаут ожидания в секундах (по умолчанию: `300`)

**Примеры:**
```bash
# Развертывание с настройками по умолчанию
./deploy.sh

# Развертывание в другой namespace
NAMESPACE=production ./deploy.sh

# Развертывание без сборки образа
BUILD_IMAGE=false ./deploy.sh

# Развертывание без PostgreSQL
POSTGRESQL_ENABLED=false ./deploy.sh
```

### 2. `undeploy.sh` - Удаление приложения

Удаляет развертывание приложения из Kubernetes.

**Использование:**
```bash
./undeploy.sh
```

**Переменные окружения:**
- `NAMESPACE` - namespace (по умолчанию: `arch`)
- `RELEASE_NAME` - имя Helm release (по умолчанию: `arepos-server`)
- `DELETE_PVC` - удалить PersistentVolumeClaim (по умолчанию: `false`)

**Примеры:**
```bash
# Удаление приложения
./undeploy.sh

# Удаление с очисткой данных PostgreSQL
DELETE_PVC=true ./undeploy.sh
```

### 3. `test-api.sh` - Тестирование API

Запускает полный набор тестов для всех API эндпоинтов.

**Использование:**
```bash
./test-api.sh
```

**Переменные окружения:**
- `NAMESPACE` - namespace (по умолчанию: `arch`)

**Что тестируется:**
- Health Check
- Users API (GET, POST, PUT)
- Models API (GET, POST)
- Notations API (POST)
- Node Types API (POST)
- Nodes API (POST)
- Components API (POST)
- Link Types API (POST)
- Links API (POST)
- Relations API (POST)
- Relation Rules API (POST)
- Audit Log API (GET)

**Вывод:**
- Статус каждого теста (✅ OK / ❌ FAILED)
- HTTP код ответа
- Итоговая статистика по количеству сущностей
- Общий результат тестирования

## Полный цикл развертывания

```bash
# 1. Развертывание
./deploy.sh

# 2. Тестирование API
./test-api.sh

# 3. Удаление (при необходимости)
./undeploy.sh
```

## Ручное развертывание

Если нужно развернуть вручную:

```bash
# 1. Сборка образа
./gradlew bootBuildImage

# 2. Развертывание через Helm
helm install arepos-server charts/arepos-server -n arch \
  -f deploy-values.yaml \
  --set postgresql.enabled=true

# 3. Проверка статуса
kubectl get pods -n arch

# 4. Проверка Health Check
curl http://arepos-server.arch.svc.cluster.local:8080/actuator/health
```

## Доступ к приложению

### Из кластера
```
http://arepos-server.arch.svc.cluster.local:8080
```

### Локальный доступ через port-forward
```bash
kubectl port-forward -n arch svc/arepos-server 8080:8080
```
Затем: `http://localhost:8080`

### Просмотр логов
```bash
kubectl logs -n arch -l app.kubernetes.io/name=arepos-server -f
```

### Подключение к PostgreSQL
```bash
kubectl exec -it -n arch $(kubectl get pods -n arch -l app.kubernetes.io/component=postgresql -o jsonpath='{.items[0].metadata.name}') -- psql -U arepos -d arepos
```

## Требования

- Kubernetes кластер (OrbStack, Minikube, или другой)
- kubectl настроен и подключен к кластеру
- Helm 3.x
- Docker (для сборки образа)
- Gradle (для сборки приложения)

## Troubleshooting

### Приложение не запускается
```bash
# Проверка статуса подов
kubectl get pods -n arch

# Просмотр логов
kubectl logs -n arch -l app.kubernetes.io/name=arepos-server

# Проверка событий
kubectl get events -n arch --sort-by='.lastTimestamp'
```

### PostgreSQL не готов
```bash
# Проверка статуса PostgreSQL
kubectl get pods -n arch -l app.kubernetes.io/component=postgresql

# Логи PostgreSQL
kubectl logs -n arch -l app.kubernetes.io/component=postgresql
```

### Проблемы с подключением к БД
```bash
# Проверка Service
kubectl get svc -n arch arepos-server-postgresql

# Проверка Endpoints
kubectl get endpoints -n arch arepos-server-postgresql
```

