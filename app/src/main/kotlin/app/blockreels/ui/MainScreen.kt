package app.blockreels.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blockreels.R
import app.blockreels.detect.Detectors
import java.io.File

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isServiceEnabled: () -> Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onShareDump: (File) -> Unit,
) {
    val enabledPackages by viewModel.enabledPackages.collectAsStateWithLifecycle()
    val dumpMode by viewModel.dumpMode.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val dumps by viewModel.dumps.collectAsStateWithLifecycle()

    var serviceEnabled by remember { mutableStateOf(false) }

    // Both the service toggle and the dump files change outside this process, so re-read
    // them whenever the user comes back from Settings or from dumping a screen.
    LifecycleResumeEffect(Unit) {
        serviceEnabled = isServiceEnabled()
        viewModel.refreshDumps()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }

        item { SectionHeader(stringResource(R.string.section_permissions)) }
        item { AccessibilityCard(serviceEnabled, onOpenAccessibilitySettings) }

        item { SectionHeader(stringResource(R.string.section_blocking)) }
        items(Detectors.all, key = { it.packageName }) { detector ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(detector.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                detector.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = detector.packageName in enabledPackages,
                            onCheckedChange = {
                                viewModel.setPackageEnabled(detector.packageName, it)
                            },
                        )
                    }
                    if (!detector.verified) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.unverified_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.blocked_count, blockedCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.blocked_count_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item { SectionHeader(stringResource(R.string.section_dumps)) }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.dump_mode_toggle),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = dumpMode,
                            onCheckedChange = viewModel::setDumpMode,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.dump_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (dumps.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.dumps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(dumps, key = { it.absolutePath }) { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onShareDump(file) }) {
                        Text(stringResource(R.string.dump_share))
                    }
                }
                HorizontalDivider()
            }
            item {
                TextButton(onClick = viewModel::clearDumps) {
                    Text(stringResource(R.string.dumps_clear))
                }
            }
        }
    }
}

@Composable
private fun AccessibilityCard(enabled: Boolean, onOpenSettings: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    if (enabled) R.string.accessibility_enabled else R.string.accessibility_disabled,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            if (!enabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.accessibility_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.restricted_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.accessibility_cta))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}
