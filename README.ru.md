# arepos-server

Сервис backend для управления доменными моделями, нотациями и связанными сущностями с полной поддержкой аудита изменений.

English version: `README.md`

Проект написан на Kotlin + Spring Boot, использует PostgreSQL (JSONB для гибких атрибутов) и рассчитан на запуск как локально, так и в Kubernetes.

## Возможности

- REST API по пути `/api/v1/*` для пользователей, моделей, нотаций, типов нод/связей, компонентов, отношений и правил отношений
- Автоматические миграции схемы через Liquibase
- Аудит изменений данных
- JWT-аутентификация с rotation refresh-токенов (одноразовые refresh-токены хранятся на сервере)
- Cookie-сессия для браузера (`warchi_access` / `warchi_refresh`) + CSRF (`warchi_csrf` / `X-CSRF-Token`); Bearer-заголовок поддерживается для API-клиентов
- Авторизация через Cerbos в enforce-only режиме
- Model live-sync через STOMP/WebSocket с опциональным transactional outbox
- Встроенные health-индикаторы для Cerbos, MinIO и model-sync outbox
- Единый формат ошибок API `{ error, message, traceId }`
- Тюнинг Hibernate (batch write, batch fetch, L2 cache для справочных сущностей)
- PostgreSQL-схема с ограничениями семантического версионирования
- Helm chart и скрипты деплоя в Kubernetes

## Технологический стек

- Kotlin 2.2.x
- Spring Boot 3.5.x
- JDK 24/25
- PostgreSQL 16+
- Liquibase
- Gradle (Kotlin DSL)

## Структура проекта

```text
src/main/kotlin/ru/kavader/arepos/
  controller/   # REST-контроллеры
  model/        # JPA-сущности
  repository/   # Spring Data репозитории
  security/     # JWT, cookies/CSRF, Cerbos, ResourceAccessService
  service/      # Бизнес-сервисы (batch save, блокировки диаграмм, файлы, …)
  config/       # конфигурация и интерсепторы

src/main/resources/
  application.yaml
  db/changelog/ # миграции Liquibase (001–039+)

charts/arepos-server/
  templates/    # Helm templates
  values.yaml   # значения chart
```

Политики Cerbos: `authz/cerbos/`. Контракты коллаборации: `docs/api-collaboration.md`. Настройка OIDC SSO: `docs/oidc.ru.md`.

## Требования

- JDK 24 или 25
- Docker (для локальной сборки образа и тестов на Testcontainers)
- PostgreSQL (если запуск вне Testcontainers)
- MinIO (если `FILE_STORAGE=minio`, режим по умолчанию)
- Kubernetes + Helm (для деплоя в кластер)

## Локальная разработка

### 1) Конфигурация окружения

Значения по умолчанию находятся в `src/main/resources/application.yaml`.

Ключевые переменные окружения:

- `DB_URL` (по умолчанию: `jdbc:postgresql://localhost:5432/arepos`)
- `DB_USERNAME` (по умолчанию: `arepos`)
- `DB_PASSWORD` (по умолчанию: `arepos`)
- `JWT_SECRET` (обязательно; минимум 32 байта)
- `JWT_ISSUER` / `JWT_AUDIENCE` (валидация issuer/audience токенов)
- `ADMIN_SECRET` (рекомендуется для bootstrap администраторов)
- `AREPOS_AUTH_COOKIE_SECURE` (`true` за HTTPS — флаг Secure на auth-cookies)
- `AREPOS_AUTH_CSRF_ENABLED` (по умолчанию `true`; double-submit CSRF для mutating-запросов cookie-сессии)
- `AREPOS_AUTH_REGISTRATION_ENABLED` (по умолчанию `true`)
- Опциональный Keycloak-совместимый OIDC SSO (`GET /api/v1/auth/sso/config` → `{ enabled, displayName }`). Полная настройка: [`docs/oidc.ru.md`](docs/oidc.ru.md).
  - `OIDC_ENABLED` (`auto` по умолчанию — включается, когда заданы issuer/client/secret/redirect; либо `true`/`false`)
  - `OIDC_ISSUER_URI` (обязателен завершающий `/`), `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URI`
  - `OIDC_POST_LOGOUT_URI`, `OIDC_FRONTEND_URL`, `OIDC_SCOPE` (по умолчанию `openid profile email`)
  - `OIDC_DISPLAY_NAME` (подпись кнопки входа, по умолчанию `SSO`)
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` (в профиле `prod` значение `*` запрещено, иначе fail-fast на старте)
- `MODEL_SYNC_OUTBOX_ENABLED` (включение transactional outbox для model sync)
- `MODEL_SYNC_OUTBOX_PUBLISH_MS`, `MODEL_SYNC_OUTBOX_BATCH_SIZE` (тюнинг outbox-паблишера)
- `CERBOS_CIRCUIT_FAILURE_THRESHOLD`, `CERBOS_CIRCUIT_OPEN_DURATION` (параметры circuit breaker для authz)
- `HIBERNATE_DEFAULT_BATCH_FETCH_SIZE`, `HIBERNATE_JDBC_BATCH_SIZE` (тюнинг JPA/Hibernate)
- `FILE_STORAGE` (`minio` по умолчанию; `disabled` для локального запуска без файлового хранилища)
- `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` (когда `FILE_STORAGE=minio`)

### 2) Сборка и запуск

```bash
./gradlew build
./gradlew bootRun
```

### 3) Тесты

```bash
./gradlew test
```

## Команды сборки

```bash
./gradlew build                      # полная сборка
./gradlew test                       # все тесты
./gradlew test --tests "*RepositoryTest"
./gradlew bootBuildImage             # сборка OCI-образа
docker build -f Dockerfile -t arch/arepos-server:dev .  # сборка через Dockerfile
```

## API

- Swagger UI: `/swagger-ui.html` — интерактивная документация API
- OpenAPI-спецификация (JSON): `/v3/api-docs`
- Auth: cookie-сессия (основной путь для wArchi) или `Authorization: Bearer`; CSRF обязателен для mutating-запросов cookie-сессии — см. `AGENTS.md` и описание в OpenAPI
- Контракты коллаборации: `docs/api-collaboration.md` (блокировки диаграмм, batch-save)
- Ошибки API из exception handlers имеют формат `{ error, message, traceId }`
- Чтение нотаций, типов нод и типов связей из редактора модели может передавать `?modelId=`; доступ даётся при прямом праве на нотацию **или** при праве редактировать модель, если эта версия нотации используется активной диаграммой модели
- Health endpoints:
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
  - `/actuator/health` (включая Cerbos/MinIO/model-sync outbox contributors)
- Prometheus-метрики:
  - `/actuator/prometheus`

## Операционные заметки

- Жизненный цикл и верификация Cerbos-политик:
  - `authz/cerbos/README.md`
  - `authz/cerbos/RUNBOOK.md`
  - `authz/cerbos/VERIFY.md`
- Включение OIDC SSO (Keycloak-совместимый): `docs/oidc.ru.md`

## Деплой

- `scripts/deploy.sh` - деплой в Kubernetes через Helm
- `scripts/undeploy.sh` - удаление release
- `scripts/helmCheck.sh` - lint/template проверки Helm
- Документация chart: `charts/arepos-server/README.md`

Флаги blue/green и другие параметры деплоя описаны в `charts/arepos-server/README.md`.

## Гайд по Open Source

Для подготовки публичного релиза начните с:

- `CONTRIBUTING.md` / `CONTRIBUTING.ru.md`
- `SECURITY.md` / `SECURITY.ru.md`
- `CODE_OF_CONDUCT.md` / `CODE_OF_CONDUCT.ru.md`

## Вклад в проект

Перед созданием pull request прочитайте `CONTRIBUTING.md`.

## Безопасность

Процесс репорта уязвимостей описан в `SECURITY.md`.

## Лицензия

Проект использует dual licensing:

- `AGPL-3.0-or-later` для open-source использования
- Коммерческая лицензия для проприетарного/закрытого коммерческого использования

См.:

- `LICENSE` / `LICENSE.ru.md`
- `LICENSE_COMMERCIAL.md` / `LICENSE_COMMERCIAL.ru.md`
