package com.universalsmapi.launcher.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalsmapi.core.detect.Finding
import com.universalsmapi.core.detect.InstallationReport
import com.universalsmapi.core.detect.PackageFacts
import com.universalsmapi.launcher.LauncherViewModel

private enum class Tone { Ok, Warn, Bad, Muted }

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.Ok -> LocalStatusColors.current.ok
    Tone.Warn -> LocalStatusColors.current.warn
    Tone.Bad -> LocalStatusColors.current.bad
    Tone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.inspectPicked(uri)
    }
    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.saveReport(uri)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header() }
            item {
                Actions(
                    onRescan = { vm.scan() },
                    onPick = { pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) },
                    onShare = { shareText(context, vm.reportText()) },
                    onSave = { saveReport.launch("smapi-launcher-detection-report.txt") },
                    busy = state.scanning || state.pickedBusy,
                )
            }
            state.error?.let { msg -> item { Banner(msg, Tone.Bad) } }
            state.notice?.let { msg -> item { Banner(msg, Tone.Ok) } }

            if (state.scanning) {
                item { Busy("Looking for Stardew Valley…") }
            } else if (state.reports.isEmpty()) {
                item { NotFoundCard(state.missing) }
            }
            items(state.reports) { report -> InstallationCard(report, vm) }

            if (state.pickedBusy) item { Busy("Reading the picked file…") }
            state.picked?.let { report ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Picked file", style = MaterialTheme.typography.titleMedium)
                        InstallationCard(report, vm)
                    }
                }
            }
            if (state.existingLaunchers.isNotEmpty()) item { OtherLaunchers(state.existingLaunchers) }
            item { Footer() }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("SMAPI Launcher", style = MaterialTheme.typography.titleLarge)
        Text("Detection prototype", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Banner(
            "Detection only. This build reads package metadata and APK structure. It does not install, patch or run SMAPI, " +
                "and nothing it reports has been tested with SMAPI.",
            Tone.Muted,
        )
    }
}

@Composable
private fun Actions(onRescan: () -> Unit, onPick: () -> Unit, onShare: () -> Unit, onSave: () -> Unit, busy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRescan, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Rescan") }
            OutlinedButton(onClick = onPick, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Pick APK…") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share report") }
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save report…") }
        }
    }
}

@Composable
private fun Banner(message: String, tone: Tone) {
    val color = toneColor(tone)
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, color = if (tone == Tone.Muted) MaterialTheme.colorScheme.onBackground else color,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun Busy(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NotFoundCard(missing: List<String>) {
    SimpleCard {
        Text("Stardew Valley was not found", style = MaterialTheme.typography.titleMedium)
        Text(
            "Either it is not installed, or Android is hiding it from this app. On Android 11 and later an app can only see " +
                "packages it declares up front, and this build declares: ${missing.joinToString()}. " +
                "You can still analyse an APK file you pick yourself.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun OtherLaunchers(launchers: List<PackageFacts>) {
    SimpleCard {
        Text("Another SMAPI launcher is installed", style = MaterialTheme.typography.titleMedium)
        for (p in launchers) {
            Text("${p.packageName}  ${p.versionName ?: ""}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        Text("It is only reported here, to help explain conflicts. This app never touches it.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Footer() {
    Text(
        "No permissions are requested. APK access is tried through the path Android reports for the installed game; if that " +
            "is refused, the exact error is shown and you can pick the APK yourself.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SimpleCard(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun InstallationCard(report: InstallationReport, vm: LauncherViewModel) {
    var expanded by rememberSaveable(report.createdAtMillis) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(report.title(), style = MaterialTheme.typography.titleMedium)
            report.pkg?.let { Text(it.packageName, style = MaterialTheme.typography.bodySmall, color = muted) }

            Text(report.gameVersion?.display() ?: "Version not understood", style = MaterialTheme.typography.headlineMedium)
            report.gameVersionSource?.let { Text("from $it", style = MaterialTheme.typography.bodySmall, color = muted) }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            FactRow("Installed by", installedByText(report))
            FactRow("Architecture", architectureText(report.architecture))
            FactRow("APK access", apkAccessText(report), if (report.apks.any { !it.ok() }) Tone.Bad else Tone.Ok)
            FactRow("Assemblies", assembliesText(report))
            FactRow("SMAPI", report.compat.headline(), Tone.Warn)

            val important = report.findings.filter { it.severity != Finding.Severity.INFO }
            if (important.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                for (f in important.take(6)) FindingRow(f)
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide technical details" else "Show technical details")
            }
            if (expanded) {
                val text = remember(report) { vm.detailText(report) }
                SelectionContainer {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, tone: Tone = Tone.Muted) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (tone == Tone.Muted) MaterialTheme.colorScheme.onSurface else toneColor(tone),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FindingRow(f: Finding) {
    val tone = if (f.severity == Finding.Severity.ERROR) Tone.Bad else Tone.Warn
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (tone == Tone.Bad) "Problem" else "Note",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = toneColor(tone),
            modifier = Modifier.width(92.dp),
        )
        Text(f.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun shareText(context: Context, text: String) {
    val limit = 180_000
    val body = if (text.length > limit) text.take(limit) + "\n...(truncated for sharing; use Save report for the full text)" else text
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "SMAPI Launcher detection report")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(send, "Share report"))
}
