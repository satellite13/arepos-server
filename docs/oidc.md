# OIDC SSO (Keycloak-compatible)

Optional single sign-on for wArchi via a Keycloak-compatible OpenID Connect provider. When disabled or not configured, password login/register work as before and the SSO button is hidden.

## How it works

1. wArchi calls `GET /api/v1/auth/sso/config` → `{ enabled, displayName }`.
2. User clicks the SSO button → `GET /api/v1/auth/sso/authorize` → browser redirects to the IdP.
3. IdP redirects back to wArchi `OIDC_REDIRECT_URI` with `code` and `state`.
4. Frontend posts to `POST /api/v1/auth/sso/callback`; the server exchanges the code, validates the ID token, syncs the user, and sets the usual auth cookies.

User sync rules:

- Match by `oidc_sub` if already linked.
- Else match by email (case-insensitive) and auto-link `oidc_sub`.
- Else create a new `USER` with profile claims (`given_name` / `family_name` / …) when present.
- Deactivated users cannot sign in via SSO.

Profile linking (signed-in user):

- `GET /api/v1/auth/sso/authorize?linkUserId=…`
- `POST /api/v1/auth/sso/link/callback`
- `GET /api/v1/auth/sso/status`, `DELETE /api/v1/auth/sso/unlink`

Link requires the IdP email to match the current account email; an `oidc_sub` already linked to another user is rejected.

## Enable / disable

| `OIDC_ENABLED` | Behavior |
|----------------|----------|
| `auto` (default) | SSO on only when issuer, client id, client secret, and redirect URI are all set |
| `true` / `1` / `yes` / `on` | Force on (still needs a working IdP config) |
| `false` / `0` / `no` / `off` | Force off |

Leave the variables empty (or `OIDC_ENABLED=false`) to keep SSO off.

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `OIDC_ENABLED` | no | `auto` / `true` / `false` (default `auto`) |
| `OIDC_ISSUER_URI` | yes* | Realm issuer **with trailing slash**, e.g. `https://idp.example.com/realms/warchi/` |
| `OIDC_CLIENT_ID` | yes* | Confidential client id |
| `OIDC_CLIENT_SECRET` | yes* | Client secret (store in a secret manager / `envSecret`) |
| `OIDC_REDIRECT_URI` | yes* | Exact frontend callback, e.g. `https://app.example.com/auth/oidc/callback` |
| `OIDC_DISPLAY_NAME` | no | Login button label (default `SSO`) |
| `OIDC_SCOPE` | no | Default `openid profile email` |
| `OIDC_POST_LOGOUT_URI` | no | Optional post-logout URL (reserved for logout UX) |
| `OIDC_FRONTEND_URL` | no | Optional frontend base URL |

\*Required for SSO to become active in `auto` mode.

`OIDC_ISSUER_URI` must end with `/` because the server builds:

- `{issuer}protocol/openid-connect/auth`
- `{issuer}protocol/openid-connect/token`

ID token `iss` is validated against the issuer **without** the trailing slash.

## Keycloak client checklist

1. Create a **confidential** client (Client authentication: ON).
2. Enable **Standard flow** (authorization code).
3. Set **Valid redirect URIs** to at least:
   - `https://<warchi-host>/auth/oidc/callback`
   - optionally `https://<warchi-host>/auth/oidc/link-callback` if you use the dedicated link route
4. Copy the client secret into `OIDC_CLIENT_SECRET`.
5. Ensure the client (or realm) mappers expose **email** and preferably **profile** claims (`email`, `sub`, `given_name`, `family_name`).
6. Set arepos-server env as above; restart / roll the deployment.
7. Confirm `GET /api/v1/auth/sso/config` returns `{ "enabled": true, "displayName": "…" }`.

Local example:

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

In `charts/arepos-server/values.yaml`, set non-secret values under `env` and put `OIDC_CLIENT_SECRET` in `envSecret` (or an external secret store). Example:

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

## wArchi UI

- Login page: SSO button appears when `enabled: true`; label comes from `displayName`.
- Profile: when SSO is enabled, the user can link / unlink the IdP account.
- Admin users list can show linked `oidc_sub` when present.

No extra frontend env vars are required; wArchi discovers SSO via `/auth/sso/config`.

## API surface

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/v1/auth/sso/config` | public | `{ enabled, displayName }` |
| GET | `/api/v1/auth/sso/authorize` | public | `{ url }` — IdP authorize URL (`linkUserId` optional) |
| POST | `/api/v1/auth/sso/callback` | public | Exchange code → session cookies |
| POST | `/api/v1/auth/sso/link/callback` | public (+ valid state) | Link IdP to existing user |
| GET | `/api/v1/auth/sso/status` | session | Link status |
| DELETE | `/api/v1/auth/sso/unlink` | session | Clear `oidc_sub` |

CSRF is not required for `/api/v1/auth/sso/**` (same exemption family as login/register/refresh). Successful callback/link still issues the normal cookie session + CSRF cookie for subsequent mutating API calls.

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| SSO button missing | Config incomplete (`auto`) or `OIDC_ENABLED=false`; check `/auth/sso/config` |
| `503` «OIDC SSO is not configured» | SSO forced off / not effectively enabled |
| Token exchange / invalid issuer | Wrong `OIDC_ISSUER_URI` (missing trailing `/`, wrong realm) |
| Invalid audience | `OIDC_CLIENT_ID` not in ID token `aud` |
| Redirect URI mismatch | Keycloak Valid redirect URI ≠ `OIDC_REDIRECT_URI` exactly |
| Account deactivated | User exists but `isActive=false` |
| Link conflict | Email differs from account, or `oidc_sub` already linked elsewhere |

Русская версия: `docs/oidc.ru.md`.
