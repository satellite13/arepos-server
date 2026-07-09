# Аудит кода arepos-server — июль 2026

Статический анализ структуры и качества кода. Объём: ~184 файла, ~16 500 строк Kotlin в `src/main/kotlin`. Liquibase-миграции 001–039 консистентны. TODO/FIXME в коде отсутствуют.

Связанные отчёты: `docs/code-audit-2026-07.md` в warchi и papirus.

## Сильные стороны

- Чёткие слои `controller` → `service`/`repository`; сложная логика в сервисах: `ModelBatchSaveService`, `DiagramEditLockService`, `DocumentRefsService`, `TypeCatalogListService`.
- Богатый набор общих хелперов: `AdminListSupport`, `NotationBoundEntityListSupport`, `CatalogTypeWriteSupport`, `NotationBoundEntityWriteSupport`, `ModelBoundEntityUpdateSupport`, `FilterQuerySupport`, `PagingSupport`.
- Сильный authz-стек: `ResourceAccessService` — единая точка (Cerbos batch, кэш решений на запрос, shares); enforce-only → 503 при недоступности Cerbos; WebSocket-авторизация на subscribe.
- `GlobalExceptionHandler` — структурированные ответы `{error, message, traceId}` без stack traces; timing-safe login, CSRF, httpOnly cookies, `SvgPreviewSecurityValidator`, санитизация filename в `FilesController`.
- TestContainers-ядро (PostgreSQL + Cerbos) с покрытием основных CRUD, auth, batch-save, locks, permissions; `open-in-view: false`, batch fetch настроен.

## Крупнейшие файлы — кандидаты на декомпозицию

| Строк | Файл | Комментарий |
|------:|------|-------------|
| 865 | `security/ResourceAccessService.kt` | God-service: Cerbos batch, кэш, shares, notation-diagram access |
| 539 | `controller/DiagramsController.kt` | CRUD + share-links (`:296–437`) + SVG + baseline versioning в контроллере |
| 515 | `service/ModelDiffService.kt` | Допустимо как сервис, но 33 использования `!!` |
| 491 | `repository/RelationRulesRepository.kt` | Сложные projection-запросы — норма для perf |
| 195 | `controller/NotationImportController.kt` | Весь импорт нотации в контроллере (`:68–156+`), без сервиса |

## Ключевые проблемы

### Высокий приоритет

1. **Валидация входных данных.** `@Valid` только на ~10 из ~40 `@RequestBody`. Без bean validation: `ComponentRequest`, `RelationRequest` (`dto/notation/NotationDtos.kt:44–89`), `LinkRequest`, `NodeTypeRequest` (`dto/model/ModelDtos.kt:71–78`), `AccessShareRequest` (`dto/access/AccessDtos.kt:8–13`), запросы `UsersController` (`:134,156,195`), `DiagramShareLinkRequest`/`DiagramUpdateRequest`.
2. **Толстые контроллеры.** `DiagramsController` (539 строк) — бизнес-логика share-links и versioning в контроллере; `NotationImportController` — весь импорт без отдельного сервиса.
3. **`@Transactional` на методах контроллеров** (антипаттерн, риск длинных транзакций на HTTP-запрос): `ModelsController:95,186,273,289`, `NotationsController:88,265,299`, `DiagramsController:243,419`, `AuthController:117,162`, `AccessSharesController:39`, `NotationImportController:35`, `RelationRulesSyncController:32`.
4. **Perf.** `getNotationMeta` (`NotationsController.kt:168–175`) грузит все диаграммы по notationId без пагинации вместо EXISTS-запроса (аналог `existsViewableModelDiagramWithNotation` уже есть).
5. **Обработка ошибок.** Нет глобального `@ExceptionHandler(Exception::class)` — необработанные исключения идут в Spring `/error`; `application-prod.yaml` не задаёт `server.error.include-stacktrace: never`.

### Средний приоритет

6. **Тесты.** Без integration-тестов: `AccessSharesController`, `NotationImportController`, `ModelDiffController`, `DocumentsController`, `FilesController` (upload/download/ACL). Из 29 сервисов покрыто ~6 (без тестов: `ModelBatchSaveService`, `ModelCopyService`, `FileStorageService`, `DocumentRefsService`, `DiagramEditLockService` и др.). `ControllerIntegrationTest.truncateTables` не включает `resource_shares`, `document_refs`, `diagram_preview_links`, `refresh_tokens`, `model_sync_outbox`, `diagram_edit_locks`.
7. **Kotlin-качество.** ~140+ использований `!!` (концентрация: `NotationMappers.kt` — 19, `ModelMappers.kt` — 13, `ResourceAccessService.kt` — 24, `ModelDiffService.kt` — 33); широкие `catch (Exception)` без логирования (`FileStorageService.kt:61,80`, `ModelCopyService.kt:179,192,200`, `ModelAttrsService.kt:26`).
8. **Дублирование.** `resolveReadableOwner()` идентичен в `ModelsController.kt:318–321` и `NotationsController.kt:315–318`; паттерн `map*Page` с batch permissions похож в 5 контроллерах; ручная валидация `AccessShareRequest` в контроллере (`AccessSharesController.kt:41–48`).
9. **Миграции.** `001-init.sql` с `runOnChange: true` (`db.changelog-master.yaml:5`) и `DROP TYPE/TABLE` — риск деструктивной переинициализации на существующей БД.

### Низкий приоритет

10. `LOCK_TTL = 180` секунд захардкожен (`DiagramEditLockService.kt:36`); mappers в `dto/` зависят от `ResourceAccessService` — размытие слоёв; `ModelMapper.toResponse(model)` без precomputed permission вызывает Cerbos на каждый элемент; N+1 в `DocumentRefsService.kt:39–49` при множественных refs.

## Рекомендации

### Высокий приоритет

1. Вынести бизнес-логику из `DiagramsController` в `DiagramShareLinkService` / `DiagramLifecycleService`.
2. Системно добавить bean validation (`@NotBlank`/`@NotNull`) на все create/update DTO + `@Valid` на все `@RequestBody`.
3. Декомпозировать `ResourceAccessService` (865 строк): TopLevelAccess / NotationDiagramAccess / BatchEvaluator / ShareResolver.
4. Заменить full scan в `getNotationMeta` на repository EXISTS-запрос.
5. Добавить catch-all `@ExceptionHandler` + `server.error.include-stacktrace: never` в prod-профиль.
6. Перенести `@Transactional` из контроллеров в сервисный слой.
7. Integration-тесты для `AccessSharesController`, `NotationImportController`, `FilesController` (критичны для ACL и файлов).

### Средний приоритет

8. Создать `NotationImportService`, убрать импорт-логику из контроллера.
9. Обобщить `resolveReadableOwner` и `map*Page` между контроллерами.
10. Расширить `truncateTables` на все таблицы схемы.
11. Сервисные тесты для `ModelBatchSaveService`, `DiagramEditLockService`, `FileStorageService`, `DocumentRefsService`.
12. Снизить `!!` в mappers через `requireNotNull`; вынести `LOCK_TTL` и таймауты в `application.yaml`.
13. Пересмотреть `001-init.sql`: immutable initial migration без DROP при `runOnChange`.

### Низкий приоритет

14. Перенести mappers из `dto/` в отдельный слой (`mapper/` или `service/`).
15. Typed exceptions + debug-логирование вместо широких `catch (_: Exception)`.
16. Унифицировать сообщения ошибок (`"Access denied"` vs `"Admin only"` vs коды).
17. Задокументировать, почему `NotationImport` не требует `requireCanEditNotation` (если intentional).

## Итоговая оценка

| Критерий | Оценка |
|----------|--------|
| Консистентность слоёв | 7/10 |
| DRY / хелперы | 7/10 |
| Kotlin quality | 7/10 |
| Безопасность | 8/10 |
| Тесты | 6/10 |
| Миграции | 8/10 |

Наибольший ROI: декомпозиция `DiagramsController`/`ResourceAccessService`, системная bean validation и закрытие пробелов в тестах ACL/shares/files.
