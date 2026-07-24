# Task 5 final security fixes report

## Outcome

Implemented every Critical/Important final-review finding in the isolated
`codex/moderation-claim-lease` worktree. The ordinary user frontend required no
integration change.

## RED/GREEN evidence

- Stored approved-media XSS: `VideoServiceStorageTest` RED ran 10 tests with 5
  expected failures for retained names/extensions and accepted spoofed or
  active content. GREEN passed 10/10 after server-selected UUID extensions,
  signature/MIME/extension validation, active/polyglot rejection, and cleanup.
- Optimistic-version migration: the first RED failed compilation because
  `LegacyVideoVersionMigration` did not exist. GREEN passed 3/3 real-H2 and
  MySQL-statement tests. A self-review RED then failed the missing non-null JPA
  mapping; GREEN passed the expanded 4/4 migration tests.
- PENDING startup audit: `LegacyVideoMigrationTest` RED ran 8 tests with 3
  expected failures for uninspected public-only, duplicate, and missing
  thumbnail states. GREEN passed 8/8.
- Approval compensation: the focused RED failed 1/1 because reverse-move
  inconsistency was ignored. GREEN passed the full 16-test moderation service
  suite with a clear exception, original cause, and suppressed compensation
  failure.
- Offset-bearing timestamps: `ModerationTimestampContractTest` RED failed 2/2
  type/JSON assertions. GREEN passed 18/18 focused timestamp and moderation
  tests after migrating claimed, expiry, and reviewed values to `Instant`.
- Lease safety: the focused RED failed 1/1 because a 60-second lease was
  accepted. GREEN passed 17/17 moderation service tests after enforcing a lease
  duration greater than the fixed heartbeat interval.
- Deployment hardening: the staff deployment contract RED ran 9 tests with 3
  expected failures for datasource defaults, public HTTP/TLS behavior, and
  missing `nosniff`. GREEN passed 9/9.
- Staff timezone coverage: the new Asia/Shanghai-browser countdown scenario
  passed 1/1 with the existing epoch-based component once supplied the new UTC
  API timestamp. No staff production code change was required.

## Implemented security and migration behavior

- Only MP4 `ftyp`, WebM EBML, Ogg `OggS`, JPEG, PNG, and WebP signatures are
  accepted with matching MIME/filename metadata. Stored names are UUID plus a
  server-selected extension; user filenames are never retained.
- A highest-precedence JDBC migration backfills null `videos.version` values,
  sets default `0`, and enforces non-null with explicit MySQL/MariaDB and H2
  statements before the JPA-based video migration.
- Startup audits every PENDING video and optional thumbnail, moves public-only
  legacy media private, and fails closed on duplicate or missing state.
  Approval compensation failures now surface with original failure context.
- Claimed, lease-expiry, and reviewed persistence/API values use `Instant` and
  serialize with `Z`; upload time remains unchanged.
- Production requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`.
  Local datasource/JWT defaults are confined to the explicit `dev` profile.
- Nginx listens only on `127.0.0.1:8081` behind mandatory TLS termination,
  preserves incoming Host/proto headers, and sends `nosniff` for public media.
- Lease configuration fails startup unless it is longer than the 60-second
  staff heartbeat.

## Final verification

- Backend `mvn.cmd test`: 76 tests passed, 0 failures, 0 errors, 0 skipped.
- User frontend `npm.cmd test`: 22 files and 72 tests passed.
- User frontend `npm.cmd run build`: passed; 121 modules transformed.
- Staff `npm.cmd ci --ignore-scripts`: passed; 209 packages installed.
- Staff `npm.cmd test`: 11 files and 46 tests passed.
- Staff `npm.cmd run build`: passed; 87 modules transformed.
- `git diff --check`: passed before staging the reports; repeated after staging
  and before commit.

## Self-review and concerns

The staged patch was reviewed against each Task 5 requirement, including
storage cleanup paths, migration ordering/idempotency, pending-media failure
states, compensation cause preservation, UTC serialization, deployment
headers, and documentation.

The H2 migration is exercised against a real legacy table. Production MySQL
syntax is selected and asserted exactly but was not executed against a live
MySQL server in this workspace. TLS termination is an external deployment
prerequisite and is verified here by Nginx/documentation contracts rather than
an end-to-end load balancer. Existing Java-agent, Hibernate dialect, Vite CJS,
and npm deprecation warnings remain non-failing and predate this task.
