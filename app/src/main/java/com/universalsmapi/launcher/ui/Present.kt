package com.universalsmapi.launcher.ui

import com.universalsmapi.core.compat.CompatResolver
import com.universalsmapi.core.detect.ArchitectureAssessment
import com.universalsmapi.core.detect.InstallationReport
import com.universalsmapi.core.dotnet.AssemblyLayout
import java.util.Locale

/** Plain-language wording for core-library results. Kept out of the composables so it is easy to review. */

internal fun architectureText(a: ArchitectureAssessment): String = when (a.verdict) {
    ArchitectureAssessment.Verdict.ARM64 -> "64-bit ARM (arm64-v8a)"
    ArchitectureAssessment.Verdict.ARM64_PACKAGED -> "64-bit ARM code is packaged; running ABI not reported"
    ArchitectureAssessment.Verdict.ARM32 -> "32-bit ARM (armeabi-v7a)"
    ArchitectureAssessment.Verdict.X86 -> "x86"
    ArchitectureAssessment.Verdict.UNKNOWN -> "Unknown"
}

internal fun installedByText(r: InstallationReport): String {
    val installer = r.pkg?.installerPackage ?: return "Not reported"
    return CompatResolver.storeLabel(installer) ?: installer
}

internal fun apkAccessText(r: InstallationReport): String {
    val primary = r.apks.firstOrNull { it.role == "base" || it.role == "picked" } ?: return "No APK path reported"
    val facts = primary.facts
    return if (facts != null) {
        "Readable: ${facts.entryCount} entries, ${formatBytes(primary.size)}"
    } else {
        "Not readable: ${primary.error ?: "unknown reason"}"
    }
}

internal fun assembliesText(r: InstallationReport): String {
    val l = r.assemblies ?: return "Not identified (no APK could be read)"
    val named = if (l.canListNames()) "" else "; names not listable"
    return when (l.kind) {
        AssemblyLayout.Kind.NONE -> "None found in the APKs that were read"
        AssemblyLayout.Kind.LOOSE_ASSEMBLIES -> "${l.looseAssemblyEntries.size} loose .dll files in ${r.assembliesFrom}"
        AssemblyLayout.Kind.STORE_BLOB -> {
            val mapped = l.entriesFor(r.architecture.installedAbi)
            if (mapped.isEmpty()) {
                "Assembly store (${l.stores.size} blob files) in ${r.assembliesFrom}$named"
            } else {
                "Assembly store: ${mapped.size} assemblies, ${mapped.count { it.isCompressed }} LZ4-compressed"
            }
        }
        AssemblyLayout.Kind.STORE_IN_ELF -> "Assembly store inside a native library in ${r.assembliesFrom}$named"
        AssemblyLayout.Kind.MIXED -> "Mixed layout in ${r.assembliesFrom}$named"
        AssemblyLayout.Kind.UNKNOWN -> "Unrecognised layout"
    }
}

internal fun formatBytes(n: Long): String {
    if (n < 0) return "unknown size"
    if (n < 1024) return "$n B"
    var v = n.toDouble()
    val units = arrayOf("KB", "MB", "GB")
    var i = -1
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return String.format(Locale.ROOT, "%.1f %s", v, units[i])
}
