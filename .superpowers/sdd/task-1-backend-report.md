# Task 1 Backend Report

## Commit

- Commit hash: this report is committed atomically with the implementation; resolve the
  immutable object ID with `git rev-parse HEAD` on branch
  `codex/moderation-claim-lease`.

## TDD evidence

### RED

- `mvn.cmd '-Dtest=UserRoleServiceTest,JwtAuthenticationFilterTest,ModerationServiceTest' test`
  - Failed during test compilation as expected because `Role`, `VideoStatus`,
    `ModerationDecisionRequest`, and `ModerationService` did not exist.
- `mvn.cmd '-Dtest=AdminBootstrapTest,LegacyVideoMigrationTest,VideoServiceStorageTest' test`
  - Failed during test compilation as expected because the bootstrap and legacy
    migration components did not exist.
- `mvn.cmd '-Dtest=VideoServiceStorageTest#invalidThumbnailDoesNotLeaveTheVideoInPendingStorage' test`
  - Failed as expected: one pending video file remained after invalid thumbnail
    validation.

### GREEN

- Focused role, JWT, moderation, storage, bootstrap, migration, authorization, and
  concurrency suites were rerun throughout implementation.
- `mvn.cmd test`
  - Final result: 49 tests, 0 failures, 0 errors, 0 skipped.
  - Build result: `BUILD SUCCESS`.

## Files changed

- Build/configuration:
  - `video-sharing-backend/pom.xml`
  - `video-sharing-backend/src/main/resources/application.properties`
  - `video-sharing-backend/src/main/resources/schema.sql`
- Domain and persistence:
  - `model/User.java`, `model/Video.java`, `model/Role.java`, `model/VideoStatus.java`
  - `repository/UserRepository.java`, `repository/VideoRepository.java`
- Security/authentication:
  - `security/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`
  - `service/UserService.java`
  - `controller/UserController.java`, `controller/StaffAuthController.java`,
    `controller/AdminReviewerController.java`
  - `dto/AuthResponse.java`, `dto/UserProfileResponse.java`, `dto/ReviewerResponse.java`
- Moderation, storage, and migration:
  - `service/VideoService.java`, `service/ModerationService.java`,
    `service/ModerationConflictException.java`
  - `controller/UserVideoController.java`, `controller/ModerationController.java`
  - `config/AdminBootstrap.java`, `config/LegacyVideoMigration.java`,
    `config/ApiExceptionHandler.java`
  - moderation and submission DTOs under `dto/`
- Tests:
  - role-aware auth/JWT and Spring Security authorization tests
  - real-JPA concurrent claim tests
  - moderation lease/decision/preview tests
  - pending storage/public filtering/submission tests
  - admin bootstrap and legacy migration tests

No user or staff frontend files were changed.

## Concerns

- The passing Maven run emits existing/toolchain informational warnings from annotation
  processing, Spring Security test auto-configuration, Hibernate's test dialect, and
  ByteBuddy dynamic agent loading. They do not correspond to test failures.
- The literal commit object ID cannot be embedded inside a file in that same commit
  because changing the file changes the object ID; the exact ID is reported by the
  handoff status and `git rev-parse HEAD`.
