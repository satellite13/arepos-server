# Cerbos policies (local workflow)

Этот каталог хранит baseline-политики Cerbos для `arepos-server`.

## Структура

- `policies/` — resource policies (YAML).
- `releases/` — локальные versioned-архивы policy bundle (создаются скриптом).

## Локальный релиз policy bundle

Запуск:

`./scripts/release-cerbos-policies.sh`

Скрипт делает:

1. Проверку чистоты git (можно отключить флагом).
2. Валидацию policy через `cerbos compile` (если CLI не установлен — fallback через Docker image).
3. Упаковку в `authz/cerbos/releases/policy-<git-sha>.tar.gz`.
4. (Опционально) деплой через `deploy.sh` в `shadow` режиме.

Дополнительно:

- `SKIP_POLICY_TESTS=false` — запускать policy-тесты в `cerbos compile`.

## Проверка shadow-сходимости

Базовый отчёт (с рестарта pod):

`./scripts/check-cerbos-shadow.sh`

Создать baseline (точка X):

`WRITE_BASELINE=true MAX_ERRORS=999 ./scripts/check-cerbos-shadow.sh`

Проверка "с момента X" (дельта от baseline):

`MODE=baseline ./scripts/check-cerbos-shadow.sh`

## Gate перед enforce в deploy.sh

`deploy.sh` может автоматически блокировать релиз, если shadow-gate не пройден:

`CHECK_SHADOW_GATE=true SHADOW_GATE_MODE=baseline ./deploy.sh`

По умолчанию gate срабатывает только при попытке включить enforce через `HELM_EXTRA_ARGS`
(`cerbosEnforceEnabled=true` или `cerbos.mode=ENFORCE`).

Есть shorthand-флаги, чтобы не писать длинный `HELM_EXTRA_ARGS`:

- `CERBOS_SHADOW=true` — включает Cerbos в shadow-режиме.
- `CERBOS_ENFORCE=true` — включает Cerbos в enforce-режиме.
- `CERBOS_OFF=true` — гарантированно отключает Cerbos и сбрасывает shadow/enforce.
- `CERBOS_BUNDLE_VERSION=policy-<sha>` — версия policy bundle.
- `CERBOS_FAIL_OPEN=true|false` — стратегия при ошибке запроса к Cerbos.
- `CERBOS_DEPLOY=true|false` — поднимать ли Cerbos через Helm chart.

Пороги можно задать переменными:

- `SHADOW_GATE_MAX_MISMATCH` (по умолчанию `0`)
- `SHADOW_GATE_MAX_ERRORS` (по умолчанию `0`)
- `SHADOW_GATE_MIN_MATCH_RATE` (по умолчанию `99.9`)

Мягкий режим для dev:

- `SHADOW_GATE_WARN_ONLY=true` — при непройденном gate деплой не блокируется.

## Важно

- Текущий baseline покрывает только owner/admin для ресурсов `model`, `notation`, `diagram`.
- Семантика share пока остается в legacy-логике backend и должна быть добавлена отдельными policy-правилами до включения `enforce`.
