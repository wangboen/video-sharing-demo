# Task 3 staff frontend report

## Commit

- Commit hash: this report is committed atomically with the implementation; resolve the
  immutable object ID with `git rev-parse HEAD` on branch
  `codex/moderation-claim-lease`.

## RED evidence

The initial complete staff suite was written before production modules and run with:

```text
npm.cmd test
```

All 6 test files failed at import resolution because `staffAuth`, the staff router,
and the three new staff views/components did not exist. These were expected
feature-missing failures, not assertion or fixture errors.

A review-focused second RED cycle added two edge contracts:

```text
npm.cmd test -- src/services/staffAuth.test.js src/views/ReviewerManagementView.test.js
```

The two focused assertions failed as expected because the 401 handler was not
exported/implemented and the reviewer form did not yet opt out of browser-native
validation.

## GREEN evidence

The first complete GREEN run passed:

```text
6 test files passed, 26 tests passed
```

After the review-focused fixes, the two focused suites passed 10 tests. Fresh final
verification then passed:

```text
npm.cmd test      6 test files passed, 27 tests passed
npm.cmd run build Vite production build completed successfully (86 modules)
```

The production output uses the required `/staff/` base contract.

## Changed files

- Package and entry points:
  - `video-sharing-staff-frontend/package.json`
  - `video-sharing-staff-frontend/package-lock.json`
  - `video-sharing-staff-frontend/vite.config.js`
  - `video-sharing-staff-frontend/index.html`
  - `video-sharing-staff-frontend/src/main.js`
  - `video-sharing-staff-frontend/src/App.vue`
- Staff-only infrastructure:
  - `src/config/apiBase.js`
  - `src/services/api.js`
  - `src/services/staffAuth.js`
  - `src/router/index.js`
- Interface:
  - `src/components/StaffShell.vue`
  - `src/components/LeaseRail.vue`
  - `src/views/StaffLoginView.vue`
  - `src/views/ModerationView.vue`
  - `src/views/ReviewerManagementView.vue`
  - `src/assets/main.css`
- Tests:
  - focused Vitest files beside auth, router, shell, login, moderation, and reviewer
    management sources
  - `src/test/setup.js`

No backend or ordinary-user frontend files were changed.

## Design self-critique

- The interface stays focused on trained reviewers: the media preview owns the
  desktop canvas while decisions remain in a fixed-width rail and collapse beneath
  the preview on smaller screens.
- Amber is used only by the vertical lease rail/countdown, so it consistently means
  ownership and time pressure. Structural blue, approval green, and reject red carry
  the remaining action semantics.
- `Microsoft YaHei UI` carries Chinese interface copy and `Bahnschrift` is reserved
  for versions, timestamps, IDs, and lease telemetry.
- The login view makes the strongest typographic statement; the authenticated shell
  and workbench intentionally remove decoration so the video and decision remain
  primary.
- Visible focus, semantic status/error regions, labels, responsive breakpoints, and
  reduced-motion handling are included. The design uses no gradients or decorative
  numbered steps.
- The cockpit is deliberately dense enough for desktop review work. On very narrow
  screens, metadata becomes longer vertically, which is preferable to compressing
  decision controls or media.

## Concerns

- Verification used the repository's already-installed Node runtime through a local,
  ignored junction because a fresh network install hung in the restricted
  environment. The committed lockfile was regenerated without junction paths, and
  the final tests/build used the declared package scripts.
- No live-backend browser session was available; request shapes, conflict handling,
  media Blob behavior, timers, and cleanup are covered by component tests against the
  backend contracts.
- A commit cannot contain its own final object ID because adding that ID changes the
  commit. The exact hash is reported in the handoff and available through
  `git rev-parse HEAD`.

## Review-fix follow-up

### Heartbeat serialization and lease generations

Root-cause review found that the original heartbeat used only `video.id` as its
completion guard. A late success could therefore update a later lease for the same
video, while a late 409 cleared the current task without even checking the ID.
Release and decision requests also started while a heartbeat was unresolved.

RED:

```text
npm.cmd test -- src/views/ModerationView.test.js src/services/leaseHeartbeat.test.js src/assets/styleContract.test.js src/toolchainContract.test.js
```

- The decision request was sent immediately with stale version `4` instead of waiting
  for the deferred heartbeat version `8`.
- Release was sent before the deferred heartbeat settled.
- The generation-guard module did not exist.
- Accessibility and toolchain assertions failed on the previous CSS and package
  versions.

GREEN:

- Each claimed task now receives an opaque generation token; numeric video IDs are
  never used as async ownership proof.
- Every heartbeat success, conflict, and service-failure completion checks that token
  before mutating state or messages.
- A per-generation in-flight promise serializes release and decisions behind the
  current heartbeat. Decisions use the heartbeat's returned version.
- Timers pause during mutation and restart only when a retryable failure leaves the
  same generation active.
- Deferred-promise/fake-timer tests cover heartbeat plus decision, heartbeat plus
  release, and late success/conflict from a replaced same-ID generation.

Focused heartbeat verification:

```text
2 test files passed, 14 tests passed
```

### Accessibility

- Lease countdown, label, and hint text now use ink `#172033`; amber remains only on
  the vertical lease accent.
- Keyboard focus uses a solid `#173B8F` three-pixel outline instead of translucent
  blue.
- A CSS contract suite locks both decisions.

Focused accessibility verification:

```text
1 test file passed, 2 tests passed
```

### Toolchain alignment

- Runtime metadata declares Node `>=20.17.0`, npm `11.12.x`,
  `packageManager: npm@11.12.1`, and `.nvmrc` pins the verified local Node `24.15.0`.
- The test/build stack is aligned on Vite `5.4.21`, `@vitejs/plugin-vue` `5.2.4`,
  Vitest `1.6.1`, and jsdom `24.1.3`.
- Dependencies were installed normally and the lockfile was regenerated without
  junctions, `file:` dependencies, links, or parent-directory package paths.

Installed top-level versions:

```text
@vitejs/plugin-vue 5.2.4
jsdom               24.1.3
vite                5.4.21
vitest              1.6.1
```

### Final review-fix verification

```text
npm.cmd test      9 test files passed, 35 tests passed
npm.cmd run build Vite 5.4.21 production build passed, 87 modules transformed
```

Review-fix commit: this report is committed atomically with the follow-up; resolve its
immutable object ID with `git rev-parse HEAD`.

### Review-fix files

- `video-sharing-staff-frontend/.nvmrc`
- `video-sharing-staff-frontend/package.json`
- `video-sharing-staff-frontend/package-lock.json`
- `video-sharing-staff-frontend/src/services/leaseHeartbeat.js`
- `video-sharing-staff-frontend/src/services/leaseHeartbeat.test.js`
- `video-sharing-staff-frontend/src/views/ModerationView.vue`
- `video-sharing-staff-frontend/src/views/ModerationView.test.js`
- `video-sharing-staff-frontend/src/assets/main.css`
- `video-sharing-staff-frontend/src/assets/styleContract.test.js`
- `video-sharing-staff-frontend/src/toolchainContract.test.js`

### Review-fix concerns

The aligned dependencies were installed into the staff package itself, so the earlier
temporary-runtime concern is superseded. `npm.cmd ci` emits one non-blocking
deprecation warning for jsdom 24's transitive `whatwg-encoding` package; the staff
tests and production build emit no failures.
