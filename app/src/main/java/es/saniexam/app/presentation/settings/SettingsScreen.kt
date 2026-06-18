package es.saniexam.app.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.saniexam.app.R
import es.saniexam.app.domain.model.SubjectPack
import java.io.File
import java.io.FileOutputStream

/**
 * Stateless Settings surface. Renders the pack info, the export /
 * import buttons, the destructive-import dialog and the optional
 * "Deshacer importación" affordance when an undo is available.
 *
 * Export writes the bytes to the app-scoped files directory
 * (`context.filesDir/exports/`). The spec allows app-scoped storage
 * by default; a future PR can add a `FileProvider`-based share
 * intent if a "user-chosen export path" is required.
 *
 * Import uses the SAF `OpenDocument` contract so the user picks the
 * file from a system file picker. The picked bytes are passed back
 * to the ViewModel which sets a `pendingImport`; the dialog asks for
 * explicit confirmation before [BackupRepository.import] is called.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onExportRequested: () -> Unit,
    onImportPicked: (ByteArray) -> Unit,
    onImportConfirmed: () -> Unit,
    onImportCancelled: () -> Unit,
    onUndoLastImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        onImportPicked(bytes)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isWorking) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PackInfo(state.pack)
                ActionButtons(
                    canUndo = state.canUndo,
                    onExport = onExportRequested,
                    onImport = { importLauncher.launch(arrayOf("application/json")) },
                    onUndo = onUndoLastImport,
                )
            }
        }
    }

    if (state.pendingImport != null) {
        BackupConfirmationDialog(
            onConfirm = onImportConfirmed,
            onDismiss = onImportCancelled,
        )
    }

    // Persist the export bytes to app-scoped storage so the user can find
    // the file via a file manager (the spec allows app-scoped by default).
    state.lastExport?.let { last ->
        androidx.compose.runtime.LaunchedEffect(last) {
            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(dir, last.fileName)
            FileOutputStream(file).use { it.write(last.bytes) }
        }
    }
}

@Composable
private fun PackInfo(pack: SubjectPack?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pack?.let { stringResource(id = R.string.home_pack_id, it.id) }
                    ?: stringResource(id = R.string.settings_no_pack),
                style = MaterialTheme.typography.titleMedium,
            )
            pack?.let { p ->
                Text(
                    text = stringResource(id = R.string.home_pack_version, p.version),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(id = R.string.home_pack_attribution, p.sourceAttribution),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(id = R.string.home_pack_license, p.license),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    canUndo: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onUndo: () -> Unit,
) {
    OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(id = R.string.settings_export))
    }
    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(id = R.string.settings_import))
    }
    if (canUndo) {
        OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.settings_undo))
        }
    }
}
