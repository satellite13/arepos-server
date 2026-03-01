# arepos-server

Сервис backend для управления доменными моделями, нотациями и связанными сущностями с полной поддержкой аудита изменений.

English version: `README.md`

Проект написан на Kotlin + Spring Boot, использует PostgreSQL (JSONB для гибких атрибутов) и рассчитан на запуск как локально, так и в Kubernetes.

## Возможности

- REST API по пути `/api/v1/*` для пользователей, моделей, нотаций, типов нод/связей, компонентов, отношений и правил отношений
- Автоматические миграции схемы через Liquibase
- Аудит изменений данных
- JWT-аутентификация и refresh flow
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
  security/     # JWT и компоненты безопасности
  config/       # конфигурация и интерсепторы

src/main/resources/
  application.yaml
  db/changelog/ # миграции Liquibase

charts/arepos-server/
  templates/    # Helm templates
  values.yaml   # значения chart
```

## Требования

- JDK 24 или 25
- Docker (для локальной сборки образа и тестов на Testcontainers)
- PostgreSQL (если запуск вне Testcontainers)
- Kubernetes + Helm (для деплоя в кластер)

## Локальная разработка

### 1) Конфигурация окружения

Значения по умолчанию находятся в `src/main/resources/application.yaml`.

Ключевые переменные окружения:

- `DB_URL` (по умолчанию: `jdbc:postgresql://localhost:5432/arepos`)
- `DB_USERNAME` (по умолчанию: `arepos`)
- `DB_PASSWORD` (по умолчанию: `arepos`)
- `JWT_SECRET` (обязательно для production)
- `ADMIN_SECRET` (рекомендуется для bootstrap администраторов)

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
```

## API

- Swagger UI: `/swagger-ui.html` — интерактивная документация API
- OpenAPI-спецификация (JSON): `/v3/api-docs`
- Health endpoints:
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
- Prometheus-метрики:
  - `/actuator/prometheus`

## Деплой

- `deploy.sh` - деплой в Kubernetes через Helm
- `undeploy.sh` - удаление release
- `helmCheck.sh` - lint/template проверки Helm
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
