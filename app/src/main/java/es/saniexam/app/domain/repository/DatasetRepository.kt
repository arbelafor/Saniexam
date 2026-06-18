package es.saniexam.app.domain.repository

import es.saniexam.app.domain.model.DatasetVersion
import es.saniexam.app.domain.model.SubjectPack
import kotlinx.coroutines.flow.Flow

interface DatasetRepository {
    fun observeActivePacks(): Flow<List<SubjectPack>>
    fun observeAppliedVersions(): Flow<List<DatasetVersion>>
    suspend fun isApplied(packId: String, packVersion: Int): Boolean
    suspend fun recordVersion(version: DatasetVersion)
}
