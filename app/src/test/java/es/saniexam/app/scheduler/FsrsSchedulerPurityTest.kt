package es.saniexam.app.scheduler

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec `fsrs-scheduler` / "No I/O, No Android Dependencies". Purity is a
 * **build-time** invariant for slice 1 (single Android module): if anyone
 * later adds an `android.*` import under `app/src/main/java/.../scheduler/`
 * (or any other package the engine re-exports from), this test fails.
 *
 * Strategy: walk the source tree on disk and grep for forbidden imports.
 * We don't try to parse the classpath at runtime; we check the source
 * because that is the thing a contributor would actually edit.
 *
 * The check intentionally does NOT look at the generated golden file or
 * test code — only the production scheduler sources.
 */
class FsrsSchedulerPurityTest {

    @Test
    fun `scheduler sources contain no Android imports`() {
        // Resolve the project-relative scheduler main source root. JUnit
        // runs from the gradle working dir, which is `:app`. The gradle
        // plugin sets `user.dir` to that module for `testDebugUnitTest`.
        val schedulerDir = File("src/main/java/es/saniexam/app/scheduler")
        assertTrue(
            "expected scheduler source dir at $schedulerDir (cwd=${File(".").absolutePath})",
            schedulerDir.isDirectory,
        )

        val forbidden = listOf(
            "import android.",
            "import androidx.",
            "import com.google.dagger.",
            "import dagger.",
            "import javax.inject.",
        )
        val offenders = mutableListOf<String>()
        schedulerDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                for (needle in forbidden) {
                    if (text.contains(needle)) {
                        offenders += "${file.path} ($needle)"
                    }
                }
            }

        assertTrue(
            "scheduler sources must not depend on Android/Dagger/Hilt: $offenders",
            offenders.isEmpty(),
        )
    }
}
