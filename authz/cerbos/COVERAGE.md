# Cerbos authorization coverage

Этот документ фиксирует текущую карту покрытия авторизации в `arepos-server` после перехода на Cerbos-only модель.

## Целевой инвариант

- Все бизнес-проверки доступа выполняются через `ResourceAccessService`.
- `ResourceAccessService` принимает финальное решение только через Cerbos.
- При недоступности Cerbos backend возвращает `503`.
- Role-based bypass (`CurrentUser.isAdmin`, `hasRole('ADMIN')` в контроллерах) отсутствуют.

## Resource policies

Покрытые ресурсы policy:
- `model`
- `notation`
- `diagram`
- `node_type`
- `link_type`
- `node_shape`
- `file`
- `share`
- `admin_panel`
- `user_admin`

Policy-файлы: `authz/cerbos/policies/resource.*.yaml`.

## Runtime mapping (service -> Cerbos resource/action)

Ключевые методы в `src/main/kotlin/ru/kavader/arepos/security/ResourceAccessService.kt`:

- `canViewModel` / `canEditModel` -> `model:view|edit`
- `canViewNotation` / `canEditNotation` -> `notation:view|edit`
- `canViewDiagram` / `canEditDiagram` -> `diagram:view|edit`
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
- Диаграммы/файлы/документы: `DiagramsController`, `FilesController`, `DocumentsController`
- Каталоги: `NodeTypesController`, `LinkTypesController`, `NodeShapesController`
- Доступ/шаринг/дашборд: `PermissionsController`, `AccessSharesController`, `DashboardController`
- Platform admin: `UsersController`

Публичные и auth-only контроллеры (не ресурсная Cerbos-авторизация):

- `AuthController` (login/register/refresh)
- `RootController`
- `SystemController`
- `AuditLogController` (доступ регулируется общими security правилами и бизнес-контекстом endpoint)

## Верификация (audit commands)

Проверка отсутствия role bypass в контроллерах:

```bash
rg "CurrentUser\\.isAdmin\\(|hasRole\\('ADMIN'\\)" src/main/kotlin/ru/kavader/arepos/controller
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
