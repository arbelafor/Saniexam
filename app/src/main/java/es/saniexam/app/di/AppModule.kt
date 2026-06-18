package es.saniexam.app.di

import android.content.Context
import android.content.res.AssetManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import es.saniexam.app.scheduler.FsrsEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

/**
 * App-wide singletons: dispatchers, JSON, clock, zone, the AssetManager
 * for the bundled question pack, and the FSRS engine.
 *
 * The engine is provided here (not via `@Inject constructor`) so the
 * scheduler package stays free of `javax.inject.*` imports — the
 * `fsrs-scheduler` "No I/O, No Android Dependencies" spec requires the
 * engine to be plain JVM Kotlin, and the
 * `FsrsSchedulerPurityTest` enforces the boundary.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideAssetManager(
        @ApplicationContext context: Context,
    ): AssetManager = context.assets

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    fun provideFsrsEngine(): FsrsEngine = FsrsEngine()
}

