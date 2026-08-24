# Collaboration and batch-save API notes

Short contract notes for clients (wArchi, warchi-mcp, and other API tools). Full schemas: Swagger `/swagger-ui.html`.

## Auth reminder

Browser clients: cookies `warchi_access` / `warchi_refresh` + CSRF header `X-CSRF-Token` on mutating calls.  
Non-browser: `Authorization: Bearer <accessToken>` (CSRF not required when using Bearer-only flows without cookies).

MCP / API keys: create in wArchi **Profile**; exchange via `POST /api/v1/auth/api-keys/exchange`. Keys support `mode=all` (global `models:read` / `models:write`) or `mode=grants` (per-model scopes). After create, scopes and grants are immutable — rename or revoke and recreate. Admin can list/revoke a user’s keys (`GET|DELETE /api/v1/admin/users/{userId}/api-keys/{keyId}`) without seeing plaintext.

OIDC SSO setup: [`oidc.md`](oidc.md).

## Diagram edit locks

Base path: `/api/v1/diagram-locks`

| Method | Path | Notes |
|--------|------|--------|
| `POST` | `/{diagramId}/acquire` | Always **HTTP 200** on success or “held by other”. If another user holds the lock, body has `reason=LOCKED_BY_OTHER` (do not expect 409 for the normal contention case). |
| `POST` | `/{diagramId}/heartbeat` | Extend own lock TTL |
| `POST` | `/{diagramId}/release` | Release own lock → **204** |
| `POST` | `/{diagramId}/force-release` | Admin force release → **204** |
| `GET` | `/` | List active locks; optional `?modelId=` |
| `POST` | `/{diagramId}/spectate` | Start spectating → **204** |
| `POST` | `/{diagramId}/spectate/ping` | Keep spectate session alive |
| `DELETE` | `/{diagramId}/spectate` | Leave spectate |
| `POST` | `/{diagramId}/live` | Relay one live message. Body is a wArchi envelope (`v=1`, `kind=patch` or `snapshot-chunk`, `seq`, `upsert*` / `remove*`, optional `chunkIndex` / `chunkCount`) or legacy `{ nodes, edges }`. Server does not interpret the envelope: lock check plus broadcast into STOMP `instances`. HTTP **413** `instances payload too large` if **one** body exceeds 512 KB — a per-message DoS cap, not a diagram-size limit; the client splits chunks. |
| `POST` | `/{diagramId}/pointer` | Relay collaborator pointer |

HTTP **409** may still appear from the lock service on rare races; clients should treat `LOCKED_BY_OTHER` on **200** as the primary “blocked editor” signal.

## Model batch save

`POST /api/v1/models/{modelId}/batch-save`

- Body: `BatchSaveRequest` — `force`, `nodes` / `links` / `diagrams` with `create` / `update` / `delete`.
- Updates/deletes carry `baseUpdatedAt` for optimistic concurrency.
- Success: `BatchSaveResponse` (id remaps for creates, etc.).
- Conflict: **HTTP 409**, body includes `error=BATCH_SAVE_CONFLICT` and `conflicts[]` (entity id, client/server timestamps). Client may retry with `force=true` after user confirmation.

Primary save path for the wArchi model editor.

## Related model APIs

| Method | Path | Notes |
|--------|------|--------|
| `GET` | `/api/v1/models/{id}/package` | Export self-contained ZIP (model + used notations + wiki/`document_refs`) |
| `POST` | `/api/v1/models/package` | Async ZIP import (`multipart`); creates a **new** model for the current user |
| `GET` | `/api/v1/models/package/jobs/{jobId}` | Import job status |
| `POST` | `/api/v1/models/package/jobs/{jobId}/retry` | After `MODEL_EXISTS`, retry with name/version overrides (no ZIP re-upload) |
| `POST` | `/api/v1/models/{id}/oef/normalize` | Multipart OEF XML → compact JSON for the import wizard (edit permission; ~100 MB) |
| `POST` | `/api/v1/models/{targetModelId}/diagram-copies/preview` | Preview mapping into another model |
| `POST` | `/api/v1/models/{targetModelId}/diagram-copies/commit` | Commit copy; requires resolutions for unmatched entities |

Package import **reuses** a notation with the same name+version when the user can view it and the structure is compatible; otherwise the job fails with a reason. Diagram copy does not copy wiki/files in v1.

Notation catalog reads from the model editor may pass `?modelId=`: allowed with direct notation permission **or** model edit rights when that notation version is used by an active diagram in the model.
