# Task 4 deployment integration report

## Commit

Primary Task 4 implementation commit: `1592227bb2bbe5c2dc6ee03964a151573d5cf200`
(`feat: configure same-origin deployment`).

## RED/GREEN evidence

- User API configuration: added DEV, production same-origin, empty-value, and
  absolute-value tests. RED: `resolveApiBaseUrl is not a function`. GREEN:
  `npm.cmd test -- src/config/api.test.js` passed 3 tests.
- Staff Vite port: added a config contract. RED: `vite.config.js` did not match
  port 5174. GREEN: the staff toolchain and deployment contracts passed 6 tests.
- Backend CORS: added origin-list and wildcard-rejection tests. RED: backend
  test compilation failed because `SecurityConfig` lacked the configured-origin
  constructor. GREEN: `mvn.cmd test -Dtest=SecurityConfigCorsTest` passed 2
  tests.
- Nginx/public-directory deployment contract: RED: the legacy Nginx placeholder
  lacked SPA mounts, the 127.0.0.1 API proxy, public aliases, and pending denial.
  GREEN: the deployment contract passed. A later directory-environment contract
  also failed before `FILE_UPLOAD_DIR` and `FILE_THUMBNAIL_DIR` support was
  added, then passed with 4 tests.

## Verification

- `video-sharing-backend`: `mvn.cmd test` — passed, 59 tests, 0 failures/errors.
- `video-sharing-frontend`: `npm.cmd test` — passed, 22 files and 72 tests.
- `video-sharing-frontend`: `npm.cmd run build` — passed.
- `video-sharing-staff-frontend`: `npm.cmd ci --ignore-scripts` — passed
  (209 packages installed).
- `video-sharing-staff-frontend`: `npm.cmd test` — passed, 10 files and 40 tests.
- `video-sharing-staff-frontend`: `npm.cmd run build` — passed.
- `git diff --check` — passed before commit.

## Files changed

- `video-sharing-frontend/src/config/api.js`
- `video-sharing-frontend/src/config/api.test.js`
- `video-sharing-staff-frontend/vite.config.js`
- `video-sharing-staff-frontend/src/toolchainContract.test.js`
- `video-sharing-staff-frontend/src/deploymentContract.test.js`
- `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/security/SecurityConfig.java`
- `video-sharing-backend/src/main/resources/application.properties`
- `video-sharing-backend/src/main/resources/application-dev.properties`
- `video-sharing-backend/src/main/resources/nginx.conf`
- `video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/security/SecurityConfigCorsTest.java`
- `video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/security/SecurityConfigConfiguredCorsTest.java`
- `docs/deployment.md`
- `.superpowers/sdd/task-4-deployment-integration-report.md`

## Self-review and concerns

The Nginx configuration explicitly mounts separate user and staff SPAs, denies
`/pending/` without an alias, restricts public media to approved directories,
and retains byte-range support. CORS uses only explicit origins, rejects
wildcards, and disables credentials. The deployment guide deliberately avoids
documenting a real secret or a production secret default.

One first full user Vitest run exited nonzero after all assertions had passed
because a worker exited while a concurrent production build was running. A
subsequent isolated full run passed (22 files, 72 tests), so final verification
uses that isolated result. Existing Maven/Vitest warnings about dynamically
loaded Java agents, Hibernate dialect configuration, and Vite's deprecated CJS
Node API predate this task and were not changed here.

## Review follow-up

Review follow-up commit is recorded after this report is force-added despite
the repository's `.superpowers/` ignore rule.

### Corrections

- Removed the default JWT secret fallback. The normal configuration now requires
  `JWT_SECRET`; `application-dev.properties` holds a development-only secret,
  and the guide specifies `SPRING_PROFILES_ACTIVE=dev` only for local use.
- Documented exact public media environment values matching the Nginx aliases:
  `FILE_UPLOAD_DIR=/srv/video-sharing/public/uploads` and
  `FILE_THUMBNAIL_DIR=/srv/video-sharing/public/thumbnails`. Private pending
  paths are explicitly `/srv/video-sharing/private/pending/uploads` and
  `/srv/video-sharing/private/pending/thumbnails`.
- Replaced direct-literal CORS default testing with Spring MVC tests that bind
  default application properties and an explicit configured-origin property.
- Strengthened the pending-media contract to inspect the optional-modifier
  pending location block itself for `return 404` and no `alias` directive.

### Follow-up RED/GREEN and verification

- RED: deployment contract failed when the default JWT property contained a
  fallback and the guide lacked exact public/private path assignments. GREEN:
  6 deployment-contract tests pass after the configuration, dev profile, and
  documentation changes.
- CORS property-binding tests: focused Maven verification passed 3 tests
  (default origins/credentials, wildcard rejection, and configured origins).
- Full follow-up verification:
  - backend `mvn.cmd test`: 60 passed, 0 failures/errors;
  - user frontend `npm.cmd test`: 22 files, 72 passed;
  - user frontend `npm.cmd run build`: passed;
  - staff frontend `npm.cmd ci --ignore-scripts`: passed;
  - staff frontend `npm.cmd test`: 10 files, 42 passed;
  - staff frontend `npm.cmd run build`: passed.

## Task 5 security supersession

The verification counts above are historical Task 4 evidence. Task 5 changes
the deployment inventory and records the current whole-branch verification in
`.superpowers/sdd/task-5-final-security-fixes-report.md`.

- Default datasource configuration now requires `DB_URL`, `DB_USERNAME`, and
  `DB_PASSWORD`; localhost/root values exist only in the explicit dev profile.
- The supplied Nginx server no longer presents public HTTP/80. It listens only
  on `127.0.0.1:8081` behind a mandatory TLS terminator, preserves incoming
  host/proto metadata, and sends `nosniff` on approved media.
- Deployment documentation now identifies mandatory production variables,
  private listener requirements, the optimistic-version backfill, the complete
  PENDING-media startup audit, and the minimum safe lease duration.
- The Task 4 files additionally revised by Task 5 are
  `application.properties`, `application-dev.properties`, `nginx.conf`,
  `src/deploymentContract.test.js`, and `docs/deployment.md`.
