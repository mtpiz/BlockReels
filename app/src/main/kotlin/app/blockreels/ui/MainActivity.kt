package app.blockreels.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    onShareDump = ::shareDump,
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun shareDump(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.dumps", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, file.name))
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
