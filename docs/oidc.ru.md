# OIDC SSO (совместимо с Keycloak)

Опциональный единый вход в wArchi через OpenID Connect IdP, совместимый с Keycloak. Если SSO выключен или не сконфигурирован, работают обычные логин/регистрация, кнопка SSO скрыта.

## Как это работает

1. wArchi вызывает `GET /api/v1/auth/sso/config` → `{ enabled, displayName }`.
2. Пользователь нажимает SSO → `GET /api/v1/auth/sso/authorize` → редирект в IdP.
3. IdP возвращает браузер на `OIDC_REDIRECT_URI` с `code` и `state`.
4. Фронтенд вызывает `POST /api/v1/auth/sso/callback`; сервер обменивает code, проверяет ID token, синхронизирует пользователя и выставляет обычные auth-cookies.

Синхронизация пользователя:

- По `oidc_sub`, если уже привязан.
- Иначе по email (без учёта регистра) с автопривязкой `oidc_sub`.
- Иначе создаётся новый `USER` (профиль из claims, если есть).
- Деактивированные пользователи через SSO не входят.

Привязка из профиля (уже вошедший пользователь):

- `GET /api/v1/auth/sso/authorize?linkUserId=…`
- `POST /api/v1/auth/sso/link/callback`
- `GET /api/v1/auth/sso/status`, `DELETE /api/v1/auth/sso/unlink`

Для привязки email в IdP должен совпадать с email аккаунта; `oidc_sub`, уже привязанный к другому пользователю, отклоняется.

## Включение / выключение

| `OIDC_ENABLED` | Поведение |
|----------------|-----------|
| `auto` (по умолчанию) | SSO включается только если заданы issuer, client id, client secret и redirect URI |
| `true` / `1` / `yes` / `on` | Принудительно включено |
| `false` / `0` / `no` / `off` | Принудительно выключено |

Пустые переменные (или `OIDC_ENABLED=false`) оставляют SSO выключенным.

## Переменные окружения

| Переменная | Обязательна | Описание |
|------------|-------------|----------|
| `OIDC_ENABLED` | нет | `auto` / `true` / `false` (по умолчанию `auto`) |
| `OIDC_ISSUER_URI` | да* | Issuer realm **со слешем в конце**, напр. `https://idp.example.com/realms/warchi/` |
| `OIDC_CLIENT_ID` | да* | ID confidential-клиента |
| `OIDC_CLIENT_SECRET` | да* | Секрет клиента (в secret store / `envSecret`) |
| `OIDC_REDIRECT_URI` | да* | Точный callback фронтенда, напр. `https://app.example.com/auth/oidc/callback` |
| `OIDC_DISPLAY_NAME` | нет | Подпись кнопки входа (по умолчанию `SSO`) |
| `OIDC_SCOPE` | нет | По умолчанию `openid profile email` |
| `OIDC_POST_LOGOUT_URI` | нет | Опциональный URL после logout |
| `OIDC_FRONTEND_URL` | нет | Опциональный базовый URL фронтенда |

\*Нужны, чтобы SSO стал активным в режиме `auto`.

`OIDC_ISSUER_URI` должен заканчиваться на `/`: сервер собирает URL вида `{issuer}protocol/openid-connect/auth` и `…/token`. Claim `iss` в ID token сверяется с issuer **без** завершающего `/`.

## Чеклист клиента Keycloak

1. Создайте **confidential**-клиент (Client authentication: ON).
2. Включите **Standard flow** (authorization code).
3. В **Valid redirect URIs** укажите как минимум:
   - `https://<warchi-host>/auth/oidc/callback`
   - при необходимости `https://<warchi-host>/auth/oidc/link-callback`
4. Секрет клиента положите в `OIDC_CLIENT_SECRET`.
5. Убедитесь, что в токене есть **email** и желательно profile-claims (`email`, `sub`, `given_name`, `family_name`).
6. Задайте env arepos-server и перезапустите деплой.
7. Проверьте `GET /api/v1/auth/sso/config` → `{ "enabled": true, "displayName": "…" }`.

Локальный пример:

```bash
export OIDC_ENABLED=auto
export OIDC_ISSUER_URI=http://localhost:8081/realms/warchi/
export OIDC_CLIENT_ID=warchi
export OIDC_CLIENT_SECRET=change-me
export OIDC_REDIRECT_URI=http://localhost:5173/auth/oidc/callback
export OIDC_DISPLAY_NAME=Keycloak
export OIDC_FRONTEND_URL=http://localhost:5173/
export OIDC_POST_LOGOUT_URI=http://localhost:5173/
```

## Helm / Kubernetes

В `charts/arepos-server/values.yaml` несекретные значения — в `env`, секрет — в `envSecret` (или внешнем store). Пример:

```yaml
env:
  - name: OIDC_ENABLED
    value: "auto"
  - name: OIDC_ISSUER_URI
    value: "https://idp.example.com/realms/warchi/"
  - name: OIDC_CLIENT_ID
    value: "warchi"
  - name: OIDC_REDIRECT_URI
    value: "https://app.example.com/auth/oidc/callback"
  - name: OIDC_DISPLAY_NAME
    value: "Company SSO"
# OIDC_CLIENT_SECRET → envSecret / external secret
```

## UI wArchi

- Экран входа: кнопка SSO при `enabled: true`; подпись из `displayName`.
- Профиль: при включённом SSO — привязка / отвязка IdP.
- В админке у пользователей может отображаться `oidc_sub`.

Отдельные frontend env для SSO не нужны: wArchi читает `/auth/sso/config`.

## API

| Метод | Путь | Auth | Назначение |
|-------|------|------|------------|
| GET | `/api/v1/auth/sso/config` | public | `{ enabled, displayName }` |
| GET | `/api/v1/auth/sso/authorize` | public | `{ url }` — URL IdP (`linkUserId` опционально) |
| POST | `/api/v1/auth/sso/callback` | public | Обмен code → cookie-сессия |
| POST | `/api/v1/auth/sso/link/callback` | public (+ валидный state) | Привязка IdP к пользователю |
| GET | `/api/v1/auth/sso/status` | session | Статус привязки |
| DELETE | `/api/v1/auth/sso/unlink` | session | Сброс `oidc_sub` |

Для `/api/v1/auth/sso/**` CSRF не требуется (как у login/register/refresh). После успешного callback выставляется обычная cookie-сессия и CSRF-cookie для дальнейших mutating-запросов.

## Типичные проблемы

| Симптом | Вероятная причина |
|---------|-------------------|
| Нет кнопки SSO | Конфиг неполный (`auto`) или `OIDC_ENABLED=false`; смотрите `/auth/sso/config` |
| `503` «OIDC SSO is not configured» | SSO фактически выключен |
| Ошибка обмена токена / invalid issuer | Неверный `OIDC_ISSUER_URI` (нет `/` в конце, неверный realm) |
| Invalid audience | `OIDC_CLIENT_ID` отсутствует в `aud` ID token |
| Redirect URI mismatch | Valid redirect URI в Keycloak ≠ `OIDC_REDIRECT_URI` |
| Account deactivated | Пользователь есть, но `isActive=false` |
| Конфликт привязки | Email не совпадает или `oidc_sub` уже у другого пользователя |

English version: `docs/oidc.md`.
