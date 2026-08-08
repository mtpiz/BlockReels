package app.blockreels.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blockreels.R
import app.blockreels.detect.Detectors
import app.blockreels.dump.FixtureLabels
import java.io.File

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isServiceEnabled: () -> Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onShareDumps: (List<File>) -> Unit,
    onSaveDumps: (List<File>) -> Unit,
) {
    val enabledPackages by viewModel.enabledPackages.collectAsStateWithLifecycle()
    val dumpMode by viewModel.dumpMode.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val dumps by viewModel.dumps.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(true) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsAllowed = granted }

    // The service toggle, the notification grant and the dump files all change outside this
    // process, so re-read them whenever the user comes back from Settings or from dumping.
    LifecycleResumeEffect(Unit) {
        serviceEnabled = isServiceEnabled()
        notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
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
                            onCheckedChange = { enabled ->
                                // The notification *is* the dump trigger, so without this
                                // grant the whole feature is silently dead on Android 13+.
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermission.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                                viewModel.setDumpMode(enabled)
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.dump_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The Dump action is posted by the accessibility service, so with the
                    // service off, switching dump mode on appears to do nothing at all.
                    if (dumpMode && !serviceEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.dump_needs_service),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (dumpMode && !notificationsAllowed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.notifications_blocked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (dumpMode && serviceEnabled && notificationsAllowed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.dump_where),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
            item {
                // Saving beats sharing for the actual job: GitHub's upload form is a file
                // picker, and a picker can reach Downloads but not a share sheet.
                Button(
                    onClick = { onSaveDumps(dumps) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dump_save_all, dumps.size))
                }
            }
            item {
                TextButton(
                    onClick = { onShareDumps(dumps) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dump_share_all, dumps.size))
                }
            }
            item {
                Text(
                    stringResource(R.string.dump_label_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(dumps, key = { it.absolutePath }) { file ->
                DumpRow(
                    file = file,
                    onLabel = { label -> viewModel.labelDump(file, label.fileName) },
                    onShare = { onShareDumps(listOf(file)) },
                )
                HorizontalDivider()
            }
            item {
                TextButton(onClick = viewModel::clearDumps) {
                    Text(stringResource(R.string.dumps_clear))
                }
            }
        }

        item {
            // An update that silently failed to install looks exactly like one that
            // worked, so name the build that is actually running.
            Text(
                text = stringResource(R.string.build_version, buildVersion(context)),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
            )
        }
    }
}

private fun buildVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
}.getOrDefault("?")

/**
 * A capture is only usable as a fixture once its filename encodes the expected verdict, so
 * the name is chosen from the known surfaces rather than typed — there is no good way to
 * rename a file on a phone.
 */
@Composable
private fun DumpRow(
    file: File,
    onLabel: (FixtureLabels.Label) -> Unit,
    onShare: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val labelled = FixtureLabels.forFileName(file.name)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = labelled?.display ?: stringResource(R.string.dump_unlabelled),
                style = MaterialTheme.typography.bodyMedium,
                color = if (labelled == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text(stringResource(R.string.dump_label))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                FixtureLabels.all.forEach { label ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label.display + if (label.blocks) "  (block)" else "  (allow)",
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onLabel(label)
                        },
                    )
                }
            }
        }

        TextButton(onClick = onShare) {
            Text(stringResource(R.string.dump_share))
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
