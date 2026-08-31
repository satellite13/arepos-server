# Code review: arepos-server 0.14.5

Ревью кодовой базы на коммите `98142ad` (Release v0.14.5).  
Охват: security/authz, collaboration (batch-save, locks, merge), controllers, soft-delete, migrations.

Стиль и косметика не включались. Приоритет — баги, authz/data integrity, гонки, потеря данных.

---

## Краткий вердикт

Сильные стороны: Cerbos fail-closed (503), refresh-token rotation через atomic `markUsed`, JWT secret fail-fast, batch-save конфликты со structured `BATCH_SAVE_CONFLICT`, lock acquire с `PESSIMISTIC_WRITE`, OEF XXE-safe parser, prod-профиль (registration/swagger/cookie-secure).

Главные риски: OIDC без проверки подписи ID token + небезопасный account link; CSRF bypass при смеси cookie+Bearer; batch-save без model-scope на update/delete diagrams; `isCommonType` делает типы деактивированных пользователей глобальными.

---

## Critical

### C1. OIDC ID token не проверяется по подписи

**Где:** `OidcAuthService.extractClaimsFromIdToken` (~79–106)

Только `SignedJWT.parse` + проверки `iss` / `aud` / `exp`. Нет JWKS, нет верификации подписи, нет `nbf`/clock skew, нет `email_verified`.

**Риск:** Подделанный ID token с нужными claims принимается → account takeover через auto-link по email.

**Фикс:** Nimbus `ConfigurableJWTProcessor` / Spring Security OIDC + JWKS; требовать `email_verified` при auto-link.

---

### C2. OIDC account-link без аутентифицированной сессии

**Где:** `OidcController.authorize` (~58–67), `linkCallback` (~82–112)

`GET /authorize?linkUserId=` публичный и подписывает state для любого UUID. `POST /link/callback` доверяет state и линкует IdP → user без проверки `SecurityContext`.

**Риск:** Атакующий, прошедший IdP как `victim@…`, привязывает SSO к чужому локальному аккаунту.

**Фикс:** Требовать login для link-flow; в state класть `CurrentUser.getId()`; игнорировать клиентский `linkUserId`, если ≠ session user.

---

### C3. Batch-save update/delete без проверки model scope

**Где:** `BatchGraphOpsExecutor.updateNodes` (~137–156), `updateLinks` (~277–294), `updateDiagrams` (~382–408), `deleteDiagrams` (~410–417)

`deleteModelScoped` для nodes/links проверяет модель; update и soft-delete diagrams — нет. Conflict collector при чужой модели просто `continue` (не conflict), затем executor мутирует сущность.

**Риск:** Пользователь с edit на модель A и знанием UUID из модели B может менять/soft-delete сущности B через `POST /models/{A}/batch-save`.

**Фикс:** Assert `entity.model.id == model.id` на всех путях; 400 при mismatch. Тесты на cross-model ids.

---

## High

### H1. CSRF skip при любом Bearer, пока cookie побеждает в auth

**Где:** `CsrfFilter` (~37–41), `JwtAuthenticationFilter.extractToken` (~116–124)

CSRF пропускается, если есть `Authorization: Bearer …`. JWT берётся из cookie раньше Bearer.

**Риск:** Same-site атакующий шлёт `Bearer garbage` → CSRF off, а cookie `warchi_access` аутентифицирует запрос.

**Фикс:** Skip CSRF только если auth реально через Bearer (нет access cookie); либо CSRF всегда при наличии cookie.

---

### H2. `isCommonType`: неактивный owner → глобально usable типы

**Где:** `ResourceAccessService` (~107–109, 472–473)

```kotlin
owner.email.equals("system@arepos.local", …) || !owner.isActive
```

**Риск:** Деактивация пользователя открывает его node/link types всем, вместо закрытия доступа.

**Фикс:** Только явный system-owner (или флаг); inactive → deny, кроме обычных view/share правил.

---

### H3. Soft-deleted model/notation доступны через children

**Где:** `NodesController` / `LinksController` / `ComponentsController`; `canViewModel` не смотрит `deleted`

`ModelsRepository.findById` скрывает soft-deleted, но children грузятся по своим id; authz идёт через lazy parent без `deleted=false`.

**Риск:** После soft-delete модели owner/grantee продолжают CRUD по nodes/links.

**Фикс:** Reject при `model.deleted` / `notation.deleted` в `canView*`/`canEdit*` (или в репозиториях).

---

### H4. Audit log для non-admin: post-filter вместо SQL scope

**Где:** `AuditLogController` (~40–72)

Запрос по `tableName`/`rowId`/`operation` без `changedBy`, затем filter in-memory по себе; `PageImpl(..., filtered.size)`.

**Риск:** Утечка метаданных чужих изменений (page shape/timing); неверная пагинация.

**Фикс:** Всегда `changedBy = currentUser` в SQL для non-admin.

---

### H5. MCP/API-key JWT наследует полную роль (включая ADMIN)

**Где:** `JwtAuthenticationFilter`, `McpScopeFilter`; Cerbos admin rules

MCP token грузит DB role; scope filter только проверяет наличие `models:read|write`, не ограничивает маршруты.

**Риск:** Admin API key для «узкого» MCP получает admin_panel / user_admin / shares на ~TTL access token.

**Фикс:** Для `MCP_ACCESS` — отдельный principal/роль; path allowlist; deny admin actions в Cerbos по token type.

---

### H6. WebSocket handshake: role из JWT claim, без `isActive`

**Где:** `ModelSyncStompHandshakeHandler`, `JwtQueryTokenHandshakeInterceptor`

HTTP перечитывает role/`isActive` из DB; WS доверяет claim.

**Риск:** Demoted/deactivated user сохраняет elevated Cerbos на STOMP до expiry access JWT.

**Фикс:** Resolve user из DB в handshake; reject inactive; authorities из DB.

---

### H7. OIDC auto-link по email без `email_verified`

**Где:** `OidcAuthService.syncUser` (~122–143)

Неизвестный `oidc_sub` → find by email → записать attacker `oidc_sub`.

**Риск:** Account takeover при IdP с неверифицированным email (усиливается C1).

**Фикс:** Auto-link только при `email_verified==true`; иначе явный authenticated link flow.

---

### H8. Optimistic concurrency опциональна (last-write-wins)

**Где:** `BatchConflictCollector` (~93–100): `base ?: continue`; `force=true` снимает OCC

**Риск:** Клиенты без `baseUpdatedAt` тихо перетирают чужие правки.

**Фикс:** Требовать `baseUpdatedAt` на update/delete (кроме явного `force`).

---

### H9. Merge: remap canvas nodes без дедупа keep-instance

**Где:** `ModelValidationMergeService` (~437–463); link-path дедупит, node-path — нет

**Риск:** Два instance с одним `modelNodeId` на canvas после merge.

**Фикс:** Как у links: если keep instance есть — удалить drop instances.

---

### H10. Merge теряет wiki/docs drop-node

**Где:** `ModelValidationMergeService` — preview `hasDocuments`, commit не переносит docs

**Риск:** Тихая потеря документации; `document_refs.node_id` → NULL.

**Фикс:** Блок merge при docs без opt-in transfer, либо перенос `documentFileId`/refs.

---

## Medium

| ID | Тема | Где | Суть |
|----|------|-----|------|
| M1 | Login OIDC callback игнорирует `state` | `OidcController.callback` | Login CSRF / session fixation |
| M2 | `OidcProperties.stateSecret` не используется | `OidcStateToken` | Ephemeral HMAC; multi-instance ломает link |
| M3 | Нет PKCE / `nonce` | `buildAuthorizationUrl` | Слабее против code interception |
| M4 | Refresh reuse без family revocation | `AuthTokenService` | Украденный refresh после race живёт |
| M5 | Tokens в JSON + cookies | Auth/OIDC responses | XSS exfiltrate refresh |
| M6 | JWT в WS query `?token=` | Handshake interceptor | Логи/прокси/Referer |
| M7 | Diagram locks не защищают HTTP save | batch-save / instances:merge | Lock только live relay |
| M8 | Soft-deleted diagram unique (полная UNIQUE) | `005-add-diagrams.sql` | В отличие от models — слот занят навсегда; UX trash непоследователен вне batch |
| M9 | Load-all-then-filter lists | NodeShapes, ValidationScripts, grouped Models/Notations | Memory/DoS + N× Cerbos; max page 25000 |
| M10 | Sync events неполные для side effects | orphan cleanup, canvas cleanup, incident links | WS клиенты stale до full reload |
| M11 | STOMP broadcast до commit (outbox off) | `ModelSyncBroadcaster` | Rollback → ложный `model_changed` |
| M12 | Email case: DB `upper()`, login case-sensitive | Auth + `048-…` | Login fail / странный CONFLICT |
| M13 | Audit retention без ShedLock | `AuditRetentionScheduler` | Multi-pod двойной cleanup |
| M14 | Parent node из другой модели на update | `NodesController` update | Create проверяет, update — нет |
| M15 | Batch-save без `MdFileLinkValidator` | vs ensure/copy | Битые `mdfile://` через batch |
| M16 | MinIO до DB → orphans | `FileStorageService` | Storage leak при DB fail |
| M17 | MCP grants create: `canView` для write scope | `ApiKeyService` | Scope overpromise (runtime edit всё же нужен) |
| M18 | Public shares = все authenticated | `ShareResolver` | Ошибочный «public» = org-wide |

---

## Low / Info

- CSRF compare не constant-time (`!=`).
- `register-admin` публичен при установленном `ADMIN_SECRET` — нужен bootstrap-only / rate limit.
- Default `cookie-secure=false` / registration on вне prod — fail-fast вне local/test.
- Spectators in-memory — не multi-instance.
- `FileStorageService.updateMarkdown` трогает `createdAt`.
- ResourceShares JPA `@UniqueConstraint` vs DB partial indexes — drift при ddl validation.
- Положительно: Cerbos fail-closed, JWT secret checks, refresh `markUsed`, OEF XXE-safe, lock PESSIMISTIC_WRITE, package job `afterCommit`, prod hardening.

---

## Рекомендуемый порядок фиксов

1. **OIDC hardening:** JWKS verify + `email_verified` + authenticated link + login `state` (C1, C2, H7, M1–M3).
2. **Batch model-scope** на все graph ops + тесты (C3).
3. **CSRF/Bearer/cookie** (H1).
4. **`isCommonType`** (H2) + soft-delete parent enforcement (H3).
5. **Audit SQL scope** (H4), MCP privilege confinement (H5), WS DB role/`isActive` (H6).
6. **OCC mandatory** (H8), merge node dedupe + docs (H9–H10).
7. Diagram partial unique + unified trash UX (M8); list SQL scoping (M9); sync side-effect events (M10); outbox default/afterCommit (M11).

---

## Вне скоупа этого отчёта

- Полный построчный audit всех 315 `.kt` файлов и всех Liquibase-скриптов.
- Performance profiling / load testing.
- Frontend (wArchi) и отдельный репозиторий Papirus (см. закрытый PR #1 / `docs/papirus-code-review-0.9.13.md` на ветке ревью Papirus).

При необходимости следующий шаг — issue tickets по C1–C3 / H1–H10 или точечные PR с фиксами.
