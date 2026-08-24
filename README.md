# Roaches

Roaches is an original, source-built Android client for browsing and playing
community-indexed film and television streams. It is intentionally free of
advertising SDKs, analytics, attribution code, accounts, subscriptions and
paywalls.

The application is not affiliated with MovieBox or Netflix. Netflix is used
only as a product-quality reference for content hierarchy and playback polish;
Roaches has its own name, icon, interface and implementation.

## Product surface

- artwork-led Home with a rotating feature, editorial shelves and genre browsing;
- search with debounced live results;
- film and series details, seasons and episodes;
- stream, quality, audio and external subtitle selection;
- native Media3/ExoPlayer playback, speed, crop, swipe controls and picture-in-picture;
- saved, liked, recents and continue-watching state stored on device;
- private app-scoped downloads with progress tracking and offline playback;
- local video import through Android's document picker;
- dark and light themes, audio preference and verified in-app updates;
- adaptive phone, foldable and tablet layouts;
- no telemetry, ads, login or premium gates.

## Build

Requirements: JDK 17 and Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

The installable APK is written to
`app/build/outputs/apk/debug/roaches-debug.apk`. GitHub Actions also publishes
the APK and verification reports on every pull request.

## Signed releases

The `Roaches release` workflow publishes a signed APK for version tags. Add
`ROACHES_KEYSTORE_BASE64`, `ROACHES_STORE_PASSWORD`, `ROACHES_KEY_ALIAS` and
`ROACHES_KEY_PASSWORD` as repository secrets before running it. Keeping that
signing key stable is required for Android to install future releases as
updates over an existing installation. Roaches verifies each release checksum
and package identity before handing it to Android's system installer; Android
always asks the user to approve installation. Developer debug builds use a
separate package and require a one-time move to the first stable release.

## Quality contract

The interface is governed by [DESIGN.md](DESIGN.md). The CI pipeline treats
design-token drift, prohibited UI patterns, tests, lint and APK assembly as
release gates rather than optional polish.

## Upstream protocol attribution

The clean-room request signing and endpoint behaviour were independently
ported from the open-source MovieBox-TUI provider. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
