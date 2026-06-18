# Design: SaniExam — FSRS-Driven Healthcare Exam Prep (Android, Offline-First, Slice 1)

> Change: `saniexam` · Project: `sanitest` · Stack: Kotlin 1.9+, minSdk 24 / targetSdk 34, Compose, Hilt, Coroutines + StateFlow, Room, Navigation Compose.
> Locks resolved here: package id `es.saniexam.app`, display name "SaniExam", single Gradle module `:app` with strict internal packages, own-engine FSRS, chained PRs for the 400-line budget.
> Aligns to six specs: `dataset-import`, `fsrs-scheduler`, `exam-simulation`, `review-session`, `progress-stats`, `progress-backup`.

## Technical Approach

Single-module Android app whose internal packages enforce Clean Architecture boundaries so the slice-2 split into `:core:scheduler` / `:core:data` / `:feature:*` is mechanical. Room is the SSOT; after first-launch asset ingest the app runs fully offline. The FSRS engine is pure-Kotlin with golden-file tests pinned to a known `ts-fsrs` reference, satisfying `fsrs-scheduler`'s plain-JVM rule. Review and Exam Simulation are isolated: only Review writes `CardState` and appends to `ReviewLog`; Exam is a read-and-score loop that must never touch them (`exam-simulation`). Backup/export/import covers the device-change escape hatch with zero network code (`progress-backup` + `dataset-import` no-network rule).

## Architecture Decisions

| # | Decision | Alternatives | Rationale |
|---|---|---|---|
| D1 | **Single module `:app`** with `presentation/`, `domain/`, `data/`, `scheduler/` packages | Multi-module `:core:*` + `:feature:*` from day one | Slice 1 must stay under the 400-line review budget; multi-module's Gradle/convention-plugin overhead would dominate. Migration in §Rollout. |
| D2 | **Package id `es.saniexam.app`**, display name `SaniExam` | `com.sanitest.saniexam` (folder-aligned) | Product-first id; `es.` aligns with the Spanish market and store handle. Folder `SANITEST` is a repo artefact, not a brand artefact. |
| D3 | **Own FSRS engine, pure-Kotlin, JVM-only** under `scheduler/` | Community Kotlin port; embed Rust via JNI | No first-party Kotlin FSRS lib; community ports are small/stale. Algorithm fits in ~300 LOC, deterministic, satisfies "no Android deps". |
| D4 | **Room SSOT, Hilt DI, Coroutines + StateFlow**; no WorkManager/Retrofit/auth in v1 | Koin; RxJava; ContentProvider for the dataset | Matches the agent-marker stack and the `android-data-layer` + `android-viewmodel` skills. WorkManager/Retrofit are explicitly fenced out. |
| D5 | **Review writes; Exam never writes.** `CardState` + `ReviewLog` touched only by Review's commit path. Exam holds an in-memory `ExamSession`. | Allow Exam to update `CardState` for "leech" hints | Spec mandate (`exam-simulation` "No FSRS Perturbation"). Orthogonal modes so practice never pollutes the schedule. |
| D6 | **Bundle dataset as JSON** under `assets/question-packs/sanidad-v1.json`; ingest on first launch only; SHA-256 + per-row `packVersion` | Ship a Room `.db` asset; fetch on first launch | JSON is diffable and reviewable. First-launch-only ingest satisfies "no remote fetch" and gives us the same pipeline later for slice 3. |
| D7 | **Chained PR slicing for slice 1** (see §Rollout) | Single big PR; auto-merge | `review_budget_lines: 400` is active. PRs ordered so the FSRS engine lands with green tests before any UI work. |
| D8 | **JUnit JVM layer for scheduler + use cases only**; Compose UI tests / instrumentation deferred | Full test pyramid on day one; Robolectric everywhere | `strict_tdd: false`, no test runner yet. JUnit JVM tests cover the highest-risk logic; UI smoke uses the `android-emulator-skill` scripts. |

## Data Flow

```
   assets/question-packs/sanidad-v1.json ──▶ DatasetImporter (validate schema, SHA-256, FK)
                                                  │ Room @Transaction
                                                  ▼
   Room DB (SSOT) ──Flows──▶ ViewModels (StateFlow<UiState>, SharedFlow<UiEvent>) ──▶ Compose UI
   CardState / ReviewLog ◀─── Review commit only (Exam never writes)
   FSRS engine (pure JVM) ◀─── scheduler.preview() / commit()
   BackupService ◀────────── JSON, schemaVersion, SHA-256, atomic @Transaction
```

## File Plan (slice 1)

| Area | Paths |
|---|---|
| Build/config | `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper |
| App entry | `AndroidManifest.xml` (no `INTERNET`), `SaniExamApp.kt` (`@HiltAndroidApp`, `applicationScope`), `MainActivity.kt` (`@AndroidEntryPoint`, `NavHost`) |
| DI | `di/DatabaseModule.kt`, `di/AppModule.kt` (dispatchers, JSON, asset manager) |
| Domain (pure Kotlin) | `domain/model/*` (`Question`, `Option`, `Topic`, `SubjectPack`, `DatasetVersion`, `CardState`, `ReviewLog`, `UserSettings`, `Rating`), `domain/repository/*` interfaces, `domain/usecase/*` (`EnsureDatasetImported`, `GetDueQueue`, `CommitRating`, `RunExamSession`, `GetStats`, `ExportBackup`, `ImportBackup`) |
| Scheduler (pure Kotlin) | `scheduler/FsrsEngine.kt`, `SchedulerVersion.kt`, `Preview.kt` |
| Data | `data/db/SaniExamDb.kt`, `Converters.kt`, `entity/*`, `dao/*`, `mapper/*`, `repository/*`, `import/DatasetImporter.kt`, `backup/BackupCodec.kt` |
| Presentation | `presentation/nav/SaniExamNavGraph.kt`, `theme/*`, plus five features (`home`, `review`, `exam`, `stats`, `settings`) each with `Screen` + `ViewModel` + `UiState` |
| Resources | `res/values/strings.xml` (es-ES) + `res/values-night/strings.xml` mirror; rating `contentDescription` strings |
| Assets | `assets/question-packs/sanidad-v1.json` (cleared-of-rights), `assets/pack-manifest.json` (id, version, SHA-256, license, attribution) |
| Tests | `src/test/.../scheduler/FsrsSchedulerGoldenTest.kt`, `FsrsSchedulerFuzzTest.kt`, plus use-case tests (due-queue, commit, stats reconciliation, backup round-trip, importer validation) |

## Interfaces / Contracts

```kotlin
// scheduler/ — pure Kotlin
object SchedulerVersion { const val CURRENT = 1 }
enum class Rating { Again, Hard, Good, Easy }
interface FsrsEngine {
    fun preview(state: FsrsState, rating: Rating, now: Instant): FsrsPreview
    fun commit(state: FsrsState, rating: Rating, now: Instant): FsrsState
}
// domain/ — pure Kotlin
interface CardStateRepository {
    fun observeDue(now: Instant, limit: Int): Flow<List<CardStateWithQuestion>>
    suspend fun get(id: String): CardState?; suspend fun upsert(state: CardState) // Review ONLY
}
interface ReviewLogRepository {
    suspend fun append(log: ReviewLog) // Review ONLY; append-only
    fun observeAll(): Flow<List<ReviewLog>>; suspend fun count(): Int
}
interface BackupRepository {
    suspend fun export(): ByteArray // user rows only, schemaVersion + SHA-256
    suspend fun import(bytes: ByteArray, confirmDestructive: Boolean): BackupResult
}
```

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (JVM) | FSRS engine golden + fuzz; use cases (due-queue, commit, stats, backup round-trip, importer) | JUnit4 + kotlin-stdlib. Golden JSON pinned in `app/src/test/resources/golden/`. |
| Integration | Room migrations: identity v1→v1, plus synthetic "add `schedulerVersion` column" proving history survives | `Room.inMemoryDatabaseBuilder` with explicit `Migration` objects (Robolectric deferred to slice 2). |
| UI smoke | Home → Review → Exam → Stats → Settings happy paths; reveal/rating; exam timer expiry | `android-emulator-skill` scripts (`navigator.py`, `gesture.py`, `screen_mapper`); logged via `log_monitor`. |
| Manual | Accessibility (TalkBack rating descriptions); light/dark; large-font | Emulator matrix pre-release. |

## Migration / Rollout

- **No data migration in v1.** Fresh app, empty repo, schema v1. PR3 ships the migration-test scaffold per `fsrs-scheduler` "Version mismatch handled" + `progress-backup` round-trip requirements.
- **Slice 1 ships as a chained PR sequence** (≤ 400 changed lines per PR), each independently buildable.

| PR | Scope | Why this order | Lines |
|---|---|---|---|
| PR1 | Gradle skeleton, `libs.versions.toml`, Hilt + Compose + Room wired, blank Compose screen | Build + CI smoke | ~350 |
| PR2 | `scheduler/` engine + golden + fuzz tests, no Android | Highest risk isolated, JVM-testable from day one | ~350 |
| PR3 | Room entities/DAOs, mappers, repositories, bundled pack, `DatasetImporter`, `EnsureDatasetImportedUseCase` | Data layer before UI can render | ~380 |
| PR4 | Home + Stats + Settings (no rating, no exam), `StatsViewModel`, `BackupRepository` round-trip | Read-only surfaces first; backup end-to-end testable | ~360 |
| PR5 | Review: queue, reveal, rating row, preview, `CommitRatingUseCase` | First writing surface; tests FSRS integration | ~390 |
| PR6 | Exam: timed session, results, no-perturbation guard | Last; reads Question/Option only | ~370 |
| PR7 | Polish: a11y, light/dark tokens, string audit, emulator smoke | Closes slice 1 | ~250 |

If PR5/PR6 trends past 400 lines, split along `ViewModel` ↔ `Composable` boundary — `ViewModel` first, `Composable` in a follow-up.

**Multi-module migration (slice 2, not v1).** `scheduler/` → `:core:scheduler` (JVM); `data/`+`di/` → `:core:data` (Android lib); `presentation/{home,review,exam,stats,settings}/` → `:feature:*` (Android lib); `:app` to thin glue. Package boundaries in D1 mirror that target so the move is mechanical.

## Risks (design-level mitigations)

R1 Dataset provenance → `DatasetVersion.license` + `pack-manifest.json` SHA-256; CI fails closed on `license="unknown"`. R2 FSRS correctness → own engine + golden + 1000-case fuzz; `schedulerVersion` on every `CardState`; preview and commit share the same code path. R3 Room migrations → `exportSchema=true`; PR3 migration tests; `schedulerVersion` mismatches handled by documented re-init or typed refusal. R4 Single-module wall → packages mirror target module layout. R5 Content staleness → Settings surfaces "Última actualización"; slice 3 update flow fenced. R6 Review budget → chained PR slicing with per-PR caps; `work-unit-commits` skill. R7 No test runner → JUnit JVM for scheduler + use cases; emulator scripts for UI smoke. R8 Binary signal → `ReviewLog` append-only, schema-permissive for future taxonomy. R9 Naming drift → package id locked here as `es.saniexam.app`; store handle `es.saniexam.app`.

## Open Questions

- [ ] **FSRS reference snapshot** — which exact `ts-fsrs` commit/tag and weights vector do we pin against for the golden file? (Blocks PR2, not the design.)
- [ ] **`sanidad-v1` content sourcing** — design is content-agnostic, but `dataset-import` license gate is enforced in the importer and we need the cleared-of-rights attestation before the first public build.
- [ ] **Accessibility depth in PR7** — emulator-script smoke + manual TalkBack sign-off, or invest in a Compose a11y test? (Suggested: scripts + manual for v1; Compose a11y tests in slice 2.)
