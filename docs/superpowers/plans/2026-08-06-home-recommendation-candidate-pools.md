# Home Recommendation Candidate Pools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an MVP homepage recommendation pipeline that uses multiple candidate pools, Redis-backed caching, hot-pool fallback, and strong extension points for later growth optimization.

**Architecture:** Keep the existing `/api/videos/recommended` public API, but move recommendation composition out of `VideoService` into focused recommendation services. Candidate providers return video ID lists, a mixer deduplicates and fills with hot candidates, then video details are loaded in batch and mapped to `PublicVideoResponse`.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, PostgreSQL, Redis through Spring Data Redis, JUnit 5, Mockito.

## Global Constraints

- Return only `VideoStatus.APPROVED` videos.
- MVP fallback source is the hot pool; do not create a separate fallback repository path.
- Cache candidate ID lists and user profiles, not full video response objects.
- Do not use database `ORDER BY random()` for exploration.
- Preserve existing `/api/videos/recommended?page={page}&size={size}&categoryId={categoryId}&tagId={tagId}` compatibility.
- Respect current maximum page size limit of 50 in `VideoController`.
- Ignore unrelated changes in `video-sharing-staff-frontend`.

---

## File Structure

- Create `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/RecommendationService.java` for the main homepage recommendation orchestration.
- Create `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/CandidatePoolType.java` for pool source names.
- Create `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/CandidatePoolResult.java` for provider outputs.
- Create `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/RecommendationRequest.java` for normalized request inputs.
- Create `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/RecommendationMixer.java` for allocation, dedupe, and hot fallback.
- Create `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/RecommendationCache.java` for Redis-backed cache operations with no-op fallback.
- Create `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/InterestProfileService.java` for category/tag profile derivation.
- Create `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/HotCandidateProvider.java`, `FreshCandidateProvider.java`, `ExploreCandidateProvider.java`, `CategoryInterestCandidateProvider.java`, `TagInterestCandidateProvider.java`, and `ContinueWatchingCandidateProvider.java`.
- Modify `VideoController.java` to pass the authenticated user when present without requiring auth for homepage recommendations.
- Modify `VideoService.java` to delegate recommendation calls to `RecommendationService`.
- Modify `VideoRepository.java` and `WatchHistoryRepository.java` to add the focused queries needed by candidate providers.
- Modify `schema.sql` to add recommendation-oriented indexes.
- Add tests under `video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/recommendation/`.

## Task 1: Introduce Recommendation Request and Mixer

**Files:**
- Create: `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/CandidatePoolType.java`
- Create: `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/CandidatePoolResult.java`
- Create: `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/RecommendationRequest.java`
- Create: `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/RecommendationMixer.java`
- Test: `video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/recommendation/RecommendationMixerTest.java`

**Interfaces:**
- Produces: `RecommendationMixer.mix(RecommendationRequest request, List<CandidatePoolResult> pools, List<Long> hotFallbackIds): List<Long>`
- Produces: `CandidatePoolResult(CandidatePoolType type, List<Long> videoIds)`

- [ ] **Step 1: Add failing mixer tests**

Cover these cases in `RecommendationMixerTest`:

```java
@Test
void mixesLoggedInRichHistoryWithDedupeAndHotFallback() {
    RecommendationRequest request = RecommendationRequest.home(10L, 0, 12, null, null, true, true);
    List<CandidatePoolResult> pools = List.of(
            new CandidatePoolResult(CandidatePoolType.CATEGORY_INTEREST, List.of(1L, 2L, 3L)),
            new CandidatePoolResult(CandidatePoolType.TAG_INTEREST, List.of(3L, 4L, 5L)),
            new CandidatePoolResult(CandidatePoolType.HOT, List.of(5L, 6L, 7L)),
            new CandidatePoolResult(CandidatePoolType.FRESH, List.of(8L, 9L)),
            new CandidatePoolResult(CandidatePoolType.CONTINUE_WATCHING, List.of(10L)),
            new CandidatePoolResult(CandidatePoolType.EXPLORE, List.of(11L))
    );

    List<Long> mixed = new RecommendationMixer().mix(request, pools, List.of(12L, 13L, 14L));

    assertThat(mixed).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 8L, 9L, 10L, 11L, 12L, 13L);
}

@Test
void mixesAnonymousTrafficWithoutInterestPools() {
    RecommendationRequest request = RecommendationRequest.home(null, 0, 12, null, null, false, false);
    List<CandidatePoolResult> pools = List.of(
            new CandidatePoolResult(CandidatePoolType.HOT, List.of(1L, 2L, 3L, 4L, 5L, 6L)),
            new CandidatePoolResult(CandidatePoolType.FRESH, List.of(7L, 8L, 9L)),
            new CandidatePoolResult(CandidatePoolType.EXPLORE, List.of(10L))
    );

    List<Long> mixed = new RecommendationMixer().mix(request, pools, List.of(11L, 12L, 13L));

    assertThat(mixed).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```powershell
cd video-sharing-backend
./mvnw -Dtest=RecommendationMixerTest test
```

Expected: compilation fails because the recommendation classes do not exist.

- [ ] **Step 3: Implement the records, enum, and mixer**

Implement pool allocation:

```text
anonymous: HOT 6, FRESH 3, EXPLORE 1, hot fallback fills the rest
logged in without rich history: HOT 5, FRESH 3, CATEGORY_INTEREST + TAG_INTEREST 2 total, EXPLORE 1, hot fallback fills the rest
logged in with rich history: CATEGORY_INTEREST 3, TAG_INTEREST 3, HOT 3, FRESH 2, CONTINUE_WATCHING 1, EXPLORE 1, hot fallback fills the rest
```

The mixer must dedupe by `videoId`, preserve pool order within each allocation, and stop at `request.size()`.

- [ ] **Step 4: Run the mixer tests**

Run:

```powershell
cd video-sharing-backend
./mvnw -Dtest=RecommendationMixerTest test
```

Expected: PASS.

## Task 2: Add Repository Queries and Indexes

**Files:**
- Modify: `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/repository/VideoRepository.java`
- Modify: `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/repository/WatchHistoryRepository.java`
- Modify: `video-sharing-backend/src/main/resources/schema.sql`
- Test: `video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/config/SchemaSqlTest.java`

**Interfaces:**
- Produces: video ID page queries for hot, fresh, category, tag, and exploration providers.
- Produces: watch-history queries for recent history and continue watching.

- [ ] **Step 1: Add repository method tests where existing repository test style allows**

Add focused assertions to existing repository/config tests that `schema.sql` contains these indexes:

```sql
idx_videos_status_views_upload_id
idx_videos_status_upload_id_desc
idx_videos_status_category_upload_id
idx_watch_history_user_completed_last
```

- [ ] **Step 2: Add repository methods**

Add query methods returning `List<Long>`:

```java
List<Long> findHotCandidateIds(VideoStatus status, Pageable pageable);
List<Long> findHotCandidateIdsByCategory(VideoStatus status, Long categoryId, Pageable pageable);
List<Long> findFreshCandidateIds(VideoStatus status, Instant uploadedAfter, Pageable pageable);
List<Long> findFreshCandidateIdsByCategory(VideoStatus status, Long categoryId, Instant uploadedAfter, Pageable pageable);
List<Long> findExploreCandidateIds(VideoStatus status, Instant uploadedAfter, long maxViews, Pageable pageable);
List<Long> findCategoryCandidateIds(VideoStatus status, Long categoryId, Pageable pageable);
List<Long> findTagCandidateIds(VideoStatus status, Long tagId, Pageable pageable);
List<Video> findAllByIdIn(Collection<Long> ids);
```

Use `@Query` for deterministic order. Exploration should order by `uploadDate desc, id desc` inside a bucket-friendly window, not random.

- [ ] **Step 3: Add watch-history queries**

Add:

```java
List<WatchHistory> findTop20ByIdUserIdOrderByLastWatchedAtDesc(Long userId);

@Query("""
        select h from WatchHistory h
        join fetch h.video v
        where h.id.userId = :userId
          and h.completed = false
          and h.progressPercent >= 5.00
          and h.progressPercent < 95.00
          and v.status = :status
        order by h.lastWatchedAt desc
        """)
List<WatchHistory> findContinueWatchingCandidates(Long userId, VideoStatus status, Pageable pageable);
```

- [ ] **Step 4: Add indexes to `schema.sql`**

Append:

```sql
CREATE INDEX idx_videos_status_views_upload_id
    ON videos (status, views DESC, upload_date DESC, id DESC);

CREATE INDEX idx_videos_status_upload_id_desc
    ON videos (status, upload_date DESC, id DESC);

CREATE INDEX idx_videos_status_category_upload_id
    ON videos (status, category_id, upload_date DESC, id DESC);

CREATE INDEX idx_watch_history_user_completed_last
    ON user_video_watch_history (user_id, completed, last_watched_at DESC, video_id);
```

- [ ] **Step 5: Run schema and repository tests**

Run:

```powershell
cd video-sharing-backend
./mvnw -Dtest=SchemaSqlTest,VideoServiceSearchTest,WatchHistoryServiceTest test
```

Expected: PASS.

## Task 3: Add Redis Cache Abstraction

**Files:**
- Create: `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/recommendation/RecommendationCache.java`
- Modify: `video-sharing-backend/src/main/resources/application.properties`
- Test: `video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/recommendation/RecommendationCacheTest.java`

**Interfaces:**
- Produces: `Optional<List<Long>> getIdList(String key)`
- Produces: `void putIdList(String key, List<Long> ids, Duration ttl)`
- Produces: `Optional<String> getString(String key)`
- Produces: `void putString(String key, String value, Duration ttl)`

- [ ] **Step 1: Write cache key and TTL tests**

Verify that empty lists are cached, TTL jitter remains within configured bounds, and cache can be disabled in tests without failing recommendation execution.

- [ ] **Step 2: Implement cache abstraction**

Use `StringRedisTemplate` if present. If Redis is unavailable or disabled, return `Optional.empty()` and let providers query the database. Store ID lists as comma-separated ASCII strings such as `1,2,3`.

- [ ] **Step 3: Add properties**

Add defaults:

```properties
recommendation.cache.enabled=true
recommendation.cache.hot-ttl-seconds=60
recommendation.cache.fresh-ttl-seconds=300
recommendation.cache.explore-ttl-seconds=1800
recommendation.cache.profile-ttl-seconds=600
recommendation.cache.continue-ttl-seconds=60
recommendation.cache.ttl-jitter-seconds=30
```

- [ ] **Step 4: Run cache tests**

Run:

```powershell
cd video-sharing-backend
./mvnw -Dtest=RecommendationCacheTest test
```

Expected: PASS.

## Task 4: Implement Hot, Fresh, and Explore Providers

**Files:**
- Create: `HotCandidateProvider.java`
- Create: `FreshCandidateProvider.java`
- Create: `ExploreCandidateProvider.java`
- Test: `CandidateProviderTest.java`

**Interfaces:**
- Produces: `CandidatePoolResult candidates(RecommendationRequest request, int limit)`

- [ ] **Step 1: Write provider tests**

Test that:

```text
Hot provider uses global or category hot keys.
Fresh provider uses 7 day window keys.
Explore provider chooses bucket by userId/date or anonymous session/date.
Providers cache empty results.
Providers fall back to repository when cache misses.
```

- [ ] **Step 2: Implement hot provider**

Cache keys:

```text
rec:pool:hot:global:page:{page}:size:{size}
rec:pool:hot:category:{categoryId}:page:{page}:size:{size}
```

- [ ] **Step 3: Implement fresh provider**

Cache keys:

```text
rec:pool:fresh:global:window:7d:limit:{limit}
rec:pool:fresh:category:{categoryId}:window:7d:limit:{limit}
```

- [ ] **Step 4: Implement explore provider**

Cache keys:

```text
rec:pool:explore:bucket:{bucketId}:limit:{limit}
rec:pool:explore:category:{categoryId}:bucket:{bucketId}:limit:{limit}
```

Use `bucketCount = 8` and `maxViews` from property:

```properties
recommendation.explore.bucket-count=8
recommendation.explore.max-views=100
recommendation.explore.window-days=30
```

- [ ] **Step 5: Run provider tests**

Run:

```powershell
cd video-sharing-backend
./mvnw -Dtest=CandidateProviderTest test
```

Expected: PASS.

## Task 5: Implement User Interest and Continue Watching Providers

**Files:**
- Create: `InterestProfileService.java`
- Create: `CategoryInterestCandidateProvider.java`
- Create: `TagInterestCandidateProvider.java`
- Create: `ContinueWatchingCandidateProvider.java`
- Test: `InterestProfileServiceTest.java`
- Test: `PersonalizedCandidateProviderTest.java`

**Interfaces:**
- Produces: `InterestProfile categoryProfile(Long userId)`
- Produces: `InterestProfile tagProfile(Long userId)`
- Produces: personalized `CandidatePoolResult` values.

- [ ] **Step 1: Write profile tests**

Use recent watch history fixtures with categories, tags, completed flags, progress percent, and last watched timestamps. Assert:

```text
completed videos score more than partial videos
recent videos score more than older videos
category profile keeps Top 3
tag profile keeps Top 10
```

- [ ] **Step 2: Implement profile scoring**

Use:

```text
base score = 1.0
completed bonus = +2.0
progress >= 50 bonus = +1.0
watched within 7 days bonus = +1.0
```

- [ ] **Step 3: Implement category and tag providers**

Read profile cache first:

```text
rec:user:{userId}:category_profile
rec:user:{userId}:tag_profile
```

On miss, derive from `findTop20ByIdUserIdOrderByLastWatchedAtDesc`.

- [ ] **Step 4: Implement continue watching provider**

Cache key:

```text
rec:user:{userId}:continue_watching
```

Query:

```text
completed = false
5 <= progress_percent < 95
order by last_watched_at desc
limit 10
```

- [ ] **Step 5: Run personalized provider tests**

Run:

```powershell
cd video-sharing-backend
./mvnw -Dtest=InterestProfileServiceTest,PersonalizedCandidateProviderTest test
```

Expected: PASS.

## Task 6: Wire Recommendation Service into Existing API

**Files:**
- Create: `RecommendationService.java`
- Modify: `VideoService.java`
- Modify: `VideoController.java`
- Test: `RecommendationServiceTest.java`
- Test: `VideoControllerSafeResponseTest.java`

**Interfaces:**
- Produces: `Page<Video> recommend(User user, int page, int size, Long categoryId, Long tagId)`
- Consumes: all candidate providers and `RecommendationMixer`.

- [ ] **Step 1: Write service integration tests**

Assert:

```text
anonymous requests use hot/fresh/explore and hot fallback
logged-in requests include personalized pools
categoryId constrains category-aware providers
tagId preserves existing tag filter behavior
duplicate IDs are removed
details are returned in mixed order
provider failure returns hot fallback
```

- [ ] **Step 2: Implement recommendation orchestration**

Call providers with oversampling:

```text
provider limit = max(size * 3, 30)
hot fallback limit = max(size * 4, 50)
```

Catch provider exceptions individually and continue. If all providers fail, call existing hot repository query.

- [ ] **Step 3: Preserve video detail order**

After `findAllByIdIn(ids)`, reorder records in memory according to the mixed ID list.

- [ ] **Step 4: Update `VideoService`**

Keep existing methods as API-compatible wrappers:

```java
public Page<Video> getRecommendedVideos(Pageable pageable)
public Page<Video> getRecommendedVideosByCategory(Long categoryId, Pageable pageable)
public Page<Video> getRecommendedVideosByTag(Long tagId, Pageable pageable)
```

Add an overload:

```java
public Page<Video> getRecommendedVideos(User user, Pageable pageable, Long categoryId, Long tagId)
```

- [ ] **Step 5: Update `VideoController`**

For `/api/videos/recommended`, resolve user only when `Authentication` is present. Anonymous requests must keep working.

- [ ] **Step 6: Run backend recommendation tests**

Run:

```powershell
cd video-sharing-backend
./mvnw -Dtest=RecommendationServiceTest,VideoControllerSafeResponseTest test
```

Expected: PASS.

## Task 7: Add Observability and Operational Guardrails

**Files:**
- Modify: `RecommendationService.java`
- Modify: `application.properties`
- Test: `RecommendationServiceTest.java`

**Interfaces:**
- Produces: structured logs with request scene, pool sizes, fallback count, elapsed milliseconds, and degradation reason.

- [ ] **Step 1: Add tests for degradation behavior**

Mock one provider throwing an exception. Assert recommendation still returns hot fallback and logs or exposes the degradation reason through a testable internal result object.

- [ ] **Step 2: Add timing and pool-size logging**

Log one compact line per request:

```text
recommendation scene=home user=present page=0 size=12 pools=hot:30,fresh:10,explore:8,category:12,tag:9,continue:2 fallback=3 elapsedMs=42 degraded=false
```

- [ ] **Step 3: Add timeout properties**

Add:

```properties
recommendation.provider-timeout-ms=80
recommendation.request-timeout-ms=150
```

MVP may enforce these as soft budget checks after each provider call rather than hard async cancellation.

- [ ] **Step 4: Run observability tests**

Run:

```powershell
cd video-sharing-backend
./mvnw -Dtest=RecommendationServiceTest test
```

Expected: PASS.

## Task 8: Frontend Compatibility Check

**Files:**
- Modify only if needed: `video-sharing-frontend/src/views/HomeView.vue`
- Test: `video-sharing-frontend/src/views/HomeView.test.js`

**Interfaces:**
- Consumes: same `/api/videos/recommended` response shape as today.

- [ ] **Step 1: Run existing frontend test**

Run:

```powershell
cd video-sharing-frontend
npm test -- HomeView.test.js
```

Expected: PASS if response shape is unchanged.

- [ ] **Step 2: Only update frontend if backend response shape changes**

If `candidateSource` or `recommendReason` is added, do not render it in the MVP UI. Keep it as debug data only.

- [ ] **Step 3: Run frontend regression tests**

Run:

```powershell
cd video-sharing-frontend
npm test -- HomeView.test.js VideoCard.test.js
```

Expected: PASS.

## Task 9: Load and Failure Testing

**Files:**
- Create: `docs/recommendation-load-test.md`

**Interfaces:**
- Produces: repeatable pressure scenarios and target thresholds.

- [ ] **Step 1: Document pressure targets**

Write:

```text
homepage recommendation P95 < 100ms
homepage recommendation P99 < 200ms
Redis pipeline P95 < 10ms
DB batch detail query P95 < 50ms
cache hit rate > 90%
hot pool hit rate > 98%
error rate < 0.1%
```

- [ ] **Step 2: Document load scenarios**

Include:

```text
anonymous homepage 500 QPS
logged-in homepage 500 QPS
category homepage 300 QPS
all candidate-pool caches expired at 500 QPS
video detail cache miss at 500 QPS
watch progress upload 1000-5000 QPS
```

- [ ] **Step 3: Document failure drills**

Include:

```text
Redis unavailable -> DB provider path plus hot fallback
profile provider throws -> hot/fresh/explore still return
tag pool miss -> category pool fallback
all personalized pools empty -> hot fallback fills page
```

## Final Verification

- [ ] Run backend recommendation and schema tests:

```powershell
cd video-sharing-backend
./mvnw -Dtest=RecommendationMixerTest,CandidateProviderTest,InterestProfileServiceTest,PersonalizedCandidateProviderTest,RecommendationServiceTest,SchemaSqlTest test
```

- [ ] Run existing backend smoke tests:

```powershell
cd video-sharing-backend
./mvnw test
```

- [ ] Run frontend homepage tests:

```powershell
cd video-sharing-frontend
npm test -- HomeView.test.js VideoCard.test.js
```

- [ ] Manually verify:

```text
anonymous homepage returns 12 approved videos
logged-in homepage returns personalized candidates when watch history exists
category filter still returns category-specific videos
tag filter still returns tag-specific videos
Redis disabled still returns hot recommendations
```

