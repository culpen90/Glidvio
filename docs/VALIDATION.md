# Glidvio 0.1.0 validation

Verified on 2026-10-18 using JDK 21, Gradle 8.13, and a physical Android device (SM-S938U, Android 17 / API 37).

## Results

- Debug APK assembly: passed.
- JVM tests: **23 passed**, zero failures, errors, or skips.
- Real phone instrumentation tests: **5 passed**, zero failures, errors, or skips.
- Android lint: **0 errors, 29 warnings, 1 hint**. Nonfatal findings include dependency update recommendations, supported-link configuration, PiP transition recommendations, and style guidance.
- Final APK installed successfully. Its SHA-256 on the phone matched the built artifact.
- GPL and third-party notices were verified inside the APK against the source copies.
- Final UI smoke: typing a search and pressing Enter displayed real search results; Discover returned to music charts with a cleared search field.

## Evidence and scope

| Requirement | Evidence |
| --- | --- |
| Native Android YouTube viewing | Real search plus continuation page; direct video and audio media bytes; moving decoded video on the phone. |
| Ad-free playback | NewPipe Extractor supplies content streams directly to Media3. The app includes no YouTube embedded player, advertising SDK, ad loader, or ad media item configuration. Real native playback and content-byte requests passed. |
| Automatic sponsor skipping | SponsorBlock is checked before playback. Unit tests cover privacy-prefix matching, error handling, malformed ranges, overlap/adjacency, exact boundaries, duration clipping, and Undo suppression. Live tests verify seeking inside a reported sponsor, crossing its boundary during normal playback, and Undo playback without re-skipping. |
| Player controls | Real accessibility actions enter/exit fullscreen, enter PiP, return via the launcher, and save a video. Tests require the same activity/player and continued playback through the transitions. |
| Local library | Actual Save action persisted the selected video; test data was restored afterward. History and skip settings use app-private on-device preferences. |

Tests use real YouTube and SponsorBlock responses, without mocked media. Curated test XML and lint evidence are retained locally in `artifacts/verification/`; generated artifacts and device logs are excluded from Git. Reproduce with `scripts/check.sh` and `ANDROID_SERIAL=<selected-device> scripts/live-test.sh --allow-physical-device`.

## Delivered artifact

- File: `artifacts/Glidvio-0.1.0.apk`
- Package: `com.glidvio.app`
- Version: `0.1.0` (code 1), development signing certificate.
- Size: 25,846,214 bytes.
- SHA-256: `79055e1e02ea4843aa47ba4499aa2b5f7bab162daa069babb099468232cdcc65`

## Limits

SponsorBlock can skip only reported segments. A successful empty response does not prove that a video contains no sponsorship. Lookup failures hold playback for Retry while skip categories are enabled. Unreported sponsorships may remain. Private, age-restricted, region-restricted, or unavailable YouTube videos may fail, and upstream extractor updates can be needed. API 26 is the configured minimum; this validation used API 37 and does not claim testing on every supported Android version.
