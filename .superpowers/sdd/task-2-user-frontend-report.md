# Task 2 user frontend report

## RED evidence

Focused Vitest tests were added before implementation and run with:

```text
npm.cmd test -- src/services/auth.test.js src/utils/safeVideoUrls.test.js src/router/userRoleGuard.test.js src/views/AuthRoleViews.test.js src/views/ModerationDashboard.test.js src/views/UploadModeration.test.js
```

The initial run failed as expected for role persistence, stored-staff route access, non-USER login/register responses, unsafe DTO-path URL construction, upload copy/destination, and submission loading/status/error states. One new upload-test mock setup error was corrected before re-running it; it then failed on the expected pending-review copy assertion.

## GREEN evidence

The focused suite passed after the minimal implementation:

```text
6 test files passed, 10 tests passed
```

The final complete frontend verification passed:

```text
npm.cmd test      21 test files passed, 68 tests passed
npm.cmd run build vite production build completed successfully
```

## Changes

- Stored auth identity now retains `role`; public login/register reject non-USER responses, clear auth, and display a stable Chinese access error.
- Protected user routes require a stored USER role. Stale staff identities are cleared and redirected to login with the access error.
- API base URL reads `VITE_API_BASE_URL` and falls back to `http://localhost:8080` only when unset.
- Media helpers accept only `videoUrl` and `thumbnailUrl` from safe DTOs.
- Upload success now says the video was submitted for review and links to the user dashboard.
- The dashboard loads `/api/users/me/videos`, renders moderation statuses, rejection reasons, timestamps, and existing loading/empty/error state components.

## Changed files

`video-sharing-frontend/src/App.vue`, auth/API/router/video utilities, user auth/upload/dashboard views, existing affected tests, and six focused Vitest files for the new behavior.

## Commit

Implementation commit: `4e89737` (`feat: add ordinary user moderation flow`).

## Concerns

None. The Vite test/build output continues to emit the pre-existing CJS Node API deprecation warning; it does not fail either verification command.

## Review follow-up

Review findings were fixed with a second RED/GREEN cycle. Focused tests first failed for the backend `uploadDate` field, relative safe DTO media URLs resolving against the dev backend origin, and stale upload guidance; they then passed (5 files, 14 tests).

- Dashboard submission fixtures and rendering now use `uploadDate`.
- Relative `videoUrl` and `thumbnailUrl` values resolve against the configured API base origin, while absolute URLs and an empty same-origin production base remain unchanged.
- Upload guidance now directs users to submit for review and check their submissions.

Follow-up verification:

```text
npm.cmd test      21 test files passed, 68 tests passed
npm.cmd run build vite production build completed successfully
```

Review-fix implementation commit: `7748640` (`fix: align user moderation DTO handling`).

## Configuration decoupling follow-up

The API base URL now lives in `src/config/api.js`, which is imported by both the Axios service and video URL helpers. This prevents view/component tests that mock `services/api.js` from coupling to configuration exports.

TDD evidence: the new `src/config/api.test.js` first failed because the config module did not exist, then passed with the focused safe-media test.

```text
npm.cmd test      22 test files passed, 69 tests passed
npm.cmd run build vite production build completed successfully
```

Configuration-refactor implementation commit: `55d205a` (`refactor: share API base configuration`).

## Safe DTO uploader follow-up

Safe public-video fixtures now prove that cards, recommendation list items, the player author panel, and author-name search use `uploader`. A shared `getAuthorName` helper prefers `uploader` and retains the nested `user.username` fallback for legacy data.

TDD evidence: the safe-uploader card, list, player, and search tests first failed in all four surfaces, then passed (4 files, 19 tests).

```text
npm.cmd test      22 test files passed, 70 tests passed
npm.cmd run build vite production build completed successfully
```

Safe-uploader implementation commit: `ba09a16` (`fix: support safe DTO uploaders`).
