package app.blockreels.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.blockreels.data.Settings
import app.blockreels.dump.DumpStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val dumpStore = DumpStore(app)

    val enabledPackages: StateFlow<Set<String>> =
        settings.enabledPackages.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val dumpMode: StateFlow<Boolean> =
        settings.dumpMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val blockedCount: StateFlow<Int> =
        settings.blockedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _dumps = MutableStateFlow<List<File>>(emptyList())
    val dumps: StateFlow<List<File>> = _dumps.asStateFlow()

    /** Dumps are written by the service, so the UI re-reads the directory on resume. */
    fun refreshDumps() {
        _dumps.value = dumpStore.list()
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) = viewModelScope.launch {
        settings.setPackageEnabled(packageName, enabled)
    }

    fun setDumpMode(enabled: Boolean) = viewModelScope.launch {
        settings.setDumpMode(enabled)
    }

    fun labelDump(file: File, fileName: String) {
        dumpStore.rename(file, fileName)
        refreshDumps()
    }

    fun clearDumps() {
        dumpStore.clear()
        refreshDumps()
    }
}
