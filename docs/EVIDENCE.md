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
