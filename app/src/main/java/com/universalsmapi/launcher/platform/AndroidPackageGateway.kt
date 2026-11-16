package com.universalsmapi.launcher.platform

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.universalsmapi.core.apk.FileApkSource
import com.universalsmapi.core.detect.DetectionPipeline
import com.universalsmapi.core.detect.DeviceFacts
import com.universalsmapi.core.detect.PackageFacts
import com.universalsmapi.core.detect.ProbeResult
import com.universalsmapi.core.diag.DiagnosticLog
import java.io.File
import java.security.MessageDigest

/**
 * The only class that talks to PackageManager. It converts Android objects into the pure-data types the core library
 * understands, and records what the OS actually allowed, so nothing about access is assumed.
 */
class AndroidPackageGateway(private val context: Context) {

    fun deviceFacts(): DeviceFacts = DeviceFacts(
        Build.VERSION.SDK_INT,
        Build.VERSION.RELEASE ?: "?",
        Build.MANUFACTURER ?: "?",
        Build.MODEL ?: "?",
        Build.SUPPORTED_ABIS.toList(),
        Build.SUPPORTED_64_BIT_ABIS.toList(),
        Build.SUPPORTED_32_BIT_ABIS.toList(),
    )

    /**
     * Read package metadata. Returns null when the package is not installed OR not visible to this app (Android 11+
     * package visibility makes those two cases indistinguishable by design).
     */
    fun readPackage(name: String, log: DiagnosticLog): PackageFacts? {
        val pm = context.packageManager
        val info = try {
            loadInfo(pm, name)
        } catch (e: PackageManager.NameNotFoundException) {
            log.info(TAG, "$name: not found. Either not installed, or hidden because it is not declared in <queries>.")
            return null
        } catch (e: RuntimeException) {
            log.error(TAG, "$name: PackageManager lookup failed", e)
            return null
        }
        val app = info.applicationInfo ?: run {
            log.warn(TAG, "$name: PackageInfo has no ApplicationInfo")
            return null
        }

        val b = PackageFacts.Builder()
        b.packageName = name
        b.label = app.loadLabel(pm).toString()
        b.versionName = info.versionName
        b.versionCode = info.longVersionCode
        b.firstInstallTime = info.firstInstallTime
        b.lastUpdateTime = info.lastUpdateTime
        b.sourceDir = app.sourceDir
        b.nativeLibraryDir = app.nativeLibraryDir
        b.minSdk = app.minSdkVersion
        b.targetSdk = app.targetSdkVersion
        b.enabled = app.enabled
        info.splitNames?.let { b.splitNames.addAll(it) }
        app.splitSourceDirs?.let { b.splitSourceDirs.addAll(it) }

        info.signingInfo?.let { si ->
            b.multipleSigners = si.hasMultipleSigners()
            for (sig in si.apkContentsSigners) {
                b.signerSha256.add(sha256Hex(sig.toByteArray()))
            }
        }
        try {
            val src = pm.getInstallSourceInfo(name)
            b.installerPackage = src.installingPackageName
            b.initiatingPackage = src.initiatingPackageName
        } catch (e: Exception) {
            log.warn(TAG, "$name: install source not available", e)
        }
        log.info(TAG, "$name: version=${b.versionName} code=${b.versionCode} installer=${b.installerPackage} splits=${b.splitNames}")
        return b.build()
    }

    /** One APK input per file the OS lists for this package (base first). */
    fun apkInputs(pkg: PackageFacts): List<DetectionPipeline.ApkInput> {
        val inputs = mutableListOf<DetectionPipeline.ApkInput>()
        pkg.sourceDir?.let { inputs += DetectionPipeline.ApkInput("base", FileApkSource(File(it))) }
        for (path in pkg.splitSourceDirs) {
            val role = "split:" + File(path).name.removePrefix("split_").removeSuffix(".apk")
            inputs += DetectionPipeline.ApkInput(role, FileApkSource(File(path)))
        }
        return inputs
    }

    /** Facts about file-level access, recorded as evidence and never used to decide anything. */
    @Suppress("DEPRECATION")
    fun accessProbes(pkg: PackageFacts): List<ProbeResult> {
        val out = mutableListOf<ProbeResult>()
        pkg.sourceDir?.let { path ->
            val f = File(path)
            out += ProbeResult.info(
                "base-apk-stat",
                "exists=${f.exists()} isFile=${f.isFile} length=${f.length()} canRead=${f.canRead()} path=$path",
            )
        }
        pkg.nativeLibraryDir?.let { path ->
            val f = File(path)
            val entries = try {
                f.list()?.size?.toString() ?: "not listable"
            } catch (e: SecurityException) {
                "SecurityException: ${e.message}"
            }
            out += ProbeResult.info("native-lib-dir", "exists=${f.exists()} entries=$entries path=$path")
        }
        val otherAppData = File(Environment.getExternalStorageDirectory(), "Android/data/${pkg.packageName}/files")
        out += ProbeResult.info(
            "other-app-external-data",
            "exists=${otherAppData.exists()} canRead=${otherAppData.canRead()} " +
                "(Android 11+ scoped storage is expected to hide another app's Android/data from this app)",
        )
        return out
    }

    /**
     * Opt-in probes: can this process read, and load, native libraries from the game's own library folder? System.load
     * runs a library's initialisers, so this is only called when the user asks for host probes. Only libraries without
     * JNI entry points (the Mono runtime, a data-only library and an AOT image) are tried.
     */
    fun nativeLoadProbes(pkg: PackageFacts): List<ProbeResult> {
        val dir = pkg.nativeLibraryDir
            ?: return listOf(ProbeResult.skipped("native-load", "no nativeLibraryDir was reported for the package"))
        val elf = byteArrayOf(0x7F, 0x45, 0x4C, 0x46)
        val out = mutableListOf<ProbeResult>()
        for (name in HOST_PROBE_LIBS) {
            val f = File(dir, name)
            val magic = try {
                f.inputStream().use { s -> ByteArray(4).also { b -> s.read(b) } }
            } catch (e: Exception) {
                null
            }
            out += ProbeResult.info(
                "native-read:$name",
                "exists=${f.exists()} length=${f.length()} canRead=${f.canRead()} elfMagic=${magic != null && magic.contentEquals(elf)}",
            )
            if (!f.exists()) continue
            try {
                System.load(f.absolutePath)
                out += ProbeResult.ok("native-load:$name", "System.load succeeded for ${f.absolutePath}")
            } catch (e: UnsatisfiedLinkError) {
                out += ProbeResult.failed("native-load:$name", "UnsatisfiedLinkError: ${e.message}")
            } catch (e: SecurityException) {
                out += ProbeResult.failed("native-load:$name", "SecurityException: ${e.message}")
            } catch (e: Throwable) {
                out += ProbeResult.failed("native-load:$name", "${e.javaClass.name}: ${e.message}")
            }
        }
        return out
    }

    private fun loadInfo(pm: PackageManager, name: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "android-gateway"
        val HOST_PROBE_LIBS = listOf("libmonosgen-2.0.so", "libxamarin-app.so", "libaot-StardewValley.dll.so")
    }
}
