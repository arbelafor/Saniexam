package es.saniexam.app.domain.model

/**
 * Review-queue row: a [CardState] together with the immutable
 * [Question] and its [Option] list. Bundled in memory so the Review
 * ViewModel never has to fan out to three repositories per card.
 *
 * Review is read-only with respect to [Question] / [Option]; the
 * mutation surface is the [cardState] (via [es.saniexam.app.scheduler.FsrsEngine.commit])
 * and the [es.saniexam.app.domain.model.ReviewLog] (appended by
 * [es.saniexam.app.domain.usecase.CommitRatingUseCase]).
 */
data class CardStateWithQuestion(
    val cardState: CardState,
    val question: Question,
    val options: List<Option>,
)
