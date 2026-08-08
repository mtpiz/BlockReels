package app.blockreels.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.blockreels.R
import app.blockreels.dump.DumpExporter
import app.blockreels.service.BlockReelsService
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this),
            ) {
                val vm: MainViewModel = viewModel()
                MainScreen(
                    viewModel = vm,
                    isServiceEnabled = ::isAccessibilityServiceEnabled,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onShareDumps = ::shareDumps,
                    onSaveDumps = ::saveDumpsToDownloads,
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun saveDumpsToDownloads(files: List<File>) {
        val saved = DumpExporter.saveToDownloads(this, files)
        Toast.makeText(
            this,
            getString(R.string.dump_saved, saved, DumpExporter.FOLDER),
            Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * Shares every capture in one go. Uploading to GitHub from a phone is tedious enough
     * without doing it six times, and the upload form takes a multi-select.
     */
    private fun shareDumps(files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { FileProvider.getUriForFile(this, "$packageName.dumps", it) })
        val send = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.dump_share_title)))
    }

    /**
     * There is no API to ask whether *our* accessibility service is running, so this reads
     * the same secure setting the system writes when the user flips the toggle.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, BlockReelsService::class.java).flattenToString()
        val enabled = AndroidSettings.Secure.getString(
            contentResolver,
            AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
