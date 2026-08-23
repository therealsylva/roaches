# IJM payload recovery

## Finding

The phone build is protected by an Ijiami/IJM loader. APKTool can decode its
resources and manifest, but the visible DEX contains only four shell classes.
The real `com.transsion.*` application code is decrypted and loaded at
runtime.

Static edits to the manifest alone are unsafe: the hidden payload still
initializes SDKs and references declared components. Deleting those declarations
before recovering and patching their call sites can cause startup, playback, or
callback failures.

## Recovery sequence

1. Run the exact pinned APK in an isolated Android environment with a compatible
   ARM translation/native runtime.
2. Allow the IJM application shell to load the protected payload.
3. Capture all loaded DEX objects and validate their headers, maps, class
   definitions, and checksums.
4. Deduplicate and order the recovered DEX files.
5. Decompile for dependency mapping and retain smali as the patching source of
   truth.
6. Rebuild an unprotected baseline before changing advertising or telemetry.
7. Compare launch and core feature behaviour with the pinned upstream.

Only after the unprotected baseline passes does component removal begin.

## Patch order

1. Neutralize advertising initialization and presentation/callback gates.
2. Remove advertising components, resources, and SDK code.
3. Disable telemetry/attribution initialization and upload paths.
4. Remove associated components, permissions, resources, and SDK code.
5. Remove optional subsystems one at a time with regression tests between
   changes.

Player, codec, subtitle, and downloader native libraries remain untouched until
the final dependency map proves one is unused.
