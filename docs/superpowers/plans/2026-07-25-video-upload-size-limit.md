# 512MB Video Upload Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise the video upload limit to 512MB while retaining the 32MB thumbnail limit.

**Architecture:** Give each `MediaKind` its own byte limit and use that limit in both upload staging and pending-file inspection. Set the outer Spring and Nginx request limits high enough for one maximum-size video plus one maximum-size thumbnail and multipart overhead.

**Tech Stack:** Java 17, Spring Boot 3.2, JUnit 5, Mockito, Nginx, Vitest

## Global Constraints

- Video files must be limited to 512MB.
- Thumbnail files must remain limited to 32MB.
- Spring single-file limit must be 512MB.
- Spring total-request and Nginx body limits must be 545MB.
- Existing format validation, active-content scanning, cleanup, storage, and moderation behavior must remain unchanged.
- Do not include unrelated working-tree changes in implementation commits.

---

### Task 1: Enforce Per-Media Upload Limits

**Files:**
- Modify: `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/service/MediaFileValidator.java`
- Test: `video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/service/VideoServiceStorageTest.java`

**Interfaces:**
- Consumes: `MediaFileValidator.MediaKind` passed by `VideoService` and `ModerationService`.
- Produces: `MediaKind.maxBytes()` and `MediaKind.limitLabel()` used by staging and inspection size checks.

- [ ] **Step 1: Write failing behavior tests**

Update the large-video test to use a payload larger than the old limit:

```java
long payloadSize = 33L * 1024 * 1024;
```

Replace the old 33MB video rejection test with:

```java
@Test
void rejectsVideosBeyond512MbWithoutOpeningTheUpload() {
    long payloadSize = 513L * 1024 * 1024;

    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> service().uploadVideo(user(), "Title", "Description",
                    new StreamingMultipartFile("too-large.mp4", "video/mp4", payloadSize), null));

    assertTrue(failure.getMessage().contains("512 MB limit"));
    assertDirectoryEmpty(temp.resolve("pending-videos"));
    verify(repository, never()).save(any());
}

@Test
void keepsThumbnailLimitAt32Mb() {
    long payloadSize = 33L * 1024 * 1024;

    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> service().uploadVideo(user(), "Title", "Description",
                    upload("file", "video.mp4", "video/mp4", mp4()),
                    new StreamingMultipartFile("cover.png", "image/png", payloadSize)));

    assertTrue(failure.getMessage().contains("32 MB limit"));
    assertDirectoryEmpty(temp.resolve("pending-videos"));
    assertDirectoryEmpty(temp.resolve("pending-thumbnails"));
    verify(repository, never()).save(any());
}
```

These tests catch a regression to a shared 32MB or shared 512MB limit. The declared-size precheck makes the rejection tests fast and avoids materializing a 513MB fixture.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=VideoServiceStorageTest test
```

Working directory: `video-sharing-backend`

Expected: the 33MB video is rejected with the old 32MB message, and the 513MB expectation does not match.

- [ ] **Step 3: Implement kind-specific limits**

Replace the shared `MAX_MEDIA_BYTES` with limits owned by the enum:

```java
enum MediaKind {
    VIDEO(512L * 1024 * 1024, "512 MB"),
    THUMBNAIL(32L * 1024 * 1024, "32 MB");

    private final long maxBytes;
    private final String limitLabel;

    MediaKind(long maxBytes, String limitLabel) {
        this.maxBytes = maxBytes;
        this.limitLabel = limitLabel;
    }

    long maxBytes() {
        return maxBytes;
    }

    String limitLabel() {
        return limitLabel;
    }
}
```

In `stage`, reject a trustworthy declared oversize before opening or staging the upload, while retaining the streaming check:

```java
if (file.getSize() > kind.maxBytes()) {
    throw invalid("Uploaded media exceeds the " + kind.limitLabel() + " limit.");
}
```

Pass `kind` into the streaming methods:

```java
copyAndScan(input, output, kind);
scan(input, kind);
```

Use the kind-specific threshold in both loops:

```java
if (total > kind.maxBytes()) {
    throw invalid(source + " exceeds the " + kind.limitLabel() + " limit.");
}
```

Keep `copyAndScan` and `scan` responsible for streaming enforcement so a false or missing multipart size declaration cannot bypass the limit.

- [ ] **Step 4: Run tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=VideoServiceStorageTest test
```

Expected: all `VideoServiceStorageTest` tests pass.

- [ ] **Step 5: Commit only Task 1 hunks**

Stage only the new size-limit hunks, excluding unrelated edits already present in `VideoServiceStorageTest.java`:

```powershell
git diff -- video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/service/MediaFileValidator.java video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/service/VideoServiceStorageTest.java
git add -p -- video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/service/MediaFileValidator.java video-sharing-backend/src/test/java/com/videosharing/videosharingspringboot/service/VideoServiceStorageTest.java
git commit -m "feat: allow 512MB video uploads"
```

---

### Task 2: Align Spring and Nginx Transport Limits

**Files:**
- Modify: `video-sharing-backend/src/main/resources/application.properties`
- Modify: `video-sharing-backend/src/main/resources/nginx.conf`
- Test: `video-sharing-staff-frontend/src/deploymentContract.test.js`

**Interfaces:**
- Consumes: the 512MB video and 32MB thumbnail application limits from Task 1.
- Produces: a 512MB single-file transport allowance and a 545MB aggregate request allowance.

- [ ] **Step 1: Write the failing deployment contract**

Add:

```javascript
it('permits a 512MB video plus a 32MB thumbnail without widening thumbnail validation', () => {
  expect(applicationProperties).toMatch(/^spring\.servlet\.multipart\.max-file-size=512MB$/m)
  expect(applicationProperties).toMatch(/^spring\.servlet\.multipart\.max-request-size=545MB$/m)
  expect(nginx).toMatch(/client_max_body_size\s+545m;/)
})
```

This test catches mismatches between the two request-boundary layers.

- [ ] **Step 2: Run the targeted test and verify RED**

Run:

```powershell
npm test -- --run src/deploymentContract.test.js -t "permits a 512MB video"
```

Working directory: `video-sharing-staff-frontend`

Expected: FAIL because the current Spring and Nginx limits are 32MB.

- [ ] **Step 3: Update transport configuration**

In `application.properties`, change only the multipart lines:

```properties
spring.servlet.multipart.max-file-size=512MB
spring.servlet.multipart.max-request-size=545MB
```

In `nginx.conf`, change:

```nginx
client_max_body_size 545m;
```

Do not alter the existing datasource or JWT edits in `application.properties`.

- [ ] **Step 4: Run the targeted test and verify GREEN**

Run:

```powershell
npm test -- --run src/deploymentContract.test.js -t "permits a 512MB video"
```

Expected: the targeted deployment contract passes.

- [ ] **Step 5: Run combined verification**

Run:

```powershell
.\mvnw.cmd -Dtest=VideoServiceStorageTest test
```

Working directory: `video-sharing-backend`

Then run:

```powershell
npm test -- --run src/deploymentContract.test.js -t "permits a 512MB video"
```

Working directory: `video-sharing-staff-frontend`

Expected: both commands pass without new warnings or errors.

- [ ] **Step 6: Commit only Task 2 hunks**

Stage only the multipart lines in the already-modified properties file:

```powershell
git diff -- video-sharing-backend/src/main/resources/application.properties video-sharing-backend/src/main/resources/nginx.conf video-sharing-staff-frontend/src/deploymentContract.test.js
git add -p -- video-sharing-backend/src/main/resources/application.properties video-sharing-backend/src/main/resources/nginx.conf video-sharing-staff-frontend/src/deploymentContract.test.js
git commit -m "config: raise video upload transport limit"
```

---

### Task 3: Final Regression Verification

**Files:**
- Verify only; no production files should change.

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: evidence that the complete backend storage suite and targeted deployment contract pass together.

- [ ] **Step 1: Check the final diff**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; pre-existing unrelated changes remain present and uncommitted.

- [ ] **Step 2: Run backend regression tests**

Run:

```powershell
.\mvnw.cmd -Dtest=VideoServiceStorageTest test
```

Working directory: `video-sharing-backend`

Expected: PASS.

- [ ] **Step 3: Run the deployment limit contract**

Run:

```powershell
npm test -- --run src/deploymentContract.test.js -t "permits a 512MB video"
```

Working directory: `video-sharing-staff-frontend`

Expected: PASS.

- [ ] **Step 4: Review mutation coverage**

Confirm the tests fail under each realistic incorrect mutation:

- video limit changed back to 32MB;
- thumbnail limit changed to 512MB;
- Spring single-file limit differs from 512MB;
- Spring request or Nginx body limit differs from 545MB.
