# Вклад в arepos-server

Спасибо за интерес к проекту.

## Требования для разработки

- JDK 24/25
- Docker
- PostgreSQL (для ручного локального запуска)

## Локальный запуск

```bash
./gradlew build
./gradlew test
```

Запуск приложения:

```bash
./gradlew bootRun
```

## Ветки и Pull Request

1. Создайте ветку от `master`
2. Делайте коммиты атомарными и сфокусированными
3. Добавляйте/обновляйте тесты при изменении поведения
4. Открывайте PR с описанием контекста и плана проверки

## Рекомендации по коммитам

- Используйте чёткие заголовки в повелительном наклонении
- Поясняйте *зачем* сделано изменение, а не только *что* изменено
- По возможности отделяйте рефакторинг от функциональных изменений

## Что запускать перед PR

```bash
./gradlew build
./gradlew test
```

Если меняется деплой:

```bash
helm lint ./charts/arepos-server
bash -n ./scripts/deploy.sh
```

Если меняется авторизация:

```bash
bash ./scripts/verify-cerbos-only.sh
```

## Continuous Integration

GitHub Actions (`.github/workflows/ci.yml`) запускается на каждый pull request и на push в `master`:

| Job | Что делает |
|-----|------------|
| **Build & test** | JDK 24 + `./gradlew build` (Testcontainers нужен Docker) |
| **Cerbos-only checks** | `scripts/verify-cerbos-only.sh` |
| **Helm chart** | `helm lint` / `helm template` + smoke-проверки + `bash -n` скриптов |
| **Docker image** | Сборка multi-stage `Dockerfile` (без push) |

Перед merge CI должен быть зелёным; локальный `./gradlew build` соответствует основному test job.

## Checklist для PR

- [ ] Проект собирается локально
- [ ] Тесты проходят локально
- [ ] CI зелёный (или эквивалентные локальные проверки)
- [ ] Новое поведение покрыто тестами
- [ ] Документация обновлена при необходимости
- [ ] В репозиторий не добавлены секреты или приватные данные

## Баг-репорты

Указывайте:

- ожидаемое поведение
- фактическое поведение
- шаги воспроизведения
- логи/stack trace (если есть)
