package es.saniexam.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import es.saniexam.app.data.repository.CardStateRepositoryImpl
import es.saniexam.app.data.repository.DatasetRepositoryImpl
import es.saniexam.app.data.repository.OptionRepositoryImpl
import es.saniexam.app.data.repository.QuestionRepositoryImpl
import es.saniexam.app.data.repository.TopicRepositoryImpl
import es.saniexam.app.domain.repository.CardStateRepository
import es.saniexam.app.domain.repository.DatasetRepository
import es.saniexam.app.domain.repository.OptionRepository
import es.saniexam.app.domain.repository.QuestionRepository
import es.saniexam.app.domain.repository.TopicRepository
import javax.inject.Singleton

/**
 * Binds the data-layer repository implementations to their domain
 * interfaces. PR3 wires only the dataset and `CardState` repos;
 * `ReviewLogRepository` and `BackupRepository` land with PR5 / PR4.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds @Singleton
    abstract fun bindDatasetRepository(impl: DatasetRepositoryImpl): DatasetRepository

    @Binds @Singleton
    abstract fun bindQuestionRepository(impl: QuestionRepositoryImpl): QuestionRepository

    @Binds @Singleton
    abstract fun bindOptionRepository(impl: OptionRepositoryImpl): OptionRepository

    @Binds @Singleton
    abstract fun bindTopicRepository(impl: TopicRepositoryImpl): TopicRepository

    @Binds @Singleton
    abstract fun bindCardStateRepository(impl: CardStateRepositoryImpl): CardStateRepository
}
