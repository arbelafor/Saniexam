package es.saniexam.app.data.backup

import androidx.room.withTransaction
import es.saniexam.app.data.dao.CardStateDao
import es.saniexam.app.data.dao.observeAllOnce
import es.saniexam.app.data.db.SaniExamDb
import es.saniexam.app.data.entity.toDomain
import es.saniexam.app.data.entity.toEntity
import es.saniexam.app.di.IoDispatcher
import es.saniexam.app.domain.model.CardState
import es.saniexam.app.domain.repository.BackupEnvelope
import es.saniexam.app.domain.repository.BackupException
import es.saniexam.app.domain.repository.BackupRepository
import es.saniexam.app.domain.repository.PreImportSnapshot
import es.saniexam.app.domain.repository.ReviewLogRepository
import es.saniexam.app.domain.repository.UserSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Atomic export / import of user state. The codec (`BackupCodec`) is
 * the only format owner; this class adds:
 *  - **Atomicity.** A `db.withTransaction { ... }` block wraps the
 *    destructive part of [import] so a mid-flight failure leaves the
 *    DB byte-equivalent to its pre-import state.
 *  - **Session-scoped undo.** [snapshotForUndo] is taken in memory
 *    immediately before each import and cleared on a successful
 *    follow-up import. The spec "Deshacer importación within the
 *    same session" maps to [undoLastImport].
 *  - **Bounded surface.** Only user-generated tables are touched:
 *    `CardState`, `ReviewLog`, `UserSettings`. Bundled content
 *    (Question/Option/Topic/SubjectPack/DatasetVersion) is never
 *    read or written here.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val db: SaniExamDb?,
    private val cardStateDao: CardStateDao,
    private val reviewLogRepository: ReviewLogRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val codec: BackupCodec,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BackupRepository {

    @Volatile
    private var undoSnapshot: PreImportSnapshot? = null

    override suspend fun export(): BackupEnvelope = withContext(io) {
        val cardStates: List<CardState> = cardStateDao.observeAllOnce().map { it.toDomain() }
        val reviewLogs = reviewLogRepository.snapshot()
        val userSettings = userSettingsRepository.get()
        val exportedAt = Instant.now()
        val bytes = codec.encode(
            cardStates = cardStates,
            reviewLogs = reviewLogs,
            userSettings = userSettings,
            exportedAt = exportedAt,
            appVersion = APP_VERSION,
        )
        val stamp = DateTimeFormatter.ISO_INSTANT.format(exportedAt)
            .replace(":", "-")
            .replace(".", "-")
        BackupEnvelope(
            bytes = bytes,
            suggestedFileName = "saniexam-backup-$stamp.json",
        )
    }

    override suspend fun import(bytes: ByteArray): Unit = withContext(io) {
        val decoded = codec.decode(bytes)
        val database = db ?: error("BackupRepositoryImpl requires a non-null SaniExamDb in production")

        // Snapshot for undo. Done outside the destructive transaction so a
        // serialisation failure leaves the snapshot intact for the caller
        // to inspect and the DB unchanged.
        val snapshot = PreImportSnapshot(
            cardStates = cardStateDao.observeAllOnce().map { it.toDomain() },
            reviewLogs = reviewLogRepository.snapshot(),
            userSettings = userSettingsRepository.get(),
        )
        undoSnapshot = snapshot

        try {
            database.withTransaction {
                cardStateDao.deleteAll()
                if (decoded.cardStates.isNotEmpty()) {
                    cardStateDao.upsertAll(decoded.cardStates.map { it.toEntity() })
                }
                reviewLogRepository.replaceAll(decoded.reviewLogs)
                userSettingsRepository.update(decoded.userSettings)
            }
        } catch (t: Throwable) {
            // Transaction failed: Room already rolled back. Discard the
            // snapshot so the UI cannot "undo" into a partial state.
            undoSnapshot = null
            throw t
        }
    }

    override suspend fun undoLastImport() {
        val snapshot = undoSnapshot ?: throw BackupException(BackupException.Reason.NothingToUndo)
        val database = db ?: error("BackupRepositoryImpl requires a non-null SaniExamDb in production")
        database.withTransaction {
            cardStateDao.deleteAll()
            if (snapshot.cardStates.isNotEmpty()) {
                cardStateDao.upsertAll(snapshot.cardStates.map { it.toEntity() })
            }
            reviewLogRepository.replaceAll(snapshot.reviewLogs)
            userSettingsRepository.update(snapshot.userSettings)
        }
        undoSnapshot = null
    }

    override fun canUndoLastImport(): Boolean = undoSnapshot != null

    private companion object {
        /** Static for now; the value will move to a `BuildConfig` field in PR7. */
        const val APP_VERSION = "0.1.0"
    }
}
