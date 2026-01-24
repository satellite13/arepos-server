# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Arepos Server is a Spring Boot application for managing domain models with comprehensive audit trails. It uses Kotlin, PostgreSQL with JSONB support, and is designed for Kubernetes deployment.

## Technology Stack

- **Language:** Kotlin 2.2.21
- **Framework:** Spring Boot 3.5.7
- **Java:** JDK 24-25
- **Database:** PostgreSQL 16.4 with Liquibase migrations
- **Build:** Gradle with Kotlin DSL

## Build Commands

```bash
./gradlew build              # Full build with tests
./gradlew test               # Run all tests (requires Docker for TestContainers)
./gradlew test --tests "*RepositoryTest"  # Run specific test class
./gradlew bootBuildImage     # Build Docker image
```

## Architecture

### Layered Structure
- `controller/` - REST endpoints at `/api/v1/*` with pagination support
- `repository/` - Spring Data JPA repositories
- `model/` - JPA entities with UUID primary keys
- `config/` - Spring configuration including audit interceptor

### Key Patterns

**Audit System:** All database changes are automatically logged via PostgreSQL triggers. The `AuditInterceptor` captures user ID from `X-User-Id` HTTP header and sets it in the PostgreSQL session variable `app.current_user_id`. For tests without HTTP context, use `AuditInterceptor.setCurrentUserId(uuid)`.

**JSONB Attributes:** Most entities have an `attrs` field (JSONB) for flexible, extensible data storage.

**Ownership Model:** Entities have an `owner` field (FK to Users) enabling multi-tenant access control.

**Versioning:** Models, Notations, Components, and Relations use semantic versioning enforced by PostgreSQL domain type.

### Entity Relationships

```
Users ─┬─> Models ─> Nodes (tree-structured, parent-child)
       │         └─> Links (source/target between nodes)
       ├─> Notations ─┬─> Components (links to NodeTypes)
       │              └─> Relations (links to LinkTypes) ─> RelationRules
       ├─> NodeTypes
       └─> LinkTypes
```

## Testing

Tests use TestContainers with a shared PostgreSQL container across the test suite. Extend `RepositoryTestBase` for repository tests - it provides test data builders:

- `persistUser()`, `persistModel()`, `persistNotation()`, `persistNode()`
- `persistNodeType()`, `persistLinkType()`, `persistComponent()`
- `persistLink()`, `persistRelation()`, `persistRelationRule()`

Controller tests should mock repositories and use MockMvc.

## Configuration

Database connection via environment variables (with defaults for local development):
- `DB_URL` - jdbc:postgresql://localhost:5432/arepos
- `DB_USERNAME` - arepos
- `DB_PASSWORD` - arepos

## Deployment

Kubernetes deployment uses Helm chart in `charts/arepos-server/`:

```bash
./deploy.sh              # Deploy to Kubernetes
./undeploy.sh            # Remove from Kubernetes
./helmCheck.sh           # Validate Helm chart
```

Health endpoints: `/actuator/health/liveness`, `/actuator/health/readiness`
Metrics: `/actuator/prometheus`

## API

OpenAPI specification is in `openapi.yaml`. All endpoints require `X-User-Id` header for audit tracking.
