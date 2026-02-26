# Arepos Server — Контекст для Qwen Code

## Обзор проекта

**Arepos Server** — backend-сервис на Kotlin + Spring Boot для управления доменными моделями, нотациями, узлами, связями и компонентами с полной поддержкой аудита изменений.

### Ключевые характеристики

- **REST API** под `/api/v1/*` для управления пользователями, моделями, диаграммами, узлами, связями, компонентами, типами узлов/связей, правилами связей, файлами и журналом аудита
- **База данных**: PostgreSQL 16+ с JSONB для гибких атрибутов сущностей
- **Миграции**: Liquibase для управления схемой БД
- **Аутентификация**: JWT с access/refresh токенами
- **Аудит**: Автоматическое логирование изменений данных
- **Хранение файлов**: MinIO (настраиваемое)
- **Метрики**: Prometheus через Micrometer
- **Развертывание**: Kubernetes + Helm (поддержка blue/green)

### Технологический стек

| Компонент | Версия |
|-----------|--------|
| Kotlin | 2.2.x |
| Spring Boot | 3.5.x |
| JDK | 24/25 |
| PostgreSQL | 16+ |
| Gradle | Kotlin DSL |

---

## Структура проекта

```
arepos-server/
├── src/main/kotlin/ru/kavader/arepos/
│   ├── AreposServerApplication.kt    # Точка входа Spring Boot
│   ├── config/                       # Конфигурация (Security, CORS, Interceptors)
│   ├── controller/                   # REST контроллеры
│   ├── model/                        # JPA-сущности
│   ├── repository/                   # Spring Data репозитории
│   ├── security/                     # JWT, Authentication, Authorization
│   ├── service/                      # Бизнес-логика
│   └── metrics/                      # Метрики приложения
├── src/main/resources/
│   ├── application.yaml              # Основная конфигурация
│   └── db/changelog/                 # Liquibase миграции
├── src/test/kotlin/ru/kavader/arepos/
│   ├── controller/                   # Тесты контроллеров
│   ├── repository/                   # Тесты репозиториев
│   ├── security/                     # Тесты безопасности
│   └── support/                      # Тестовые утилиты
├── charts/arepos-server/             # Helm chart для Kubernetes
├── openapi.yaml                      # OpenAPI спецификация (3421 строка)
├── build.gradle.kts                  # Gradle сборка
├── deploy.sh                         # Скрипт деплоя в Kubernetes
└── undeploy.sh                       # Скрипт удаления из Kubernetes
```

---

## Сборка и запуск

### Требования

- JDK 24 или 25
- Docker (для сборки образов и Testcontainers)
- PostgreSQL (для локального запуска вне Testcontainers)
- Kubernetes + Helm (для деплоя)

### Локальная разработка

**1. Настройка окружения**

Переменные окружения (значения по умолчанию в `src/main/resources/application.yaml`):

| Переменная | Значение по умолчанию | Описание |
|------------|----------------------|----------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/arepos` | JDBC URL БД |
| `DB_USERNAME` | `arepos` | Пользователь БД |
| `DB_PASSWORD` | `arepos` | Пароль БД |
| `JWT_SECRET` | (dev-ключ) | Секрет JWT (требуется для prod) |
| `ADMIN_SECRET` | (пусто) | Секрет для создания администраторов |
| `MINIO_ENDPOINT` | `http://localhost:9000` | Endpoint MinIO |
| `MINIO_ACCESS_KEY` | `minioadmin` | Access key MinIO |
| `MINIO_SECRET_KEY` | `minioadmin` | Secret key MinIO |
| `MINIO_BUCKET` | `arepos-files` | Bucket для файлов |

**2. Сборка и запуск**

```bash
./gradlew build        # Полная сборка
./gradlew bootRun      # Запуск приложения
```

**3. Тесты**

```bash
./gradlew test                    # Все тесты
./gradlew test --tests "*RepositoryTest"  # Тесты репозиториев
```

**4. Сборка Docker-образа**

```bash
./gradlew bootBuildImage         # OCI image: arch/arepos-server:<version>
```

---

## API

### Основные endpoints

| Endpoint | Описание |
|----------|----------|
| `/api/v1/auth/*` | Аутентификация (login, register, refresh) |
| `/api/v1/users/*` | Управление пользователями (ADMIN) |
| `/api/v1/models/*` | Управление моделями |
| `/api/v1/diagrams/*` | Управление диаграммами |
| `/api/v1/nodes/*` | Управление узлами |
| `/api/v1/links/*` | Управление связями между узлами |
| `/api/v1/relations/*` | Управление отношениями |
| `/api/v1/components/*` | Управление компонентами |
| `/api/v1/notations/*` | Управление нотациями |
| `/api/v1/node-types/*` | Типы узлов |
| `/api/v1/link-types/*` | Типы связей |
| `/api/v1/relation-rules/*` | Правила связей |
| `/api/v1/audit-log/*` | Журнал аудита |
| `/api/v1/files/*` | Управление файлами |
| `/api/v1/access-shares/*` | Делегирование прав доступа |
| `/api/v1/system/version` | Версия приложения |

### Actuator endpoints

| Endpoint | Описание |
|----------|----------|
| `/actuator/health/liveness` | Liveness probe |
| `/actuator/health/readiness` | Readiness probe |
| `/actuator/prometheus` | Метрики Prometheus |

---

## Деплой в Kubernetes

### Скрипты

| Скрипт | Описание |
|--------|----------|
| `deploy.sh` | Деплой через Helm |
| `undeploy.sh` | Удаление релиза |
| `helmCheck.sh` | Линтинг Helm chart |

### Параметры deploy.sh

```bash
NAMESPACE=arch                    # Kubernetes namespace
RELEASE_NAME=arepos-server        # Имя релиза Helm
VALUES_FILE=deploy-values.yaml    # Файл значений
POSTGRESQL_ENABLED=true           # Развернуть PostgreSQL
BUILD_IMAGE=true                  # Собирать Docker образ
WAIT_TIMEOUT=300                  # Таймаут ожидания rollout
BLUE_GREEN=false                  # Blue/green деплой
BG_SWITCH=true                    # Переключать трафик
```

### Blue/Green деплой

```bash
BLUE_GREEN=true BG_SWITCH=true ./deploy.sh
```

### Проверка после деплоя

Скрипт автоматически проверяет:
1. Rollout deployment
2. Соответствие Docker-образа
3. Health check (`/actuator/health`)
4. Версию приложения (`/api/v1/system/version`)

---

## Тестирование

### Структура тестов

```
src/test/kotlin/ru/kavader/arepos/
├── AreposServerApplicationTests.kt  # Интеграционные тесты
├── controller/                      # Тесты REST контроллеров
├── repository/                      # Тесты репозиториев (Testcontainers)
└── security/                        # Тесты JWT и авторизации
```

### Особенности

- Testcontainers для PostgreSQL
- Mockito с ByteBuddy agent (настраивается в `build.gradle.kts`)
- Spring Security Test для проверки авторизации

---

## Внесение изменений

### Чеклист перед PR

- [ ] Код собирается: `./gradlew build`
- [ ] Тесты проходят: `./gradlew test`
- [ ] Новые изменения покрыты тестами
- [ ] Документация обновлена (при необходимости)
- [ ] Нет секретов или приватных данных

### Стиль коммитов

- Заголовки в императивном наклонении
- Описывайте **почему** изменение нужно, а не только **что** изменено
- Рефакторинг отделяйте от функциональных изменений

---

## Релизный процесс

См. `MEMORY.md` для детального чеклиста.

**Кратко:**

1. Поднять версию в файлах:
   - `build.gradle.kts` → `version = "X.Y.Z"`
   - `openapi.yaml` → `info.version: X.Y.Z`
   - `charts/arepos-server/values.yaml` → `image.tag: "X.Y.Z"`
   - `deploy-values.yaml` → `image.tag: "X.Y.Z"`
   - `charts/arepos-server/Chart.yaml` → `version` и `appVersion`

2. Прогнать проверки:
   ```bash
   ./gradlew build
   ./gradlew test
   ```

3. Создать коммит и тег:
   ```bash
   git commit -m "Release vX.Y.Z."
   git tag -a vX.Y.Z -m "Release vX.Y.Z."
   git push && git push --tags
   ```

4. После деплоя проверить версию:
   ```bash
   curl http://<service>/api/v1/system/version
   ```

---

## Лицензирование

Проект использует двойное лицензирование:

- **AGPL-3.0-or-later** — для open-source
- **Commercial license** — для проприетарного/коммерческого использования

См. `LICENSE` и `LICENSE_COMMERCIAL.md`.

---

## Дополнительные документы

| Файл | Описание |
|------|----------|
| `CONTRIBUTING.md` | Руководство по внесению изменений |
| `SECURITY.md` | Политика безопасности |
| `CODE_OF_CONDUCT.md` | Кодекс поведения |
| `docs/OPEN_SOURCE_PREPARATION.md` | Подготовка к публикации |
| `charts/arepos-server/README.md` | Документация Helm chart |
