# Arepos Server Helm Chart

Helm chart для развертывания Arepos Server в Kubernetes.

## Требования

- Kubernetes 1.19+
- Helm 3.0+
- PostgreSQL база данных (внутри или вне кластера)

## Установка

### Базовая установка

```bash
helm install arepos-server ./charts/arepos-server \
  --set postgresql.enabled=false \
  --set database.host=postgresql \
  --set database.password=your-password
```

### Использование существующего Secret

```bash
helm install arepos-server ./charts/arepos-server \
  --set postgresql.enabled=false \
  --set database.existingSecret=postgresql-secret
```

### Установка с кастомными значениями

```bash
helm install arepos-server ./charts/arepos-server \
  -f custom-values.yaml
```

## Конфигурация

### Основные параметры

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `replicaCount` | Количество реплик | `1` |
| `image.repository` | Репозиторий образа | `arch/arepos-server` |
| `image.tag` | Тег образа | `0.0.1-SNAPSHOT` |
| `service.port` | Порт сервиса | `8080` |

### База данных

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `database.host` | Хост БД | `postgresql` |
| `database.port` | Порт БД | `5432` |
| `database.name` | Имя БД | `arepos` |
| `database.username` | Пользователь БД | `arepos` |
| `database.password` | Пароль БД | `""` |
| `database.existingSecret` | Имя существующего Secret | `""` |

### Ресурсы

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `resources.limits.cpu` | Лимит CPU | `500m` |
| `resources.limits.memory` | Лимит памяти | `512Mi` |
| `resources.requests.cpu` | Запрос CPU | `200m` |
| `resources.requests.memory` | Запрос памяти | `256Mi` |

### Health Checks

Приложение использует Spring Boot Actuator для health checks:
- `startupProbe`: `/actuator/health/readiness` (30s initial delay)
- `livenessProbe`: `/actuator/health/liveness` (60s initial delay)
- `readinessProbe`: `/actuator/health/readiness` (30s initial delay)

## Примеры

### Пример values.yaml для production

```yaml
replicaCount: 3

image:
  repository: registry.example.com/arepos-server
  tag: "1.0.0"

database:
  existingSecret: postgresql-credentials
  host: postgresql.production.svc.cluster.local

resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

ingress:
  enabled: true
  className: "nginx"
  hosts:
    - host: api.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: api-tls
      hosts:
        - api.example.com
```

## Обновление

```bash
helm upgrade arepos-server ./charts/arepos-server \
  --set database.password=new-password
```

## Удаление

```bash
helm uninstall arepos-server
```

## Troubleshooting

### Проверка логов

```bash
kubectl logs -f deployment/arepos-server
```

### Проверка переменных окружения

```bash
kubectl exec deployment/arepos-server -- env | grep DB_
```

### Проверка подключения к БД

Убедитесь, что:
1. PostgreSQL доступен по указанному хосту и порту
2. База данных и пользователь созданы
3. Пароль указан правильно или Secret существует

