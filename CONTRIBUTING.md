# Contributing to arepos-server

Thanks for your interest in contributing.

## Development Prerequisites

- JDK 24/25
- Docker
- PostgreSQL (for local manual runs)

## Local Setup

```bash
./gradlew build
./gradlew test
```

Run the application:

```bash
./gradlew bootRun
```

## Branching and PR Workflow

1. Fork or create a feature branch from `master`
2. Keep commits focused and atomic
3. Add/update tests for behavior changes
4. Open a pull request with context and test notes

## Commit Guidelines

- Use clear, imperative commit titles
- Mention *why* the change is needed, not only *what* changed
- Keep refactoring separate from functional changes when possible

## Testing Expectations

Before creating a PR, run:

```bash
./gradlew build
./gradlew test
```

If your change affects deployment:

```bash
helm lint ./charts/arepos-server
bash -n ./scripts/deploy.sh
```

If your change affects authorization:

```bash
bash ./scripts/verify-cerbos-only.sh
```

## Continuous Integration

GitHub Actions workflow `.github/workflows/ci.yml` runs on every pull request and on pushes to `master`:

| Job | What it does |
|-----|----------------|
| **Build & test** | JDK 24 + `./gradlew build` (Testcontainers needs Docker) |
| **Cerbos-only checks** | `scripts/verify-cerbos-only.sh` (static authz invariants + focused tests) |
| **Helm chart** | `helm lint` / `helm template` + kubectl client dry-run + `bash -n` on deploy scripts |
| **Docker image** | Multi-stage `Dockerfile` build (no push) |

Fix CI failures before merge; local `./gradlew build` should match the main test job.

## Pull Request Checklist

- [ ] Code builds locally
- [ ] Tests pass locally
- [ ] CI is green (or equivalent local checks)
- [ ] Tests are in place to cover new behavior.
- [ ] Documentation is updated if needed
- [ ] No secrets or private data were added

## Reporting Issues

Please include:

- expected behavior
- actual behavior
- reproduction steps
- logs or stack trace (if available)
