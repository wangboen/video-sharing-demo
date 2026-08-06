# Home Recommendation Load Test

## Targets

- Homepage recommendation P95 < 100 ms.
- Homepage recommendation P99 < 200 ms.
- Redis pipeline P95 < 10 ms.
- DB batch detail query P95 < 50 ms.
- Overall recommendation cache hit rate > 90%.
- Hot pool cache hit rate > 98%.
- Recommendation API error rate < 0.1%.

## Load Scenarios

- Anonymous homepage: 500 QPS, `GET /api/videos/recommended?page=0&size=12`.
- Logged-in homepage: 500 QPS with users that have recent watch history.
- Category homepage: 300 QPS with `categoryId`.
- Cache expiry drill: 500 QPS while hot, fresh, explore, and profile keys are expired together.
- Detail cache miss drill: 500 QPS with video detail cache disabled or flushed.
- Watch progress upload: 1000-5000 QPS with frontend-style 10-15 second throttling.

## Failure Drills

- Redis unavailable: recommendation should continue through repository-backed providers and hot fallback.
- Category or tag profile provider throws: hot, fresh, explore, and continue-watching pools still return.
- Tag pool miss: request should continue and rely on category or hot fallback.
- All personalized pools empty: hot fallback fills the requested page size.
- One provider exceeds soft budget: request logs degradation and still returns available pools.

## Metrics To Capture

- Request latency P50/P95/P99 by anonymous, logged-in, and category traffic.
- Redis get/set count, hit rate, error rate, and latency.
- DB query count per recommendation request.
- `findAllByIdIn` batch query latency.
- Pool result sizes for hot, fresh, explore, category interest, tag interest, and continue watching.
- Fallback count per request.
- Degraded request count and degradation reason.

