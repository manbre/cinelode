# Cinelode Vega client

This is the native Vega WebView wrapper for the existing React frontend. It
does not contain a second UI or the backend. The backend remains on the
development machine. Do not put credentials in the client or its URL.

## Status and configuration

Local checks have used Vega SDK 2.0.13986, Vega CLI 1.4.9, React Native
Kepler 4.0.2 and WebView 4.0.5. No Fire TV was connected for these checks.
The manifest targets Vega OS 2.0; device support, installation, signing and
playback are **not verified**. A Fire TV model must actually support the
specified Vega runtime and WebView module; do not assume all Fire TVs do.

Copy `config.example.json` to `config.json` and `manifest.example.toml` to
`manifest.toml` before building. Both local files are ignored by Git. The
manifest points to `assets/image/cinelode.png`, the 512 × 512 Vega app icon.
Set `frontendUrl` in `config.json` to your LAN-accessible HTTPS origin. On this
computer, the autostarted service is `https://cinelode.duckdns.org:5175`; the
generic `npm run dev:lan` command uses port 5173. Use the actual origin serving
your frontend, with a hostname that resolves to the development machine on the
device. An IP address is also accepted if the certificate has that IP address
as an IP SAN. Do not use loopback as the address of a remote server.
The initial value is empty because no server address is configured here.

The wrapper imports this JSON at bundle time; no URL environment variable is
implemented. Rebuild and reinstall after changing it. The validator accepts
HTTPS, a hostname or bracketed IPv6 address, an optional numeric port and
optional trailing slash; it rejects paths, query strings, fragments and
credentials. Private IPv4 HTTP origins are currently also
accepted for local development, but do not use them for LAN deployment.
It is not a DNS or certificate validator. An empty or rejected value
shows a configuration error. Do not commit machine-local configuration.

## Development machine and HTTPS

Follow the [root development instructions](../README.md#development) to set
up credentials and media paths. LAN mode is started with `npm run dev:lan`.
Set `CINELODE_DEV_TLS_CERT` to the certificate chain file and
`CINELODE_DEV_TLS_KEY` to the corresponding private key file in the process
environment first. This computer's systemd frontend service already points to
the ACME-managed certificate under `~/.local/share/cinelode-acme/`. Keep the
private key in the certificate manager's storage; do not copy it into the
repository or share it.
See the [LAN instructions](../README.md#lan-and-fire-tv-development) for an
example configuration.

Choose a hostname resolving to the server's LAN address from the Fire TV.
Inspect the configured certificate's subject alternative names with
`openssl x509 -in "$CINELODE_DEV_TLS_CERT" -noout -ext subjectAltName`.
The exact hostname must appear in a DNS SAN, or the exact IP in an IP SAN.
This inspection does not establish trust or prove the key matches. Check the
chain, validity period and key using your certificate issuer's instructions.
Use a chain trusted by the target device; a private CA or a browser trust store
on another device does not establish Vega WebView trust.

The LAN Vite server listens on network interfaces using HTTPS. Restrict its
port to the trusted LAN with the host firewall and do not forward it on the
router. Vite proxies `/api` over loopback HTTP to Spring on `127.0.0.1:8080`;
do not expose the backend separately. From another trusted LAN client, verify
DNS, TCP reachability and HTTPS using normal certificate verification. A curl
probe of the configured HTTPS origin can help diagnose network and certificate
problems, but does not prove that the Fire TV trusts the chain. Do not use
insecure TLS options or bypass SSL errors in the app.

The browser-visible UI and `/api` share the same scheme, host and port; CORS
is not needed for those requests. LAN mode sets `Secure` cookies: `JSESSIONID`
is `HttpOnly` and `SameSite=Strict`, while `XSRF-TOKEN` is readable by
JavaScript and `SameSite=Strict`. The frontend obtains a CSRF token before
login and logout. After login, the HTTPS LAN mode also uses a secure, HTTP-only
persistent login cookie to restore sessions. The WebView rejects SSL errors
and does not enable third-party cookies. Actual session persistence and
authentication in Vega remain hardware tests.

## Vega Virtual Device on Pop!_OS 24.04

On the inspected x86_64 Pop!_OS 24.04 machine, SDK 2.0.13986 and CLI 1.4.9
successfully booted the included TV VVD image with a mapped graphical window
and exposed it as `VirtualDevice`. This demonstrates operation on that host,
not official Pop!_OS support. Amazon documents Ubuntu, but we have not found
a statement specifically supporting Pop!_OS.

**Cinelode's native WebView cannot be tested with this Linux VVD image.**
Installation of the existing x86_64 package was rejected because the device
lacks `/com.amazon.kepler.webview_4@IWebview_4`. Amazon's
[VVD troubleshooting guide](https://developer.amazon.com/docs/vega/2.0/kvd-issues)
also states that VVD WebView is unsupported on Ubuntu and Intel Macs and
suggests an Apple Silicon Mac or a compatible Fire TV device. Neither
alternative has been tested here. Do not remove the required module from the
manifest: that does not supply the WebView implementation. The VVD can still
be used for compatible non-WebView Vega apps; a desktop browser can test the
React UI but does not validate Vega WebView, device cookies or media behavior.

### Host prerequisites and start

The SDK already included the TV image and its own emulator/QEMU. The bundled
`emulator-check accel` reported usable KVM, and the logged-in user had
read/write access to `/dev/kvm` and GPU render nodes via existing ACLs.
An X11/XWayland display and sufficient free RAM and disk space are needed.
No system packages were installed and no permissions were changed for this
test. Required host libraries depend on the machine; diagnose missing
dependencies from emulator logs rather than installing or replacing libraries
at random. `ldd` on the internal QEMU binary alone may report bundled
libraries as missing because the emulator launcher sets up its library paths.

In a graphical terminal, from the project root:

```sh
source ~/vega/env
vega --version
QT_QPA_PLATFORM=xcb vega virtual-device start --gui --timeoutSeconds 180
vega virtual-device status
vega device list
vega device is-connected --device VirtualDevice
```

The default start uses OpenGL acceleration. The earlier default GUI start
timed out while waiting for the device shell, so accelerated startup has not
been verified on this host. A successful launch used XWayland (`xcb`) together
with `--no-gl-accel`; that option disables graphics acceleration and can make
the host CPU do the rendering, causing high load and desktop lag. Use it only
as a fallback when accelerated graphics prevent startup. Boot took
approximately 2.5 minutes on this host. The emulator emitted graphics
validation warnings, and the device adapter briefly reported offline even
while the VVD process was running. A mapped window and a running process alone
do not prove the device is ready: verify the device connection as above. If it
is unavailable, inspect the logs and retry only after diagnosing the cause.

On hybrid-graphics laptops, verify which renderer is active before falling
back to software rendering. `nvidia-smi` can show whether the emulator process
is using the NVIDIA GPU; `glxinfo -B` (if already available) reports the
OpenGL renderer. If accelerated startup fails, inspect the emulator logs
before switching to `--no-gl-accel`.

When finished, stop the VVD cleanly and verify the stopped state:

```sh
vega virtual-device stop
vega virtual-device status
vega device list
```

These stop and restart commands were tested. VVD creates a persistent instance
and device data under the active SDK's `vvd/instances` directory; do not delete
it blindly. `cannot find virtual device instance: default_instance` before
the first start simply indicates no default instance exists yet.

### Packages and logs

For the x86_64 VVD, the existing package path was
`client/build/x86_64-release/cinelode_x86_64.vpkg`; it passed `vpt` validation
but installation failed as described above. The package still has an empty
frontend URL. The build and manifest-preservation cautions in the next section
apply before any new build. No install/start script is supplied because it
would fail on this VVD.

The following installed CLI commands were checked; the log stream was started
and stopped, and `get-log-info` worked once but also produced an intermittent
CLI error after a restart:

```sh
vega device get-log-info --device VirtualDevice
vega device start-log-stream --device VirtualDevice
vega device stop-log-stream --device VirtualDevice
```

The SDK's `vvd/virtual_device.log` and the instance's `virtual_device.err`
and `virtual_device.out` contain host-side boot diagnostics; SDK CLI errors
also point to the active SDK's `logs/` directory. Logs may contain device
identifiers, URLs or other sensitive information; review before sharing.

For a compatible target on which WebView is present, the installed CLI exposes
`vega device install-app --device <device> --packagePath <package>` and
`vega device launch-app --device <device> --appName de.cinelode.client.main`.
Those commands were not successfully tested with Cinelode on such a target.
Its architecture must match the package. Configure the trusted HTTPS origin
and rebuild before attempting a real UI test.

## Local checks and packages

The existing lockfile is used with `npm --prefix client ci`. From the project
root, the following checks and build were run with the versions above:

```sh
./client/node_modules/.bin/tsc -p client/tsconfig.json
npm --prefix client run lint
npm --prefix client test -- --runInBand
npm --prefix client run build:release
vega exec vpt validate --no-color client/manifest.toml
```

Release packages are generated under `client/build/` for aarch64, armv7 and
x86_64. The current packages were built with an **empty** `frontendUrl`; they
cannot connect to Cinelode until rebuilt with your origin. Use
`vega exec vpt info <package>`, `vega exec vpt show-contents <package>` and
`vega exec vpt validate --no-color <package>` to inspect the relevant package.
The generated manifest contains the Vega 2.0, Kepler and WebView module needs.

**Build side effect:** SDK 2.0.13986's Release build reproducibly removes the
explicit `/com.amazon.vega.os@IVega_2_0` entry from the *source* manifest while
putting it in the package. Thus a direct build does not preserve that source
entry. The existing root manifest was left intact. Prefer building in a
separate copy with its own `node_modules`, or compare the source manifest
before and after building and restore the entry without losing other changes.
Do not treat the original manifest as validated until the post-build
`vpt validate` succeeds. A symlinked `node_modules` outside the project did
not resolve in Metro during our isolated test; a real copied dependency tree
did.

The SDK's staging code unconditionally copies `build/lib/rn-bundles/Release/
assets/*` into the bundle staging directory. In this project Metro emits no
such asset files, so the SDK prints `cp: no such file or directory` for each
target. The separate `client/assets/index.html`, app JSON, JS/Hermes bundles
and runtime configuration were present in the inspected packages. The current
wrapper imports no Metro image assets and does not load the template HTML;
this makes the empty-glob warning apparently harmless for the current bundle,
not proof that future assets or runtime loading work. Reinspect contents if
assets are added. No SDK files have been modified.

## Dependency risk assessment

The audit on 2026-11-11 reported 28 affected dependency nodes: 11 high,
7 moderate and 10 low. Counts include dependent packages, not necessarily
distinct vulnerabilities. `npm audit --omit=dev` reported nine nodes
(six moderate, three low), including React Native CLI dependencies installed
through peer relationships; this is not a claim that they execute on device.
No lockfile update was made.

| Priority | Installed path and risk | Recommended action |
|---|---|---|
| High | Direct development tool `@amazon-devices/kepler-cli-platform@0.22.14` pins transitive `lodash@4.17.21` (template code injection and prototype-pollution advisories). | Ask SDK maintainers for a compatible release using a fixed lodash; registry latest observed was still 0.22.14. Lodash 4.17.24 or later addresses the reported ranges, but an override conflicts with the exact pin and needs SDK build and device regression tests. |
| High | Direct dev tools `@amazon-devices/eslint-plugin-kepler@0.1.16` and `@amazon-devices/kepler-cli-platform` via `kepler-module-manifest-builder` depend on `toml@3.0.0` (prototype pollution and denial of service). | Seek vendor updates; the reported ranges extend through 5.0.0 and 5.0.1 exists. A major override across the exact `3.0.0`/`^3.0.0` requirements is not proven compatible. Avoid untrusted manifests in builds meanwhile. |
| High | Direct `@typescript-eslint/eslint-plugin@6.21.0` and the React Native ESLint config's v8 tree pull `typescript-estree` and affected `minimatch` instances (glob ReDoS). The v6 parser pins 9.0.3. | Test a coordinated compatible linter upgrade only after upstream publishes a fix for all current advisories; minimatch 11.0.1 is outside the pinned requirement. The audit's `fixAvailable: true` is not proof of compatibility or a complete fix. Avoid untrusted glob patterns. |
| Moderate | Direct `@amazon-devices/eslint-plugin-kepler` pins `uuid@11.0.5`; versions before 11.1.1 have a buffer bounds advisory. | Request a vendor fix; do not override the exact pin without testing the plugin. |
| Moderate/low | `react-native` and its CLI dependency tree include `fast-xml-parser@4.5.7` and `deepmerge@4.3.1`. The advisories concern XML handling and prototype poisoning. | Investigate with React Native/CLI maintainers. The reported `fast-xml-parser` ranges require at least 5.11.3 for both advisories; the observed 5.12.0 is a major change from v4. No fixed deepmerge release was identified in the audit. Restrict untrusted build input. |
| Low | Jest, ts-jest and testing-library are reported through the `deepmerge`/Jest tree. npm proposes downgrades to older major versions. | Do not apply `audit fix --force`; revisit after compatible upstream updates and rerun tests and Vega builds. |

These are audit findings, not proof of an exploitable app path. Build tooling
and local untrusted project inputs are the principal identified exposure;
assess runtime reachability separately. Keep SDK and Node updates controlled.

## Back and navigation: implemented

The wrapper has one native `hardwareBackPress` listener. It calls the
installed WebView 4.0.5 `goBack()` method only when the last navigation event
reported `canGoBack`. At the root it forwards a DOM `BrowserBack` event into
the WebView, allowing the frontend to show its exit confirmation dialog. A
confirmed `cinelode-exit-confirmed` message calls the installed
`BackHandler.exitApp()` API. `allowSystemKeyEvents` is deliberately not
enabled: the SDK documents that it consumes Back in the WebView and prevents
the native fallback.

The frontend uses spatial focus navigation for the D-pad and native Enter
activation. Focus-visible styling is applied globally, focused controls scroll
into view, and controls hidden by the fullscreen player are removed from the
tab order. Text inputs, selects, and range controls retain their native
keyboard behavior.

For external movie entries, the frontend shows the poster and sends the
validated HTTP(S) link to the wrapper when Play is pressed. The wrapper
passes it to Vega's `Linking.openURL`; failure is shown in the frontend.
The installed app or system determines how the link is handled. Opening
Amazon links in Prime Video has not been verified on a device.

Catalog selections add a same-document history entry. Escape/BrowserBack and
the native Back path return from playback to the library before showing the
exit confirmation at the catalog root. The last catalog focus is restored when
a title view is closed. Play/Pause is handled by the media element when the
device dispatches `MediaPlayPause`; fast-forward and rewind seek by 30 and 10
seconds respectively.

Preview playback advances after 25 seconds. The client fades the current clip
out for 350 ms, starts the next clip, and fades it in for 350 ms. The
backend preview endpoint remains unchanged; the client-side cutoff avoids a
second preview implementation while preserving the existing HMR/LAN setup.
On large screens, Vega and browser previews share the same player and metadata
layout. The Vega video rendering remains device-specific; fullscreen playback
and external posters are unchanged.
The WebView receives ordinary DOM keyboard events and one native Back bridge.
Browser history, spatial focus geometry, keyboard input, media-key delivery,
and WebView video behavior must still be checked on the target Fire TV;
desktop tests cannot prove device-specific event delivery.

### Changed files and verification

The native wrapper behavior is covered in `test/App.spec.tsx`. Frontend TV
key mapping and the existing catalog/auth/playback utilities are covered by
the Node test suite. On 2026-11-12, frontend tests (19), frontend lint and
Vite production build passed. Client lint, Jest (15 tests), TypeScript
type-check, and the Vega Release build passed. Real Fire TV validation of
D-pad spatial behavior, keyboard input, media-key delivery, WebView history,
TLS trust, and application exit remains hardware-only.

## Real-device test plan — all hardware steps untested

Record device model, Vega OS/runtime versions, architecture, SDK/CLI versions,
package build number, test date and results. Do not record passwords, cookies
or private keys in logs or reports.

1. **Install/start:** build with the actual URL and appropriate architecture;
   check manifest and package as above. Connect a compatible device with the
   SDK; `vega device list` currently reports no devices. The installed CLI
   exposes `vega device install-app --packagePath <package>` and
   `vega device launch-app --appName de.cinelode.client.main`; their help was
   inspected, but installation, developer authorization and signing have not
   been tested here. Verify launch and visible loading/error messages.
2. **Origin:** resolve the configured hostname on the device's network and
   load the HTTPS UI. Confirm the same hostname and port are used for `/api`.
3. **TLS:** verify the device accepts the intended certificate chain and
   matching SAN. Test a certificate failure only in a controlled environment;
   it must be rejected, with no bypass. A desktop success is not device trust.
4. **Login:** enter credentials with the remote/keyboard; confirm successful
   and rejected login, then logout. Do not capture credentials.
5. **Session/CSRF:** confirm authenticated catalog requests work, unauthenticated
   access fails, login/logout with valid CSRF succeeds, and a controlled request
   without a valid token is rejected. Check secure/HttpOnly/SameSite attributes
   and expected session behavior after relaunch and expiry without recording
   cookie values.
6. **Remote:** verify D-pad focus and selection across login, catalog, filters,
   movie details and controls; check visible focus, scrolling, text entry and
   selection widgets.
7. **Back:** test a selection control, player/fullscreen, catalog state, actual
   page history and root. Record the observed behavior and any unexpected
   results, including whether the app exits unexpectedly.
8. **Catalog:** open movies and series, select seasons/episodes, check posters,
   previews and return to the catalog.
9. **Playback:** start a movie and an episode, verify picture and sound,
   fullscreen support and authenticated requests, including byte-range behavior
   where applicable. Check errors and logs without exposing credentials.
10. **Controls:** pause, resume, seek forward/backward and near the end; check
    displayed position and actual playback position.
11. **Tracks:** select different audio and subtitle tracks, including off and
    automatic; verify the audible/visible result and behavior after seeking.
12. **Network loss:** disconnect and restore the LAN during page loading and
    playback. Record timeout/error, retry focus and recovery; verify no TLS or
    authentication bypass. Do not assume interrupted media automatically
    resumes.

Local checks cover mock events, build output and static configuration only.
They do not substitute for any of the hardware checks above.
