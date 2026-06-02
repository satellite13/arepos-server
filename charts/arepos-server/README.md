# arepos-server Helm chart

This chart deploys `arepos-server` and optional local infrastructure components.

## What chart can deploy

- `arepos-server` application
- PostgreSQL (`postgresql.enabled=true`)
- MinIO (`minio.enabled=true`)
- Cerbos sidecar deployment (`cerbos.deploy=true`) with policy bundle settings
- Blue/Green topology (`blueGreen.enabled=true`)

## Basic usage

```bash
helm upgrade --install arepos-server ./charts/arepos-server -n arch -f ./charts/arepos-server/values.yaml
```

With local deploy helper:

```bash
./scripts/deploy.sh
```

## Important values

- `image.repository`, `image.tag`, `image.pullPolicy`
- `blueGreen.enabled`, `blueGreen.activeColor`, `blueGreen.image.blueTag`, `blueGreen.image.greenTag`
- `postgresql.enabled`, `postgresql.auth.*`, `postgresql.persistence.*`
- `minio.enabled`, `minio.auth.*`, `minio.bucket`, `minio.persistence.*`
- `cerbos.enabled`, `cerbos.deploy`, `cerbos.host`, `cerbos.port`, `cerbos.timeoutMs`, `cerbos.bundleVersion`
- `env` (additional environment variables)

## Deploy script flags (most used)

`scripts/deploy.sh` supports key environment flags:

- `NAMESPACE` (default `arch`)
- `RELEASE_NAME` (default `arepos-server`)
- `VALUES_FILE` (default `deploy-values.yaml`)
- `IMAGE_TAG` (defaults to `<appVersion>-<git-sha>`)
- `BUILD_IMAGE` (`true|false`)
- `IMAGE_BUILD_MODE` (`buildpack|dockerfile`, default `buildpack`)
- `POSTGRESQL_ENABLED` (`true|false`)
- `BLUE_GREEN` (`true|false`)
- `BG_SWITCH` (`true|false`)
- `CERBOS_DEPLOY` (`true|false`)
- `CERBOS_BUNDLE_VERSION` (default `policy-<git-sha>`)

Example:

```bash
BLUE_GREEN=true BG_SWITCH=true IMAGE_TAG=0.3.1-abcdef IMAGE_BUILD_MODE=dockerfile CERBOS_DEPLOY=true CERBOS_BUNDLE_VERSION=policy-abcdef ./scripts/deploy.sh
```

## Cerbos notes

- Service is designed for Cerbos-enabled authorization.
- For policy rollout/runbook, see:
  - `authz/cerbos/README.md`
  - `authz/cerbos/RUNBOOK.md`
  - `authz/cerbos/VERIFY.md`
