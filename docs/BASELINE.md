# Upstream baseline

This baseline is pinned to the APK identified in `config/upstream.json`.

## Packaging

- APK size: 63,673,979 bytes.
- One 61,816,004-byte DEX is present, but it exposes only four IJM loader
  classes: `s.h.e.l.l.A`, `C`, `N`, and `S`.
- The protected payload is loaded by Ijiami assets and native code including
  `IJMDal.Data`, `libijmDataEncryption*.so`, and `ijm_lib/*/libexec*.so`.
- Native libraries are supplied for `arm64-v8a` and `armeabi-v7a`.

## Advertising and telemetry

The decoded manifest and resources identify:

| Category | Embedded systems |
| --- | --- |
| Advertising | Google Ads/Advertising ID, HiSavana, Pangle, MBridge/Mintegral, Vungle |
| Telemetry | Firebase Analytics, Performance, Crashlytics, Google Measurement/DataTransport, APM Insight |
| Configuration/push requiring review | Firebase Remote Config and Messaging |

Remote Config and Messaging are not automatically classified as removable:
their content and notification dependencies must be measured at runtime.

## Permission exposure

The upstream requests substantially more authority than basic media playback.
High-priority removal candidates include:

- advertising ID and AdServices attribution;
- precise/coarse location and media location;
- microphone and camera;
- calendar, accounts, and sync settings;
- package installation, system overlays, and settings modification;
- Wi-Fi management and nearby-device access;
- full-screen intent, battery-optimization bypass, and boot persistence;
- Transsion-specific data and push-provider permissions.

`config/target-permissions.txt` is the initial allowlist. A permission is
retained only when a preserved feature demonstrably requires it.

## Functional baseline contract

The cleanup must preserve functionality already accessible in the supplied
community build:

- launch and navigation;
- catalogue browsing, search, and details;
- streaming, seeking, audio selection, subtitles, and PiP;
- downloads, resume, and offline playback;
- favourites/history and existing account state;
- notifications when retained.

Existing accessible features are preserved. Server-side subscription
entitlements are outside the cleanup scope.
