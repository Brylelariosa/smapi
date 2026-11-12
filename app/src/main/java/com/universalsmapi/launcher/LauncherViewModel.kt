package com.universalsmapi.launcher

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universalsmapi.core.detect.DeviceFacts
import com.universalsmapi.core.detect.InstallationReport
import com.universalsmapi.core.detect.PackageFacts
import com.universalsmapi.launcher.platform.DetectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val scanning: Boolean = true,
    val device: DeviceFacts? = null,
    val reports: List<InstallationReport> = emptyList(),
    val missing: List<String> = emptyList(),
    val existingLaunchers: List<PackageFacts> = emptyList(),
    val picked: InstallationReport? = null,
    val pickedBusy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo by lazy { DetectionRepository(app) }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        _state.update { it.copy(scanning = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repo.scan() }
                _state.update {
                    it.copy(
                        scanning = false,
                        device = result.device,
                        reports = result.reports,
                        missing = result.missing,
                        existingLaunchers = result.existingLaunchers,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(scanning = false, error = "Scan failed: ${e.javaClass.simpleName}: ${e.message}") }
            }
        }
    }

    fun inspectPicked(uri: Uri) {
        _state.update { it.copy(pickedBusy = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val report = withContext(Dispatchers.IO) { repo.inspectPicked(uri) }
                _state.update { it.copy(pickedBusy = false, picked = report) }
            } catch (e: Exception) {
                _state.update { it.copy(pickedBusy = false, error = "Could not analyse that file: ${e.javaClass.simpleName}: ${e.message}") }
            }
        }
    }

    fun detailText(report: InstallationReport): String = repo.detailText(_state.value.device, report)

    fun reportText(): String {
        val s = _state.value
        return repo.reportText(s.device, s.reports + listOfNotNull(s.picked))
    }

    fun saveReport(uri: Uri) {
        viewModelScope.launch {
            val text = reportText()
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("The chosen location could not be opened for writing")
                }
                _state.update { it.copy(notice = "Report saved.") }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not save the report: ${e.javaClass.simpleName}: ${e.message}") }
            }
        }
    }
}
