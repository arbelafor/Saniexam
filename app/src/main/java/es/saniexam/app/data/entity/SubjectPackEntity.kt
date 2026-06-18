package es.saniexam.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import es.saniexam.app.domain.model.SubjectPack
import es.saniexam.app.domain.model.Topic

@Entity(tableName = "subject_pack", primaryKeys = ["id", "version"])
data class SubjectPackEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "version") val version: Int,
    @ColumnInfo(name = "source_attribution") val sourceAttribution: String,
    @ColumnInfo(name = "published_at") val publishedAt: String,
    @ColumnInfo(name = "license") val license: String,
    @ColumnInfo(name = "license_notes") val licenseNotes: String,
)

internal fun SubjectPackEntity.toDomain() = SubjectPack(
    id, version, sourceAttribution, publishedAt, license, licenseNotes,
)
internal fun SubjectPack.toEntity() = SubjectPackEntity(
    id, version, sourceAttribution, publishedAt, license, licenseNotes,
)

@Entity(
    tableName = "topic",
    primaryKeys = ["id", "pack_id"],
    foreignKeys = [ForeignKey(
        entity = SubjectPackEntity::class,
        parentColumns = ["id", "version"],
        childColumns = ["pack_id", "pack_version"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["pack_id", "pack_version"])],
)
data class TopicEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "pack_version") val packVersion: Int,
    @ColumnInfo(name = "name") val name: String,
)

internal fun TopicEntity.toDomain() = Topic(id, packId, name)
internal fun Topic.toEntity(packVersion: Int) = TopicEntity(id, packId, packVersion, name)
