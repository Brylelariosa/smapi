package com.universalsmapi.launcher.platform

import android.content.Context
import android.net.Uri
import com.universalsmapi.core.compat.CompatDatabase
import com.universalsmapi.core.detect.DetectionPipeline
import com.universalsmapi.core.detect.DeviceFacts
import com.universalsmapi.core.detect.InstallationReport
import com.universalsmapi.core.detect.PackageFacts
import com.universalsmapi.core.detect.ProbeResult
import com.universalsmapi.core.diag.DiagnosticLog
import com.universalsmapi.core.diag.ReportFormatter
import com.universalsmapi.launcher.BuildConfig
import com.universalsmapi.launcher.KnownPackages

/** Result of scanning the device. */
data class ScanResult(
    val device: DeviceFacts,
    val reports: List<InstallationReport>,
    /** Known package ids that were not found (not installed, or hidden from this app). */
    val missing: List<String>,
    val existingLaunchers: List<PackageFacts>,
)

/** Wires the Android gateway to the core pipeline. All calls are blocking; run them off the main thread. */
class DetectionRepository(private val context: Context) {
    val log = DiagnosticLog()
    private val gateway = AndroidPackageGateway(context)
    private val pipeline by lazy { DetectionPipeline(CompatDatabase.loadBundled()) }

    fun scan(): ScanResult {
        val device = gateway.deviceFacts()
        log.info(TAG, "scan started on API ${device.sdkInt}, 64-bit=${device.has64Bit()}")
        val reports = mutableListOf<InstallationReport>()
        val missing = mutableListOf<String>()
        for (name in KnownPackages.stardewValley) {
            val pkg = gateway.readPackage(name, log)
            if (pkg == null) {
                missing += name
                continue
            }
            val probes = mutableListOf<ProbeResult>()
            probes += ProbeResult.ok("package-visibility", "getPackageInfo succeeded for $name (declared in <queries>)")
            probes += gateway.accessProbes(pkg)
            reports += pipeline.inspect(pkg, device, gateway.apkInputs(pkg), probes, log)
        }
        val others = KnownPackages.existingLaunchers.mapNotNull { gateway.readPackage(it, log) }
        log.info(TAG, "scan finished: ${reports.size} installation(s), ${missing.size} missing, ${others.size} other launcher(s)")
        return ScanResult(device, reports, missing, others)
    }

    fun inspectPicked(uri: Uri): InstallationReport {
        val device = gateway.deviceFacts()
        val probes = mutableListOf<ProbeResult>()
        probes += ProbeResult.info("saf-pick", "user selected $uri")
        val inputs = listOf(DetectionPipeline.ApkInput("picked", SafApkSource(context.contentResolver, uri)))
        return pipeline.inspect(null, device, inputs, probes, log)
    }

    fun reportText(device: DeviceFacts?, reports: List<InstallationReport>): String =
        ReportFormatter.format(BuildConfig.VERSION_NAME, device, reports, log)

    /** Installation-only text (no global event log), for the per-card details view. */
    fun detailText(device: DeviceFacts?, report: InstallationReport): String =
        ReportFormatter.format(BuildConfig.VERSION_NAME, device, listOf(report), DiagnosticLog())

    private companion object {
        const val TAG = "repository"
    }
}
