package es.saniexam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import es.saniexam.app.presentation.nav.SaniExamNavGraph
import es.saniexam.app.presentation.theme.SaniExamTheme

/**
 * Hosts the Compose graph. PR4 ships Home + Stats + Settings; later
 * PRs add Review (PR5) and Exam (PR6) without changing the activity
 * shell. The graph lives in [SaniExamNavGraph] so the activity stays
 * a thin host.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaniExamTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SaniExamNavGraph()
                }
            }
        }
    }
}
