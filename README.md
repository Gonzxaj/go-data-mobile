# go-data-mobile

Android app (Kotlin + Jetpack Compose) for a **go-data** server. It shows the same information as
the go-data web dashboard by reading the same HTTP API. The server needs no changes and no API key:
the app logs in with the same session cookie the browser uses.

## Features

- **Home**: live stats, host info, active alarms and uptime sessions.
- **Metrics**: CPU, memory, disk, network, connections and containers, each with its history chart.
  Swipe or use the chip row to switch between them.
- **Security**: SSH events and connection events.
- **Settings**: server URL, session state and logout.
- Polls every 3 s, only the endpoints of the visible section. Polling pauses while the app is in the
  background. After repeated connection failures it shows an offline screen with a retry button.
- Dark theme matching go-data's web palette.

The UI text is in Spanish, like go-data itself.

## Requirements

- A reachable go-data server (`AUTH_MODE=none` or `AUTH_MODE=session`).
- Android 8.0 (API 26) or newer.
- To build: Docker only. No local JDK or Android SDK is needed.

## Build and install

```sh
./docker/build-apk.sh     # runs the unit tests and builds dist/go-data.apk (debug)
./docker/install-apk.sh   # installs it on a USB-connected phone (USB debugging on) and opens it
```

`docker/gradle.sh <tasks>` runs any Gradle task in the same container. The source is copied into the
container, so no root-owned build output lands in the repo. Named Docker volumes keep the Gradle cache
and the debug keystore between runs, so updates install over the previous build.

To also run the live test against a real server:

```sh
GODATA_URL=http://<server>:<port> GODATA_USER=<user> GODATA_PASS=<password> \
  ./docker/gradle.sh testDebugUnitTest
```

The project also opens as-is in Android Studio.

## How it works

1. **Server URL**: the user enters `http://server:port`. It is saved in SharedPreferences.
2. **Auth mode detection** with `GET /api/auth/me`:
   - `200`: `AUTH_MODE=none`, or the saved session is still valid. Go to the dashboard.
   - `401` with a JSON body: `AUTH_MODE=session`. Go to the login screen.
   - `401` with `WWW-Authenticate: Basic`: `AUTH_MODE=basic`. Not supported yet.
3. **Login**: `POST /api/auth/login`. The app takes `godata_session` from `Set-Cookie` and sends it as
   `Cookie` on every request.
4. **Dashboard**: each poll calls `/api/stats` first (it doubles as the session check), then the
   section's own endpoints. A `401` sends the user back to the login screen.

### Endpoints used

| Section | Endpoints |
|---------|-----------|
| All | `/api/stats` |
| Home | `/api/host`, `/api/alarms`, `/api/uptime/sessions` |
| CPU, Memory, Disk, Network | `/api/history?metric=...&points=60` |
| Connections | `/api/network/summary`, `/api/network/history`, `/api/network/top`, `/api/network/connections`, `/api/network/events`, `/api/network/ip-history` |
| Containers | `/api/containers` |
| Security | `/api/security/ssh`, `/api/security/connections` |

## Project layout

Source is in `app/src/main/java/ar/godata/monitor/`:

- `Api.kt`: HTTP client (`HttpURLConnection`) and the endpoints each section fetches.
- `Model.kt`: data classes mirroring go-data's JSON.
- `AppViewModel.kt`: app state, the URL → login → dashboard flow, and polling.
- `Format.kt`: formatting for bytes, percentages, durations and dates.
- `ui/`: the app's own components (cards, metrics, chart, icons), screens, and one view per section.
- `lumo/`: theme (colors, typography, `Dimens`) and the [Lumo UI](https://github.com/nomanr/lumo-ui)
  components the app uses (buttons, text field, scaffold, spinner, divider), generated from its
  templates. They are part of this codebase and edited here. See `lumo/NOTICE.md`.

Tests are in `app/src/test/`: API parsing, formatting, icons, and the optional live server test.

### Stack

Kotlin 2.4.21, Android Gradle Plugin 9.5.1, Gradle 9.9.1, Compose BOM 2026.09.00,
kotlinx-serialization 1.13.0, kotlinx-coroutines 1.11.0. `compileSdk` 37, `targetSdk` 36, `minSdk` 26.

## Security notes

- Over plain HTTP, the password and the session token travel unencrypted. The app warns when the URL
  starts with `http://`. Cleartext traffic is allowed because go-data on a LAN usually runs over HTTP.
  Use HTTPS (for example behind a reverse proxy) if the server is reachable from outside your network.
- The session token is stored unencrypted in the app's private SharedPreferences.
- HTTPS servers with a self-signed certificate do not connect.

## Roadmap

- Alarm notifications while the app is closed (WorkManager + notifications).
- Users section (create, delete and edit users; admin only).
- `AUTH_MODE=basic`.

## License

[MIT](LICENSE). The Lumo UI-derived code in `lumo/` keeps its original Apache License 2.0
(see `lumo/LICENSE` and `lumo/NOTICE.md`).
