# wArchi Site Community API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend arepos-server with safe, auditable roadmap ordering and feedback moderation APIs for warchi-site.

**Architecture:** Keep all authorization in `ResourceAccessService` and Cerbos; use the existing site controllers and services as API boundaries. Persist feedback merges rather than deleting the source item, and use each entity’s `updatedAt` as the optimistic-concurrency token for roadmap changes.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, PostgreSQL/Liquibase, Cerbos, JUnit 5, MockMvc/Testcontainers.

---

### Task 1: Define API contracts and migration for feedback merges

**Files:**
- Create: `src/main/resources/db/changelog/042-site-feedback-moderation.sql`
- Modify: `src/main/resources/db/changelog/db.changelog-master.yaml`
- Modify: `src/main/kotlin/ru/kavader/arepos/model/FeedbackItem.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/dto/site/SiteDtos.kt`

- [ ] **Step 1: Write DTO-shape tests in `FeedbackControllerTest.kt`**

```kotlin
mockMvc.perform(post("/api/v1/feedback/{id}/merge", sourceId)
    .withAuth(admin.id!!, Role.ADMIN)
    .with(csrf())
    .contentType(MediaType.APPLICATION_JSON)
    .content("""{"targetId":"$targetId"}"""))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.sourceId").value(sourceId.toString()))
    .andExpect(jsonPath("$.targetId").value(targetId.toString()))

mockMvc.perform(get("/api/v1/feedback/{id}", sourceId))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.mergedIntoId").value(targetId.toString()))
```

- [ ] **Step 2: Run the focused controller test and verify it fails**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.FeedbackControllerTest"`

Expected: compilation failure because the merge route and DTO fields do not exist.

- [ ] **Step 3: Add the schema migration**

```sql
ALTER TABLE feedback_items
  ADD COLUMN merged_into_id UUID REFERENCES feedback_items(id),
  ADD COLUMN merged_at TIMESTAMPTZ,
  ADD CONSTRAINT feedback_items_not_merged_into_self CHECK (id <> merged_into_id);

CREATE INDEX idx_feedback_items_merged_into_id
  ON feedback_items (merged_into_id)
  WHERE merged_into_id IS NOT NULL;
```

Add `042-site-feedback-moderation.sql` as the next Liquibase `sqlFile` change set in
`db.changelog-master.yaml`, with `splitStatements: false`.

- [ ] **Step 4: Add model and DTO fields**

```kotlin
// FeedbackItem.kt
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "merged_into_id")
var mergedInto: FeedbackItem? = null,

@Column(name = "merged_at")
var mergedAt: Instant? = null,

// SiteDtos.kt
data class MergeFeedbackRequest(val targetId: UUID)
data class MergeFeedbackResponse(
    val sourceId: UUID,
    val targetId: UUID,
    val mergedAt: Instant,
    val target: FeedbackItemResponse
)
```

Extend `FeedbackItemResponse` with nullable `mergedIntoId` and `mergedAt`; extend
`UpdateFeedbackRequest` with nullable `type` and `baseUpdatedAt`.

- [ ] **Step 5: Re-run the focused test**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.FeedbackControllerTest"`

Expected: the test still fails at request routing, but DTO compilation succeeds.

- [ ] **Step 6: Commit the contract and migration**

```bash
git add src/main/resources/db/changelog src/main/kotlin/ru/kavader/arepos/model/FeedbackItem.kt src/main/kotlin/ru/kavader/arepos/dto/site/SiteDtos.kt src/test/kotlin/ru/kavader/arepos/controller/FeedbackControllerTest.kt
git commit -m "feat: add feedback moderation schema contracts"
```

### Task 2: Implement feedback delete, merge, and moderation history

**Files:**
- Modify: `src/main/kotlin/ru/kavader/arepos/service/FeedbackService.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/controller/FeedbackController.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/repository/FeedbackItemRepository.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/repository/FeedbackVoteRepository.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/repository/FeedbackCommentRepository.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/security/ResourceAccessService.kt`
- Test: `src/test/kotlin/ru/kavader/arepos/controller/FeedbackControllerTest.kt`

- [ ] **Step 1: Add failing authorization and merge tests**

```kotlin
mockMvc.perform(delete("/api/v1/feedback/{id}", planned.id!!)
    .withAuth(author.id!!, Role.USER).with(csrf()))
    .andExpect(status().isForbidden)

mockMvc.perform(delete("/api/v1/feedback/{id}", planned.id!!)
    .withAuth(admin.id!!, Role.ADMIN).with(csrf()))
    .andExpect(status().isNoContent)

mockMvc.perform(post("/api/v1/feedback/{id}/merge", source.id!!)
    .withAuth(admin.id!!, Role.ADMIN).with(csrf())
    .contentType(MediaType.APPLICATION_JSON)
    .content("""{"targetId":"${target.id}"}"""))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.target.voteCount").value(2))
```

- [ ] **Step 2: Run and verify the test fails**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.FeedbackControllerTest"`

Expected: `404` for DELETE and `/merge`.

- [ ] **Step 3: Implement access checks and service operations**

Add `requireCanDeleteFeedback(item)` that permits `canManageFeedback()` or the existing
Cerbos `DELETE` decision using the item author and status. Add `delete(id)` that loads
the item, invokes the check, and deletes it.

Implement `merge(sourceId, request)` transactionally:

```kotlin
requireCanManageFeedback()
require(sourceId != request.targetId) { "Feedback item cannot be merged into itself" }
val source = getEntity(sourceId)
val target = getEntity(request.targetId)
require(source.mergedInto == null) { "Feedback item is already merged" }
moveVotesSkippingExistingUsers(source, target)
moveComments(source, target)
moveRoadmapLinksSkippingDuplicates(source, target)
source.mergedInto = target
source.mergedAt = Instant.now()
source.status = "declined"
recalculateVoteCount(target)
```

Return the source/target IDs, merge timestamp, and mapped target response. Keep the
source row so public clients can redirect to its target.

- [ ] **Step 4: Expose controller endpoints**

```kotlin
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
fun delete(@PathVariable id: UUID) = feedbackService.delete(id)

@PostMapping("/{id}/merge")
fun merge(
    @PathVariable id: UUID,
    @RequestBody request: MergeFeedbackRequest
): MergeFeedbackResponse = feedbackService.merge(id, request)
```

On GET, map `mergedInto?.id` and `mergedAt` into `FeedbackItemResponse`. Add an
admin-only `include=audit` parameter that reads the existing audit repository and
returns audit entries only when requested by an administrator.

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.FeedbackControllerTest"`

Expected: PASS, including self-merge and already-merged `400` cases.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ru/kavader/arepos/{service,controller,repository,security} src/test/kotlin/ru/kavader/arepos/controller/FeedbackControllerTest.kt
git commit -m "feat: add feedback moderation operations"
```

### Task 3: Add paginated feedback text search

**Files:**
- Modify: `src/main/kotlin/ru/kavader/arepos/repository/FeedbackItemRepository.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/service/FeedbackService.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/controller/FeedbackController.kt`
- Test: `src/test/kotlin/ru/kavader/arepos/controller/FeedbackControllerTest.kt`

- [ ] **Step 1: Write a failing search test**

```kotlin
mockMvc.perform(get("/api/v1/feedback")
    .param("q", "diagram")
    .param("page", "0")
    .param("size", "20"))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$.items[0].title").value("Diagram templates"))
    .andExpect(jsonPath("$.total").value(1))
```

- [ ] **Step 2: Run and verify failure**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.FeedbackControllerTest"`

Expected: the test fails because `q` is ignored and the full unfiltered result is returned.

- [ ] **Step 3: Add a repository query and thread it through the list operation**

```kotlin
@Query("""
    select f from FeedbackItem f
    where (:q is null or lower(f.title) like lower(concat('%', :q, '%'))
        or lower(f.body) like lower(concat('%', :q, '%')))
      and (:type is null or f.type = :type)
      and (:status is null or f.status = :status)
""")
fun findByFilters(
    @Param("q") q: String?,
    @Param("type") type: String?,
    @Param("status") status: String?,
    pageable: Pageable
): Page<FeedbackItem>
```

Normalize blank `q` to null in `FeedbackService.list()`. Preserve existing `votes` and
`recent` sort handling and return existing `items`, `total`, `page` and `size` fields.

- [ ] **Step 4: Expose the query parameter**

```kotlin
@GetMapping
fun list(
    @RequestParam(required = false) q: String?,
    @RequestParam(required = false) type: String?,
    @RequestParam(required = false) status: String?,
    @RequestParam(defaultValue = "recent") sort: String,
    pageable: Pageable
) = feedbackService.list(q, type, status, sort, pageable)
```

- [ ] **Step 5: Run focused tests**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.FeedbackControllerTest"`

Expected: PASS for unfiltered lists, text search, type/status filters and pagination.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ru/kavader/arepos/{repository,service,controller}/Feedback* src/test/kotlin/ru/kavader/arepos/controller/FeedbackControllerTest.kt
git commit -m "feat: add feedback text search"
```

### Task 4: Add optimistic roadmap update and atomic reordering

**Files:**
- Modify: `src/main/kotlin/ru/kavader/arepos/dto/site/SiteDtos.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/service/RoadmapService.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/controller/RoadmapController.kt`
- Modify: `src/main/kotlin/ru/kavader/arepos/controller/GlobalExceptionHandler.kt`
- Test: `src/test/kotlin/ru/kavader/arepos/controller/RoadmapControllerTest.kt`

- [ ] **Step 1: Add failing reorder and stale-update tests**

```kotlin
mockMvc.perform(put("/api/v1/roadmap/milestones/order")
    .withAuth(admin.id!!, Role.ADMIN).with(csrf())
    .contentType(MediaType.APPLICATION_JSON)
    .content("""{"items":[{"id":"${second.id}","sortOrder":0},{"id":"${first.id}","sortOrder":1}]}"""))
    .andExpect(status().isOk)
    .andExpect(jsonPath("$[0].id").value(second.id.toString()))

mockMvc.perform(put("/api/v1/roadmap/milestones/{id}", first.id!!)
    .withAuth(admin.id!!, Role.ADMIN).with(csrf())
    .contentType(MediaType.APPLICATION_JSON)
    .content("""{"title":"Stale","baseUpdatedAt":"2000-01-01T00:00:00Z"}"""))
    .andExpect(status().isConflict)
    .andExpect(jsonPath("$.code").value("ROADMAP_UPDATE_CONFLICT"))
```

- [ ] **Step 2: Run and verify failure**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.RoadmapControllerTest"`

Expected: `404` for `/order` and DTO deserialization ignores `baseUpdatedAt`.

- [ ] **Step 3: Add request/response DTOs**

```kotlin
data class RoadmapMilestoneOrderItem(val id: UUID, val sortOrder: Int)
data class ReorderRoadmapMilestonesRequest(
    val items: List<RoadmapMilestoneOrderItem>,
    val baseUpdatedAt: Map<UUID, Instant> = emptyMap()
)
```

Add nullable `baseUpdatedAt` to `UpdateRoadmapMilestoneRequest`.

- [ ] **Step 4: Implement transactional service behavior**

In `update`, compare `request.baseUpdatedAt` to the entity `updatedAt`; throw a
domain conflict if they differ. Add `reorder(request)` that requires roadmap management,
rejects duplicate IDs or sort orders, loads every ID, compares all supplied timestamps,
updates `sortOrder` and `updatedAt` in one transaction, then returns
`findAllByOrderBySortOrderAsc()` mapped to responses.

- [ ] **Step 5: Map conflicts and expose the route**

```kotlin
@PutMapping("/milestones/order")
fun reorder(
    @RequestBody request: ReorderRoadmapMilestonesRequest
): List<RoadmapMilestoneResponse> = roadmapService.reorder(request)
```

Map stale update and reorder exceptions to HTTP 409 with `code` values
`ROADMAP_UPDATE_CONFLICT` and `ROADMAP_ORDER_CONFLICT`, respectively. The response must
include the conflicting IDs and server `updatedAt` values.

- [ ] **Step 6: Run focused tests**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.RoadmapControllerTest"`

Expected: PASS for create, update, delete, links, reorder, non-admin denial, and 409.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/ru/kavader/arepos/{dto/site,service,controller} src/test/kotlin/ru/kavader/arepos/controller/RoadmapControllerTest.kt
git commit -m "feat: support ordered roadmap updates"
```

### Task 5: Complete policies, audit coverage, and server verification

**Files:**
- Modify: `authz/cerbos/policies/resource.feedback_item.yaml`
- Modify: `charts/arepos-server/cerbos/policies/resource.feedback_item.yaml`
- Modify: `authz/cerbos/COVERAGE.md`
- Modify: `src/test/kotlin/ru/kavader/arepos/controller/FeedbackControllerTest.kt`
- Modify: `src/test/kotlin/ru/kavader/arepos/controller/RoadmapControllerTest.kt`

- [ ] **Step 1: Add policy coverage tests**

Add tests that a USER can delete only their `new` feedback, that an ADMIN can delete or
merge all feedback, and that an authenticated non-admin receives 403 on roadmap reorder.

- [ ] **Step 2: Run tests to verify policy gaps**

Run: `./gradlew test --tests "ru.kavader.arepos.controller.FeedbackControllerTest" --tests "ru.kavader.arepos.controller.RoadmapControllerTest"`

Expected: any policy mismatch fails before the policy copies are synchronized.

- [ ] **Step 3: Synchronize Cerbos policies**

Keep the two policy files byte-for-byte equivalent. Declare `manage` explicitly for
`ADMIN`, retain public `view`, authenticated `create`, `vote`, `comment`, and constrain
author `edit`/`delete` to `request.resource.attr.status == "new"`.

Document `feedback_item` and `roadmap_milestone` in `COVERAGE.md`, including
`view/create/vote/comment/edit/delete/manage`.

- [ ] **Step 4: Run focused and full verification**

Run:

```bash
./gradlew test --tests "ru.kavader.arepos.controller.FeedbackControllerTest" --tests "ru.kavader.arepos.controller.RoadmapControllerTest"
./gradlew test
```

Expected: both focused suites and the full Testcontainers suite pass.

- [ ] **Step 5: Commit**

```bash
git add authz/cerbos charts/arepos-server/cerbos src/test/kotlin/ru/kavader/arepos/controller
git commit -m "test: cover site moderation permissions"
```
