# arepos-server

Backend service for managing domain models, notations, and related entities with full audit trail support.

Русская версия: `README.ru.md`

The project is built with Kotlin + Spring Boot, stores flexible entity attributes in PostgreSQL JSONB, and is designed to run both locally and in Kubernetes.

## Features

- REST API under `/api/v1/*` for users, models, notations, node/link types, components, relations, and relation rules
- Automatic database migrations via Liquibase
- Audit logging for data changes
- JWT-based authentication and refresh flow
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
- Kubernetes + Helm (for cluster deployment)

## Local Development

### 1) Configure environment

Default values are defined in `src/main/resources/application.yaml`.

Important environment variables:

- `DB_URL` (default: `jdbc:postgresql://localhost:5432/arepos`)
- `DB_USERNAME` (default: `arepos`)
- `DB_PASSWORD` (default: `arepos`)
- `JWT_SECRET` (required for production)
- `ADMIN_SECRET` (recommended for admin bootstrap flow)

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
- Health endpoints:
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
- Prometheus metrics:
  - `/actuator/prometheus`

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
