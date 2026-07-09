# Cerbos authorization coverage

Этот документ фиксирует текущую карту покрытия авторизации в `arepos-server` после перехода на Cerbos-only модель.

## Целевой инвариант

- Все бизнес-проверки доступа выполняются через `ResourceAccessService`.
- `ResourceAccessService` принимает финальное решение только через Cerbos.
- При недоступности Cerbos: типично **default-deny → 403**; явный **503** `Authorization service is unavailable` — только если `CerbosDecisionService` бросает (см. `authz/cerbos/README.md`).
- Role-based bypass (`CurrentUser.isAdmin`, `hasRole('ADMIN')` в контроллерах) отсутствуют.

## Resource policies

Ресурсы Cerbos, к которым **runtime** обращается из Kotlin (`CerbosResourceKind` в `CerbosAuthzModel.kt`):
- `model`
- `notation`
- `node_type`
- `link_type`
- `node_shape`
- `file`
- `share`
- `admin_panel`
- `user_admin`

Отдельного ресурса `diagram` в Cerbos нет: права на диаграмму выводятся из прав на **модель**, к которой привязана диаграмма (см. маппинг ниже).

Policy-файлы: `authz/cerbos/policies/resource.*.yaml`.

## Runtime mapping (service -> Cerbos resource/action)

Ключевые методы в `src/main/kotlin/ru/kavader/arepos/security/ResourceAccessService.kt`:

- `canViewModel` / `canEditModel` -> `model:view|edit`
- `canViewNotation` / `canEditNotation` -> `notation:view|edit`
- `canViewDiagram` / `canEditDiagram` -> **`model:view|edit`** (делегируют в `canViewModel` / `canEditModel` по `diagram.model`)
- `canViewNodeType` / `canEditNodeType` -> `node_type:view|edit`
- `canViewLinkType` / `canEditLinkType` -> `link_type:view|edit`
- `canViewNodeShape` / `canEditNodeShape` -> `node_shape:view|edit`
- `canViewFile` -> `file:view`
- `canManageShares` -> `share:manage`
- `canViewAdminPanel` -> `admin_panel:view`
- `canManageUsers` -> `user_admin:manage`

Все `requireCan*` методы используют эти же решения для принудительной проверки.

## Endpoint coverage by controller group

Контроллеры с бизнес-авторизацией через `accessService`:

- Модели/дерево: `ModelsController`, `NodesController`, `LinksController`, `ModelBatchSaveController`, `ModelDiffController`
- Нотации/типы/правила: `NotationsController`, `ComponentsController`, `RelationsController`, `RelationRulesController`, `RelationRulesSyncController`, `NotationImportController`
- Диаграммы/файлы/документы: `DiagramsController`, `DiagramEditLocksController`, `FilesController`, `DocumentsController`
- Каталоги: `NodeTypesController`, `LinkTypesController`, `NodeShapesController`
- Доступ/шаринг/дашборд: `PermissionsController`, `AccessSharesController`, `DashboardController`, `AuditLogController`
- Platform admin: `UsersController`

`DiagramEditLocksController` использует проверки через `DiagramEditLockService` и `DiagramCollaborationService`:
- acquire/heartbeat/release, live/pointer (держатель блокировки) -> `model:edit` через `requireCanEditDiagram`
- spectate start/ping/leave -> `model:view` через `requireCanViewDiagram`
- list locks по `modelId` -> `model:view` через `requireCanViewModel`
- list locks без `modelId` (все активные) -> `admin_panel:view` через `canViewAdminPanel`
- force-release -> `admin_panel:view` через `canViewAdminPanel`

Публичные и auth-only контроллеры (не ресурсная Cerbos-авторизация):

- `AuthController` (login/register/refresh)
- `RootController`
- `SystemController`
- `UsersController` только для endpoint self-service/public:
  - `GET /api/v1/users/{id}/public`
  - `GET /api/v1/users/public/by-email`
  - `GET /api/v1/users/public/search`
  - `POST /api/v1/users/public/batch`
  - `GET /api/v1/users/me/profile`
  - `PUT /api/v1/users/me/profile`

## Осознанные границы Cerbos

Ниже случаи, которые **осознанно** остаются вне ресурсной policy-авторизации Cerbos:

- Authentication/identity поток (`AuthController`): проверка credentials, refresh-token, `adminSecret`, `isActive`.
- Доступность endpoint-ов на уровне Spring Security (`authenticated()/permitAll()` в `SecurityConfig`).
- Self-service операции профиля текущего пользователя (`/users/me/profile`) по `currentUserId`.
- Публичные карточки пользователей (`/users/public/*`) для аутентифицированных пользователей с фильтрацией `ADMIN`.

Это не fail-open bypass; это границы между:
1) авторизацией доступа к доменным ресурсам (Cerbos), и  
2) базовой аутентификацией/идентичностью и публичным read-only функционалом.

## Верификация (audit commands)

Проверка отсутствия role bypass в контроллерах:

```bash
rg "CurrentUser\\.isAdmin\\(|CurrentUser\\.isEditorOrAdmin\\(|hasRole\\('ADMIN'\\)" src/main/kotlin/ru/kavader/arepos/controller
```

Проверка, что проверки доступа идут через `accessService`:

```bash
rg "accessService\\.(requireCan|can)" src/main/kotlin/ru/kavader/arepos/controller
```

Проверка ресурсов Cerbos в коде authz-модели:

```bash
rg "enum class CerbosResourceKind" src/main/kotlin/ru/kavader/arepos/security/CerbosAuthzModel.kt
```

## Notes for new endpoints

Для каждого нового защищенного endpoint обязательно:

1. Выбрать `resource` и `action` Cerbos.
2. Добавить/обновить policy в `authz/cerbos/policies`.
3. Выполнять проверку только через `ResourceAccessService`.
4. Добавить тестовый сценарий с `ALLOW` и `DENY` для нового действия.
