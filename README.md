# Glidvio

An Android YouTube viewer with ad-free native playback and automatic skipping of community-reported sponsor segments. Built with Kotlin, Jetpack Compose, Media3, [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5), and [SponsorBlock](https://sponsor.ajay.app/).

## Ads and sponsors

Glidvio resolves video content streams with the pinned NewPipe Extractor `v0.26.5` and plays them directly through Media3. It has no embedded YouTube player or advertising SDK and does not request or insert YouTube advertising breaks.

SponsorBlock supplies timestamps for promotions included in the video itself. Glidvio checks these before starting playback, then automatically seeks past matching reported intervals, including after seeking into one. Sponsors, self promotion, and subscribe/like reminders are enabled by default. Settings also offer intros, outros, previews/recaps, non music sections, and filler. Undo returns to the position before a skip and permits that interval to play for the current video.

**Unreported sponsor segments can remain.** Community reports do not establish that a video contains no promotions. Each video shows whether reports were found, automatic skipping is off, or SponsorBlock could not be reached. If a SponsorBlock lookup fails while any skip category is enabled, playback is held with a visible error and Retry action. A successful lookup with no community reports can play; it is not presented as confirmed sponsor-free coverage.

## Features

- Search YouTube videos and load additional result pages.
- Browse YouTube music charts and topic searches.
- Paste a YouTube URL or video ID, open supported YouTube links, or share a link into Glidvio.
- Native video and audio playback, including separate adaptive streams, with fullscreen landscape viewing.
- Playback speeds from 0.5× to 2×, captions when available, and Android picture-in-picture.
- Save videos for later and keep a local watch history of the last 200 videos; history can be cleared in Settings.
- Per-category sponsor skipping, skip status, session time saved, and Undo.
- Share the original video link through Android's share sheet.

Glidvio does not require an account. Account-only, private, age-restricted, region-restricted, or unavailable videos may not play. Extractor compatibility depends on YouTube's current responses. There is no background audio playback service; playback pauses when the activity stops outside picture-in-picture. Saved videos are bookmarks, not offline downloads.

## Build and install

Requirements:

- Android 8.0 / API 26 or newer to run the app.
- JDK 17 or 21.
- Android SDK 36, with platform tools and the build tools selected by the Android Gradle plugin.
- Android SDK location configured through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `local.properties` (`sdk.dir=...`).

The project pins Gradle 8.13, Android Gradle Plugin 8.13.2, and Kotlin 2.2.21. The wrapper downloads Gradle; Google Maven, Maven Central, and JitPack supply dependencies. No YouTube API key is required.

```sh
./scripts/check.sh
```

This assembles the debug APK, runs JVM unit tests, and runs Android lint. On a Mac with Homebrew OpenJDK 21 installed at the standard Apple Silicon path, the script selects it automatically; otherwise it uses the current Java environment.

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug APK uses a development signing certificate. To install on a selected emulator or an authorized USB/wireless debugging device:

```sh
adb devices -l
export ANDROID_SERIAL='your-device-serial'
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
```

With wireless debugging, complete Android's pairing/connect flow first, then use the connected serial displayed by `adb devices -l`.

## Live verification

The network-dependent instrumentation suite exercises real search pagination, playback extraction and media bytes, moving decoded video, sponsor skips during playback and after seeking, Undo, fullscreen, picture-in-picture, and saving a video. It installs and launches the app on the selected device, so it can interrupt current playback.

```sh
# Emulator; emulator-5554 is the script's default.
ANDROID_SERIAL=emulator-5554 ./scripts/live-test.sh

# Explicitly selected physical phone.
ANDROID_SERIAL='your-device-serial' ./scripts/live-test.sh --allow-physical-device
```

These are commands for running verification, not a claim that every listed check has passed on every device. The live suite needs reachable YouTube media and SponsorBlock services, plus a community-covered test video. No finite sample establishes universal sponsor coverage.

## Data and network use

Searches, public metadata, captions, thumbnails, and video/audio content are fetched from YouTube and its Google media/image hosts. Those services receive the requests needed to find and play content, including search terms or requested video identifiers, and normal connection information such as the device's public IP address. There is no Glidvio backend or account synchronization.

SponsorBlock requests go to `sponsor.ajay.app` using the first four hexadecimal characters of the video's SHA-256 hash, together with requested categories and the `skip` action. The client matches the full video ID against returned buckets locally. It does not submit segments, votes, or watched-segment reports. See [SponsorBlock's API documentation](https://wiki.sponsor.ajay.app/w/API_Docs) and [privacy policy](https://sponsor.ajay.app/privacy/).

Saved video metadata, watch history, and skipping preferences are stored in the app's private on-device preferences. Android cloud backup is disabled. Glidvio has no advertising or analytics SDK and stores no signed-in account cookies. It requires internet and network-state permissions.

## License and credits

Glidvio is licensed under **GNU GPL version 3 or later**. See [LICENSE](LICENSE). NewPipe Extractor is also GPL-3.0-or-later. SponsorBlock segment data is credited to Ajay Ramachandran and the SponsorBlock community under **CC BY-NC-SA 4.0**; see the [database and API license](https://github.com/ajayyy/SponsorBlock/wiki/Database-and-API-License).

Dependencies and their source/license links are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Copies of the GPL and third-party notices are bundled as application assets. YouTube media and thumbnail rights remain with their respective owners. Glidvio is an independent project and is not affiliated with YouTube or Google.
