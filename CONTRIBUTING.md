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
- Keep refactors separate from functional changes when possible

## Testing Expectations

Before creating a PR, run:

```bash
./gradlew build
./gradlew test
```

If your change affects deployment:

```bash
helm lint ./charts/arepos-server
bash -n ./deploy.sh
```

## Pull Request Checklist

- [ ] Code builds locally
- [ ] Tests pass locally
- [ ] New behavior is covered by tests
- [ ] Documentation is updated if needed
- [ ] No secrets or private data were added

## Reporting Issues

Please include:

- expected behavior
- actual behavior
- reproduction steps
- logs or stack trace (if available)
