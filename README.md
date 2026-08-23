# Roaches

Roaches is an original, source-built Android client for browsing and playing
community-indexed film and television streams. It is intentionally free of
advertising SDKs, analytics, attribution code, accounts, subscriptions and
paywalls.

The application is not affiliated with MovieBox or Netflix. Netflix is used
only as a product-quality reference for content hierarchy and playback polish;
Roaches has its own name, icon, interface and implementation.

## Product surface

- artwork-led Discover home with editorial shelves;
- search with debounced live results;
- film and series details, seasons and episodes;
- stream, quality, audio and external subtitle selection;
- native Media3/ExoPlayer playback, landscape and picture-in-picture;
- watchlist, recents and continue-watching state stored on device;
- private app-scoped downloads with progress tracking;
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

## Quality contract

The interface is governed by [DESIGN.md](DESIGN.md). The CI pipeline treats
design-token drift, prohibited UI patterns, tests, lint and APK assembly as
release gates rather than optional polish.

## Upstream protocol attribution

The clean-room request signing and endpoint behaviour were independently
ported from the open-source MovieBox-TUI provider. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
