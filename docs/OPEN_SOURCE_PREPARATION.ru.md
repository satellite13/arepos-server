# Чеклист подготовки к Open Source

Документ помогает подготовить `arepos-server` к публичному open-source релизу.

## 1. Легал и метаданные репозитория

- [ ] Выбрать лицензию (`MIT`, `Apache-2.0`, `GPL-3.0` и т.д.) и добавить `LICENSE`
- [ ] Проверить совместимость лицензий зависимостей
- [ ] Добавить описание репозитория, topics и homepage
- [ ] Указать контакты/мейнтейнеров

## 2. Security readiness

- [ ] Удалить/заменить все dev-секреты и небезопасные дефолты из публичных конфигов
- [ ] Проверить, что в истории/скриптах/values/docs нет реальных кредов
- [ ] Добавить/проверить `SECURITY.md` с процессом disclosure
- [ ] Проверить безопасные production-настройки (`JWT_SECRET`, `ADMIN_SECRET`)
- [ ] Проверить контроль доступа к endpoint-ам и контекст пользователя

## 3. Минимальный набор документации

- [x] `README.md` с запуском, тестами и деплоем
- [x] `CONTRIBUTING.md` с процессом внесения изменений
- [x] `CODE_OF_CONDUCT.md`
- [x] `SECURITY.md`
- [ ] Примеры API-сценариев (auth, CRUD, pagination)
- [ ] Архитектурная схема (опционально, но желательно)

## 4. Quality gate (build/test)

- [ ] CI pipeline для `build` и `test` на pull request
- [ ] Опционально: lint/static analysis
- [ ] Политика по coverage (если нужна)
- [ ] Воспроизводимый локальный setup

Рекомендуемый минимум для CI:

1. `./gradlew build`
2. `./gradlew test`
3. `helm lint charts/arepos-server`

## 5. Release process

- [ ] Определить версионирование (сейчас semver-like snapshot в Gradle)
- [ ] Зафиксировать стратегию changelog (`CHANGELOG.md` или policy)
- [ ] Зафиксировать формат тегов (`vX.Y.Z`)
- [ ] Описать политику публикации контейнеров (registry, immutable tags)

## 6. Kubernetes/Operations readiness

- [ ] Документировать требуемые ресурсы/права в Kubernetes
- [ ] Проверить production-поведение `deploy.sh`
- [ ] Проверить blue/green rollout и rollback
- [ ] Документировать backup/restore БД
- [ ] Документировать ожидания по совместимости миграций

## 7. Аудит чувствительных данных

Перед публичным релизом вручную проверить:

- [ ] Нет секретов в:
  - `deploy-values.yaml`
  - shell-скриптах
  - curl-примерах
  - Helm values
- [ ] Нет приватных endpoint-ов/доменов/internal-only ссылок
- [ ] Нет персональных данных в тестовых фикстурах/логах/доках

## 8. Community setup (опционально)

- [ ] Добавить issue templates (`bug`, `feature request`)
- [ ] Добавить pull request template
- [ ] Настроить labels и triage-процесс
- [ ] Определить политику поддержки (best effort/SLA/none)

## Команды перед первым публичным тегом

```bash
./gradlew build
./gradlew test
helm lint ./charts/arepos-server
bash -n ./deploy.sh
```
