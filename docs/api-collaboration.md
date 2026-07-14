# Collaboration and batch-save API notes

Short contract notes for clients (wArchi and API tools). Full schemas: Swagger `/swagger-ui.html`.

## Auth reminder

Browser clients: cookies `warchi_access` / `warchi_refresh` + CSRF header `X-CSRF-Token` on mutating calls.  
Non-browser: `Authorization: Bearer <accessToken>` (CSRF not required when using Bearer-only flows without cookies).

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
| `POST` | `/{diagramId}/live` | Relay live canvas instances (JSON body) |
| `POST` | `/{diagramId}/pointer` | Relay collaborator pointer |

HTTP **409** may still appear from the lock service on rare races; clients should treat `LOCKED_BY_OTHER` on **200** as the primary “blocked editor” signal.

## Model batch save

`POST /api/v1/models/{modelId}/batch-save`

- Body: `BatchSaveRequest` — `force`, `nodes` / `links` / `diagrams` with `create` / `update` / `delete`.
- Updates/deletes carry `baseUpdatedAt` for optimistic concurrency.
- Success: `BatchSaveResponse` (id remaps for creates, etc.).
- Conflict: **HTTP 409**, body includes `error=BATCH_SAVE_CONFLICT` and `conflicts[]` (entity id, client/server timestamps). Client may retry with `force=true` after user confirmation.

Primary save path for wArchi model editor node/link/diagram changes.
