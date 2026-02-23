# Open Source Preparation Checklist

This document helps prepare `arepos-server` for public open-source release.

## 1. Legal and Repository Metadata

- [ ] Choose a license (`MIT`, `Apache-2.0`, `GPL-3.0`, etc.) and add `LICENSE`
- [ ] Ensure all dependencies are license-compatible
- [ ] Add repository description, topics, and homepage URL
- [ ] Add maintainer/contact information

## 2. Security Readiness

- [ ] Replace all development secrets and defaults in public configs/examples
- [ ] Ensure no real credentials are present in history, scripts, values, or docs
- [ ] Add/verify `SECURITY.md` with disclosure process
- [ ] Ensure auth defaults are safe for production (`JWT_SECRET`, `ADMIN_SECRET`)
- [ ] Review endpoint access controls and header-based user context usage

## 3. Documentation Baseline

- [x] `README.md` with setup, run, test, deploy overview
- [x] `CONTRIBUTING.md` with contribution workflow
- [x] `CODE_OF_CONDUCT.md`
- [x] `SECURITY.md`
- [ ] API examples for most common scenarios (auth, CRUD, pagination)
- [ ] Architecture diagram (optional but recommended)

## 4. Build and Test Quality Gate

- [ ] CI pipeline for `build` and `test` on pull requests
- [ ] Optional quality checks (lint/static analysis)
- [ ] Minimum test coverage policy (if required)
- [ ] Reproducible local setup instructions

Recommended CI baseline:

1. Run `./gradlew build`
2. Run `./gradlew test`
3. Validate Helm chart (`helm lint charts/arepos-server`)

## 5. Release Process

- [ ] Define versioning policy (currently semver-like snapshots in Gradle)
- [ ] Add changelog strategy (`CHANGELOG.md` or release notes policy)
- [ ] Define release tags format (`vX.Y.Z`)
- [ ] Define container publishing policy (registry, immutable tags)

## 6. Kubernetes/Operations Readiness

- [ ] Document required Kubernetes resources and permissions
- [ ] Validate `deploy.sh` default behavior for production
- [ ] Validate blue/green deployment flow and rollback steps
- [ ] Document database backup/restore strategy
- [ ] Document migration compatibility expectations

## 7. Sensitive Data Audit

Before going public, manually verify:

- [ ] No secrets in:
  - `deploy-values.yaml`
  - shell scripts
  - example curls
  - Helm values
- [ ] No private endpoints, domains, or internal-only references
- [ ] No personal data in test fixtures/logs/docs

## 8. Community Setup (Optional but Recommended)

- [ ] Add issue templates (`bug`, `feature request`)
- [ ] Add pull request template
- [ ] Add labels and triage process
- [ ] Define support policy (best effort, SLA, none)

## Pre-Release Command Checklist

Run before first public tag:

```bash
./gradlew build
./gradlew test
helm lint ./charts/arepos-server
bash -n ./deploy.sh
```
