# Cinelode

Cinelode is a local movie and series library built with Spring Boot, SQLite,
React/Vite and FFmpeg. Catalog, poster, playback, preview, track-selection,
seeking and byte-range endpoints require an authenticated session.
FFprobe reads track metadata and duration together; results are cached in memory
and refreshed when the media file's size or modification time changes.
Previews select a random segment entirely within the middle 20%–80% of the
media duration, up to 25 seconds long.

## Requirements

- Java 25
- Node.js 24 and npm
- FFmpeg and FFprobe available on `PATH` (or configure their paths)
- A configured media directory and a configured login password hash

## Development

When you run `npm run dev` or `npm run dev:lan`, Cinelode checks for a
configured password hash first. If none exists, it prompts for a password and
confirmation, saves the BCrypt hash in `backend/.env`, then starts the
application. To configure it separately, run:

```sh
npm run setup:password
```

Enter and confirm the password in the terminal prompt. The command writes the
hash directly to `backend/.env`, preserving its other settings and restricting
the file to owner read/write permissions. For `npm run dev`, keep the media
paths in the same file:

```properties
CINELODE_USERNAME=cinelode
CINELODE_MEDIA_ROOT=/path/to/movies
CINELODE_SERIES_ROOT=/path/to/series
```

The backend intentionally refuses to start without a password hash rather than
creating a default or unsecured account.

Run the frontend and backend on loopback for local development:

```sh
npm run dev
```

The Vite frontend is available at `http://127.0.0.1:5173`; the backend remains
bound to `127.0.0.1:8080`. The development server does not terminate unrelated
processes or listen on LAN interfaces by default. Local `npm run dev` creates
an authenticated development session automatically, so the login form is
skipped. Catalog and media APIs still require an authenticated session.

### Media library layout

Set `CINELODE_MEDIA_ROOT` and `CINELODE_SERIES_ROOT` to readable directories
outside the repository. Cinelode scans them using these naming conventions:

```text
movies/                                   # CINELODE_MEDIA_ROOT
  Example Film (2024).mkv
  Example Film (2024).jpg                 # optional movie poster
  External Film (2022).txt               # contains an HTTP(S) URL
  Example Collection/
    01_First Film (2020).mkv
    01_First Film (2020).jpg
    02_Second Film (2023).mkv

series/                                   # CINELODE_SERIES_ROOT
  Example Series (2021)/
    Example_01_01.mkv                    # season 01, episode 01
    Example_01_02.mkv
    Example_01.jpg                       # optional season 01 poster
    Example_02_01.mkv
    Example_02.jpg
```

- Movie names must end with a space, a four-digit year in parentheses, and
  `.mkv` or `.txt`. Underscores in movie titles are displayed as spaces.
  Movie folders are scanned recursively; the immediate parent folder is used
  as the collection name. An optional numeric prefix such as `01_` gives
  films within a collection an ordering.
- A `.txt` movie entry must contain a valid HTTP(S) URL. When a matching MKV
  and TXT entry have the same title, year and collection, the MKV is preferred.
  Movie posters use the exact media filename without its extension, plus
  `.jpg`. External entries show their poster instead of a preview. Play opens
  the link in a new browser tab, or passes it to the device on Vega. Whether
  a particular link opens Prime Video depends on the device and registered
  apps; opening a specific title in Prime Video has not yet been verified.
  External entries stop automatic playback until Play is pressed.
- Each series must have its own direct child directory named `Title (YYYY)`.
  Episodes and optional season posters must be directly inside that directory,
  not in separate season folders. Episode names end in `_SS_EE.mkv` and
  poster names in `_SS.jpg`; `SS` and `EE` are two-digit season and episode
  numbers. Duplicate episode or season-poster numbers are rejected. A series
  without recognized episodes is not listed.
- Symbolic links are not followed during discovery. Keep private media,
  posters, and library contents out of Git and public repositories.

### LAN and Fire TV development

LAN mode requires a certificate whose subject alternative name matches the
hostname clients will use. For broad Fire TV/browser trust, use a publicly
trusted certificate issued with DNS-01 and resolve its hostname to the Cinelode
server's private address using LAN DNS. This does not require opening an
inbound port on the router. Do not assume a privately installed CA is trusted
on every Fire TV model. Certificate Transparency may disclose the hostname.

Keep the server certificate (or certificate chain) and its matching private
key in the certificate manager's storage, outside the repository. Keep the
private key readable only by its owner and never share or commit it. From the
project root, set the actual certificate paths in the shell that starts Cinelode:

```sh
export CINELODE_DEV_TLS_CERT="/path/to/fullchain.pem"
export CINELODE_DEV_TLS_KEY="/path/to/privkey.pem"
export CINELODE_DEV_ALLOWED_HOST="your-hostname.example"
npm run dev:lan
```

Set `CINELODE_DEV_ALLOWED_HOST` to the exact DNS hostname used to access the
development server. Vite needs it to accept the live-reload WebSocket; leave it
unset when using localhost or an IP address. Do not use a wildcard.

Open `https://<certificate-hostname-or-ip>:5173` on a trusted LAN client, using
the exact hostname or IP address listed in the certificate's subject alternative
names. The standard `npm run dev:lan` uses port 5173; this computer's
autostarted Fire TV frontend uses `https://cinelode.duckdns.org:5175`. For a
certificate issued by a local CA such as mkcert, each client must
trust that CA independently; trust on the server does not establish trust on a
Fire TV. Verify that the device supports the required trust configuration. Never
copy the CA's private key to clients.

This mode exposes the Vite HTTPS frontend on LAN interfaces, keeps the Spring
API on loopback, and marks the session and CSRF cookies `Secure`. Configure the
host firewall to allow the Vite port only from the trusted LAN; do not configure
router port forwarding. Do not use LAN mode without verified certificate trust.
Sign in once on the TV. Over HTTPS, a secure, HTTP-only persistent login cookie
restores its authenticated session after expiry or a server restart. Its
maximum lifetime is 10 years and is renewed when a session is restored, but the
device may delete it sooner (for example when browser data is cleared).
Logging out removes the cookie on that device;
changing the password invalidates all persistent login cookies. A copied cookie
remains usable until it expires or the password changes, so protect devices and
do not expose the server to untrusted networks.

The `dev:lan:http` convenience mode does not provide persistent login; do not
use it on an untrusted network. The actual Fire TV model/browser must still be
tested for cookie persistence before claiming device compatibility.

### Automatic start on Linux

The backend can run as a systemd user service at computer startup. The example
unit in `deploy/systemd/user/cinelode-backend.service` runs Spring on loopback
port 8080, enables the secure persistent-login cookie used by the HTTPS
frontend, and restarts the process after a failure. It reads the media paths,
database and password hash from the backend's existing environment files.
The unit assumes the project is at `~/Dokumente/code/cinelode`; adjust its
paths if your checkout is elsewhere.

Install and enable it from the project root:

```sh
mkdir -p ~/.config/systemd/user
cp deploy/systemd/user/cinelode-backend.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now cinelode-backend.service
```

User services normally start when that user logs in. To start Cinelode during
boot before login, enable lingering for the account with
`sudo loginctl enable-linger "$USER"`. An HTTPS frontend service must also be
running and proxy `/api` to `127.0.0.1:8080`. If you start the complete stack
with `npm run dev:lan`, stop the separate backend service first to avoid two
processes competing for port 8080. Check service output with
`journalctl --user -u cinelode-backend.service`.

### Amazon Vega WebView client

`client/` is a native wrapper around the existing React frontend, not a second
UI or a backend. The backend and Vite run on the server; a compatible Vega Fire
TV loads Vite over the LAN. The generated project targets Vega OS 2.0 and was
built locally using Vega SDK 2.0.13986, Vega CLI 1.4.9, React Native Kepler
4.0.2 and WebView 4.0.5. A device must support that runtime and WebView module;
not every Fire TV runs Vega.

1. Install the compatible Vega SDK and Node.js 24, then run
   `npm --prefix client ci`.
2. Copy `client/config.example.json` to `client/config.json` and
   `client/manifest.example.toml` to `client/manifest.toml`. These local files
   are ignored by Git. Set `frontendUrl` in `client/config.json` to the frontend's HTTPS origin,
   for example `https://cinelode.duckdns.org:5175` for this computer's
   autostarted Fire TV service. Replace it with your own LAN-resolved hostname
   and port if different. Only an HTTPS origin with an optional
   port and trailing slash is recommended: no path, query, fragment or
   credentials. The wrapper also accepts private IPv4 HTTP origins for local
   development, but HTTPS is required for LAN deployment.
   The initial empty value intentionally shows a configuration error. The URL
   is embedded at build time; rebuild after changing it. Do not commit local
   configuration changes or put credentials or tokens in the URL.
3. Configure the LAN certificate and start `npm run dev:lan` as described above.
   Its SAN must include the exact DNS hostname (or IP address) used in the URL.
   Inspect the configured certificate, for example:

   ```sh
   openssl x509 -in "$CINELODE_DEV_TLS_CERT" -noout -ext subjectAltName
   ```

   This only displays SANs; it does not prove that the Fire TV trusts the
   certificate. Provision a certificate chain trusted by the actual device and
   test it there. The wrapper rejects TLS errors; it never bypasses validation.
4. Run the client checks and build:

   ```sh
   ./client/node_modules/.bin/tsc -p client/tsconfig.json
   npm --prefix client run lint
   npm --prefix client test -- --runInBand
   npm --prefix client run build:release
   ```

   The build places architecture-specific `.vpkg` files under
   `client/build/`. Use `vega exec vpt info <package>` and
   `vega exec vpt show-contents <package>` to inspect a package; use the
   installed SDK's device tooling and a suitable signing profile to deploy it.
   The installed build tooling has been observed to remove the explicit
   `/com.amazon.vega.os@IVega_2_0` entry from the source manifest while adding
   it to the packaged manifest. Restore that source entry if this happens;
   `vega exec vpt validate client/manifest.toml` checks the OS declaration.
   The build also reports a missing Metro `assets/*` copy when there are no
   Metro assets, even though these packages validate; check the output rather
   than treating the build as proof of device compatibility.
   See the [Vega client guide](docs/vega-client.md) for the reproduced build
   side effect, dependency assessment, implemented navigation behavior and
   device test plan.

Vite serves both the UI and proxied `/api` requests from the same HTTPS origin;
Spring remains on `127.0.0.1:8080`. Third-party cookies are not enabled. The
existing secure session and CSRF cookies and media access remain in place;
actual WebView persistence and playback behavior still require device
testing. Vite is a development server, not a production deployment.

The frontend adds same-document history entries when a movie or season is
opened. Escape or the native Back key returns from playback to the library,
then shows the exit confirmation at the catalog root. From the series library,
Back first switches to the movie library; the next Back opens the exit
confirmation. The last catalog focus is restored when a title view closes. The
wrapper handles WebView page history and forwards Back at the root to the
frontend. Directional navigation,
keyboard entry, focus, Back behavior, media controls, seeking, track selection,
previews and byte-range playback must still be verified on the target device;
a successful package build does not prove these capabilities.

### Direct Spring Boot deployment

For deployments that do not use Vite, configure a valid TLS keystore, a
non-loopback `CINELODE_SERVER_ADDRESS` restricted to the intended interface,
`CINELODE_USERNAME`, and a strong encoded `CINELODE_PASSWORD_HASH`. Keep the
cookie secure flag enabled. Do not expose the server through plain HTTP.

## Data and migrations

The `backend/data/` directory is retained in Git with a placeholder; its
database, backups and other local contents are ignored.
Common local media directories and video file formats are also ignored, except
for the bundled intro. Keep media libraries outside the repository and review
the files staged for every commit; ignore rules do not cover every format and
do not remove files already tracked.

SQLite schema changes are versioned with Flyway. Before applying pending
migrations, Cinelode creates a SQLite-consistent backup and verifies database
and foreign-key integrity. Migration testing must use a separate database copy;
do not point rehearsal commands at the user's live database.

Relevant environment properties (defaults are shown in
`backend/src/main/resources/application.properties`):

| Variable | Purpose |
|---|---|
| `CINELODE_PASSWORD_HASH` | Required Spring Security encoded password hash. |
| `CINELODE_USERNAME` | Login name; defaults to `cinelode`. |
| `CINELODE_REMEMBER_ME` | Enable persistent login cookies when secure cookies are enabled; defaults to `false` and is enabled by `npm run dev:lan`. |
| `CINELODE_MEDIA_ROOT` | Movie media directory. |
| `CINELODE_SERIES_ROOT` | Series media directory. |
| `CINELODE_DATABASE_URL` | SQLite JDBC URL; defaults to `jdbc:sqlite:data/cinelode.db`. |
| `CINELODE_DB_BACKUP_DIRECTORY` | Optional directory for verified pre-migration database backups. |
| `CINELODE_FFMPEG_PATH`, `CINELODE_FFPROBE_PATH` | FFmpeg/FFprobe executable paths; default to the executable names on `PATH`. |
| `CINELODE_SERVER_ADDRESS`, `CINELODE_TLS_ENABLED`, `CINELODE_TLS_KEYSTORE`, `CINELODE_TLS_KEYSTORE_PASSWORD` | Spring bind address and HTTPS keystore settings for direct deployment. |
| `OMDB_API_KEY` | Optional OMDb metadata API key. |

Protect the database, backups, media directories, TLS private key and
environment files with operating-system permissions.

## Quality checks

```sh
npm test
npm --prefix frontend run lint
npm --prefix frontend run build
```

The real-binary transcode smoke test is optional and requires FFmpeg/FFprobe on
`PATH`:

```sh
CINELODE_FFMPEG_SMOKE=true ./backend/mvnw -f backend/pom.xml -Dtest=FfmpegPlaybackSmokeTest test
```
