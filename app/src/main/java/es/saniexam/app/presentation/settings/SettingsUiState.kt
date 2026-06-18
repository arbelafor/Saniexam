package es.saniexam.app.presentation.settings

import es.saniexam.app.domain.model.SubjectPack

/**
 * State of the Settings surface. The screen is read-only by default
 * and exposes two transient flows: the export confirmation
 * ([LastExport]) and the destructive import ([PendingImport]).
 */
data class SettingsUiState(
    val pack: SubjectPack? = null,
    val isWorking: Boolean = false,
    val pendingImport: PendingImport? = null,
    val lastExport: LastExport? = null,
    val canUndo: Boolean = false,
    val oneShot: OneShotEvent? = null,
) {
    /** Bytes the user picked from SAF; the dialog asks for explicit confirmation before applying. */
    data class PendingImport(val bytes: ByteArray, val sizeBytes: Int) {
        override fun equals(other: Any?): Boolean = other is PendingImport && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** Last successful export — carries the bytes so the user can save them via a system intent. */
    data class LastExport(val bytes: ByteArray, val fileName: String, val sizeBytes: Int) {
        override fun equals(other: Any?): Boolean =
            other is LastExport && bytes.contentEquals(other.bytes) && fileName == other.fileName
        override fun hashCode(): Int = 31 * bytes.contentHashCode() + fileName.hashCode()
    }

    /** Snackbars and toasts. Single-shot; the VM clears it after the screen consumes it. */
    sealed interface OneShotEvent {
        data class ExportSuccess(val fileName: String) : OneShotEvent
        data class ImportSuccess(val cardStatesRestored: Int) : OneShotEvent
        data class ImportUndone(val cardStatesRestored: Int) : OneShotEvent
        data class Error(val reason: Reason) : OneShotEvent
    }

    enum class Reason {
        ChecksumMismatch, UnsupportedSchemaVersion, MalformedPayload, NothingToUndo, Other,
    }
}
