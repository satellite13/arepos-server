---
name: release-arepos-server
description: Executes the full release cycle for arepos-server: bump version in build.gradle.kts and Helm/chart files, run Gradle build and tests, create release commit and annotated tag, push. Includes post-deploy verification (rollout, health, GET /api/v1/system/version). Use when the user asks to release arepos-server, make a release, tag a version, or when following MEMORY.md release playbook.
---

# Релиз arepos-server

Для arepos-server «релизим» — полный релизный цикл: проверка состояния → подъём версии → сборка/тесты → релизный коммит → тег → push → проверка после деплоя.

## Чеклист

- [ ] Проверить рабочее состояние
- [ ] Поднять версию релиза во всех файлах
- [ ] Прогнать проверки (build, при необходимости test)
- [ ] Релизный коммит
- [ ] Аннотированный тег
- [ ] Push коммита и тегов
- [ ] После деплоя — проверка версии

## Шаги

### 1. Проверить рабочее состояние

```bash
git status --short
```

Убедиться, что в релиз входят только нужные изменения.

### 2. Поднять версию релиза

Версия формата **X.Y.Z** (без `-SNAPSHOT`). Обновить во всех местах:

| Файл | Что менять |
|------|------------|
| `build.gradle.kts` | `version = "X.Y.Z"` (без `-SNAPSHOT`; версия в Swagger из buildInfo) |
| `charts/arepos-server/values.yaml` | `image.tag: "X.Y.Z"` |
| `deploy-values.yaml` | `image.tag: "X.Y.Z"` |
| `charts/arepos-server/Chart.yaml` | `version` (минимум patch) и `appVersion: "X.Y.Z"` |

### 3. Проверки перед релизом

```bash
./gradlew build
```

При необходимости отдельно: `./gradlew test`.

### 4. Релизный коммит

```bash
git add <релизные файлы>
git commit -m "Release vX.Y.Z."
```

### 5. Аннотированный тег

```bash
git tag -a vX.Y.Z -m "Release vX.Y.Z."
```

### 6. Публикация в remote

```bash
git push
git push --tags
```

### 7. Проверка публикации

```bash
git log --oneline -1
git tag --list "vX.Y.Z"
```

## Проверка после деплоя

1. Выполнить деплой (`deploy.sh`) с нужными параметрами окружения.
2. Убедиться, что скрипт успешно прошёл:
   - rollout deployment
   - соответствие ожидаемого образа
   - health check
   - проверка версии: `GET /api/v1/system/version`
3. Если версия из endpoint не совпадает с релизной **X.Y.Z**, деплой считается неуспешным.

## Шаблоны

| Что | Значение |
|-----|----------|
| Коммит | `Release vX.Y.Z.` |
| Имя тега | `vX.Y.Z` |
| Аннотация тега | `Release vX.Y.Z.` |
| Команда тега | `git tag -a vX.Y.Z -m "Release vX.Y.Z."` |
