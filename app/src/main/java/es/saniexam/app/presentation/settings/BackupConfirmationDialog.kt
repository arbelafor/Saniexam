package es.saniexam.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.saniexam.app.R

/**
 * Spec-mandated destructive-import confirm dialog (es-ES). The button
 * copy matches the `progress-backup` "Sí, reemplazar" / "Deshacer
 * importación" requirements; the body lists what will be replaced.
 *
 * The dialog is intentionally stateless — the [SettingsViewModel]
 * owns the `pendingImport` bytes and the apply decision.
 */
@Composable
fun BackupConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.settings_import_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(id = R.string.settings_import_dialog_body))
                Text(
                    text = stringResource(id = R.string.settings_import_dialog_will_replace),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.settings_import_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.settings_import_dialog_cancel))
            }
        },
    )
}
