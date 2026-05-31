# 🔍 Полный рефакторинг-анализ `arepos-server`

Дата: 2026-05-31

---

## 📊 Общая картина

| Слой | Файлов | Критических проблем | Важных проблем | Улучшений кода |
|------|--------|---------------------|----------------|----------------|
| Контроллеры | 25 | 3 | 7 | 15+ |
| Сервисы | 15 | 5 | 4 | 6 |
| Безопасность | 9 | 3 | 3 | 4 |
| Конфигурация | 12 | 1 | 2 | 3 |
| Модели/JPA | 18 | 2 | 4 | 5 |
| Репозитории | 22 | 2 | 3 | 4 |
| DTO | 5 | 1 | 2 | 3 |

---

## 🔴 КРИТИЧЕСКИЕ БАГИ (надо исправить немедленно)

### 1. `CurrentUser.isEditorOrAdmin()` — проверяет только админа
**Файл:** `src/main/kotlin/ru/kavader/arepos/security/CurrentUser.kt:19`
```kotlin
fun isEditorOrAdmin(): Boolean = isAdmin()
```
Метод с названием «редактор или админ» проверяет **только** `isAdmin()`. Пользователи с ролью `EDITOR` никогда не пройдут эту проверку. Это означает, что любой код, полагающийся на этот метод, молча отказывает редакторам.

**Исправление:** `fun isEditorOrAdmin(): Boolean = isAdmin() || isEditor()`

---

### 2. `NodesController.updateNode` — `parentNodeId = null` обнуляет родителя
**Файл:** `src/main/kotlin/ru/kavader/arepos/controller/NodesController.kt:190-209`
```kotlin
val parentNode = if (request.parentNodeId != null) {
    // ... resolve parent
} else {
    null  // <-- ЗДЕСЬ: обнуляет существующего родителя!
}
// ...
parentNode = parentNode,  // применяется всегда
```
Когда клиент **не передаёт** `parentNodeId` в запросе обновления, код присваивает `parentNode = null` и **стирает существующую связь с родителем**. Это отличается от всех остальных nullable-полей, где используется `request.field ?: entity.field`.

**Исправление:** Добавить различие между «поле не передано» и «поле передано как null». Либо использовать `request.parentNodeId ?: entity.parentNode?.id`.

---

### 3. `UserDetailsServiceImpl` — пустой пароль при `null`-хеше
**Файл:** `src/main/kotlin/ru/kavader/arepos/security/UserDetailsServiceImpl.kt:22`
```kotlin
.password(user.passwordHash ?: "")
```
Если поле `passwordHash` в базе равно `null` (например, после миграции), Spring Security принимает **пустой пароль** для этого пользователя.

**Исправление:** `?: throw IllegalStateException("User ${user.id} has no password hash")`

---

### 4. `ResourceAccessService` — утечка `ThreadLocal` кеша авторизации
**Файл:** `src/main/kotlin/ru/kavader/arepos/security/ResourceAccessService.kt:42`
```kotlin
private val localDecisionCache = ThreadLocal.withInitial { mutableMapOf<String, Any>() }
```
Кеш **никогда не очищается** (нет вызова `.remove()`). В thread-pool'е веб-сервера устаревшие авторизационные решения от предыдущего запроса могут «протечь» на следующий запрос, вызывая ложные разрешения или запреты доступа.

**Исправление:** Добавить `localDecisionCache.remove()` в `finally`-блок перехватчика, либо использовать request-scoped бин вместо `ThreadLocal`.

---

### 5. `MinioDiagramSvgStorage.getSvg` — утечка InputStream
**Файл:** `src/main/kotlin/ru/kavader/arepos/service/MinioDiagramSvgStorage.kt:42`
```kotlin
return minioClient.getObject(...).readAllBytes()
```
Поток от MinIO **не закрывается**. При ошибке чтения HTTP-соединение утекает.

**Исправление:**
```kotlin
return minioClient.getObject(...).use { it.readAllBytes() }
```

---

## 🟠 ВАЖНЫЕ ПРОБЛЕМЫ

### 6. Спуфинг аудит-логов через `X-User-Id`
**Файл:** `src/main/kotlin/ru/kavader/arepos/config/AuditInterceptor.kt:58-59`

На `permitAll` эндпоинтах любой клиент может установить HTTP-заголовок `X-User-Id` в любой UUID, и он будет записан в аудит как действующий пользователь. Заголовок должен приниматься только когда он был явно установлен JWT-фильтром.

**Исправление:** Добавить флаг «аутентифицирован» в `AuditInterceptor`, поднимаемый только из `JwtAuthenticationFilter`.

---

### 7. `ModelSyncOutboxPublishService` — STOMP-отправка внутри транзакции
**Файл:** `src/main/kotlin/ru/kavader/arepos/service/ModelSyncOutboxPublishService.kt:26-39`
```kotlin
@Transactional
fun publishPending() {
    // ... PESSIMISTIC_WRITE lock ...
    messagingTemplate.convertAndSend(...)  // внутри транзакции!
}
```
Если отправка STOMP-сообщения падает — **вся транзакция откатывается**, включая уже успешно обработанные строки. Успешные сообщения будут повторно отправлены при следующем запуске. Это нарушает семантику outbox-паттерна.

**Исправление:** Обрабатывать каждую строку в отдельной транзакции, либо отправлять STOMP после коммита через `TransactionSynchronization.afterCommit`.

---

### 8. Массовая проблема: `updatedAt` не обновляется при UPDATE
Затронуты **10+ контроллеров**: `DiagramsController`, `LinksController`, `NodesController`, `RelationsController`, `ComponentsController`, `ModelsController`, `NotationsController`, `LinkTypesController`, `NodeTypesController`, `RelationRulesController`.

Во всех методах `update*` нет присвоения `updatedAt = Instant.now()`. Если JPA-аудирование не настроено, `updatedAt` остаётся stale.

**Исправление:** Добавить `@LastModifiedDate` в сущности с `@EntityListeners(AuditingEntityListener::class)` или явно обновлять `updatedAt` в update-методах.

---

### 9. Циклические ссылки `parentNode` → `StackOverflowError`
**Файл:** `src/main/kotlin/ru/kavader/arepos/service/ModelDiffService.kt:57-76`
```kotlin
private fun buildNodePathMap(nodes: List<Nodes>): Map<UUID, String> {
    // ... рекурсивный resolvePath без проверки циклов
}
```
При наличии циклических ссылок `parentNode` → бесконечная рекурсия → `StackOverflowError`.

**Исправление:** Добавить `visited: MutableSet<UUID>` в `resolvePath` для детекции циклов.

---

### 10. Гонка данных в `DocumentRefsService.registerRef`
**Файл:** `src/main/kotlin/ru/kavader/arepos/service/DocumentRefsService.kt:148-171`

Проверка дубликата (строки 148-155) и `save` (строка 171) не атомарны — между ними может вклиниться другой поток и создать дубликат.

**Исправление:** Добавить `@Transactional` и уникальный constraint в БД на `(file_id, node_type_id)` и `(file_id, link_type_id)`.

---

### 11. `FileStorageService` — сравнение со строковым литералом `"null"`
**Файл:** `src/main/kotlin/ru/kavader/arepos/service/FileStorageService.kt:162`
```kotlin
latestVersion.versionId != "null"
```
Вместо проверки на `null`, сравнивается со строкой `"null"`. Если MinIO когда-либо вернёт версию с ID `"null"`, она будет пропущена.

**Исправление:** `latestVersion.versionId != null`

---

## 🟡 ДУБЛИРОВАНИЕ КОДА (крупнейшая проблема архитектуры)

### 12. Разрешение `ownerId` при create — 10 идентичных копий
```kotlin
val resolvedOwnerId = if (accessService.canViewAdminPanel()) {
    request.ownerId ?: currentUserId
} else {
    currentUserId
}
```
Дублируется в:
- `ComponentsController.kt:84`
- `DiagramsController.kt:92`
- `LinksController.kt:136`
- `LinkTypesController.kt:148`
- `ModelsController.kt:191`
- `NodesController.kt:107`
- `NodeTypesController.kt:148`
- `NotationsController.kt:202`
- `RelationRulesController.kt:98`
- `RelationsController.kt:84`

**→ Вынести в `ResourceAccessService.resolveOwner(request.ownerId)`.**

---

### 13. Разрешение `owner` при update — 10 идентичных копий
```kotlin
val owner = if (accessService.canViewAdminPanel()) {
    request.ownerId?.let { usersRepository.findById(it).orElseThrow { ... } } ?: entity.owner
} else {
    entity.owner
}
```
Дублируется в тех же 10 контроллерах.

**→ Вынести в `ResourceAccessService.resolveOwnerForUpdate(request.ownerId, entity.owner)`.**

---

### 14. Дублирование между контроллерами и `ModelBatchSaveController`
Методы `isNodeTypeUsedInModelDiagramNotations` и `isLinkTypeUsedInModelDiagramNotations` скопированы 1:1 из обычных контроллеров в `ModelBatchSaveController.kt:614-662`.

---

### 15. `parseSemver` — 3 копии
- `ModelsController.kt:172-179`
- `NotationsController.kt:400-407`
- `DiagramsController.kt:477-484`

---

### 16. Мёртвый код: `getCurrentUser` определён, но не используется
- `LinksController.kt:279-283`
- `RelationsController.kt:180-184`
- `RelationRulesController.kt:223-227`

---

### 17. `throw` внутри `orElseThrow` — 2 копии
`LinkTypesController.kt:211`, `NodeTypesController.kt:211`:
```kotlin
val entity = repo.findById(id).orElseThrow {
    throw ResponseStatusException(HttpStatus.NOT_FOUND, "...")  // лишний throw
}
```
Лямбда `orElseThrow` должна возвращать исключение, а не бросать его. Корректно:
```kotlin
orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "...") }
```

---

### 18. `resolveReadableOwner` — 4 почти идентичные реализации
В `ModelsController`, `NotationsController`, `LinkTypesController`, `NodeTypesController`.

Отличаются только вызываемым репозиторным методом (`findAccessibleByOwnerForUser` vs `existsAccessibleByOwnerForUser`).

**Рекомендация:** Создать общий `CrudHelper` или абстрактный базовый класс для CRUD-операций с учётом ACL.

---

## 🔵 ПРОБЛЕМЫ ТРАНЗАКЦИЙ

### 19. `ModelSyncBroadcaster` вызывается после `save()` без синхронизации с транзакцией
**12+ точек вызова** в: `DiagramsController`, `LinksController`, `ModelsController`, `NodesController`.

Если broadcast падает, БД уже закоммичена → клиенты видят устаревшие данные. Модель изменена, но уведомление не ушло.

**→ Использовать `TransactionSynchronization.afterCommit` или `@TransactionalEventListener(phase = AFTER_COMMIT)`.**

---

### 20. Множественные операции без `@Transactional`
- `AccessSharesController.grantShare` — удаление старых шеров + сохранение нового неатомарны. Если save падает, delete уже закоммичен → потеря данных.
- `ModelsController.createModel` — создание модели + root node неатомарны. Если node creation падает, модель уже сохранена без `treeRootNodeId`.
- `NotationImportController.importNotation` — подвержен гонке при параллельных импортах одного и того же пакета. `@Transactional` не спасает от дубликатов — нужен constraint в БД.

---

### 21. `FileStorageService` — распределённая транзакция MinIO + PostgreSQL
Загрузка в MinIO может пройти успешно, а сохранение в БД — упасть → орфанные файлы в MinIO. Требуется компенсирующая логика или outbox-паттерн.

---

## 🟢 ДИЗАЙН JPA-СУЩНОСТЕЙ

### 22. `data class` для JPA-сущностей — 14 из 18
`data class` генерирует `equals`/`hashCode` по всем полям конструктора. С `FetchType.LAZY` и прокси Hibernate это вызывает:
- Лишние запросы к БД при сравнении (доступ к lazy-полям)
- Изменение `hashCode` при переходе `id` от `null` к значению после `persist()` → ломает `HashSet`/`HashMap`

Только `DiagramEditLocks` и `ModelSyncOutbox` используют обычный `class` — это правильный подход.

**→ Заменить `data class` на `class` во всех сущностях.**

---

### 23. `@UniqueConstraint` в аннотациях расходится с миграциями
`Models` и `Notations` имеют `@UniqueConstraint(name = "models_name_version_key")`, но миграция `024` **удалила** эти constraint'ы и заменила на частичные уникальные индексы (`WHERE deleted = false`). При `ddl-auto=validate` Hibernate упадёт.

---

### 24. `DocumentRefs` — нет индексов вообще
Ни в аннотациях сущности, ни в миграциях. Все 10+ поисковых запросов делают sequential scan по:
- `file_id`
- `node_type_id`
- `link_type_id`
- `model_id`
- `notation_id`
- `component_id`
- `node_id`
- `diagram_id`
- `relation_id`
- `node_shape_id`

---

### 25. `ResourceShares.granteeUserId` nullable в уникальном constraint
```kotlin
UniqueConstraint(
    name = "resource_shares_unique_share",
    columnNames = ["resource_type", "resource_id", "grantee_user_id", "permission"]
)
```
В PostgreSQL `NULL`-значения считаются различными → возможны дубликаты публичных шеров (где `grantee_user_id IS NULL`).

---

## ⚙️ УЛУЧШЕНИЯ КОДА

### 26. Смешанные стили валидации
- `AccessSharesController` валидирует поля вручную (if-else)
- `ModelBatchSaveController` использует кастомное исключение
- Остальные контроллеры полагаются на Kotlin null-safety
- Нет `@Valid`/`@NotNull`/`@Size` ни на одном DTO

### 27. Три разных способа получить текущего пользователя
- `accessService.currentUserId()`
- `CurrentUser.getId()`
- `SecurityContextHolder.getContext().authentication?.principal as? UUID`

**→ Унифицировать через `CurrentUser` или `accessService`.**

### 28. `JwtTokenProvider` — платформозависимая кодировка ключа
```kotlin
jwtProperties.secret.toByteArray()  // нет указания Charset
```
На разных платформах разный дефолтный charset → разные ключи. Нужно: `toByteArray(Charsets.UTF_8)`.

### 29. `JwtTokenProvider` — повторный парсинг JWT
`getUserId`, `getRole`, `getTokenType` каждый вызывает `parseClaims()` заново, повторно валидируя подпись и expiry. Стоит распарсить один раз и сохранить claims.

### 30. `ModelSyncOutbox` — `createdAt = Instant.now()` в конструкторе
Время фиксируется при создании объекта в памяти, а не при вставке в БД. Если между созданием и `persist()` проходит время, значение некорректно.

### 31. `BatchSaveConflictException` с пустым списком конфликтов
```kotlin
class BatchSaveConflictException(val conflicts: List<BatchConflictItem>)
    : RuntimeException("Batch save conflict: ${conflicts.size} entity(ies)")
```
Семантически невозможно — исключение с 0 конфликтов. Нужен guard в конструкторе.

### 32. `BatchNodeCreate`/`BatchNodeUpdate` — нет поля `stableId`
В БД колонка `stable_id` — `NOT NULL`. Если не генерируется в сервисном слое, будет ошибка constraint violation.

### 33. `RelationRulesRepository` — 6 почти идентичных методов
Огромное количество идентичного SQL с минимальными различиями в проекциях (`findByFilters`, `findProjectedByFilters`, `findProjectedLightByFilters` и их версии `ForUser`). Любое изменение логики доступа требует правок в 6 местах.

### 34. `@Autowired` field injection в `JpaConfig`
```kotlin
@Autowired private lateinit var auditInterceptor: AuditInterceptor
```
Вместо constructor injection. Сложнее тестировать, менее идиоматично для Kotlin.

### 35. Все сущности без `@DynamicUpdate`
Каждый UPDATE перезаписывает все колонки, включая большие JSONB-поля (`attrs`). Для сущностей с большими JSON это лишний I/O.

---

## 📋 ПЛАН РЕФАКТОРИНГА (рекомендуемый порядок)

### Этап 1: Критические исправления (безопасность + баги)

| # | Что | Файлы |
|---|-----|-------|
| 1 | `isEditorOrAdmin()` проверяет только админа | `security/CurrentUser.kt:19` |
| 2 | `parentNodeId = null` стирает родителя | `controller/NodesController.kt:190` |
| 3 | Пустой пароль при `null`-хеше | `security/UserDetailsServiceImpl.kt:22` |
| 4 | Утечка `ThreadLocal` кеша | `security/ResourceAccessService.kt:42` |
| 5 | Утечка MinIO `InputStream` | `service/MinioDiagramSvgStorage.kt:42` |
| 6 | Спуфинг аудит-логов | `config/AuditInterceptor.kt:58` |

### Этап 2: Устранение дублирования (архитектура)

| # | Что | Как |
|---|-----|-----|
| 7 | `resolveOwner` при create (10 копий) | Вынести в `ResourceAccessService` |
| 8 | `resolveOwner` при update (10 копий) | Вынести в `ResourceAccessService` |
| 9 | `parseSemver` (3 копии) | Вынести в утилитный object/класс |
| 10 | `resolveReadableOwner` (4 копии) | Параметризовать и вынести |
| 11 | `isNodeTypeUsed...` (2 копии) | Общий сервисный метод |
| 12 | Унифицировать `getCurrentUser` | Единый метод в `CurrentUser` |

### Этап 3: Транзакционная целостность

| # | Что | Как |
|---|-----|-----|
| 13 | STOMP-отправка внутри транзакции outbox | `TransactionSynchronization.afterCommit` |
| 14 | Broadcast после save без транзакции | `@TransactionalEventListener(AFTER_COMMIT)` |
| 15 | Гонка в `registerRef` | `@Transactional` + уникальный constraint |
| 16 | Гонка в `importNotation` | `INSERT ... ON CONFLICT` или constraint |

### Этап 4: Улучшение JPA-слоя

| # | Что | Как |
|---|-----|-----|
| 17 | `data class` → `class` для сущностей | По образцу `DiagramEditLocks` |
| 18 | Рассогласование `@UniqueConstraint` и миграций | Удалить неактуальные аннотации |
| 19 | Индексы для `DocumentRefs` | Добавить в миграции и аннотации |
| 20 | `@DynamicUpdate` на сущностях с JSON | Добавить аннотацию |
| 21 | `granteeUserId` nullable в constraint | Сделать `NOT NULL` или изменить constraint |

### Этап 5: Чистота кода

| # | Что | Как |
|---|-----|-----|
| 22 | `throw` внутри `orElseThrow` | Убрать лишний `throw` |
| 23 | `@Autowired` field injection | Constructor injection |
| 24 | Валидация DTO | Добавить `@Valid`/`@NotNull`/`@Size` |
| 25 | Удалить мёртвый код (`getCurrentUser`) | 3 файла |
| 26 | Платформозависимый `toByteArray()` | `toByteArray(Charsets.UTF_8)` |
| 27 | Кешировать результат `parseClaims` | Разбирать JWT один раз |
| 28 | `createdAt` в outbox — время persist | `@PrePersist` или триггер БД |

---

## 📝 Методология

Анализ проведён по методике многоракурсного code review:

- **Angle A** — построчное сканирование диффа и окружающих функций
- **Angle B** — аудит удалённого поведения/инвариантов
- **Angle C** — трассировка вызовов между файлами (callers/callees)
- **Angle D** — поиск языковых и фреймворковых ловушек (Kotlin, JPA, Spring)
- **Angle E** — проверка корректности обёрток/прокси
- **Reuse** — поиск переизобретённого функционала
- **Simplification** — избыточная сложность
- **Efficiency** — wasted work (лишние запросы, блокирующие операции)
- **Altitude** — правильный уровень абстракции (bandaid vs механизм)
