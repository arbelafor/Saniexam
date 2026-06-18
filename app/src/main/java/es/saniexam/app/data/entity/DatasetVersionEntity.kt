package es.saniexam.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import es.saniexam.app.domain.model.DatasetStatus
import es.saniexam.app.domain.model.DatasetVersion
import java.time.Instant

@Entity(tableName = "dataset_version", primaryKeys = ["pack_id", "pack_version", "imported_at"])
data class DatasetVersionEntity(
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "pack_version") val packVersion: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "bytes") val bytes: Long,
    @ColumnInfo(name = "checksum_sha256") val checksumSha256: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
)

internal fun DatasetVersionEntity.toDomain() = DatasetVersion(
    packId, packVersion,
    status = when (status) { "applied" -> DatasetStatus.Applied; "rejected" -> DatasetStatus.Rejected; else -> DatasetStatus.Unknown },
    bytes, checksumSha256, Instant.ofEpochMilli(importedAt),
)

internal fun DatasetVersion.toEntity() = DatasetVersionEntity(
    packId, packVersion,
    status = when (status) { DatasetStatus.Applied -> "applied"; DatasetStatus.Rejected -> "rejected"; else -> "unknown" },
    bytes, checksumSha256, importedAt.toEpochMilli(), null,
)
