# AGENTS.md - Arepos Server

This file provides essential guidance for AI coding agents working with the arepos-server codebase.

## Project Overview

Arepos Server is a Kotlin/Spring Boot backend service for managing domain models, notations, and related entities with comprehensive audit trail support. It provides a REST API for managing users, models, notations, node/link types, components, relations, and relation rules.

**Key Features:**
- REST API under `/api/v1/*`
- JWT-based authentication with refresh tokens
- Automatic database migrations via Liquibase
- Audit logging via PostgreSQL triggers
- Multi-tenant access control with ownership model
- File storage via MinIO (S3-compatible)
- Prometheus metrics and health endpoints

## Technology Stack

| Component  | Version/Technology               |
|------------|----------------------------------|
| Language   | Kotlin 2.2.21                    |
| Framework  | Spring Boot 3.5.7                |
| JDK        | 24-25                            |
| Database   | PostgreSQL 16+                   |
| Migrations | Liquibase                        |
| Build Tool | Gradle (Kotlin DSL)              |
| Testing    | JUnit 5, TestContainers, Mockito |
| Deployment | Kubernetes + Helm                |

## Project Structure

```
src/main/kotlin/ru/kavader/arepos/
├── AreposServerApplication.kt    # Application entry point
├── config/                       # Configuration classes
│   ├── AuditInterceptor.kt       # Hibernate interceptor for audit
│   ├── AuditRetentionScheduler.kt # Audit cleanup scheduler
│   ├── JpaConfig.kt              # JPA configuration
│   ├── MinioConfig.kt            # MinIO client configuration
│   └── MinioProperties.kt        # MinIO properties
├── controller/                   # REST controllers (API layer)
│   ├── *Controller.kt            # One per entity type
│   └── PagingSupport.kt          # Pagination utilities
├── model/                        # JPA entities
│   ├── Users.kt                  # User entity with roles
│   ├── Models.kt, Nodes.kt       # Domain model entities
│   ├── Notations.kt, Components.kt
│   ├── NodeTypes.kt, LinkTypes.kt
│   ├── Links.kt, Relations.kt, RelationRules.kt
│   ├── Diagrams.kt               # Visualization diagrams
│   ├── Files.kt, FileVersions.kt # File storage entities
│   ├── ResourceShares.kt         # Access sharing
│   └── AuditLog.kt               # Audit log entity
├── repository/                   # Spring Data JPA repositories
├── security/                     # Security configuration
│   ├── SecurityConfig.kt         # Spring Security setup
│   ├── JwtAuthenticationFilter.kt
│   ├── JwtTokenProvider.kt
│   ├── AuthCookieService.kt / CsrfFilter.kt
│   ├── CerbosDecisionService.kt
│   ├── ResourceAccessService.kt  # Permission checking
│   └── CurrentUser.kt            # Current user utilities
├── service/                      # Business services
│   ├── FileStorageService.kt
│   ├── ModelBatchSaveService.kt
│   ├── DiagramEditLockService.kt
│   └── MdFileLinkValidator.kt
└── metrics/                      # Custom metrics
    └── CustomMetricsService.kt

src/main/resources/
├── application.yaml              # Main configuration
└── db/changelog/                 # Liquibase migrations
    ├── db.changelog-master.yaml
    ├── 001-init.sql              # Initial schema
    └── 002-039-*.sql             # Incremental migrations

src/test/kotlin/ru/kavader/arepos/
├── support/PostgresContainerTest.kt  # TestContainers base
├── repository/RepositoryTestBase.kt  # Repository test utilities
├── repository/*RepositoryTest.kt     # Repository tests
├── controller/*ControllerTest.kt     # Controller tests
└── security/JwtTokenProviderTest.kt

charts/arepos-server/             # Helm chart for K8s deployment
├── Chart.yaml
├── values.yaml                   # Default values
└── templates/                    # K8s manifests
```

## Build Commands

```bash
# Full build with tests
./gradlew build

# Run all tests (requires Docker for TestContainers)
./gradlew test

# Run specific test class
./gradlew test --tests "*RepositoryTest"

# Run application locally
./gradlew bootRun

# Build Docker image
./gradlew bootBuildImage
# Build Docker image via Dockerfile
docker build -f Dockerfile -t arch/arepos-server:dev .

# Check Helm chart
helm lint ./charts/arepos-server
```

## Testing Strategy

### Test Architecture

Tests use **TestContainers** with shared PostgreSQL and Cerbos containers across the test suite:

- **Base Class**: `PostgresContainerTest` — PostgreSQL 16.4 plus `ghcr.io/cerbos/cerbos` with policies from `authz/cerbos/policies` (config `src/test/resources/cerbos-test/config.yaml`). `@DynamicPropertySource` sets `arepos.authz.cerbos.endpoint` to the mapped HTTP port. Requires **Docker** and a working directory at the project root when running tests (Gradle default).
- **Repository Tests**: Extend `RepositoryTestBase` which provides test data builders
- **Controller Tests**: Use MockMvc with mocked repositories

### Test Data Builders (RepositoryTestBase)

```kotlin
// Available builder methods for creating test data:
persistUser(email = "test@example.com")
persistModel(owner = user, name = "Test Model", version = "1.0.0")
persistNotation(owner = user)
persistNodeType(owner = user)
persistNode(model = model, nodeType = nodeType)
persistLinkType(owner = user)
persistLink(model = model, source = node1, target = node2)
persistComponent(notation = notation)
persistRelation(notation = notation)
persistRelationRule(relation = relation)
persistDiagram(model = model)
persistAuditLog(tableName = "users", operation = "INSERT")
```

### Running Tests

```bash
# All tests (Docker required)
./gradlew test

# Specific repository tests
./gradlew test --tests "ModelsRepositoryTest"

# Specific controller tests
./gradlew test --tests "ModelsControllerTest"
```

## Database & Migrations

### PostgreSQL Schema

- **Custom Domain Types**: `version_type` (semantic versioning), `email_type`, `url_type`, etc.
- **JSONB Columns**: Most entities have `attrs` field for flexible data storage
- **Audit Triggers**: Automatic audit logging via PostgreSQL trigger `audit_trigger`
- **Ownership**: All entities have `owner` field (FK to users) for access control

### Liquibase Migrations

Migrations are in `src/main/resources/db/changelog/`:
- `001-init.sql` - Initial schema with tables, indexes, triggers
- `002-039-*.sql` - Incremental changes (locks, outbox, refresh tokens, login lockout, …)
- `db.changelog-master.yaml` - Migration order

**Guidelines for new migrations:**
1. Create new SQL file with next sequence number
2. Add entry to `db.changelog-master.yaml`
3. Use `splitStatements: false` for PostgreSQL function definitions
4. Include `runOnChange: true` for idempotent changes

### Audit System

Audit logging works via:
1. `AuditInterceptor` captures user ID from `X-User-Id` header or ThreadLocal
2. Sets PostgreSQL session variable `app.current_user_id`
3. Trigger `audit_trigger` writes to `audit_log` table

For tests without HTTP context:
```kotlin
AuditInterceptor.setCurrentUserId(userId)
// ... perform operation
AuditInterceptor.clearCurrentUserId()
```

## Security & Authentication

### Cookie session (primary for browser / wArchi)

Browser clients authenticate via httpOnly cookies set by `AuthCookieService` on login/register/refresh:

| Cookie | Purpose |
|--------|---------|
| `warchi_access` | Short-lived JWT access token |
| `warchi_refresh` | Long-lived refresh token |
| `warchi_csrf` | Double-submit CSRF token (readable by JS) |

- Mutating requests (`POST`/`PUT`/`PATCH`/`DELETE`) require header `X-CSRF-Token` matching `warchi_csrf` (`CsrfFilter`). Login/register/refresh are exempt.
- `POST /api/v1/auth/logout` clears auth cookies.
- Login/register/refresh responses still include `accessToken`/`refreshToken` in JSON **and** set cookies (dual mode for API clients / tests).

### Bearer fallback

`JwtAuthenticationFilter` resolves the access token in order:

1. Cookie `warchi_access`
2. Header `Authorization: Bearer <token>`

Use Bearer for non-browser API clients and tests. Swagger documents Bearer; cookie+CSRF is the wArchi path.

### User Roles

```kotlin
enum class Role { USER, EDITOR, ADMIN }
```

- **USER**: Standard user, can manage own resources
- **EDITOR**: Legacy/shared-edit role (resource access is Cerbos + shares, not role bypass)
- **ADMIN**: Admin-panel capabilities via Cerbos `admin_panel` / `user_admin` policies

### Access Control

- Resources have `owner` field for ownership-based access
- `ResourceShares` allows sharing resources with specific permissions
- `ResourceAccessService` checks view/edit permissions via Cerbos (enforce-only)
- Model-editor notation reads with `?modelId=` (`NotationsController`, `NodeTypesController`, `LinkTypesController`): allowed when the user can view the notation directly **or** can edit the model and the notation version is used by an active diagram in that model (`canUseNotationInModelDiagramEditor`)
- Cerbos outage behavior: unavailable Cerbos → **503** `Authorization service is unavailable` (see `authz/cerbos/README.md`); policy deny remains **403**

### Collaboration / batch APIs

See `docs/api-collaboration.md` for:

- `POST /api/v1/diagram-locks/{id}/acquire` → **200** + `reason=LOCKED_BY_OTHER` when held by another user
- `POST /api/v1/models/{id}/batch-save` → **409** `BATCH_SAVE_CONFLICT` + `conflicts[]`
- model ZIP package, OEF normalize, and diagram-copy preview/commit
- API keys (`mode=all` / `grants`) and OIDC (`docs/oidc.md`)

### Environment Variables for Security

```bash
JWT_SECRET                    # Required in production (min 256 bits)
ADMIN_SECRET                  # For admin registration
JWT_ACCESS_EXPIRATION         # Default: PT30M
JWT_REFRESH_EXPIRATION        # Default: P7D
AREPOS_AUTH_COOKIE_SECURE     # Set true behind HTTPS (Secure cookie flag)
AREPOS_AUTH_CSRF_ENABLED      # Default true; disable only for controlled non-browser setups
AREPOS_AUTH_REGISTRATION_ENABLED  # Default true
```

## Configuration

### Application Properties (application.yaml)

```yaml
# Database
DB_URL=jdbc:postgresql://localhost:5432/arepos
DB_USERNAME=arepos
DB_PASSWORD=arepos

# JWT
JWT_SECRET=change-in-production
JWT_ACCESS_EXPIRATION=PT30M
JWT_REFRESH_EXPIRATION=P7D

# Admin
ADMIN_SECRET=your-secret-for-admin-registration

# Audit
AUDIT_RETENTION=PT24H
AUDIT_CLEANUP_CRON=0 0 * * * *

# File Storage
FILE_STORAGE=minio  # or "disabled" for tests
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=arepos-files
```

## Deployment

### Kubernetes Deployment

```bash
# Deploy to Kubernetes
./scripts/deploy.sh

# Undeploy
./scripts/undeploy.sh

# Validate Helm chart
./scripts/helmCheck.sh
```

### Deployment Options

Environment variables for deployment:
```bash
NAMESPACE=arch                    # K8s namespace
RELEASE_NAME=arepos-server        # Helm release name
VALUES_FILE=deploy-values.yaml    # Values file
POSTGRESQL_ENABLED=true           # Deploy PostgreSQL
BUILD_IMAGE=true                  # Build Docker image
IMAGE_BUILD_MODE=dockerfile       # buildpack | dockerfile (default: dockerfile)
BLUE_GREEN=false                  # Blue/green deployment
```

### Health Endpoints

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- Metrics: `GET /actuator/prometheus`

### Blue/Green Deployment

```bash
BLUE_GREEN=true BG_SWITCH=true ./scripts/deploy.sh
```

Creates two deployments (`-blue` and `-green`), service routes to active color.

## Development Guidelines

### Code Style

- **Language**: Kotlin with idiomatic patterns
- **Null Safety**: Use nullable types (`UUID?`) for JPA entity IDs
- **Immutability**: Use `val` in DTOs; JPA entities are mutable (`var`) for Hibernate updates
- **Entity Pattern**: Regular `class` with JPA annotations (not `data class`)

### Entity Conventions

JPA entities use `class` + `var` so Hibernate can mutate loaded instances. Shared field sets are expressed as interfaces (`CatalogTypeEntity` for node/link types, `NotationBoundEntity` for components/relations).

```kotlin
@Entity
@Table(name = "entities", schema = "public")
@JsonIgnoreProperties(ignoreUnknown = true)
class EntityName(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant? = null,

    @Column(name = "updated_at")
    var updatedAt: Instant? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "jsonb")
    var attrs: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner", nullable = false)
    var owner: Users
)
```

List/update helpers in `controller/`: `listPageByOwnerAndName`, `ResourceAccessService.listPageWithAdminBypass`, `CatalogTypeWriteSupport`, `NotationBoundEntityWriteSupport`, `ModelBoundEntityUpdateSupport`.

### Controller Conventions

```kotlin
@RestController
@RequestMapping("/api/v1/entities")
class EntityController(
    private val repository: EntityRepository,
    private val accessService: ResourceAccessService
) {
    @GetMapping
    fun list(pageable: Pageable): Page<EntityResponse> { ... }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): EntityResponse { ... }

    @PostMapping
    fun create(@RequestBody request: CreateRequest): EntityResponse { ... }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: UpdateRequest): EntityResponse { ... }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) { ... }
}
```

### Repository Conventions

```kotlin
@Repository
interface EntityRepository : JpaRepository<Entity, UUID> {
    fun findByOwner(owner: Users, pageable: Pageable): Page<Entity>
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Entity>
}
```

### Pagination

Use Spring Data's `Pageable` and `Page` types:
```kotlin
@GetMapping
fun list(pageable: Pageable): Page<EntityResponse> {
    return repository.findAll(pageable).map { it.toResponse() }
}
```

For in-memory pagination, use `toPage()` extension from `PagingSupport.kt`.

## API Reference

OpenAPI documentation is generated at runtime by springdoc:
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

Main endpoints:
- `POST /api/v1/auth/register` - User registration (cookies + optional tokens in body)
- `POST /api/v1/auth/login` - Login
- `POST /api/v1/auth/refresh` - Refresh (cookie and/or body refresh token)
- `POST /api/v1/auth/logout` - Clear auth cookies
- `GET /api/v1/auth/me` - Current user info
- `GET /api/v1/auth/sso/config` - OIDC SSO flag + display name (see `docs/oidc.md`)
- `POST /api/v1/auth/api-keys/exchange` - API key → short-lived JWT (MCP / non-browser)
- `GET /api/v1/models` - List models
- `POST /api/v1/models/{id}/batch-save` - Atomic node/link/diagram save (see `docs/api-collaboration.md`)
- `GET /api/v1/models/{id}/package` / `POST /api/v1/models/package` - Model ZIP export / async import
- `POST /api/v1/models/{id}/oef/normalize` - Multipart OEF XML → compact JSON for client import wizard (edit permission; up to ~100 MB)
- `POST /api/v1/models/{id}/diagram-copies/preview|commit` - Copy a diagram into another model
- `POST /api/v1/diagram-locks/{id}/acquire` - Diagram edit lock (see `docs/api-collaboration.md`)
- `GET /api/v1/nodes` - List nodes
- `GET /api/v1/notations` - List notations
- And more...

## Entity Relationships

```
Users
├── Models ──┬─> Nodes (tree-structured with parent-child)
│            └─> Links (source/target between nodes)
│            └─> Diagrams (model + notation based)
├── Notations ─┬─> Components (links to NodeTypes)
│              └─> Relations (links to LinkTypes) ─> RelationRules
├── NodeTypes
└── LinkTypes

ResourceShares - cross-cutting permission delegation
```

## Important Notes

1. **UUID Generation**: All entities use `GenerationType.UUID` for IDs
2. **Versioning**: Models, Notations, Components, Relations use semantic versioning enforced by PostgreSQL domain type `version_type`
3. **Soft Delete**: Some entities have `deleted` flag for soft deletion
4. **Audit Trail**: All changes are automatically logged to `audit_log` table
5. **JSONB Attributes**: Use `attrs` field for extensible, schema-less data
6. **X-User-Id Header**: Required for audit tracking on modifying operations

## License

Dual licensing:
- `AGPL-3.0-or-later` for open-source usage
- Commercial license for proprietary usage

See `LICENSE` and `LICENSE_COMMERCIAL.md` for details.
