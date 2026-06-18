package es.saniexam.app.data.ingest

import android.content.res.AssetManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indirection over `AssetManager.open(...)` so the importer is unit-testable
 * without instantiating Android's `AssetManager`. The default implementation
 * wraps the real `AssetManager`; tests pass a fake.
 */
interface PackAssetSource {
    fun read(path: String): ByteArray
}

@Singleton
class AndroidPackAssetSource @Inject constructor(
    private val assets: AssetManager,
) : PackAssetSource {
    override fun read(path: String): ByteArray =
        assets.open(path).use { it.readBytes() }
}
