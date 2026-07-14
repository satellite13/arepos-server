# Remediation аудита arepos-server — июль 2026

Реализация рекомендаций из [code-audit-2026-07.md](./code-audit-2026-07.md) на ветке `feat/code-audit-remediation`.

## Сделано

### Высокий приоритет
1. **DiagramsController** — вынесены `DiagramShareLinkService`, `DiagramLifecycleService`.
2. **Bean validation** — аннотации на create DTO + `@Valid` на create/update `@RequestBody` (частичные update DTO без ложных `@NotBlank`).
3. **ResourceAccessService** — facade (~241 строк) + `security/access/{ShareResolver,CerbosDecisionCache,BatchEvaluator,TopLevelAccess,NotationDiagramAccess}`.
4. **getNotationMeta** — `existsViewableModelDiagramWithNotation` вместо full scan.
5. **GlobalExceptionHandler** — catch-all `INTERNAL_ERROR`; `application-prod.yaml` → `include-stacktrace: never`.
6. **`@Transactional`** — перенесено в сервисы (`ModelLifecycleService`, `NotationLifecycleService`, `AuthTokenService`, `AccessShareService`, `RelationRulesSyncService`, `NotationImportService`, diagram services).
7. **Integration-тесты** — AccessShares, NotationImport, Files (+ service tests batch-save / edit-lock / file storage).

### Средний приоритет
8. **NotationImportService** — импорт вне контроллера.
9. **DRY** — `AdminListSupport.mapWithPermissions` / owner helpers.
10. **truncateTables** — shares, preview links, document_refs, file_versions, node_shapes, refresh_tokens, model_sync_outbox.
11. Service tests для критичных сервисов (см. выше).
12. `!!` → `requireNotNull` в model/notation mappers; `LOCK_TTL` → `arepos.diagram-lock.ttl-seconds`.
13. **001 migration** — убран `runOnChange: true`; см. [migrations.md](./migrations.md).

### Низкий приоритет
14. Mappers → пакет `ru.kavader.arepos.mapper`.
15. Typed catches + logging в FileStorage / ModelCopy / ModelAttrs.
16. Константы `ACCESS_DENIED` / `ADMIN_ONLY` (строки для клиентов без изменений).
17. KDoc NotationImport authz: create под caller, без `requireCanEditNotation`, reuse types by name.

## Сознательно не сломано
- REST paths и JSON field names
- Cerbos policies
- Содержимое applied `001-init.sql` (только `runOnChange`)

## Known debt
- DocumentRefsService: отдельный integration-тест можно расширить дальше
- Style/coverage thresholds для backend не поднимались в этом проходе
- Per-item Cerbos в некоторых mapper overloads без precomputed permissions — следить при list endpoints

## Проверка (2026-07-13)
`./gradlew test` — **BUILD SUCCESSFUL** (212+ tests including new AccessShares / NotationImport / Files / service tests).
