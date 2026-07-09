# Cerbos policies (enforce-only)

Этот каталог хранит policy bundle для `arepos-server` в единственном рабочем режиме:
- Cerbos всегда включен;
- fallback на legacy авторизацию отсутствует;
- при недоступности Cerbos действует **default-deny** (см. ниже).

### Поведение при outage Cerbos

Фактический runtime (`CerbosDecisionService` + `ResourceAccessService`):

1. Сетевой сбой / circuit open / неразбираемый ответ Cerbos → `CerbosDecisionService` возвращает **deny** (`false`) без исключения.
2. `ResourceAccessService` при `false` обычно отвечает клиенту **403 Access denied** (как при обычном запрете).
3. **HTTP 503** с текстом `Authorization service is unavailable` выставляется только если `CerbosDecisionService.check*` **бросает** исключение (редкий путь; helper `cerbosUnavailable`).

Итог для операторов и фронта: типичный outage Cerbos выглядит как массовые **403**, а не как глобальный 503. wArchi authz-blocker реагирует на `503` + `Authorization service is unavailable`; при default-deny UI может не показать authz-outage overlay.

## Структура

- `policies/` — resource policies (YAML).
- `releases/` — локальные архивы policy bundle.

## Локальный релиз policy bundle

```bash
./scripts/release-cerbos-policies.sh
```

Скрипт:
1. проверяет чистоту git (можно отключить флагом);
2. валидирует policy через `cerbos compile` (или Docker fallback);
3. упаковывает `authz/cerbos/releases/policy-<git-sha>.tar.gz`;
4. (опционально) запускает deploy.

Дополнительно:
- `SKIP_POLICY_TESTS=false` — запускать policy-тесты в `cerbos compile`.

## Deploy в enforce-only

Cerbos обязателен для `arepos-server`, поэтому в deploy не используется `mode`.

Пример:

```bash
CERBOS_DEPLOY=true CERBOS_BUNDLE_VERSION=policy-<sha> ./scripts/deploy.sh
```

## Parity check local vs infra

Проверка ключевых параметров Cerbos между контурами:

```bash
INFRA_VALUES_FILE=<infra-values.yaml> ./scripts/check-cerbos-config-parity.sh
```

Проверяемые ключи:
- `AREPOS_AUTHZ_CERBOS_REQUEST_TIMEOUT`
- `AREPOS_AUTHZ_CERBOS_BUNDLE_VERSION`

Опционально можно включить сравнение endpoint:

```bash
INCLUDE_ENDPOINT=true INFRA_VALUES_FILE=<infra-values.yaml> ./scripts/check-cerbos-config-parity.sh
```

## Rollback

Rollback означает возврат на предыдущий `bundleVersion` (Cerbos не выключается):

```bash
TARGET=local BUNDLE_VERSION=policy-<prev-sha> ./scripts/cerbos-rollback.sh
TARGET=infra BUNDLE_VERSION=policy-<prev-sha> VALUES_FILE=<infra-values.yaml> ./scripts/cerbos-rollback.sh
TARGET=infra BUNDLE_VERSION=policy-<prev-sha> VALUES_FILE=<infra-values.yaml> APPLY=true ./scripts/cerbos-rollback.sh
```

## Важно

- Baseline policies покрывают: `model`, `notation`, `node_type`, `link_type`, `node_shape`, `file`, `share`, `admin_panel`, `user_admin`. Доступ к диаграммам в Cerbos не выделяется отдельным ресурсом — проверяется через `model` (см. `authz/cerbos/COVERAGE.md`).
- Любой новый endpoint с проверкой доступа должен быть привязан к Cerbos resource/action и покрыт policy.
- Карта текущего покрытия endpoint/check/policy: `authz/cerbos/COVERAGE.md`.
- Перед merge/release запускайте verify-checklist: `authz/cerbos/VERIFY.md`.
