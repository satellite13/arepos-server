# Design: DRY SQL for notation-bound list/ACL queries

Date: 2026-07-23  
Repo: arepos-server  
Status: approved for planning

## Problem

Native SQL for listing Components, Relations, and RelationRules repeats the same filter and ACL predicates across `@Query` value/countQuery pairs. The worst hotspot is `RelationRulesRepository` (~491 LOC) with ~5–6 near-identical WHERE blocks. `ComponentsRepository` and `RelationsRepository` mirror each other (~259 LOC each) for `findByFilters` / `findAccessibleByFiltersForUser`.

Existing repository tests only cover persist + a single admin `findByFilters` case. ACL paths (`findAccessibleByFiltersForUser`, shares, diagram-editor model) are largely untested at repository level, so a mechanical DRY refactor risks silent regressions.

## Goals

- Deduplicate repeated SQL fragments via Kotlin compile-time `const val` string templates.
- Preserve current query semantics byte-for-byte (no intentional ACL “improvements”).
- Keep entity types and repository APIs separate (`Components`, `Relations`, `RelationRules` remain distinct).
- Lock behavior with repository tests **before** changing SQL structure.

## Non-goals

- Merging NodeTypes/LinkTypes (or Components/Relations) into a shared entity/controller.
- Moving `controller/*Support` helpers out of the controller package.
- Liquibase migrations, PostgreSQL VIEWs, or SQL functions.
- Unifying ACL semantics between Components/Relations (`v_resource_grants`) and RelationRules (`resource_shares`).
- Changing Cerbos decisions or HTTP contracts.
- Broad controller/service organization refactors.

## Approach (chosen)

**Kotlin SQL fragment objects** in `ru.kavader.arepos.repository.sql`, referenced from existing `@Query` annotations.

Rejected alternatives:

- PostgreSQL VIEW/function — single source of truth, but needs migration and higher regression risk.
- RelationRules-only cleanup — smaller, but leaves the Components↔Relations twin ACL untouched.

## Package layout

```text
src/main/kotlin/ru/kavader/arepos/repository/sql/
  NotationBoundListSql.kt         # admin filters for Components/Relations
  NotationVisibilitySql.kt       # ACL predicate for Components/Relations (v_resource_grants)
  RelationRulesFilterSql.kt      # JOIN + filter WHERE for relation_rules
  RelationRulesVisibilitySql.kt  # ForUser ACL (resource_shares) — separate from NotationVisibilitySql
```

### Fragment rules

1. Only `const val` / compile-time string templates so Spring Data `@Query` arguments remain compile-time constants. Prefer whole-query `const val` on the sql object (`@Query(value = ComponentsListSql.FIND_ACCESSIBLE, countQuery = ComponentsListSql.COUNT_ACCESSIBLE, nativeQuery = true)`) over inline annotation interpolation when that is clearer.
2. Do not rename query parameters (`:currentUserId`, `:diagramEditorModelId`, `:notationId`, `:ownerId`, `:name`, `:tagsJson`, `:relationId`, …).
3. Do not change repository method signatures or return types.
4. Alias handling: ready-made `const val` fragments per table alias (`c` / `r` / `rr`+joins), not runtime string builders.
5. RelationRules SELECT lists (entity vs projection vs light) stay different; share FROM/JOIN/WHERE(+ACL) only.
6. Preserve existing quirks exactly:
   - Components/Relations admin `findByFilters` uses `SELECT *`; accessible queries use `SELECT c.*` / `SELECT r.*`.
   - `ORDER BY` stays on value queries only (not countQuery): `c.name, c.id` / `r.name, r.id` / `rr.id`.
7. Do **not** “fix” ACL differences between list layers (see Semantic inventory below).

### Consumers (exact methods)

| Repository | Methods wired to fragments |
|------------|----------------------------|
| `ComponentsRepository` | `findByFilters`, `findAccessibleByFiltersForUser` (each: value + countQuery) |
| `RelationsRepository` | `findByFilters`, `findAccessibleByFiltersForUser` (each: value + countQuery) |
| `RelationRulesRepository` | `findByFilters`, `findProjectedByFilters`, `findProjectedLightByFilters`, `findProjectedByFiltersForUser`, `findProjectedLightByFiltersForUser` (each: value + countQuery) |

Note: RelationRules has **no** entity-level `findByFiltersForUser` — only projected ForUser variants. Keep that asymmetry.

Out of this PR’s success criteria (optional follow-up only): `existsNodeTypeReachableViaViewableNotation` / `existsLinkTypeReachableViaViewableNotation` — similar ACL text, but `:userId` + `resource_shares` shape; do not force-fit into `NotationVisibilitySql` here. Also out: derived Spring Data methods (`findByNotation`, `existsByRelationAnd…`, etc.).

### Semantic inventory (must preserve, not unify)

| Path | Grants source | Soft-delete checks (current code) |
|------|---------------|-----------------------------------|
| Components/Relations `findAccessible*` | `v_resource_grants` | Notation path: `n.deleted = false`. Diagram path: `d.deleted = false` AND `m.deleted = false`. diagramEditor: `m.deleted = false`. |
| RelationRules `*ForUser` | `resource_shares` | Notation IN-subquery: **no** `n.deleted`. Diagram IN-subquery: **no** `d.deleted` / **no** `m.deleted`. diagramEditor: `m.deleted = false` only. |

Tests must assert **current** behavior for each path. Do not add missing `deleted` filters as part of this refactor.

## Test strategy (mandatory, test-first)

Current coverage is insufficient. Add golden tests on the **current** SQL, then refactor, then re-run.

### Shared fixture helper

Extend `RepositoryTestBase` with `persistShare(...)` (pattern from `DiagramsRepositoryTest`) so ACL cases stay readable.

### Components + Relations (`findAccessibleByFiltersForUser`)

Symmetric cases for both repositories:

1. Owner sees own rows.
2. Stranger without share sees empty.
3. VIEW share on notation → visible.
4. VIEW share on model + diagram using that notation → visible.
5. Soft-deleted notation → not visible via notation path; soft-deleted model (or soft-deleted diagram) → not visible via diagram path.
6. `diagramEditorModelId` + matching `notationId` + EDIT on model → visible; VIEW-only share or no share → empty; missing `notationId`/`diagramEditorModelId` → editor branch inactive.
7. Keep existing admin `findByFilters` tags/name coverage.

Assert concrete id sets (or ordered names), not only `totalElements > 0`.

Optional (nice-to-have, not blocking): public share (`grantee_user_id IS NULL`) if easy to fixture.

### RelationRules

1. Admin id parity: `findByFilters` / `findProjectedByFilters` / `findProjectedLightByFilters` → same id set for the same filters (light omits `attrs`; compare ids only).
2. User id parity: `findProjectedByFiltersForUser` and `findProjectedLightByFiltersForUser` → same id set for the same ACL scenario.
3. User ACL scenarios: owner / notation VIEW share / model VIEW share + diagram / deny stranger / diagramEditor EDIT vs VIEW-only.
4. Soft-delete: assert **current** RelationRules semantics (see Semantic inventory). Do **not** require the same exclusions as Components/Relations unless today’s SQL already does that. At minimum cover: diagramEditor + soft-deleted model → empty; document observed behavior for soft-deleted notation on the `resource_shares` IN-path if it still returns rows.
5. No-access stranger → empty.

### Verification commands

```bash
./gradlew test --tests '*ComponentsRepositoryTest' --tests '*RelationsRepositoryTest' --tests '*RelationRulesRepositoryTest'
# plus related controller suites if ACL list endpoints are touched indirectly
./gradlew test --tests '*AccessListInvariantsTest' --tests '*ComponentsControllerTest' --tests '*RelationsControllerTest' --tests '*RelationRulesControllerTest'
```

Full `./gradlew test` before considering the change done.

## Implementation order

1. Add repository ACL/parity tests against current queries (must pass).
2. Introduce `repository/sql/*` fragment objects (copy existing SQL text).
3. Wire fragments into the three repositories (value + countQuery).
4. Re-run the suites above; fix only structural issues (imports/constants), not ACL semantics.
5. Stop. No follow-on refactors in the same change.

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Accidental ACL change while “cleaning” SQL | Copy-paste semantics only; do not unify `v_resource_grants` vs `resource_shares` or add missing `deleted` filters |
| Tests encode desired ACL instead of current ACL | Write tests against current SQL first; RelationRules soft-delete expectations follow Semantic inventory |
| Annotation non-constant string | Whole-query / fragment `const val` only; verify compile |
| Weak tests masking regressions | Assert id sets; cover deny + diagramEditor VIEW-vs-EDIT; soft-delete per path |
| Over-scoping into controller DRY / exists*Reachable | Explicit non-goals; separate PR later |
| `persistShare` needs `ResourceSharesRepository` | Add to `RepositoryTestBase` (or test-local wiring as in `DiagramsRepositoryTest`) before ACL cases |

## Success criteria

- Duplicated WHERE/ACL blocks for the listed methods live in shared `const val` fragments; repositories stay thin wrappers.
- Repository method signatures and HTTP behavior unchanged; SELECT/`deleted`/grants quirks preserved.
- New ACL/parity tests pass before and after the SQL move.
- Targeted suites + full `./gradlew test` green.
