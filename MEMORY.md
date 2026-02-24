# MEMORY

## Arepos Server Release Playbook

Для `arepos-server` слово "релизим" означает полный релизный цикл:

1. Проверяем состояние ветки и состав изменений.
2. Поднимаем релизную версию (без `-SNAPSHOT`) во всех релевантных файлах.
3. Прогоняем проверки сборки/тестов.
4. Делаем релизный коммит.
5. Ставим аннотированный git-тег.
6. Пушим коммит и тег.
7. После деплоя проверяем, что развернулась именно ожидаемая версия.

### Пошаговый чеклист с командами

1. Проверить рабочее состояние:
   - `git status --short`
   - убедиться, что в релиз входят только нужные изменения

2. Поднять версию релиза:
   - `build.gradle.kts` -> `version = "X.Y.Z"` (без `-SNAPSHOT`)
   - `openapi.yaml` -> `info.version: X.Y.Z`
   - `charts/arepos-server/values.yaml` -> `image.tag: "X.Y.Z"`
   - `deploy-values.yaml` -> `image.tag: "X.Y.Z"`
   - `charts/arepos-server/Chart.yaml`:
     - поднять `version` chart (минимум patch)
     - синхронизировать `appVersion: "X.Y.Z"`

3. Прогнать проверки перед релизом:
   - `./gradlew build`
   - при необходимости отдельно `./gradlew test`

4. Сделать релизный коммит:
   - `git add <релизные файлы>`
   - `git commit -m "Release vX.Y.Z."`

5. Поставить аннотированный тег:
   - `git tag -a vX.Y.Z -m "Release vX.Y.Z."`

6. Опубликовать в удаленный репозиторий:
   - `git push`
   - `git push --tags`

7. Проверить публикацию:
   - `git log --oneline -1`
   - `git tag --list "vX.Y.Z"`

### Проверка после деплоя

1. Выполнить деплой (`deploy.sh`) с нужными параметрами окружения.
2. Убедиться, что скрипт успешно прошел проверки:
   - rollout deployment
   - соответствие ожидаемого образа
   - health check
   - проверка endpoint версии: `GET /api/v1/system/version`
3. Если версия из endpoint не совпадает с релизной `X.Y.Z`, деплой считается неуспешным.

## Message Templates

### Commit message

`Release vX.Y.Z.`

### Tag

- tag name: `vX.Y.Z`
- annotation: `Release vX.Y.Z.`

Команда:
- `git tag -a vX.Y.Z -m "Release vX.Y.Z."`
