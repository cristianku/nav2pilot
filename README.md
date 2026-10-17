# Nav2Pilot

Send navigation destinations from your Android phone to a comma device running **openpilot** or
**sunnypilot** with **Nav Alpha**.

Share a place from Google Maps (or type an address, coordinates or a map link), pick your device, tap
**Send**. Nav2Pilot calls the device's `setNavDestination` remote method through comma's athena service.

Kotlin, Jetpack Compose, Material 3. Package `com.cristianku.nav2pilot`. Independent of the
openpilot / sunnypilot source code: it only talks to documented network APIs.

## Features

- Android share target for Google Maps (place name, address and `maps.app.goo.gl` link) and "Open with"
  for `geo:` links.
- Coordinates from full Google Maps URLs, Maps URLs API links (`api=1`), legacy `q=` / `ll=` / `daddr=`
  links, `geo:` URIs and `google.navigation:` URIs. Short links are expanded by following redirects.
- Manual entry: `lat, lng`, a pasted link, or an address search (Android `Geocoder`).
- Several devices: all devices of the comma account, plus devices added by dongle ID.
- Connection status (athena `getVersion`: shows the version and branch running on the device).
- Send and clear the destination, with confirmation or a precise error.
- Favorite destinations.
- comma token stored encrypted with an Android Keystore key.

## Requirements on the device

- openpilot with Nav Alpha (commaai/openpilot master since "nav alpha" #39066, merged 2026-10-12) or a
  sunnypilot build that includes Nav Alpha. Older software answers `method not found` and the app says so.
- The device must be **online** (athena connected) when you send.
- The destination is kept until the car goes **offroad** or the device **restarts** (see "Verified API").

## Getting a comma token

1. Open <https://jwt.comma.ai> (it redirects to `useradmin.comma.ai`) and sign in with the Google,
   Apple or GitHub account your device is paired to.
2. Copy the token.
3. In Nav2Pilot open **Devices → Add token**, paste it, **Verify and save**.

The token is checked with `GET /v1/me` before it is stored. If the token carries an `exp` claim the app
shows its expiry date; the token lifetime is not documented in the sources below, so when sending starts
failing with HTTP 401, generate a new token.

## Usage

1. In Google Maps open a place, tap **Share**, choose **Nav2Pilot** ("Send to comma device").
2. Check the destination card (use **Show on map** if in doubt; an "approximate" warning means the link
   only had the map centre).
3. Pick the device and tap **Send to …**.

## Verified API

Everything Nav2Pilot sends is taken from source code or official documentation:

| What | Source |
|------|--------|
| `POST https://athena.comma.ai/<dongle_id>` with a JSON-RPC 2.0 body is relayed to the device's websocket | <https://api.comma.ai> ("Athena"), [commaai/comma-api `openapi.yaml`](https://github.com/commaai/comma-api/blob/master/openapi.yaml) (`/{dongleId}` on server `https://athena.comma.ai`) |
| Auth header `Authorization: JWT <token>`, tokens from jwt.comma.ai | <https://api.comma.ai> ("Authentication"); openpilot `tools/lib/api.py` |
| API host `https://api.commadotai.com` | openpilot `common/api/comma_connect.py`, `tools/lib/api.py` |
| `GET /v1/me/devices/`: `dongle_id`, `alias`, `device_type`, `last_athena_ping`, `is_owner` | <https://api.comma.ai> ("List devices"), `openapi.yaml` `Device` schema |
| `GET /v1/me` validates a token | openpilot `tools/lib/auth.py` |
| `setNavDestination(latitude: float, longitude: float)` returns `{"success": 1}`, or `{"success": 0, "error": "Invalid destination coordinates"}` out of range | commaai/openpilot `openpilot/system/athena/athenad.py`; sunnypilot `feature/nav-alpha` same file |
| `clearNavDestination()` returns `{"success": 1}` | same file |
| Both write/remove the `NavDestination` param (JSON `{"latitude", "longitude"}`) that `navd` routes to | `openpilot/selfdrive/navigation/navd.py` |
| `NavDestination` flags `CLEAR_ON_MANAGER_START \| CLEAR_ON_OFFROAD_TRANSITION` | `openpilot/common/params_keys.h`, applied in `openpilot/system/manager/manager.py` |
| JSON-RPC errors: `-32601` method not found, `-32602` invalid params, `-32000` server error | `openpilot/system/athena/rpc.py` |
| `getVersion()` returns `version`, `remote`, `branch`, `commit`, `commit_date` | `openpilot/system/athena/athenad.py` |
| A queued message returns the result string "Device offline, message queued" | <https://api.comma.ai> ("Athena") |

Notes:

- Only `latitude` and `longitude` are sent. The comma-api schema still lists `place_name` and
  `place_details` for `setNavDestination` (the pre-2026 signature), but the current athenad function takes
  exactly two parameters, and the device's dispatcher answers unknown keyword arguments with `-32602`.
- athena's offline queue is documented for "some specific methods"; the schema only lists
  `uploadFilesToUrls` there. Nav2Pilot therefore expects the device to be online and does not set `expiry`.
- athena's HTTP error bodies for unknown or offline devices are not documented; the app shows the HTTP
  status and whatever text the server returns.
- `clearNavDestination` is in the device code but not in the comma-api schema.

### Google Maps links

- `maps.app.goo.gl` links are expanded one redirect at a time, reading only the `Location` header and
  only on Google hosts. The EU consent interstitial (`consent.google.com/...?continue=<maps url>`) is not
  fetched: its `continue` parameter already contains the Maps URL.
- In expanded place URLs the pin is `!3d<lat>!4d<lng>` inside `data=`. This is **not a documented
  format**, only observed in real redirects; it is preferred over the `@lat,lng,zoom` viewport, which can
  be kilometres away from the place. A viewport-only result is marked approximate.
- When a link has no coordinates, the shared address lines (or the place name) are geocoded.

## sunnylink

Investigated, **not implemented** (the `SunnylinkTransport` backend is an explicit stub):

- Verified: on the device, `sunnylinkd` imports the same JSON-RPC `dispatcher` as athenad
  (`openpilot/sunnypilot/sunnylink/athena/sunnylinkd.py`), so `setNavDestination` is reachable over the
  sunnylink websocket (`wss://athena.sunnylink.ai`).
- Found: the public [sunnylink frontend](https://github.com/sunnypilot/sunnylink-frontend) ships
  generated types for `POST https://athena.sunnylink.ai/navigation/{deviceId}/set_destination`
  (`latitude`, `longitude`, `place_name`, `place_details`), but no UI uses it and there is no documentation.
- Not available: a documented API contract, and a supported way for a third-party app to obtain a sunnylink
  token (the frontend signs users in through its own Logto OIDC client).

## Architecture

```
app/src/main/java/com/cristianku/nav2pilot/
  MainActivity.kt            share / VIEW intents -> ViewModel
  AppContainer.kt            manual dependency wiring
  model/                     Destination, PairedDevice, validation rules
  parsing/                   URL splitting, coordinates, Google Maps / geo: links, shared text
  resolve/                   short-link expansion, link -> search fallback
  geocode/                   PlaceSearch (Android Geocoder)
  net/                       HttpClient abstraction (HttpURLConnection)
  transport/                 NavigationTransport + comma athena backend + sunnylink stub
  security/                  Keystore AES-GCM cipher, encrypted token store, JWT expiry
  data/                      favorites and devices (SharedPreferences + JSON)
  ui/                        Compose screens: Send, Favorites, Devices
```

`NavigationTransport` is the backend abstraction (`listDevices`, `checkStatus`, `setDestination`,
`clearDestination`). Each device records its backend, and `TransportRegistry` picks the implementation, so
another backend can be added without touching the UI.

## Security and privacy

- The comma JWT is encrypted with AES-256-GCM using a non-exportable key in the Android Keystore; only the
  ciphertext is stored, in app-private preferences. Backups and device transfer are disabled.
- The token is never logged (the app has no logging), is excluded from `HttpRequest.toString()`, and is not
  shown again after saving.
- Network access is limited to `api.commadotai.com`, `athena.comma.ai`, Google Maps hosts (only to expand
  shared links) and the phone's Geocoder service (address search). No analytics.

## Build

Toolchain (pinned on purpose, it matches what was available locally when the project was created):

- JDK 17 (with `jlink`, e.g. Temurin 17)
- Android SDK platform 33 and build-tools 30.0.3
- Gradle 8.0 through the wrapper (distribution SHA-256 pinned), Android Gradle Plugin 8.0.0,
  Kotlin 1.8.0, Compose BOM 2023.05.01 (Material 3 1.1.0)

```
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Android Studio opens the project directly. Create `local.properties` with `sdk.dir=/path/to/Android/sdk`
if `ANDROID_HOME` is not set.

Upgrading to a current AGP / compileSdk is a separate step: it needs a newer Gradle, newer SDK packages and
newer AndroidX versions together.

## Tests

- JVM unit tests (`app/src/test`): link parsing (including a real `maps.app.goo.gl` redirect and EU consent
  hop captured on 2026-10-17), short-link expansion, resolver fallbacks, the exact athena JSON-RPC requests
  and response handling, token storage, repositories and the ViewModel flow. No network access.
- Instrumented test (`app/src/androidTest`): Keystore encryption round trip. Needs a device or emulator:
  `./gradlew connectedDebugAndroidTest`.

## CI

`.github/workflows/android.yml` runs unit tests and lint, builds the debug APK and the instrumented
test APK, and uploads `app-debug.apk` and the reports as artifacts.

## License

MIT, see [LICENSE](LICENSE).
