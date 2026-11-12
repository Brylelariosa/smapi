# Universal SMAPI Launcher: detection prototype (phase 1)

An Android app that finds an installed Stardew Valley, reads what the system and the game's APKs reveal about it
(version, architecture, how managed assemblies are packaged) and says what it knows and does not know. It does
**not** install, patch or run SMAPI yet, and it requests **no permissions**.

> Status: the core library (`:core`) is unit-tested on a plain JDK. The Android shell (`:app`) has **not** been
> compiled or run on a device by its author. Its first CI build and first run on a real phone are the
> verification step. Nothing here is claimed to work with SMAPI.

## Build

GitHub Actions (`.github/workflows/android.yml`) runs the core tests and builds the debug APK; download it from
the run's artifacts. Locally (JDK 17, Android SDK 34):

```sh
gradle wrapper --gradle-version 8.9   # once, optional
./gradlew :core:test :app:assembleDebug
```

## Use it as a probe

1. Install the APK, open it, wait for the scan.
2. If the game is hidden or unreadable, the card says exactly why (the OS's own error text is kept).
3. **Share report** or **Save report...** and send it back. The report is the evidence phase 2 needs: how the real
   game packages its assemblies, which ABI it runs as, and what Android lets an unprivileged app read.
4. **Pick APK...** analyses an APK file you choose yourself (fallback when direct reading is refused).

## Layout

```
core/   pure Java 11 library, no Android dependency: zip, apk, axml, dotnet (assembly layout), version, json,
        compat (database + resolver), detect (pipeline), diag (log + report). Tests: core/src/test.
app/    Kotlin + Jetpack Compose shell: PackageManager gateway, SAF source, ViewModel, UI.
tools/  generate_compat_db.py: rebuilds compat-db.json from a SMAPI checkout or zip.
docs/   ARCHITECTURE.md
```

Refresh the compatibility data from a newer SMAPI:

```sh
python3 tools/generate_compat_db.py path/to/SMAPI-develop.zip \
  > core/src/main/resources/com/universalsmapi/core/compat/compat-db.json
```

## Limits worth knowing

- Parsers (ZIP, Android binary XML, assembly-store headers) are tested against fixtures built from the published
  formats and against JDK-written ZIPs. They have not seen the real Stardew Valley APK yet.
- The compatibility database lists desktop SMAPI releases from SMAPI's own notes. Android SMAPI ports are separate
  builds; none is catalogued, so the app reports "no Android build catalogued" rather than "compatible".
- On Android 11+ another app's `Android/data` (where the game keeps saves) is not readable by design; saves cannot
  be inspected or migrated by this app.

See `NOTICE.md` for licensing.
