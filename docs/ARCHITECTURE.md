# Architecture

## Dependency rule

```
app (Kotlin, Android)  ->  core (Java 11, no Android)
```

`core` never imports Android. Everything Android-specific (PackageManager, ContentResolver, Compose) is in `app`
and converts to plain data (`PackageFacts`, `DeviceFacts`, `ApkSource`) before calling the core. This keeps every
parser testable on a JDK and makes the phase-2 provisioning code swappable.

## Core packages

| package | job |
|---|---|
| `io` | `SeekableSource` (random-access bytes), file/channel/slice/in-memory implementations |
| `zip` | central-directory reader (ZIP64, stored/deflate); never extracts to disk |
| `apk` | `ApkInspector` (entries, ABIs, signing block, runtime markers), `ApkSource` |
| `axml` | Android binary XML parser and `ManifestFacts` |
| `dotnet` | `AssemblyLayoutProbe`: loose DLLs / `assemblies.blob` stores / ELF-wrapped stores; headers only |
| `version` | ports of SMAPI's `SemanticVersion` and `GameVersion` (including the fourth "platform release" number) |
| `json` | `LenientJson`: comments, trailing commas, curly-quote retry, as SMAPI tolerates in manifests |
| `compat` | `CompatDatabase` (generated JSON) and `CompatResolver` (never says "compatible") |
| `detect` | `DetectionPipeline` and the report model (`InstallationReport`, `Finding`, `ProbeResult`...) |
| `diag` | bounded `DiagnosticLog` and the plain-text `ReportFormatter` |

## Principles encoded in the code

- **Probe, do not assume.** Opening an APK is an `ApkSource.open()` that may throw; the exception text is stored as a
  `ProbeResult` and surfaces as a finding. Fallback is the Storage Access Framework (`SafApkSource`).
- **Unknown is a value.** Assembly presence is `PRESENT`, `ABSENT` or `UNKNOWN` (when the layout hides names).
- **Verification levels.** Every compatibility row is `DECLARED_BY_SMAPI_SOURCE`, `DOCUMENTED_UNVERIFIED` or
  `VERIFIED_ON_DEVICE`; only a human promotes a row, and none is promoted today.
- **No permissions.** Visibility comes from a `<queries>` list, not `QUERY_ALL_PACKAGES`.

## Phase 2 seam

Provisioning (placing SMAPI) will sit behind an interface in `core` (inputs: `InstallationReport`, a verified
compat row; outputs: a plan with steps, backups and a rollback), with Android implementations in `app`. Which
strategy it implements depends on what the probe reports from a real device; see the feasibility report.
