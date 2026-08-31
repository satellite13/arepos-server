# Code review: Papirus 0.9.13

Документ сохранён в `arepos-server` (сестринский проект): у cloud-agent не было прав
на push в `satellite13/papirus` (HTTP 403). Имеет смысл перенести файл в
`papirus/docs/code-review-0.9.13.md` при наличии write-доступа.

**Дата:** 2026-08-31  
**Пакет:** `@ngroznykh/papirus` 0.9.13  
**Репозиторий:** https://github.com/satellite13/papirus  
**Объём:** ревью исходников `src/` (фокус на core / edges / routing / persistence)  
**Проверки:** `npm test` — 602/602 passed; `tsc --noEmit` — ok

## Краткий вердикт

Зрелый Canvas-движок диаграмм без runtime-зависимостей: сильная модель dirty-флагов, продуманный reconnect-preview, history через `styleOverrides`, orthogonal routing с регрессионными тестами. При этом есть дефекты с потерей данных (индекс рёбер, undo cascade для junctions), дыры в pointer/gesture lifecycle и расхождения serializer/clipboard с history.

---

## Сильные стороны

1. **`markDirty` vs `markContentDirty`** — pan/zoom не пересчитывает маршруты и endpoint’ы.
2. **Reconnect preview** выравнивает роли препятствий с `EdgeEndpointUpdater` — меньше расхождений preview/final.
3. **History snapshots** используют `styleOverrides`, а не merged theme style.
4. **Viewport culling** + обработка `contextlost` / `contextrestored`.
5. **Orthogonal router** (`routeOrthogonalAround`) покрыт scenario-тестами на реальные патологии раскладки.

---

## Critical

### 1. Устаревший `_nodeEdgeIndex` после смены binding

**Категория:** bug  
**Файлы:** `src/core/DiagramRenderer.ts` (`addEdge`, `_updateEdgeIndex`), `src/elements/Edge.ts` (`from`/`to` setters)

Индекс `nodeId → edgeIds` обновляется только при add/remove. Сеттеры `Edge.from` / `Edge.to` вызывают `bindingListener` → `markContentDirty`, но не переиндексируют.

**Сценарий:** reconnect A→B; удаление A всё ещё каскадно снимает ребро (stale index); удаление B оставляет dangling edge.

**Фикс:** в binding-listener снимать старые `nodeId` из индекса и добавлять новые (сравнивать previous vs next endpoint).

### 2. Undo delete теряет junction-рёбра

**Категория:** bug / data loss  
**Файлы:** `src/core/InteractionManager.ts` (`buildDeleteCommand`), `src/core/DiagramRenderer.ts` (`removeEdgeImmediate`)

`removeEdgeImmediate` каскадно удаляет рёбра с `from.edgeId` / `to.edgeId`. `buildDeleteCommand` собирает только selection и node-attached edges — dependents не попадают в команду.

**Сценарий:** удаление host edge (или узла, тянущего host) → junctions исчезают; Undo восстанавливает host/nodes без junctions.

**Фикс:** перед remove собрать транзитивных dependents (или snapshot), на undo добавлять в dependency order.

---

## High

### 3. Жесты ломаются при mouseup вне canvas

**Категория:** bug  
**Файлы:** `src/events/InputHandler.ts`, `src/core/InteractionManager.ts` (`handleMouseMove`)

`mouseup` / `mousemove` только на canvas; нет `setPointerCapture` / window pointerup. Ветка `buttons === 0` чистит pan / scrollbar / overlay, но не connect / reconnect / drag / resize / control points.

**Фикс:** pointer capture на mousedown или document-level `pointerup` / `pointercancel`, завершающий все gesture-менеджеры.

### 4. `connectionValidator` не действует на reconnect

**Категория:** correctness  
**Файл:** `src/core/ConnectionManager.ts`

Валидатор проверяется при создании связи; путь reconnect (`updateReconnectingEdge` / reconnect mouseup) его обходит.

**Фикс:** те же проверки (и forbidden cursor) на reconnect; при запрете — restore original endpoint.

### 5. `LabelEditor.start` теряет правки при смене лейбла

**Категория:** bug  
**Файл:** `src/core/LabelEditor.ts`

`start()` вызывает `finish(true)` без `onCommit`. Commit срабатывает только если передан `onCommit`.

**Сценарий:** правка лейбла A → dblclick на B → правки A отбрасываются без history.

**Фикс:** хранить `onCommit` в state и вызывать `finish(true, storedCommit)` при старте нового редактирования.

### 6. Serializer / clipboard пишут merged `style`, не overrides

**Категория:** correctness  
**Файлы:** `src/utils/Serializer.ts`, `src/core/ClipboardManager.ts`, `src/elements/Node.ts` / `Edge.ts`

`node.style` / `edge.style` после `applyStyleManager` — merged runtime style. History корректно снапшотит `styleOverrides`.

**Сценарий:** save/load или paste запекает цвета темы в overrides; смена темы больше не перекрашивает элементы.

**Фикс:** сериализовать/копировать `styleOverrides` (и label overrides), как в `createNodeSnapshot` / `createEdgeSnapshot`.

### 7. `clear()` / deserialize при включённых анимациях

**Категория:** bug  
**Файл:** `src/core/DiagramRenderer.ts` (`clear`, `removeNode` / `removeEdge`)

С exit-animation remove откладывается; `clear()` чистит maps без hard-remove и без снятия всех binding-listeners на retained edge-объектах.

**Фикс:** hard-clear через `remove*Immediate` / skip exit animation для clear/deserialize; snapshot ID перед итерацией.

### 8. Коллизии ID при paste

**Категория:** correctness  
**Файл:** `src/core/ClipboardManager.ts`

ID вида `` `${id}_copy_${Date.now()}` `` — двойной paste в ту же миллисекунду → `Map.set` перезаписывает элементы.

**Фикс:** `generateId()` / UUID / monotonic counter на каждый элемент.

### 9. `Edge.render` не сбрасывает `globalAlpha`

**Категория:** bug  
**Файл:** `src/elements/Edge.ts` (`applyStyle`, `render`)

`applyStyle` выставляет `globalAlpha`; `render()` не делает `save`/`restore` и не возвращает alpha в `1`.

**Сценарий:** полупрозрачное ребро «заражает» последующую отрисовку кадра (другие edges / overlays / handles).

**Фикс:** `ctx.save()`/`restore()` вокруг paint или явный reset `globalAlpha` (и dash) в конце `render`.

---

## Medium

### 10. Неполная fidelity clipboard

`ClipboardManager` не копирует icon, badges, `contentInset`, `anchorPoints`, `labelPlacement`, composite `content`, edge markers / `labelPosition` / `labelFollowPath`.

**Фикс:** переиспользовать Serializer (или общие serialize-хелперы).

### 11. Reconnect node → edge невозможен

`findEdgeDropTarget` на reconnect вызывается только если `originalEdgeEndpoint?.edgeId` задан.

### 12. SVG XSS через недоверенные стили

`SvgExporter`: text/`d`/`href` экранируются; `fill` / `stroke` / `backgroundColor` / `font-family` интерполируются raw.

**Фикс:** прогонять все attribute values через `escapeAttribute` (или allowlist цветов).

### 13. Binding listeners и destroy/clear

`destroy` снимает dirty-listeners, но binding listeners на рёбрах чистятся только в `removeEdgeImmediate`. После `clear()` retained edge refs могут удерживать renderer.

### 14. Groups silently drop при deserialize

Без `groupFactory` группы валидируются, но не восстанавливаются — round-trip теряет grouping без ошибки.

### 15. Port lock во время paint

`EdgeEndpointUpdater.lockAnchors` / `preferFacingSidePorts` мутируют `edge.from`/`to` вне history → сериализация «меняет» диаграмму при просмотре.

### 16. Keyboard gating слишком широкий

`shouldHandleKeyboard` возвращает true для произвольных focused non-input элементов host UI → Delete/Backspace могут срабатывать вне canvas.

### 17. Perf: полный scan на mousemove

Hover / outline snap / edge drop: O(n) по nodes и edges + аллокации `Array.from` на каждый move.

### 18. `PropertyChangeBatcher` на destroy

`InteractionManager.destroy` не `flush()` / не отменяет таймеры — возможны мутации после teardown.

### 19. Сложность `routeOrthogonalAround.ts` (~1.1k LOC)

Плотные special cases и magic constants; правки легко дают регрессии. Имеет смысл дробить фазы (exit → graph → score) при сохранении golden-тестов как контракта.

---

## Low / API / docs

| # | Проблема |
|---|----------|
| 20 | `ANCHOR_POINT_HITBOX_RADIUS` не масштабируется от zoom (в отличие от edge handles) |
| 21 | `HistoryManager.execute` всегда зовёт `command.execute()` — риск double side effects у кастомных команд; нужен `record()` / документированный контракт |
| 22 | `isCompatibleTarget` всегда `true` — мёртвый API |
| 23 | Не экспортированы из `src/index.ts`: `SerializerValidationError`, snapshot-хелперы, часть history-команд (упоминаются в docs) |
| 24 | В `AGENTS.md` устарели coverage thresholds относительно `vitest.config.ts` (62/62/52/62) |
| 25 | Смешение RU/EN комментариев в hot path |

---

## Покрытие тестами

- **61** файлов `*.test.ts`, пороги coverage: lines/functions/statements **62**, branches **52**.
- Хорошо покрыты: `ConnectionManager`, `InteractionManager`, `DiagramRenderer`, routing, composite, Serializer, SvgExporter.
- Слабо / без dedicated unit: `ClipboardManager`, `ResizeManager`, `NavigationManager`, `LabelEditor`, `PropertyChangeBatcher`, `AnimationManager`, `ContextMenuManager`, concrete shape nodes, `ImageExporter`.

Известный долг из `docs/README.md` (по-прежнему актуален): тесты `StyleManager`; регрессия paste edge insert + полный undo/redo после paste.

---

## Рекомендуемый порядок фиксов

1. Индекс рёбер при смене binding  
2. Cascade dependents в delete/undo  
3. Pointer capture / document pointerup  
4. Validator на reconnect + LabelEditor commit-on-switch  
5. Serializer/clipboard → `styleOverrides` + уникальные paste ID  
6. `globalAlpha` reset / hard-clear / SVG attribute escaping  

---

## Архитектурный контекст (для читателя ревью)

Фасад — `DiagramRenderer` (класса `Diagram` нет). Интерактивность собирается в `InteractionManager`. Самые крупные модули: `ConnectionManager` (~1.6k), `Node` (~1.3k), `InteractionManager` (~1.3k), `DiagramRenderer` (~1.2k), `routeOrthogonalAround` (~1.1k), `Edge` (~1.1k).

Пайплайн:

```
InputHandler → InteractionManager → Selection/Drag/Resize/Nav/Connection/History
                                    ↓
                              DiagramRenderer (rAF dirty loop)
                                    ↓
                    contentDirty → EdgeEndpointUpdater → render (cull → groups → nodes → edges → overlays)
```
