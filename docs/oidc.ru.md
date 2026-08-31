# OIDC SSO (совместимо с Keycloak)

Опциональный единый вход в wArchi через OpenID Connect IdP, совместимый с Keycloak. Если SSO выключен или не сконфигурирован, работают обычные логин/регистрация, кнопка SSO скрыта.

## Как это работает

1. wArchi вызывает `GET /api/v1/auth/sso/config` → `{ enabled, displayName }`.
2. Пользователь нажимает SSO → `GET /api/v1/auth/sso/authorize` → редирект в IdP (authorization code + **PKCE S256** + **nonce**).
3. IdP возвращает браузер на `OIDC_REDIRECT_URI` с `code` и `state`.
4. Фронтенд вызывает `POST /api/v1/auth/sso/callback`; сервер проверяет подписанный `state`, обменивает code с `code_verifier`, **верифицирует подпись ID token через JWKS**, сверяет `nonce`, синхронизирует пользователя и выставляет обычные auth-cookies.

Синхронизация пользователя:

- По `oidc_sub`, если уже привязан.
- Иначе по email (без учёта регистра) с автопривязкой `oidc_sub` **только при `email_verified=true`**.
- Иначе создаётся новый `USER` при `email_verified=true` (профиль из claims, если есть).
- Неверифицированный email не автопривязывается и не создаёт аккаунт — нужна привязка из профиля.
- Деактивированные пользователи через SSO не входят.

Привязка из профиля (уже вошедший пользователь; нужна сессия):

- `GET /api/v1/auth/sso/authorize?linkUserId=<currentUserId>` — `linkUserId` должен совпадать с аутентифицированным пользователем.
- `POST /api/v1/auth/sso/link/callback` — та же сессия; state должен быть link-токеном для этого пользователя.
- `GET /api/v1/auth/sso/status`, `DELETE /api/v1/auth/sso/unlink`

Для привязки нужен верифицированный email IdP, совпадающий с email аккаунта; `oidc_sub`, уже привязанный к другому пользователю, отклоняется.

## Включение / выключение

| `OIDC_ENABLED` | Поведение |
|----------------|-----------|
| `auto` (по умолчанию) | SSO включается только когда заданы issuer, client id/secret и redirect URI |
| `true` / `1` / `yes` / `on` | Принудительно вкл. (всё равно нужна рабочая конфигурация IdP) |
| `false` / `0` / `no` / `off` | Принудительно выкл. |

Оставьте переменные пустыми (или `OIDC_ENABLED=false`), чтобы SSO оставался выключенным.

## Переменные окружения

| Переменная | Обязательна | Описание |
|------------|-------------|----------|
| `OIDC_ENABLED` | нет | `auto` / `true` / `false` (по умолчанию `auto`) |
| `OIDC_ISSUER_URI` | да* | Issuer realm **со слэшем в конце**, напр. `https://idp.example.com/realms/warchi/` |
| `OIDC_CLIENT_ID` | да* | Confidential client id |
| `OIDC_CLIENT_SECRET` | да* | Client secret (в secret manager / `envSecret`) |
| `OIDC_REDIRECT_URI` | да* | Точный frontend callback |
| `OIDC_DISPLAY_NAME` | нет | Подпись кнопки (по умолчанию `SSO`) |
| `OIDC_SCOPE` | нет | По умолчанию `openid profile email` |
| `OIDC_POST_LOGOUT_URI` | нет | Опциональный URL после logout |
| `OIDC_FRONTEND_URL` | нет | Базовый URL фронтенда |
| `OIDC_STATE_SECRET` | нет | HMAC для `state`; по умолчанию `JWT_SECRET` (нужен для multi-instance) |

\*Нужны для активации SSO в режиме `auto`.

Сервер строит:

- `{issuer}protocol/openid-connect/auth`
- `{issuer}protocol/openid-connect/token`
- `{issuer}protocol/openid-connect/certs` (JWKS)

`iss` в ID token сверяется с issuer **без** завершающего `/`.

## Чеклист клиента Keycloak

1. Confidential client (Client authentication: ON).
2. Standard flow (authorization code). Сервер отправляет PKCE `S256`.
3. Valid redirect URIs: callback(и) wArchi.
4. `OIDC_CLIENT_SECRET` в секретах.
5. Claims: **email**, **email_verified**, желательно profile.
6. Выставить env arepos-server и перезапустить.
7. Проверить `GET /api/v1/auth/sso/config`.

Английская версия: `docs/oidc.md`.
