package es.saniexam.app.presentation.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.saniexam.app.R

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportOkMsg = stringResource(id = R.string.settings_export_success)
    val importOkMsg = stringResource(id = R.string.settings_import_success)
    val undoneMsg = stringResource(id = R.string.settings_import_undone)
    val errChecksumMsg = stringResource(id = R.string.settings_err_checksum)
    val errSchemaMsg = stringResource(id = R.string.settings_err_schema)
    val errMalformedMsg = stringResource(id = R.string.settings_err_malformed)
    val errNothingMsg = stringResource(id = R.string.settings_err_nothing_to_undo)
    val errOtherMsg = stringResource(id = R.string.settings_other_error)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val msg = when (event) {
                is SettingsUiState.OneShotEvent.ExportSuccess -> exportOkMsg
                is SettingsUiState.OneShotEvent.ImportSuccess -> importOkMsg
                is SettingsUiState.OneShotEvent.ImportUndone -> undoneMsg
                is SettingsUiState.OneShotEvent.Error -> when (event.reason) {
                    SettingsUiState.Reason.ChecksumMismatch -> errChecksumMsg
                    SettingsUiState.Reason.UnsupportedSchemaVersion -> errSchemaMsg
                    SettingsUiState.Reason.MalformedPayload -> errMalformedMsg
                    SettingsUiState.Reason.NothingToUndo -> errNothingMsg
                    SettingsUiState.Reason.Other -> errOtherMsg
                }
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.onEventConsumed()
        }
    }

    SettingsScreen(
        state = state,
        onBack = onBack,
        onExportRequested = viewModel::onExportRequested,
        onImportPicked = viewModel::onImportPicked,
        onImportConfirmed = viewModel::onImportConfirmed,
        onImportCancelled = viewModel::onImportCancelled,
        onUndoLastImport = viewModel::onUndoLastImport,
    )
}
