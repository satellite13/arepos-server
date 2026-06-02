# arepos-server

Backend service for managing domain models, notations, and related entities with full audit trail support.

Русская версия: `README.ru.md`

The project is built with Kotlin + Spring Boot, stores flexible entity attributes in PostgreSQL JSONB, and is designed to run both locally and in Kubernetes.

## Features

- REST API under `/api/v1/*` for users, models, notations, node/link types, components, relations, and relation rules
- Automatic database migrations via Liquibase
- Audit logging for data changes
- JWT-based authentication with refresh token rotation (single-use refresh tokens stored server-side)
- Cerbos-backed authorization in enforce-only mode
- Model live-sync over STOMP/WebSocket with optional transactional outbox publishing
- Built-in health indicators for Cerbos, MinIO, and model-sync outbox
- Unified error response envelope `{ error, message, traceId }`
- Hibernate performance tuning (batch writes, batch fetch, L2 cache for reference entities)
- PostgreSQL-first schema with semantic versioning constraints
- Helm chart and deployment scripts for Kubernetes

## Tech Stack

- Kotlin 2.2.x
- Spring Boot 3.5.x
- JDK 24/25
- PostgreSQL 16+
- Liquibase
- Gradle (Kotlin DSL)

## Project Structure

```text
src/main/kotlin/ru/kavader/arepos/
  controller/   # REST controllers
  model/        # JPA entities
  repository/   # Spring Data repositories
  security/     # JWT and auth-related components
  config/       # configuration and interceptors

src/main/resources/
  application.yaml
  db/changelog/ # Liquibase migrations

charts/arepos-server/
  templates/    # Helm templates
  values.yaml   # chart values
```

## Requirements

- JDK 24 or 25
- Docker (for local image build and Testcontainers-based tests)
- PostgreSQL (if running outside Testcontainers)
- MinIO (if `FILE_STORAGE=minio`, default mode)
- Kubernetes + Helm (for cluster deployment)

## Local Development

### 1) Configure environment

Default values are defined in `src/main/resources/application.yaml`.

Important environment variables:

- `DB_URL` (default: `jdbc:postgresql://localhost:5432/arepos`)
- `DB_USERNAME` (default: `arepos`)
- `DB_PASSWORD` (default: `arepos`)
- `JWT_SECRET` (required; at least 32 bytes)
- `JWT_ISSUER` / `JWT_AUDIENCE` (token issuer/audience validation)
- `ADMIN_SECRET` (recommended for admin bootstrap flow)
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` (in `prod` profile, `*` is forbidden and startup fails)
- `MODEL_SYNC_OUTBOX_ENABLED` (enable transactional outbox for model sync)
- `MODEL_SYNC_OUTBOX_PUBLISH_MS`, `MODEL_SYNC_OUTBOX_BATCH_SIZE` (outbox publisher tuning)
- `CERBOS_CIRCUIT_FAILURE_THRESHOLD`, `CERBOS_CIRCUIT_OPEN_DURATION` (authz circuit breaker)
- `HIBERNATE_DEFAULT_BATCH_FETCH_SIZE`, `HIBERNATE_JDBC_BATCH_SIZE` (JPA performance tuning)
- `FILE_STORAGE` (`minio` by default; use `disabled` for local run without file storage)
- `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` (when `FILE_STORAGE=minio`)

### 2) Build and run

```bash
./gradlew build
./gradlew bootRun
```

### 3) Run tests

```bash
./gradlew test
```

## Build Commands

```bash
./gradlew build                      # full build
./gradlew test                       # all tests
./gradlew test --tests "*RepositoryTest"
./gradlew bootBuildImage             # build OCI image
```

## API

- Swagger UI: `/swagger-ui.html` — interactive API documentation
- OpenAPI spec (JSON): `/v3/api-docs`
- Error responses from exception handlers follow `{ error, message, traceId }`
- Health endpoints:
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
  - `/actuator/health` (includes Cerbos/MinIO/model-sync outbox contributors)
- Prometheus metrics:
  - `/actuator/prometheus`

## Operations Notes

- Cerbos policy lifecycle and verification:
  - `authz/cerbos/README.md`
  - `authz/cerbos/RUNBOOK.md`
  - `authz/cerbos/VERIFY.md`

## Deployment

- `deploy.sh` - deploy to Kubernetes using Helm
- `undeploy.sh` - uninstall release
- `helmCheck.sh` - lint/template smoke checks
- Chart docs: `charts/arepos-server/README.md`

For blue/green and deployment flags, see `charts/arepos-server/README.md`.

## Open Source Guide

If you plan to publish the project publicly, start with:

- `CONTRIBUTING.md` / `CONTRIBUTING.ru.md`
- `SECURITY.md` / `SECURITY.ru.md`
- `CODE_OF_CONDUCT.md` / `CODE_OF_CONDUCT.ru.md`

## Contributing

Please read `CONTRIBUTING.md` before opening a pull request.

## Security

Please read `SECURITY.md` for reporting vulnerabilities.

## License

This project uses dual licensing:

- `AGPL-3.0-or-later` for open-source usage
- Commercial license for proprietary/closed-source commercial usage

See:

- `LICENSE`
- `LICENSE_COMMERCIAL.md`
