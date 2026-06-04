---
name: release-arepos-server
description: Executes the full release cycle for arepos-server - bump version in build.gradle.kts and Helm/chart files, run build, create release commit and annotated tag, push. Use when the user asks to release arepos-server, make a release, tag a version, or when following MEMORY.md release playbook.
---

# Релиз arepos-server

Для arepos-server **«релизим»** — это полный цикл: проверка → подъём версии → сборка/тесты → коммит → тег → push. Не только `git push`.

## Чеклист с командами

Выполнять по порядку.

### 1. Проверить состояние

- `git status --short`
- Убедиться, что в релиз входят только нужные изменения

### 2. Поднять версии

- Обновить версию "X.Y.Z" во всех файлах:
  - `build.gradle.kts` — `version = "X.Y.Z"` (без `-SNAPSHOT`)
  - `charts/arepos-server/values.yaml` — `image.tag: "X.Y.Z"`
  - `deploy-values.yaml` — `image.tag: "X.Y.Z"`
  - `charts/arepos-server/Chart.yaml` — `version` и `appVersion: "X.Y.Z"`
- Если изменения маленькие — патч, если большие — мажор или минор по семантике

### 3. Проверки перед релизом

- `./gradlew build`
- При необходимости: `./gradlew test`

### 4. Релизный коммит

- `git add <релизные файлы>`
- `git commit -m "Release vX.Y.Z."`

### 5. Аннотированный тег

- `git tag -a vX.Y.Z -m "Release vX.Y.Z."`

### 6. Публикация в remote

- `git push`
- `git push --tags`

### 7. Проверка

- `git log --oneline -1`
- `git tag --list "vX.Y.Z"`
- Убедиться, что тег и коммит есть в remote

### 8. После деплоя

- Выполнить `scripts/deploy.sh` с нужными параметрами
- Убедиться: rollout, образ, health check
- Проверить версию: `GET /api/v1/system/version` — должна совпадать с X.Y.Z

## Заметки

- Если пользователь не уточнил иное, релиз = все шаги выше, а не только push.
- Шаблон коммита и тега: `Release vX.Y.Z.`
