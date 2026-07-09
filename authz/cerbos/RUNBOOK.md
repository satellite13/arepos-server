# Cerbos runbook (enforce-only)

Runbook для эксплуатации Cerbos в модели, где `arepos-server` зависит от Cerbos как от обязательного сервиса.

## 1) Preconditions

Перед rollout:
- policy bundle собран: `policy-<sha>`;
- `cerbos compile`/policy tests пройдены;
- известен предыдущий стабильный bundle для rollback.

Проверка:

```bash
ALLOW_DIRTY=false DRY_RUN=true DEPLOY_TARGET=none ./scripts/release-cerbos-policies.sh
```

## 2) Local rollout

```bash
./scripts/release-cerbos-policies.sh
CERBOS_DEPLOY=true CERBOS_BUNDLE_VERSION=policy-<sha> ./scripts/deploy.sh
```

Критерий успеха: backend отвечает, авторизация работает; нет массовых ложных **403** из-за недоступного Cerbos и нет `503 Authorization service is unavailable` (если этот путь всё же сработал).

## 3) Parity перед infra rollout

```bash
INFRA_VALUES_FILE=<infra-values.yaml> ./scripts/check-cerbos-config-parity.sh
```

Проверяемые ключи:
- request timeout;
- bundle version;
- (опционально) endpoint.

## 4) Production rollout (infra)

1. Включить тот же `policy-<sha>`;
2. Выполнить rollout;
3. Проверить API и типовые сценарии авторизации.

## 5) Fast rollback

Rollback выполняется только сменой bundle:

```bash
TARGET=local BUNDLE_VERSION=policy-<prev-sha> ./scripts/cerbos-rollback.sh
TARGET=infra BUNDLE_VERSION=policy-<prev-sha> VALUES_FILE=<infra-values.yaml> APPLY=true ./scripts/cerbos-rollback.sh
```

Cerbos при rollback не выключается.

## 6) Incident checklist

При деградации:
1. Зафиксировать текущий bundle и время инцидента;
2. Откатить bundle до последнего стабильного;
3. Проверить восстановление API и авторизации;
4. Провести postmortem (policy change, config drift, timeout).
