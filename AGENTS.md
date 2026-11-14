# Development guide

## Project structure

- `backend/` contains the Spring Boot server and its ports and adapters.
- `frontend/` is the single React UI for browsers and the Vega client.
- `client/` is the native Amazon Vega WebView wrapper and device integration
  only. Do not copy the React UI, backend logic, or credentials into it.
  Keep the configured frontend and `/api` on the same HTTPS origin for LAN use;
  preserve certificate verification, authentication, sessions and CSRF.

## Architecture and behavior

- Keep the backend a modular monolith: domain/application code must not depend
  on web, filesystem, JDBC, or FFmpeg adapters. Put external I/O behind the
  existing ports and adapters; keep HTTP orchestration out of domain logic.
- Preserve the movie and series catalog, posters, previews, audio/subtitle
  selection, seeking, FFmpeg transcoding, and HTTP byte-range behavior.
- Require authenticated sessions for catalog and media routes. Preserve CSRF
  protection and same-origin playback; never put credentials in media URLs.
- LAN access is intentional. Keep local development loopback-only by default;
  LAN access requires HTTPS and must not trust forwarded client headers from
  arbitrary network clients.
- Resolve media and poster paths within their configured roots, reject symlink
  escapes, and keep external poster downloads bounded and redirect-free.
- Keep FFmpeg process lifecycle handling in its adapter. Ensure subprocesses
  have bounded execution, explicit failure handling, and reliable cancellation.
- Use Flyway for schema changes. Preserve existing SQLite data and rehearse
  migrations only on a separate database copy; do not migrate user data during
  tests or manual verification.
- Configure runtime paths and secrets through documented environment
  properties. Never commit credentials, local environment files, or user media.

## Verification

- Backend: `./backend/mvnw -f backend/pom.xml test`
- Frontend tests: `npm --prefix frontend test`
- Frontend lint: `npm --prefix frontend run lint`
- Frontend production build: `npm --prefix frontend run build`
- Root `npm test` runs the frontend and backend test suites.
- Vega client: `npm --prefix client ci`, then
  `./client/node_modules/.bin/tsc -p client/tsconfig.json`,
  `npm --prefix client run lint`, `npm --prefix client test -- --runInBand`
  and `npm --prefix client run build:release`. The SDK and hardware
  prerequisites and frontend URL configuration are documented in README.md.
- Run the focused test for a changed behavior first, then the relevant broader
  checks. The real-binary FFmpeg smoke test is opt-in and requires FFmpeg and
  FFprobe installed.

Add regression tests for behavior changes, especially authentication,
filesystem boundaries, database migrations, and playback/streaming semantics.
