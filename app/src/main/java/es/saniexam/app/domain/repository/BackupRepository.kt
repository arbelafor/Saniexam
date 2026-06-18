package es.saniexam.app.domain.repository

import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.model.ReviewLog
import es.saniexam.app.domain.model.UserSettings

/**
 * Local export/import of user-generated state. The codec is documented
 * in `data/backup/BackupCodec.kt` and produces a single `application/json`
 * file with `schemaVersion` + SHA-256 `checksum`. Imports are atomic
 * (single Room transaction).
 *
 * PR4 ships the codec + a `CardState` round-trip. `ReviewLog` and
 * `UserSettings` round-trip are placeholders (the codec carries the
 * fields) until PR5 lands the corresponding Room tables.
 */
interface BackupRepository {
    /**
     * Serialize the current user state to a single JSON document.
     * The returned [BackupEnvelope] carries the encoded bytes plus a
     * suggested file name (`saniexam-backup-<ISO-8601>.json`).
     */
    suspend fun export(): BackupEnvelope

    /**
     * Replace the current user state with [bytes] inside a single
     * transaction. Refuses (throws [BackupException]) on checksum
     * mismatch, unsupported `schemaVersion`, or malformed payload.
     */
    suspend fun import(bytes: ByteArray)

    /**
     * Restore the in-memory snapshot taken immediately before the
     * most recent [import]. The snapshot is per-repository-instance
     * and per-session (lost on process death), matching the spec
     * "Deshacer importación within the same session" requirement.
     */
    suspend fun undoLastImport()

    /** True if a snapshot exists for `undoLastImport`. */
    fun canUndoLastImport(): Boolean
}

/** Transport wrapper for an export. */
data class BackupEnvelope(
    val bytes: ByteArray,
    val suggestedFileName: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupEnvelope) return false
        return bytes.contentEquals(other.bytes) && suggestedFileName == other.suggestedFileName
    }
    override fun hashCode(): Int = 31 * bytes.contentHashCode() + suggestedFileName.hashCode()
}

class BackupException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception("BackupException(reason=$reason)", cause) {
    enum class Reason {
        ChecksumMismatch, UnsupportedSchemaVersion, MalformedPayload, NothingToUndo,
    }
}

/**
 * Snapshot kept in memory between an [BackupRepository.import] call and
 * a [BackupRepository.undoLastImport] call. Cleared on success of the
 * next import. Held by the implementation, never exposed to the UI.
 *
 * PR5 extends the snapshot to include [ReviewLog] and [UserSettings]
 * so the undo restores the entire user state byte-equivalent to the
 * pre-import moment.
 */
internal data class PreImportSnapshot(
    val cardStates: List<CardState>,
    val reviewLogs: List<ReviewLog>,
    val userSettings: UserSettings,
)
