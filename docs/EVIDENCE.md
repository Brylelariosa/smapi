# Evidence log

Facts observed by the detection prototype on real devices. Each entry is what the app reported, not an interpretation.

## Device 1: Infinix X675, Android 11 (API 30), app 0.1.0-detect, report of 2026-11-12

| Area | Observed |
|---|---|
| ABIs | arm64-v8a, armeabi-v7a, armeabi (64-bit supported) |
| Package | `com.chucklefish.stardewvalley` 1.6.15.0, versionCode 239, minSdk 21, targetSdk 34 |
| Install source | `com.google.android.packageinstaller` (installed from a file, not a store); no splits |
| Signing | APK v1 + v2 + v3; signer SHA-256 `68fa4c0d66be5497d2c7909962f7aba369107f0695c6e213530807c576e0cb61` |
| Package visibility | Worked with `<queries>` only; no permissions requested |
| Reading the installed `base.apk` | Worked without any permission: 395,807,350 bytes, 3,714 ZIP entries parsed |
| Picking the same APK through the file picker | Worked (Downloads provider); same size and entry count |
| Another app's `Android/data` | `exists=false canRead=false` (inconclusive: the folder may not exist yet) |
| ABIs packaged | arm64-v8a, armeabi-v7a, x86, x86_64 in one universal APK |
| Runtime | Mono for Android: `libmonodroid.so`, `libmonosgen-2.0.so`, `libSystem.Native.so`, `libxamarin-app.so`; `extractNativeLibs=true`; 75 files in `lib/arm64` |
| Content | 3,556 files (343.7 MB) under `assets/Content/`; no OBB involved |
| Launcher activity | `com.chucklefish.stardewvalley.MainActivity` (a plain class name, no generated hash) |
| Manifest permissions | ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, INTERNET, VIBRATE |
| Assembly layout | `assemblies/assemblies.blob` (5.3 MB, store version 1, id 0, 58 local / 65 global entries) plus one 1.2 MB store per ABI (id 1, 7 entries each) and `assemblies.manifest` listing 65 names |
| SMAPI's referenced assemblies | present: Stardew Valley, StardewValley.GameData, BmFont, Lidgren.Network, MonoGame.Framework, xTile; absent: GalaxyCSharp, SkiaSharp |
| Other SMAPI launcher installed | none seen (`abc.smapi.gameloader` not found) |

Open questions the 0.2.0 report answers: which assemblies are LZ4-compressed and how large each is; the full list of
manifest component names; the names of the 75 arm64 native libraries.

## Device 1, second report (app 0.2.0-detect, 2026-11-14)

| Area | Observed |
|---|---|
| Scan cost | About 0.3 s for the whole 377 MB APK, including decoding five assembly stores; no warnings or errors in the log |
| Assembly store | Version-1 layout confirmed: descriptors at offset 20, 24 bytes each, all 65 validated; every payload starts with `XALZ` (LZ4) |
| Assemblies | 65, all LZ4-compressed; 6.5 MB stored, 16.3 MB expanded. Shared store: 58; per-ABI store: 7 (`System.Private.CoreLib`, `System.Collections*`, `System.Linq`, `System.Net.Sockets`, `System.Runtime.Numerics`, `System.Text.RegularExpressions`) |
| Game assembly | Named `StardewValley` (no space): 3.0 MB stored, 8.9 MB expanded. Also `StardewValley.GameData`, `MonoGame.Framework`, `xTile`, `Lidgren.Network`, `BmFont`, `Mono.Android`, `Java.Interop`, `Xamarin.AndroidX.DocumentFile` |
| BCL | A trimmed set (65 assemblies in total); no `System.Reflection.Emit*`, `System.Text.Json` or `System.Collections.Immutable` assemblies are present |
| AOT | 65 `libaot-<assembly>.dll.so` images in `lib/arm64`, one for every assembly, so the managed code is precompiled |
| Other native libraries | `libmonodroid.so`, `libmonosgen-2.0.so`, `libxamarin-app.so`, `libmono-component-marshal-ilgen.so`, `libSystem.Native.so`, `libSystem.IO.Compression.Native.so`, `libSystem.Security.Cryptography.Native.Android.so`, `libopenal32.so`, `liblwjgl_lz4.so`, `libstb_png_writer.so` |
| Native libraries in the APK | All stored compressed (`extractNativeLibs=true`); the installer extracted them to `lib/arm64` (75 files, listable by another app) |
| Runtime bootstrap | `application` is plain `android.app.Application`; the only provider is `mono.MonoRuntimeProvider`; one activity |
| Picked APK | Identical results through the file picker (same bytes, same stores) |

## Device 1, third report (app 0.3.0-detect, 2026-11-16): host probes

| Probe | Observed |
|---|---|
| Assembly extraction | 65 of 65 assemblies decompressed (LZ4) in memory and verified as managed PE images in 240 ms; 6.5 MB stored, 16.3 MB expanded; 0 failures. Every image is `i386/AnyCPU` (platform-neutral IL). `StardewValley` is 8.9 MB expanded |
| Reading the game's native libraries | `libmonosgen-2.0.so` (3,195,488 bytes), `libxamarin-app.so` (56,416 bytes) and `libaot-StardewValley.dll.so` (21,441,704 bytes) are readable and have ELF magic |
| Loading them from another app | `System.load` succeeded for all three on Android 11, with no permission requested |

What this does not show: that the Mono runtime can be started in another process (only loading was tested, not
initialisation), that `libmonodroid.so` (which has JNI entry points) can be loaded, or that any of it works on other devices.

Open questions the 0.4.0 plugin-path probes answer: whether `createPackageContext`, `DexClassLoader` and
`getResourcesForApplication` (all public APIs) can reach the game's Java classes and assets from this app, and the exact
signatures of the game's `mono.MonoPackageManager`, `mono.android.Runtime` and `mono.MonoRuntimeProvider` classes.

