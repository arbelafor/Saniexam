package es.saniexam.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.repository.BackupException
import es.saniexam.app.domain.repository.BackupRepository
import es.saniexam.app.domain.repository.DatasetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the Settings surface state. The screen is split into:
 *  - **passive** fields (pack attribution, "Última actualización");
 *  - **actions** (export, import, undo last import);
 *  - **one-shot events** (snackbars) emitted via [events].
 *
 * The destructive-import flow follows the spec: the user picks a file
 * → [SettingsUiState.PendingImport] is set → the screen renders the
 * confirmation dialog → on accept, the VM calls
 * [BackupRepository.import] (which takes an in-memory snapshot for
 * the session-scoped undo) → on success, an [ImportSuccess] event
 * is emitted; on failure, an [Error] event.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val datasetRepository: DatasetRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsUiState.OneShotEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val pack = datasetRepository.observeActivePacks().first().firstOrNull()
            _uiState.value = _uiState.value.copy(pack = pack, canUndo = backupRepository.canUndoLastImport())
        }
    }

    fun onExportRequested() {
        if (_uiState.value.isWorking) return
        _uiState.value = _uiState.value.copy(isWorking = true)
        viewModelScope.launch {
            val outcome = runCatching { withContext(io) { backupRepository.export() } }
            _uiState.value = _uiState.value.copy(isWorking = false)
            outcome.fold(
                onSuccess = { env ->
                    _uiState.value = _uiState.value.copy(
                        lastExport = SettingsUiState.LastExport(env.bytes, env.suggestedFileName, env.bytes.size),
                    )
                    _events.trySend(SettingsUiState.OneShotEvent.ExportSuccess(env.suggestedFileName))
                },
                onFailure = { t ->
                    _events.trySend(SettingsUiState.OneShotEvent.Error(reasonFor(t)))
                },
            )
        }
    }

    fun onImportPicked(bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(pendingImport = SettingsUiState.PendingImport(bytes, bytes.size))
    }

    fun onImportCancelled() {
        _uiState.value = _uiState.value.copy(pendingImport = null)
    }

    fun onImportConfirmed() {
        val pending = _uiState.value.pendingImport ?: return
        if (_uiState.value.isWorking) return
        _uiState.value = _uiState.value.copy(isWorking = true, pendingImport = null)
        viewModelScope.launch {
            val outcome = runCatching { withContext(io) { backupRepository.import(pending.bytes) } }
            _uiState.value = _uiState.value.copy(
                isWorking = false,
                canUndo = backupRepository.canUndoLastImport(),
            )
            outcome.fold(
                onSuccess = {
                    _events.trySend(SettingsUiState.OneShotEvent.ImportSuccess(cardStatesRestored = -1))
                },
                onFailure = { t -> _events.trySend(SettingsUiState.OneShotEvent.Error(reasonFor(t))) },
            )
        }
    }

    fun onUndoLastImport() {
        if (_uiState.value.isWorking) return
        _uiState.value = _uiState.value.copy(isWorking = true)
        viewModelScope.launch {
            val outcome = runCatching { withContext(io) { backupRepository.undoLastImport() } }
            _uiState.value = _uiState.value.copy(isWorking = false, canUndo = backupRepository.canUndoLastImport())
            outcome.fold(
                onSuccess = { _events.trySend(SettingsUiState.OneShotEvent.ImportUndone(cardStatesRestored = -1)) },
                onFailure = { t -> _events.trySend(SettingsUiState.OneShotEvent.Error(reasonFor(t))) },
            )
        }
    }

    fun onEventConsumed() { _uiState.value = _uiState.value.copy(oneShot = null) }

    private fun reasonFor(t: Throwable): SettingsUiState.Reason = when (t) {
        is BackupException -> when (t.reason) {
            BackupException.Reason.ChecksumMismatch -> SettingsUiState.Reason.ChecksumMismatch
            BackupException.Reason.UnsupportedSchemaVersion -> SettingsUiState.Reason.UnsupportedSchemaVersion
            BackupException.Reason.MalformedPayload -> SettingsUiState.Reason.MalformedPayload
            BackupException.Reason.NothingToUndo -> SettingsUiState.Reason.NothingToUndo
        }
        else -> SettingsUiState.Reason.Other
    }
}
