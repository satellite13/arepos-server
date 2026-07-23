# Notation-bound SQL DRY Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deduplicate list/ACL native SQL in Components, Relations, and RelationRules repositories via Kotlin `const val` fragments without changing query semantics.

**Architecture:** Characterization tests lock current ACL behavior first. Then extract shared SQL into `repository/sql/*` and wire existing `@Query` methods to those constants. Components/Relations keep `v_resource_grants`; RelationRules keeps `resource_shares`. No entity/API merges.

**Tech Stack:** Kotlin, Spring Data JPA native `@Query`, Testcontainers PostgreSQL (`RepositoryTestBase`), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-07-23-notation-bound-sql-dry-design.md`

---

## File map

| File | Role |
|------|------|
| `src/test/.../RepositoryTestBase.kt` | Add `resourceSharesRepository` + `persistShare` |
| `src/test/.../ComponentsRepositoryTest.kt` | ACL characterization for components |
| `src/test/.../RelationsRepositoryTest.kt` | ACL characterization for relations |
| `src/test/.../RelationRulesRepositoryTest.kt` | Admin/user parity + ACL characterization |
| `src/main/.../repository/sql/NotationBoundListSql.kt` | Filter predicates for alias `c` / `r` |
| `src/main/.../repository/sql/NotationVisibilitySql.kt` | ACL for Components/Relations (`v_resource_grants`) |
| `src/main/.../repository/sql/RelationRulesFilterSql.kt` | JOIN + filter WHERE |
| `src/main/.../repository/sql/RelationRulesVisibilitySql.kt` | ForUser ACL (`resource_shares`) |
| `src/main/.../repository/ComponentsRepository.kt` | Wire fragments |
| `src/main/.../repository/RelationsRepository.kt` | Wire fragments |
| `src/main/.../repository/RelationRulesRepository.kt` | Wire fragments |

---

### Task 1: Test fixtures — `persistShare`

**Files:**
- Modify: `src/test/kotlin/ru/kavader/arepos/repository/RepositoryTestBase.kt`

- [ ] **Step 1: Add repository + helper**

Add:

```kotlin
@Autowired
protected lateinit var resourceSharesRepository: ResourceSharesRepository

protected fun persistShare(
    resourceType: ShareResourceType,
    resourceId: UUID,
    grantedBy: Users,
    grantee: Users? = null,
    permission: SharePermission = SharePermission.VIEW
): ResourceShares = resourceSharesRepository.save(
    ResourceShares(
        resourceType = resourceType,
        resourceId = resourceId,
        granteeUser = grantee,
        grantedByUser = grantedBy,
        permission = permission,
        createdAt = Instant.now()
    )
)
```

Ensure imports for `ResourceShares`, `SharePermission`, `ShareResourceType`, `UUID` (UUID already used).

- [ ] **Step 2: Compile tests**

Run: `./gradlew testClasses`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit** (only if user asked / as part of approved plan execution)

```bash
git add src/test/kotlin/ru/kavader/arepos/repository/RepositoryTestBase.kt
git commit -m "$(cat <<'EOF'
test: add persistShare helper for repository ACL fixtures

EOF
)"
```

---

### Task 2: Components ACL characterization tests

**Files:**
- Modify: `src/test/kotlin/ru/kavader/arepos/repository/ComponentsRepositoryTest.kt`

- [ ] **Step 1: Add tests for `findAccessibleByFiltersForUser`**

Cover (assert id sets):

1. Owner sees own component
2. Stranger without share → empty
3. Notation VIEW share → visible
4. Model VIEW share + diagram with notation → visible
5. Soft-deleted notation (with prior notation share) → empty
6. Soft-deleted diagram on model-share path → empty
7. diagramEditorModelId + notationId + model EDIT share → visible; VIEW-only → empty; null editor id → empty for stranger

Use `Pageable.unpaged()`. Keep existing tags `findByFilters` test.

Helper pattern:

```kotlin
private fun accessible(
    notationId: UUID? = null,
    ownerId: UUID? = null,
    name: String? = null,
    tagsJson: String? = null,
    currentUserId: UUID,
    diagramEditorModelId: UUID? = null
) = componentsRepository.findAccessibleByFiltersForUser(
    notationId, ownerId, name, tagsJson, currentUserId, diagramEditorModelId, Pageable.unpaged()
)
```

- [ ] **Step 2: Run tests — must PASS on current SQL**

Run: `./gradlew test --tests '*ComponentsRepositoryTest'`
Expected: all PASS (characterization, not red for missing API)

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
test: characterize Components accessible list ACL

EOF
)"
```

---

### Task 3: Relations ACL characterization tests

**Files:**
- Modify: `src/test/kotlin/ru/kavader/arepos/repository/RelationsRepositoryTest.kt`

- [ ] **Step 1: Mirror Task 2 for relations** (`persistRelation`, `findAccessibleByFiltersForUser`)

Same seven scenarios; keep existing tags test.

- [ ] **Step 2: Run**

Run: `./gradlew test --tests '*RelationsRepositoryTest'`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
test: characterize Relations accessible list ACL

EOF
)"
```

---

### Task 4: RelationRules parity + ACL characterization tests

**Files:**
- Modify: `src/test/kotlin/ru/kavader/arepos/repository/RelationRulesRepositoryTest.kt`

- [ ] **Step 1: Admin id parity**

Same filters → same ids from `findByFilters`, `findProjectedByFilters`, `findProjectedLightByFilters`.

- [ ] **Step 2: User ACL + light/full id parity**

For `findProjectedByFiltersForUser` / `findProjectedLightByFiltersForUser`:

- owner sees rule
- stranger empty
- notation VIEW share → visible
- model VIEW + diagram → visible
- diagramEditor EDIT → visible; VIEW-only → empty
- diagramEditor + soft-deleted model → empty
- Soft-deleted notation on share path: assert **observed current** behavior (SQL has no `n.deleted` on IN-subquery — document in test name if rows still returned)

- [ ] **Step 3: Run**

Run: `./gradlew test --tests '*RelationRulesRepositoryTest'`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
test: characterize RelationRules list/projection ACL parity

EOF
)"
```

---

### Task 5: Extract Components/Relations SQL fragments

**Files:**
- Create: `src/main/kotlin/ru/kavader/arepos/repository/sql/NotationBoundListSql.kt`
- Create: `src/main/kotlin/ru/kavader/arepos/repository/sql/NotationVisibilitySql.kt`
- Modify: `ComponentsRepository.kt`, `RelationsRepository.kt`

- [ ] **Step 1: Create fragment objects**

Copy exact filter/ACL text from current repositories into `const val`s:

```kotlin
object NotationBoundListSql {
    const val FILTERS_C = """
              (:notationId IS NULL OR c.notation = :notationId)
              AND (:ownerId IS NULL OR c.owner = :ownerId)
              AND (:name IS NULL OR c.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(c.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
    """.trimIndent()

    const val FILTERS_R = """
              (:notationId IS NULL OR r.notation = :notationId)
              AND (:ownerId IS NULL OR r.owner = :ownerId)
              AND (:name IS NULL OR r.name ILIKE CONCAT('%', :name, '%'))
              AND (:tagsJson IS NULL OR COALESCE(r.attrs -> 'tags', '[]'::jsonb) @> CAST(:tagsJson AS jsonb))
    """.trimIndent()
}
```

`NotationVisibilitySql`: `ACCESSIBLE_C` / `ACCESSIBLE_R` = exact ACL OR-block from current `findAccessible*` (including diagramEditor branch). Prefer whole-query consts if annotation interpolation is awkward:

```kotlin
object NotationVisibilitySql {
    const val ACCESSIBLE_C = """ ... exact ACL using alias c ... """
    const val ACCESSIBLE_R = """ ... exact ACL using alias r ... """
}
```

Or whole queries `FIND_BY_FILTERS_C`, `FIND_ACCESSIBLE_C`, etc. Preserve `SELECT *` vs `SELECT c.*` quirks.

- [ ] **Step 2: Wire repositories** — signatures unchanged

- [ ] **Step 3: Re-run characterization tests**

Run: `./gradlew test --tests '*ComponentsRepositoryTest' --tests '*RelationsRepositoryTest'`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor: extract Components/Relations list ACL SQL fragments

EOF
)"
```

---

### Task 6: Extract RelationRules SQL fragments

**Files:**
- Create: `RelationRulesFilterSql.kt`, `RelationRulesVisibilitySql.kt`
- Modify: `RelationRulesRepository.kt`

- [ ] **Step 1: Extract JOIN/FROM + filter WHERE + ForUser ACL** into consts; keep distinct SELECT lists for entity / projection / light

- [ ] **Step 2: Wire all five query methods (value + countQuery)**

- [ ] **Step 3: Run**

Run: `./gradlew test --tests '*RelationRulesRepositoryTest'`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor: extract RelationRules list/projection SQL fragments

EOF
)"
```

---

### Task 7: Regression suites

- [ ] **Step 1: Targeted related suites**

```bash
./gradlew test --tests '*ComponentsRepositoryTest' --tests '*RelationsRepositoryTest' --tests '*RelationRulesRepositoryTest' --tests '*AccessListInvariantsTest' --tests '*ComponentsControllerTest' --tests '*RelationsControllerTest' --tests '*RelationRulesControllerTest'
```

Expected: PASS

- [ ] **Step 2: Full test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Stop** — no follow-on refactors in this branch

---

## Spec coverage checklist

| Spec item | Task |
|-----------|------|
| persistShare / ResourceSharesRepository | Task 1 |
| Components ACL cases 1–7 | Task 2 |
| Relations ACL cases 1–7 | Task 3 |
| RelationRules admin/user parity + soft-delete current semantics | Task 4 |
| NotationBoundListSql + NotationVisibilitySql | Task 5 |
| RelationRulesFilterSql + RelationRulesVisibilitySql | Task 6 |
| No exists*Reachable / no ACL unification | Tasks 5–6 non-goals |
| Full verification | Task 7 |
