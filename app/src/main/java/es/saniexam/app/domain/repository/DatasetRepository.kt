package es.saniexam.app.domain.repository

import es.saniexam.app.domain.model.DatasetVersion
import es.saniexam.app.domain.model.SubjectPack
import kotlinx.coroutines.flow.Flow

interface DatasetRepository {
    fun observeActivePacks(): Flow<List<SubjectPack>>
    fun observeAppliedVersions(): Flow<List<DatasetVersion>>
    suspend fun isApplied(packId: String, packVersion: Int): Boolean
    suspend fun recordVersion(version: DatasetVersion)

    /**
     * PR-A: filter the active-pack read path by category. The MVP
     * ships with `TCAE` as the only registered category, so the
     * only meaningful call is `observeActivePacksByCategory("TCAE")`
     * — but the repository surface is category-aware from v1 so
     * a future "Enfermería" registration is a value change, not
     * a structural change.
     */
    fun observeActivePacksByCategory(category: String): Flow<List<SubjectPack>>
    suspend fun countActivePacksByCategory(category: String): Int
}
