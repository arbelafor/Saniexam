package es.saniexam.app.presentation.home

import es.saniexam.app.data.ingest.DatasetImportException
import es.saniexam.app.domain.model.SubjectPack

/**
 * State of the Home surface. Sealed so the Composable can render
 * the four documented states explicitly: [Loading] (cold first launch
 * + idempotent import), [Ready] (data layer is ready, counts shown),
 * [Empty] (pack exists but no questions — should not happen on the
 * dev placeholder but modelled for correctness), [Error] (the
 * importer refused; carries the [DatasetImportException.Reason]).
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val pack: SubjectPack,
        val totalQuestions: Int,
        val dueToday: Int,
    ) : HomeUiState

    data class Empty(val pack: SubjectPack?) : HomeUiState

    data class Error(
        val reason: DatasetImportException.Reason,
        val questionId: String? = null,
    ) : HomeUiState
}
