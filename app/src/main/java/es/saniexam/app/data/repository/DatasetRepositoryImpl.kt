package es.saniexam.app.data.repository

import es.saniexam.app.data.dao.DatasetVersionDao
import es.saniexam.app.data.dao.SubjectPackDao
import es.saniexam.app.data.entity.SubjectPackEntity
import es.saniexam.app.data.entity.toDomain
import es.saniexam.app.data.entity.toEntity
import es.saniexam.app.domain.model.DatasetVersion
import es.saniexam.app.domain.model.SubjectPack
import es.saniexam.app.domain.repository.DatasetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatasetRepositoryImpl @Inject constructor(
    private val packDao: SubjectPackDao,
    private val versionDao: DatasetVersionDao,
) : DatasetRepository {

    override fun observeActivePacks(): Flow<List<SubjectPack>> =
        packDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeAppliedVersions(): Flow<List<DatasetVersion>> =
        versionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun isApplied(packId: String, packVersion: Int): Boolean =
        versionDao.isApplied(packId, packVersion)

    override suspend fun recordVersion(version: DatasetVersion) {
        // Caller is expected to be inside a transaction (e.g. the importer).
        versionDao.insert(version.toEntity())
    }
}
