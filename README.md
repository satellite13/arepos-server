# arepos-server

Backend service for managing domain models, notations, and related entities with full audit trail support.

Русская версия: `README.ru.md`

The project is built with Kotlin + Spring Boot, stores flexible entity attributes in PostgreSQL JSONB, and is designed to run both locally and in Kubernetes.

## Features

- REST API under `/api/v1/*` for users, models, notations, node/link types, components, relations, and relation rules
- Automatic database migrations via Liquibase
- Audit logging for data changes
- JWT-based authentication with refresh token rotation (single-use refresh tokens stored server-side)
- Browser cookie session (`warchi_access` / `warchi_refresh`) + CSRF (`warchi_csrf` / `X-CSRF-Token`); Bearer header remains supported for API clients
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
  security/     # JWT, cookies/CSRF, Cerbos, ResourceAccessService
  service/      # Business services (batch save, diagram locks, files, …)
  config/       # configuration and interceptors

src/main/resources/
  application.yaml
  db/changelog/ # Liquibase migrations (001–039+)

charts/arepos-server/
  templates/    # Helm templates
  values.yaml   # chart values
```

Authz policies live under `authz/cerbos/`. Collaboration API notes: `docs/api-collaboration.md`. OIDC SSO setup: `docs/oidc.md`.

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
- `AREPOS_AUTH_COOKIE_SECURE` (set `true` behind HTTPS so auth cookies get the Secure flag)
- `AREPOS_AUTH_CSRF_ENABLED` (default `true`; double-submit CSRF for cookie-session mutating requests)
- `AREPOS_AUTH_REGISTRATION_ENABLED` (default `true`)
- Optional Keycloak-compatible OIDC SSO (`GET /api/v1/auth/sso/config` reports `{ enabled, displayName }`). Full setup: [`docs/oidc.md`](docs/oidc.md).
  - `OIDC_ENABLED` (`auto` by default — on when issuer/client/secret/redirect are set; or `true`/`false`)
  - `OIDC_ISSUER_URI` (trailing `/` required), `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_REDIRECT_URI`
  - `OIDC_POST_LOGOUT_URI`, `OIDC_FRONTEND_URL`, `OIDC_SCOPE` (default `openid profile email`)
  - `OIDC_DISPLAY_NAME` (login button brand, default `SSO`)
- `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` (in `prod` profile, `*` is forbidden and startup fails)
- `MODEL_SYNC_OUTBOX_ENABLED` (enable transactional outbox for model sync)
- `MODEL_SYNC_OUTBOX_PUBLISH_MS`, `MODEL_SYNC_OUTBOX_BATCH_SIZE` (outbox publisher tuning)
- `CERBOS_CIRCUIT_FAILURE_THRESHOLD`, `CERBOS_CIRCUIT_OPEN_DURATION` (authz circuit breaker)
- `HIBERNATE_DEFAULT_BATCH_FETCH_SIZE`, `HIBERNATE_JDBC_BATCH_SIZE` (JPA performance tuning)
- `FILE_STORAGE` (`minio` by default; use `disabled` for local run without file storage)
- `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `MINIO_REGION` (when `FILE_STORAGE=minio`; use `ru-central1` for Yandex Object Storage)

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
docker build -f Dockerfile -t arch/arepos-server:dev .  # build via Dockerfile
```

## API

- Swagger UI: `/swagger-ui.html` — interactive API documentation
- OpenAPI spec (JSON): `/v3/api-docs`
- Auth: cookie session (primary for wArchi) or `Authorization: Bearer`; CSRF required for cookie-session mutating calls — see `AGENTS.md` and OpenAPI description
- Collaboration contracts: `docs/api-collaboration.md` (diagram locks, batch-save)
- Error responses from exception handlers follow `{ error, message, traceId }`
- Notation, node-type, and link-type reads from the model editor may pass `?modelId=`; access is granted with direct notation permission **or** model edit rights when that notation version is used by an active diagram in the model
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
- OIDC SSO enablement (Keycloak-compatible): `docs/oidc.md`

## Deployment

- `scripts/deploy.sh` - deploy to Kubernetes using Helm
- `scripts/undeploy.sh` - uninstall release
- `scripts/helmCheck.sh` - lint/template smoke checks
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
