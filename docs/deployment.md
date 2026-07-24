# Same-origin deployment behind mandatory TLS

Build both SPAs from their project directories:

```powershell
cd video-sharing-frontend; npm.cmd ci; npm.cmd run build
cd ../video-sharing-staff-frontend; npm.cmd ci --ignore-scripts; npm.cmd run build
```

Mount the ordinary user build at `/srv/video-sharing/user/dist` and the staff
build at `/srv/video-sharing/staff/dist`. The supplied Nginx configuration
binds only to `127.0.0.1:8081`, serves the user SPA at `/`, the staff SPA at
`/staff/`, proxies `/api/` to the local Spring Boot service on port 8080,
exposes only public `/uploads/` and `/thumbnails/`, and returns 404 for
`/pending/`. Approved media responses include `X-Content-Type-Options: nosniff`.
The proxy sets a 32 MB request limit to match Spring Boot and keeps byte-range
responses available for public video.

A TLS terminator or load balancer is mandatory. It accepts public HTTPS traffic
on port 443, forwards that traffic to `127.0.0.1:8081`, and preserves the
original `Host` header and `X-Forwarded-Proto: https`. The Nginx listener on
8081 and the Spring Boot backend on 8080 must not be publicly exposed. Use host
firewall and service binding rules so only the TLS terminator can reach the
loopback listener.

Run MySQL, Redis, the Spring Boot backend, Nginx, and the TLS terminator as
required services. Production has no datasource or JWT credential fallbacks
and requires all of:

- `DB_URL`: the production JDBC URL (with transport security appropriate to
  the database deployment)
- `DB_USERNAME`: a least-privilege application database account
- `DB_PASSWORD`: that account's secret password
- `JWT_SECRET`: a unique, securely generated signing secret

Configure `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, and `REDIS_DATABASE` for
the deployed Redis service as applicable. Configure the initial administrator
only with all of `ADMIN_USERNAME`, `ADMIN_EMAIL`, and `ADMIN_PASSWORD` (or leave
all three unset).

For local development only, activate the explicit profile with
`SPRING_PROFILES_ACTIVE=dev`. That profile supplies the localhost/root
datasource and development JWT secret and must not be used in production.

Configure the media paths to match the supplied Nginx mounts exactly:
`FILE_UPLOAD_DIR=/srv/video-sharing/public/uploads` and
`FILE_THUMBNAIL_DIR=/srv/video-sharing/public/thumbnails`. Keep private review
files outside those aliases with
`PENDING_UPLOAD_DIR=/srv/video-sharing/private/pending/uploads` and
`PENDING_THUMBNAIL_DIR=/srv/video-sharing/private/pending/thumbnails`; the
pending directories must not be mounted by Nginx.

Set `MODERATION_LEASE_DURATION_MINUTES` to an integer of at least `2`; the lease
must remain longer than the fixed 60-second staff heartbeat interval. When
cross-origin development is needed, set `CORS_ALLOWED_ORIGINS` to a comma-
separated, explicit list; wildcard origins and credentialed CORS are not
supported. For a same-origin production deployment, `VITE_API_BASE_URL` may be
omitted from both frontend builds. Development without that variable uses
`http://localhost:8080`; an explicitly empty or absolute value is preserved.

On startup, null legacy optimistic versions are backfilled to `0` before video
entities are loaded or saved, the database default is set to `0`, and the
column is made non-null. Legacy users without roles are migrated to `USER`.
Every `PENDING` video is audited: public-only legacy media is moved to private
pending storage, while duplicate or missing video/thumbnail state fails
startup. Videos with a null status are included in the same migration and
marked `PENDING`. A completed public-to-private repair intentionally remains in
private storage if a later move, database save, or transaction commit fails.
The next startup audits that safer state and retries idempotently; repaired
media is never rolled back into the public aliases.
