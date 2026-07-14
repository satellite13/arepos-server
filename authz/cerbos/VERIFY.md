# Cerbos-only verification

Этот чеклист нужен перед merge/release, чтобы подтвердить инвариант Cerbos-only.

## 1) Быстрая автоматическая проверка

Запуск из корня `arepos-server`:

```bash
bash ./scripts/verify-cerbos-only.sh
```

Скрипт проверяет:
- отсутствие role-based bypass в контроллерах;
- отсутствие legacy/shadow/mode/fail-open флагов в runtime-конфиге;
- наличие обязательных policy-файлов;
- компиляцию backend;
- smoke-тесты Cerbos (`CerbosAuthzModelTest`, `CerbosDecisionServiceTest`).

Важно: проверка bypass относится к ресурсной бизнес-авторизации. Self-service/auth endpoint-ы
(`AuthController`, `/users/me/profile`, `/users/public/*`) могут использовать identity/authenticated
проверки и документируются как осознанные границы в `authz/cerbos/COVERAGE.md`.

## 2) Ручная верификация фронта (warchi)

Запуск из корня `warchi`:

```bash
npm run build
```

Проверить, что:
- при ответах backend `502/503/504` и сетевых ошибках включается глобальный блокер backend;
- при `503` с `Authorization service is unavailable` включается глобальный блокер authz (типичный outage Cerbos);
- блокер снимается автоматически после восстановления backend / успешного `/permissions/check`;
- role/owner обходы в критичных местах UI отсутствуют (используется permission-based модель).

## 3) Аудит покрытия endpoint -> Cerbos

Актуальную карту покрытия см. в:

- `authz/cerbos/COVERAGE.md`

При добавлении нового защищенного endpoint:
1. добавить/обновить policy `resource/action`;
2. подключить проверку через `ResourceAccessService`;
3. обновить `COVERAGE.md`.

Перед merge проверь, что классификация endpoint-а корректна:
- resource/business access -> через Cerbos (`ResourceAccessService`);
- auth/self-service/public profile flow -> в разделе "Осознанные границы Cerbos" в `COVERAGE.md`.
