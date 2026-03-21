# Cerbos rollout/rollback runbook

Этот документ фиксирует операционные шаги для двух контуров:
- local (`deploy.sh`);
- production (`infra` в Yandex Cloud).

## 1) Preconditions

Перед любым переключением режима:
- policy bundle собран и проверен (`policy-<sha>`);
- пройдены compile/test для policy;
- есть актуальный backup значений (`values`) и известная "последняя стабильная" версия bundle.

Проверка локальной сборки policy:

```bash
ALLOW_DIRTY=false DRY_RUN=true DEPLOY_TARGET=none ./scripts/release-cerbos-policies.sh
```

## 2) Local rollout (shadow -> enforce)

### 2.1 Выпуск policy bundle

```bash
./scripts/release-cerbos-policies.sh
```

Артефакт: `authz/cerbos/releases/policy-<sha>.tar.gz`.

### 2.2 Базовая точка (baseline) для shadow

```bash
WRITE_BASELINE=true ./scripts/check-cerbos-shadow.sh
```

### 2.3 Shadow rollout

```bash
CERBOS_SHADOW=true \
CERBOS_BUNDLE_VERSION=policy-<sha> \
./deploy.sh
```

### 2.4 Gate перед enforce

```bash
MODE=baseline ./scripts/check-cerbos-shadow.sh
```

Или сразу через deploy gate:

```bash
CHECK_SHADOW_GATE=true \
SHADOW_GATE_MODE=baseline \
CERBOS_ENFORCE=true \
CERBOS_BUNDLE_VERSION=policy-<sha> \
./deploy.sh
```

## 3) Parity check local vs infra

До production enforce обязательно сравнить параметры Cerbos:

```bash
INFRA_VALUES_FILE=<path-to-infra-values.yaml> \
./scripts/check-cerbos-config-parity.sh
```

Критичные ключи parity:
- mode;
- request timeout;
- bundle version;
- shadow/enforce flags;
- fail-open;
- cerbos enabled.

Для удобства можно выполнить combined precheck:

```bash
INFRA_VALUES_FILE=<path-to-infra-values.yaml> \
./scripts/cerbos-promote-precheck.sh
```

## 4) Production rollout (infra)

Рекомендуемый порядок:
1. выкатить тот же `policy-<sha>` в `SHADOW`;
2. собрать окно наблюдения (метрики + ошибки + mismatch);
3. проверить parity с целевым enforce-профилем;
4. включить `ENFORCE`.

Минимальные критерии перед enforce:
- нет критичных mismatch;
- error/timeout в пределах порога;
- p95/p99 без регрессии;
- готов rollback (ниже).

## 5) Fast rollback

### 5.1 Local rollback

Отключить enforce и вернуться в shadow:

```bash
CERBOS_SHADOW=true \
CERBOS_BUNDLE_VERSION=policy-<prev-sha> \
./deploy.sh
```

Полный аварийный откат на legacy:

```bash
CERBOS_OFF=true ./deploy.sh
```

### 5.2 Production rollback (infra)

При инциденте:
1. выключить enforce (`cerbos.mode=SHADOW` или `cerbosEnforceEnabled=false`);
2. откатить bundle до `policy-<prev-sha>`;
3. применить infra rollout;
4. проверить recovery (403/5xx/latency, shadow metrics).

## 6) Incident checklist

При деградации после переключения:
- зафиксировать время и bundle version;
- собрать текущий shadow report и ошибки в логах;
- выполнить rollback (секция 5);
- подтвердить восстановление SLI/SLO;
- создать postmortem с причиной (policy, config parity, timeout, fail-open/fail-closed).
