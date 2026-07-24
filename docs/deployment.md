# Same-origin deployment

Build both SPAs from their project directories:

```powershell
cd video-sharing-frontend; npm.cmd ci; npm.cmd run build
cd ../video-sharing-staff-frontend; npm.cmd ci --ignore-scripts; npm.cmd run build
```

Mount the ordinary user build at `/srv/video-sharing/user/dist` and the staff
build at `/srv/video-sharing/staff/dist`. The supplied Nginx configuration
serves the user SPA at `/`, the staff SPA at `/staff/`, proxies `/api/` to the
local Spring Boot service on port 8080, exposes only public `/uploads/` and
`/thumbnails/`, and returns 404 for `/pending/`. It sets a 32 MB request limit
to match Spring Boot and keeps byte-range responses available for public video.

Run MySQL, Redis, the Spring Boot backend, and Nginx as required services. Set
the backend database and Redis connection properties for the deployed services.
Set `JWT_SECRET` to a unique, securely generated production secret; there is no
safe production fallback. Configure the initial administrator only with all of
`ADMIN_USERNAME`, `ADMIN_EMAIL`, and `ADMIN_PASSWORD` (or leave all three unset).

Set public media directories with `FILE_UPLOAD_DIR` and
`FILE_THUMBNAIL_DIR`, and private review directories with `PENDING_UPLOAD_DIR`
and `PENDING_THUMBNAIL_DIR`; the pending directories must not be mounted by
Nginx.

Set `MODERATION_LEASE_DURATION_MINUTES` for moderation claim duration. When
cross-origin development is needed, set `CORS_ALLOWED_ORIGINS` to a comma-
separated, explicit list; wildcard origins and credentialed CORS are not
supported. For a same-origin production deployment, `VITE_API_BASE_URL` may be
omitted from both frontend builds. Development without that variable uses
`http://localhost:8080`; an explicitly empty or absolute value is preserved.

On startup, legacy users without roles are migrated to `USER`. Legacy videos
with a null status are moved from public media into the configured pending
directories and marked `PENDING`. Startup fails rather than guessing when media
state is unsafe, and rolls completed moves back if the migration transaction
does not commit.
