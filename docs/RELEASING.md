# Releasing Glidvio

Public downloads are signed universal APKs from [GitHub Releases](https://github.com/culpen90/Glidvio/releases). Version `1.0.0` uses version code `2`, package `com.glidvio.app`, Android 8.0 / API 26 or newer, and a dedicated release signing key. The release variant enables R8 code optimization, resource shrinking, and a non-debuggable manifest.

## Keep the signing identity

The maintainer's signing configuration lives outside the repository at `~/.config/glidvio/release-signing.properties`. `GLIDVIO_SIGNING_PROPERTIES` can select another absolute properties-file path. The properties file points to the private keystore and supplies its alias and passwords. Neither file belongs in Git, CI artifacts, or release uploads. Back up the keystore and credentials securely in a separate location before relying on them for updates; losing this key prevents ordinary updates for existing users.

Required Java-properties fields (replace the example values locally):

```properties
storeFile=/absolute/path/to/glidvio-release.p12
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

Restrict access to both files to their owner. Use absolute paths so invocation from another directory cannot select the wrong key. A present but incomplete properties file fails Gradle configuration.

The public `Glidvio-signing-certificate.pem` is safe to distribute. It contains no private key. The established public certificate is also committed at [`signing/release-certificate.pem`](../signing/release-certificate.pem). The verifier automatically rejects an APK whose signer SHA-256 fingerprint differs from that certificate. Signing a local build with another key creates an independently signed build that cannot update the official app in place or pass the official release verification gate.

## Build from source

Use JDK 21 and Android SDK platform 36 with build tools 36.0.0. Configure the SDK through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or the ignored `local.properties`. Dependencies and Gradle are downloaded by the checked-in wrapper; no YouTube API key is needed.

1. Check out the desired source tag or commit and inspect the changes.
2. Update `versionName` and monotonically increase `versionCode` in `app/build.gradle.kts` for a new release.
3. Configure the existing release signing identity outside the checkout.
4. Run the release script from the repository root:

```sh
./scripts/release.sh

# Optional alternate signing configuration:
GLIDVIO_SIGNING_PROPERTIES='/absolute/path/release-signing.properties' ./scripts/release.sh
```

The release script requires signing credentials and fails instead of producing a public unsigned APK. It builds, runs release unit tests and lint, then verifies and stages these files in the ignored `artifacts/release/` directory:

- `Glidvio.apk`
- `SHA256SUMS` (the APK's SHA-256 hash)
- `Glidvio-signing-certificate.pem`

`GLIDVIO_RELEASE_OUTPUT` can select an alternate staging directory. The verifier checks APK signatures against the established public certificate, ZIP/native-library alignment, the production package, non-debuggable status, minimum SDK, and bundled license contents. It also requires the APK's version code and name to match `app/build.gradle.kts`, preventing staging a stale version. It prints the APK hash and signing certificate fingerprint. Preserve the exact APK that passed validation for upload. Rebuilding with a different toolchain, dependency state, or signing identity is not a claim of byte-for-byte reproducibility.

For source checks without the private key:

```sh
./gradlew --no-daemon :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

Without signing configuration, Gradle may produce an **unsigned** release build for these checks. It is not a distributable, installable official release. GitHub Actions deliberately runs only these checks with read-only repository permissions and no signing secrets.

## Validate the exact release

- Require the release JVM tests, Android lint, build, APK signature verification, and APK alignment verification to pass.
- Inspect the APK package, version code/name, minimum SDK, and non-debuggable flag.
- Install and launch the signed APK on an explicitly selected emulator or authorized physical device. Run the network-dependent instrumentation suite against the release variant, including real search, decoded video, sponsor skips after seeking, Undo, fullscreen, picture-in-picture, and saved videos.
- Check that a second install of the same release key preserves the app's data. Record actual devices, commands, results, and service limitations in [VALIDATION.md](VALIDATION.md) or the versioned `ReleaseValidation.json` release asset it describes.

The release instrumentation variant can be selected with:

```sh
ANDROID_SERIAL='selected-device-serial' ./gradlew -Pglidvio.testBuildType=release :app:connectedReleaseAndroidTest
```

This installs and launches the app and can interrupt the device's current activity. Select a device you are authorized to use. A fresh install avoids overwriting a development install; Android rejects an update if its existing package has a different signing certificate. Do not uninstall an existing installation without preserving any data the user needs.

After validating the final signed `app/build/outputs/apk/release/app-release.apk`, verify and stage it again **without rebuilding**:

```sh
./scripts/release.sh --no-build
```

This option preserves the existing APK bytes; it does not run or replace the build, JVM tests, lint, or device checks. Version and signer checks still run. Compare its printed hash with the tested artifact before publishing; matching a version and certificate alone does not establish that a particular APK passed the device tests.

No finite playback sample establishes that all YouTube videos work or every sponsorship is reported. Release notes must describe automatic skipping of **community-reported** segments and disclose that unreported sponsor segments can remain. If SponsorBlock is unavailable while skipping is enabled, playback holds with a visible Retry action.

## Publish

1. Commit the reviewed source and validation procedure, push it, and confirm the Android workflow passes for that commit.
2. Create an annotated version tag such as `v1.0.0` at that exact commit and push the tag.
3. Create a GitHub release from the tag. Upload `Glidvio.apk`, `SHA256SUMS`, `Glidvio-signing-certificate.pem`, and the actual `ReleaseValidation.json` record; the source snapshot is available from the same tag.
4. Include Android 8.0+ installation instructions, the version, actual validation results, known limitations, and the development-certificate migration note from the README in the release notes.
5. Download the published APK and checksums independently, verify their hashes and signing certificate, and confirm the release is public with the intended version and assets.

The stable download URL is [releases/latest/download/Glidvio.apk](https://github.com/culpen90/Glidvio/releases/latest/download/Glidvio.apk). Keep the asset name `Glidvio.apk` for future releases.

On macOS, validate downloaded asset checksums from the directory containing them:

```sh
shasum -a 256 -c SHA256SUMS
```

On Linux, use `sha256sum -c SHA256SUMS`.
