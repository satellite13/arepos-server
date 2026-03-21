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

## Parity check для local vs infra

Чтобы перед `enforce` убедиться, что ключевые Cerbos-параметры совпадают в обоих контурах:

`INFRA_VALUES_FILE=infra/arepos-server-values.yaml ./scripts/check-cerbos-config-parity.sh`

Проверяемые параметры:

- `AREPOS_AUTHZ_CERBOS_MODE`
- `AREPOS_AUTHZ_CERBOS_REQUEST_TIMEOUT`
- `AREPOS_AUTHZ_CERBOS_BUNDLE_VERSION`
- `AREPOS_AUTHZ_CERBOS_SHADOW_ENABLED`
- `AREPOS_AUTHZ_CERBOS_ENFORCE_ENABLED`
- `AREPOS_AUTHZ_CERBOS_FAIL_OPEN`
- `AREPOS_AUTHZ_CERBOS_ENABLED`

Опционально можно включить сравнение endpoint:

`INCLUDE_ENDPOINT=true INFRA_VALUES_FILE=infra/arepos-server-values.yaml ./scripts/check-cerbos-config-parity.sh`

Интеграция в `deploy.sh`:

`CHECK_CERBOS_PARITY=true CERBOS_INFRA_VALUES_FILE=infra/arepos-server-values.yaml ./deploy.sh`

Для мягкого режима:

- `CERBOS_PARITY_WARN_ONLY=true` — не блокировать деплой при несовпадениях.

## Операционный runbook

Подробные пошаговые процедуры rollout/rollback вынесены в:

- `authz/cerbos/RUNBOOK.md`

Preflight перед promote можно запускать одной командой:

`INFRA_VALUES_FILE=<infra-values.yaml> ./scripts/cerbos-promote-precheck.sh`

Быстрый rollback-командогенератор для двух контуров:

`./scripts/cerbos-rollback.sh`

Полезные варианты:

- local shadow rollback c предыдущим bundle:
  - `TARGET=local MODE=shadow BUNDLE_VERSION=policy-<prev-sha> ./scripts/cerbos-rollback.sh`
- local аварийное отключение Cerbos:
  - `TARGET=local MODE=off ./scripts/cerbos-rollback.sh`
- infra rollback (сначала посмотреть команду, затем применить):
  - `TARGET=infra MODE=shadow BUNDLE_VERSION=policy-<prev-sha> VALUES_FILE=<infra-values.yaml> ./scripts/cerbos-rollback.sh`
  - `TARGET=infra MODE=shadow BUNDLE_VERSION=policy-<prev-sha> VALUES_FILE=<infra-values.yaml> APPLY=true ./scripts/cerbos-rollback.sh`

## Rollout / rollback runbook (две среды)

### 1) Local (`deploy.sh`) -> shadow

1. Выпустить bundle:
   - `./scripts/release-cerbos-policies.sh`
2. Задать baseline:
   - `WRITE_BASELINE=true ./scripts/check-cerbos-shadow.sh`
3. Прогнать shadow-трафик и gate:
   - `MODE=baseline ./scripts/check-cerbos-shadow.sh`

### 2) Local -> enforce (canary)

`CHECK_SHADOW_GATE=true SHADOW_GATE_MODE=baseline CERBOS_ENFORCE=true CERBOS_BUNDLE_VERSION=policy-<sha> ./deploy.sh`

### 3) Parity перед production (`infra`)

`INFRA_VALUES_FILE=<infra-values.yaml> ./scripts/check-cerbos-config-parity.sh`

Рекомендуемый вариант (единый шаг через deploy):

`CHECK_CERBOS_PARITY=true CERBOS_INFRA_VALUES_FILE=<infra-values.yaml> CERBOS_ENFORCE=true CERBOS_BUNDLE_VERSION=policy-<sha> ./deploy.sh`

### 4) Production (`infra`) rollout

- В `infra` используем тот же `policy-<sha>`.
- Сначала включаем `SHADOW`, затем после окна наблюдения переводим в `ENFORCE`.
- Перед переключением в `ENFORCE` повторно проверяем parity и shadow-метрики.

### 5) Быстрый rollback

- На локальном контуре:
  - вернуть bundle: `CERBOS_BUNDLE_VERSION=policy-<prev-sha>`
  - выключить enforce: `CERBOS_SHADOW=true` или `CERBOS_OFF=true`
- На production (`infra`):
  - реверт `bundleVersion` и `cerbos.mode` в `SHADOW` (или `DISABLED` при инциденте),
  - затем `helm upgrade`/infra rollout.

## Важно

- Текущий baseline покрывает `model`, `notation`, `diagram`, `node_type`, `link_type`, `node_shape`, `file`, `share`.
- Для `model`/`notation`/`diagram`/`node_type`/`link_type`/`node_shape` используются правила owner/admin/shared(view|edit).
- Перед расширением `enforce` на новые endpoint-группы обязательно прогоняйте shadow-gate и parity-check.
