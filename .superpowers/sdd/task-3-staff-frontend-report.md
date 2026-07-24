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
