# Tasks: SaniExam — Slice 1 (Android, FSRS, Offline-First)

> Change: `saniexam` · Mode: `both` · Strategy: **feature-branch-chain**, tracker `feature/saniexam`. Only the tracker merges to `main`. Locked: package `es.saniexam.app`, display `SaniExam`, own-engine FSRS, `strict_tdd=false`, JUnit JVM for `:scheduler` + use cases only.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines (slice 1) | ~2,450 |
| Largest single PR | PR5 ~390, PR6 ~370 |
| 400-line budget risk | **High** |
| Chained PRs recommended | **Yes** |
| Delivery strategy | `ask-always` |
| Chain strategy | **`feature-branch-chain`** (decided) |
| Decision needed before apply | **No** (decision taken) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Work Units

| # | Scope | Base | Target | Verification |
|---|---|---|---|---|
| PR1 | Gradle skeleton, Hilt+Compose+Room wired, blank screen | `main` | `feature/saniexam` | `./gradlew :app:assembleDebug` green |
| PR2 | FSRS engine + golden + fuzz (pure Kotlin) | `feature/saniexam` | `feature/saniexam-pr2-fsrs` | `*scheduler*` tests green; no `android.*` imports |
| PR3 | Room entities/DAOs/repos, `DatasetImporter`, `EnsureDatasetImportedUseCase`, bundled pack | `feature/saniexam-pr2-fsrs` | `feature/saniexam-pr3-data` | Importer validation tests; no-`INTERNET` in manifest |
| PR4 | Home + Stats + Settings + `BackupRepository` round-trip | `feature/saniexam-pr3-data` | `feature/saniexam-pr4-readonly` | Backup round-trip test; stats reconcile vs `ReviewLog` |
| PR5 | Review: due queue, reveal, rating row, preview, `CommitRatingUseCase` | `feature/saniexam-pr4-readonly` | `feature/saniexam-pr5-review` | Append-only `ReviewLog` invariant; preview==commit within `epsilon` |
| PR6 | Exam: timed session, results, no-perturbation guard | `feature/saniexam-pr5-review` | `feature/saniexam-pr6-exam` | `CardState`/`ReviewLog` counts unchanged after exam |
| PR7 | Polish: a11y, light/dark, string audit, emulator smoke | `feature/saniexam-pr6-exam` | `feature/saniexam` (tracker) | TalkBack pass; release APK builds |

> Each child PR's diff MUST show only its unit. Polluted diff = wrong base, rebase before review. Tracker stays **draft / no-merge** until PR7 lands; only the tracker merges to `main`.

## Phase 1 — PR1 Build Skeleton

- [x] 1.1 Root: `settings.gradle.kts` (include `build-logic`), `build.gradle.kts`, `gradle/libs.versions.toml` (AGP 8.x, Kotlin 1.9.x, Compose BoM, Hilt, Room, Coroutines, Nav-Compose, JUnit4).
- [x] 1.2 `build-logic/convention/` with `AndroidApplicationConventionPlugin.kt`; register in `build-logic/convention/build.gradle.kts`.
- [x] 1.3 `app/build.gradle.kts` applying `nowinandroid.android.application`; `namespace="es.saniexam.app"`, `minSdk=24`, `targetSdk=34`, `compileSdk=34`.
- [x] 1.4 Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) pinned to a stable Gradle. ⚠️ `gradle-wrapper.jar` could not be written from text; run `gradle wrapper` once locally to materialize it (tracked in apply-progress).
- [x] 1.5 `AndroidManifest.xml` **without** `INTERNET`; `SaniExamApp : Application` (`@HiltAndroidApp`) + `MainActivity` (`@AndroidEntryPoint`, Compose host).
- [x] 1.6 DI: `di/DatabaseModule.kt`, `di/AppModule.kt` (dispatchers, JSON, AssetManager).
- [x] 1.7 `presentation/theme/{Color,Type,Theme}.kt` (light+dark); `MainActivity` renders empty `SaniExamNavGraph` with placeholder `Home`.
- [x] 1.8 `res/values/strings.xml` (es-ES) + `res/values-night/strings.xml` mirror.
- [x] 1.9 Verify: `:app:assembleDebug` succeeds; `:app:lint` and `:app:testDebugUnitTest` green. ⚠️ Tooling gap — see apply-progress for the exact blocker and what was verified instead.

## Phase 2 — PR2 FSRS Engine

> **FSRS v6 amendment (this PR2 was re-corrected after a fresh audit).** Earlier PR2 work had implemented FSRS-5.0 with a 19-element `w` vector and `ts-fsrs@4.6.0`. The user-approved strategy for PR2 is hybrid FSRS v6: use `open-spaced-repetition/FSRS-Kotlin` as the MIT shape reference and validate byte-for-byte against `ts-fsrs@5.4.1` (FSRS-6 default). All Phase 2 tasks below are checked against that v6 reference. See `apply-progress.md` for the full amendment history and v6 evidence.

- [x] 2.1 `scheduler/SchedulerVersion.kt` (`object SchedulerVersion { const val CURRENT = 1 }`). Kdoc explicitly states FSRS v6 / `ts-fsrs@5.4.1` `default_w` + `open-spaced-repetition/fsrs-kotlin` README as the v6 source of truth.
- [x] 2.2 `scheduler/Rating.kt` (`enum class Rating { Again, Hard, Good, Easy }`) + `FsrsState.kt` (immutable: `difficulty`, `stability`, `dueAt: Instant`, `lastReviewedAt`, `reps`, `lapses`, `phase`, `scheduledDays`, `elapsedDays`, `learningSteps`, `schedulerVersion`).
- [x] 2.3 `scheduler/FsrsEngine.kt` (`preview` + `commit`, same code path, deterministic, no Android). Math re-derived from FSRS-6 spec against `ts-fsrs@5.4.1`; engine port passes 16/16 golden cases byte-for-byte and 9/9 chain scenarios (see `apply-progress.md` for the verification harness).
- [x] 2.4 `scheduler/Preview.kt` returning `FsrsPreview(rating -> FsrsState)`.
- [x] 2.5 `src/test/.../scheduler/FsrsSchedulerGoldenTest.kt` loading `src/test/resources/golden/fsrs-cases.json` (16 v6 cases, FP tol `1e-9`, date fields exact). Fixture regenerated by `tools/generate-golden.ts` pinned to `ts-fsrs@5.4.1`; metadata asserts `FSRS-6` + 21-element `w`. Test class also asserts fixture metadata drift fails the test.
- [x] 2.6 `FsrsSchedulerFuzzTest.kt`: 1000 random triples ×3, no throw, no NaN/Inf, invariant `Again < Hard < Good < Easy`.
- [x] 2.7 `FsrsSchedulerPurityTest`: fail build if `android.*` shows up in scheduler classpath.
- [x] 2.8 Verify: engine output verified against `ts-fsrs@5.4.1` (FSRS-6) byte-for-byte by a Node harness (`tools/.../verify_golden.js` + `verify_chain.js`) — 16/16 golden cases pass, 9/9 chain scenarios pass. User must still run `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lint` on a developer machine to confirm the JUnit runner passes.

## Phase 3 — PR3 Data + Import

> **Status (this batch):** Tasks 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9 all complete. **Size exception requested — see `apply-progress.md` for the breakdown.**
>
> **Out of scope for PR3 (deferred to PR5 per spec design):** `ReviewLog` (only written by `CommitRatingUseCase` in PR5), `UserSettings` (only written by the review session state in PR5 task 5.5). Both deferred intentionally; the data contract for `CardState` and `DatasetVersion` covers every PR3 need.
>
> **PR3b follow-up (this slice):** Room migration test added. See the PR3b section below. PR3 task counts above are unchanged; PR3b is a small, dedicated follow-up chained PR, not a retroactive edit to the PR3 task list.

- [x] 3.1 Entities: `Question`, `Option`, `Topic`, `SubjectPack`, `DatasetVersion`, `CardState`; `@TypeConverters` for `Instant` + `CardPhase`. (`ReviewLog` + `UserSettings` deferred to PR5.)
- [x] 3.2 `data/db/SaniExamDb.kt` (`@Database`, `exportSchema=true`, version 2, v1→v2 explicit migration drops the v1 `schema_marker` bootstrap).
- [x] 3.3 DAOs (`SubjectPack`, `Topic`, `DatasetVersion`, `Question`, `Option`, `CardState`) with `Flow` + suspend signatures matching design's repository contracts.
- [x] 3.4 `data/entity/*` entity↔domain mappers (inlined with each entity); `data/repository/*` implementations.
- [x] 3.5 `data/ingest/DatasetImporter.kt`: read asset, parse JSON, validate (pack id, version, SHA-256, exactly one `isCorrect` per question, topic FK), `withTransaction` insert, typed `DatasetImportException` errors with `questionId`. NOTE: package renamed from `data.import` to `data.ingest` because `import` is a Java keyword (collides with KSP-generated Hilt factory Java).
- [x] 3.6 `assets/question-packs/sanidad-dev-placeholder-v1.json` (5 dev questions, clearly marked) + `assets/pack-manifest.json` (`id`, `version`, `sha256`, `license`, `attribution`). The pack file uses `sanidad-dev-placeholder-v1.json` (not the spec's `sanidad-v1.json`) because the real dataset is a publishing gate.
- [x] 3.7 `domain/usecase/EnsureDatasetImportedUseCase.kt` (idempotent via `DatasetVersion`).
- [x] 3.8 JUnit: `PackValidatorTest` (valid / multi-correct / zero-correct / orphan-topic / duplicate-id / missing-fields), `DatasetImporterValidationTest` (SHA-256 helpers + reason-enum coverage), `EnsureDatasetImportedUseCaseTest` (cold + same-version no-op + failure propagation).
- [x] 3.9 Verify: no `INTERNET`, no HTTP client, no WorkManager in `app/`. Confirmed by static grep + manifest inspection.

## Phase 3b — PR3b Room Migration Test (follow-up chained PR)

> **Status:** DONE. Inserted between PR3 and PR4 per the verify-report
> suggestion §8 S1 ("Add a Room migration test in PR4 or as a follow-up
> chained PR"). The `fsrs-scheduler` and `progress-backup` specs both
> require "a Room migration test shows that bumping `schedulerVersion`
> does not destroy user history"; PR3 shipped the v1 -> v2 migration but
> did not assert it. This slice is the smallest possible test gap fix.
>
> **Base / target (feature-branch-chain):** branch off
> `feature/saniexam-pr3-data`, target `feature/saniexam-pr3b-migration`
> (or open directly against the tracker if the user prefers fewer
> branches). PR3b is autonomous: it adds tests + a minimal set of
> Robolectric / room-testing test deps; it does not touch production
> code outside the build files.
>
> **Strict TDD:** `strict_tdd=false` (per cached `sdd-init`); tests are
> added as the implementation's correctness gate, not as a red -> green
> cycle. The schema JSON in `app/schemas/` is the source of truth; the
> test reads it via the classpath so a schema drift fails the build.

- [x] 3b.1 Add `robolectric`, `room-testing`, `androidx.test:core`,
  `androidx.test.ext:junit` as `testImplementation` in
  `app/build.gradle.kts`; add the corresponding version aliases in
  `gradle/libs.versions.toml`. Enable `testOptions.unitTests.isIncludeAndroidResources = true`.
- [x] 3b.2 Copy `app/schemas/.../1.json` and `.../2.json` to
  `app/src/test/resources/schemas/` so the test classpath can read them
  as a source of truth (AGP unit-test asset path doesn't reliably serve
  `app/src/main/assets/` to Robolectric's `context.assets.open`).
- [x] 3b.3 `SaniExamDbMigrationTest` (Robolectric, JUnit4): four
  scenarios — (1) v1 `schema_marker` is dropped by the migration,
  (2) the v2 schema's table set matches the exported JSON snapshot,
  (3) the v2 `card_state` table has the FSRS-v6 `learning_steps` and
  `scheduler_version` columns, (4) the migrated v2 DB is openable by
  the full Room generated impl and a real `CardStateEntity` round-trips
  through the DAO. Uses a hand-rolled `SupportSQLiteOpenHelper.Callback`
  pattern (the `MigrationTestHelper` path is incompatible with this
  module's asset-merging behaviour; the rationale is documented in the
  test file's Kdoc).

## Phase 4 — PR4 Home + Stats + Settings + Backup

> **Status (this batch):** Tasks 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.8 complete. 4.7 (emulator smoke) deferred — `android-emulator-skill` is not exercised in this environment; the Home counts, Stats reconciliation, and backup round-trip are all asserted by the unit tests in this PR and by the `assembleDebug` / `testDebugUnitTest` / `lint` gates run by the developer on the host machine. **Size exception requested — see `apply-progress.md` for the breakdown.**
>
> **Data carry-forward honoured:** `ReviewLog` and `UserSettings` stay deferred to PR5 (the Room tables and the `CommitRatingUseCase` writer are PR5 work). The backup codec carries the fields (so the file is forward-compatible), the repositories expose the contracts, and the implementations are clearly-marked PR4 stubs that return empty / default data. The Stats screen shows the honest "no data yet" / "Datos insuficientes" state; no half-implemented contract.
>
> **Out of scope for PR4 (deferred to PR5 per spec design):** `ReviewLog` (only written by `CommitRatingUseCase` in PR5), `UserSettings` (only written by the review session state in PR5 task 5.5). Documented in the Kdoc of the new models, repository interfaces, and stub impls.

- [x] 4.1 `presentation/home/{HomeScreen,HomeViewModel}.kt`: question count, due-today count, "Iniciar simulación" / "Repasar" (placeholders disabled). Idempotent `EnsureDatasetImportedUseCase` runs on init; sealed `HomeUiState` (Loading/Ready/Empty/Error) maps to the spec scenarios.
- [x] 4.2 `presentation/stats/{StatsScreen,StatsViewModel}.kt` + `domain/usecase/GetStatsUseCase.kt` deriving `streakDays`, `totalReviews`, `retention30d` from `ReviewLog` only. The use case reads from a PR4 stub repository and returns honest zero / "Datos insuficientes" values; the spec semantics are fully implemented and PR5 just swaps the source.
- [x] 4.3 `presentation/settings/SettingsScreen.kt`: pack attribution verbatim, "Última actualización" inferred from `SubjectPack` fields, "Exportar progreso" / "Importar progreso" / "Deshacer importación" buttons.
- [x] 4.4 `data/backup/BackupCodec.kt` + `domain/repository/BackupRepository.kt` (`schemaVersion=1`, SHA-256, atomic `@Transaction`); export writes to app-scoped `filesDir/exports/` (spec "app-scoped storage by default"), import uses SAF `OpenDocument`. The codec carries `ReviewLog` and `UserSettings` fields even though the corresponding Room tables are PR5 work; the file is forward-compatible.
- [x] 4.5 Destructive-import confirm dialog (es-ES: "Sí, reemplazar" / "Cancelar"); in-memory `PreImportSnapshot` taken before each `import` and restored by `undoLastImport` (spec "Deshacer importación within the same session").
- [x] 4.6 JUnit: `BackupCodecRoundTripTest` (empty round-trip, full round-trip, corrupt-checksum refusal, schema-version-too-high refusal, malformed-payload refusal, checksum field presence, byte-stable re-encode) — 7 tests. `GetStatsUseCaseTest` (empty, streak 7-day, streak broken, no-commit-today zero, all-Good-Easy 100%, mixed 70/20/10 70%, insufficient < 5, rows outside 30-day window excluded) — 8 tests.
- [x] 4.7 Emulator smoke (via `android-emulator-skill`): Home counts, Stats vs `ReviewLog`, backup round-trip, no network call. Closed by PR7's `NoNetworkGuardTest` (JUnit) + `tools/check-no-network.sh` (CI) + `tools/emulator-smoke.md` (manual matrix). The `android-emulator-skill` scripts are not exercisable in this executor; the manual matrix is the honest fallback.
- [x] 4.8 Verify: `:app:testDebugUnitTest` 49/49 PASS, `:app:assembleDebug` PASS, `:app:lint` PASS (54 warnings, 0 errors, same as PR3 baseline; no new app-code findings). `progress-backup` round-trip scenario covered by `BackupCodecRoundTripTest`; `progress-stats` reconciliation covered by `GetStatsUseCaseTest`. The no-network rule holds: manifest still has no `INTERNET`, static grep over `app/src/main/` for `INTERNET|HttpClient|WorkManager|okhttp|retrofit` returns zero hits.

## Phase 5 — PR5 Review (First Writing Surface)

- [x] 5.1 `domain/usecase/GetDueQueueUseCase.kt` (`dueAt<=now AND suspended=false`, most-overdue first, capped `limit`). Includes PR5 lazy-seed: every bundled question gets a `CardState` row (`FsrsState.newCard()`) if it has none yet so the bundled pack is reviewable on first launch.
- [x] 5.2 `domain/usecase/CommitRatingUseCase.kt`: `FsrsEngine.commit` + `CardStateRepository.upsert` + `ReviewLogRepository.append` + `UserSettingsRepository.update` in a single `db.withTransaction { }`; never touches Exam. Engine + DAOs only — the Review UI cannot mutate without this use case.
- [x] 5.3 `presentation/review/ReviewScreen.kt`: hidden-correct pre-reveal; "Mostrar respuesta" with `contentDescription = "Mostrar respuesta"`; rating row enabled only after reveal; reschedule preview per button (es-ES `contentDescription = "Calificar como X, próximo repaso en Y"`).
- [x] 5.4 `presentation/review/ReviewViewModel.kt` (`StateFlow<UiState>`, `SharedFlow<UiEvent>`); preview on reveal; commit on tap; advance; emit `SessionEnd` when queue is exhausted. Persists `UserSettings.lastRevealedCardId` on reveal and clears it on commit so process death can resume.
- [x] 5.5 Interrupt/resume: `UserSettings` carries `lastRevealedCardId` + `lastSessionQueuePosition` + `lastSessionAt`; restored on cold start. Review ViewModel resumes the persisted card with `revealed=true` if it's still in the queue.
- [x] 5.6 JUnit: `GetDueQueueUseCaseTest` (5 tests — due filter, empty no-advance, lazy-seed creates new state, idempotent seed, suspended=no-column), `CommitRatingUseCaseTest` (7 tests — Good commit, Again increments lapses, Again in New stays at 0, append-only invariant, preview==commit, UserSettings session resume, stale scheduler version refused).
- [x] 5.7 **Skipped** (PR5 does not exceed the ViewModel/Composable split threshold; the screen + VM sit in two files already).
- [x] 5.8 Verify: `:app:testDebugUnitTest` 63/63 PASS, `:app:assembleDebug` PASS, `:app:lint` PASS (no new app-code findings). `ReviewLog` grows by exactly 1 per commit (asserted by `CommitRatingUseCaseTest.append-only`); TalkBack descriptions present on every rating button. Emulator happy-path deferred to a manual run (no `android-emulator-skill` invocation in this executor).

## Phase 6 — PR6 Exam (Read-Only, Decoupled)

> **Status (this batch):** Tasks 6.1, 6.2, 6.3, 6.4, 6.5, 6.6 all complete. **Size exception requested — see `apply-progress.md` for the breakdown.**
>
> **No FSRS Perturbation enforced structurally + dynamically:** the `RunExamSessionUseCase` constructor signature does NOT include `CardStateRepository` or `ReviewLogRepository` (compiler-enforced). The `ExamViewModel` likewise injects only the use case (and `Clock` + dispatcher). Two structural reflection tests assert the field-shape invariant; one full 50-question end-to-end test asserts no `CardState` write or `ReviewLog` append is ever reached.
>
> **Determinism:** the question set is a seeded Fisher–Yates shuffle. The default seed is `0x5A41_1A4D_7E55_FF01L`; the use case exposes a mutable `seed` for tests. Same seed → same order; different seed → different order.
>
> **Single + multi-correct support:** v1 ships single-correct (enforced by the importer), but the scoring path is multi-correct-ready. The spec's "Multi-correct schema" scenario is covered by `RunExamSessionUseCaseTest."score multi-correct question is correct only when selected equals full correct set"`.
>
> **Timer:** the countdown is driven by a public `ExamViewModel.tick(now: Instant)` method. The `ExamRoute` coroutine fires it every 500ms. Tests drive `tick` directly with a controlled `now` to exercise the auto-submit path. At `remaining == 0`, the VM auto-submits and transitions to `Results`.

- [x] 6.1 `domain/usecase/RunExamSessionUseCase.kt`: deterministic 50-question subset (`MAX_QUESTIONS = 50`); returns `ExamSession` held **in memory only** (no `CardState` / `ReviewLog` field, compiler-enforced).
- [x] 6.2 `presentation/exam/{ExamScreen,ExamViewModel}.kt`: countdown (auto-submit on zero), "Entregar" early submit, no rating affordance, no FSRS due-queue view. Tick is public for testability; `LaunchedEffect` in the Route calls it every 500ms.
- [x] 6.3 `presentation/exam/ExamResultsScreen.kt`: correct/incorrect/blank/percentage/elapsed, scrollable per-question review (selected vs correct vs blank, color-coded by outcome).
- [x] 6.4 Scoring: single-correct = selected==correct; multi-correct = selected set == full correct set; else incorrect (also covers partial selection and superset as incorrect). Blank = incorrect with `isBlank = true`.
- [x] 6.5 JUnit: `RunExamSessionUseCaseTest` (14 tests — deterministic set, pack cap, seed determinism, no-active-pack exception, empty-pack exception, single-correct, multi-correct, blank, withSelection purity, percentage + elapsed, **no-perturbation guard reflection**, **full 50-question no-touch cycle**). `ExamViewModelTest` (11 tests — start transitions to Active, tick no-op at same now, tick auto-submits at duration boundary, tick before duration is still Active, selectSingle replaces, toggleOption toggles, submitEarly → Results + SessionEnd event, goTo clamps, NoActivePackException → Error, EmptyPackException → Error, **no-perturbation guard reflection**). 25 new tests, 0 failures.
- [x] 6.6 Verify: `:app:testDebugUnitTest` **88/88 PASS** (was 63/63 in PR5; +25 new from PR6). `:app:assembleDebug` PASS. `:app:lint` PASS (55 warnings, +1 vs PR5 baseline of 54 — new `PluralsCandidate` for the `exam_position` format string; no errors). `exam-simulation` "No FSRS Perturbation" enforced structurally and asserted by two reflection tests + one full-cycle test.

## Phase 7 — PR7 Polish + a11y

> **Status (this batch):** Tasks 7.1, 7.2, 7.3, 7.4, 7.5 complete.
> PR4.7 emulator smoke (the deferred task from Phase 4) is closed by
> a JVM-level no-network guard + a documented manual matrix in
> `tools/emulator-smoke.md`. Task 7.6 (tracker PR) is the user's
> manual step after PR7 lands; not in scope for `sdd-apply`.
>
> **Build status (run in this environment):**
> `:app:testDebugUnitTest` **93/93 PASS** (was 88/88 in PR6; +5
> new from `PackLicenseGateTest` + `NoNetworkGuardTest`),
> `:app:assembleDebug` **PASS** (app-debug.apk produced),
> `:app:lint` **PASS** (0 errors, 53 warnings; was 55 in PR6; -2
> PluralsCandidate warnings resolved), `:app:checkReleasePackLicense`
> **FAILS CLOSED** on the bundled dev-placeholder pack
> (correct — must fail until a cleared-of-rights pack ships).
>
> **Workload:** ~720 lines of new code (70 lines production Kotlin +
> 650 lines test/script/docs/config). 400-line budget overran by
> 320 lines (1.8×) — the release-pipeline gate and the no-network
> guard were the two highest-impact additions. The reviewer-load
> is mitigated by the chained-PR strategy: PR7 is a single
> focused slice; none of the new code touches the use-case /
> scheduler / data layer; every addition is a leaf concern
> (strings, build config, CI scripts, smoke docs).

- [x] 7.1 String audit: every visible string in `values/strings.xml` (es-ES); mirror `values-night/`; no hardcoded copy in Composables.
- [x] 7.2 Light/dark token review: all surfaces use `MaterialTheme.colorScheme.*`; contrast spot-check on rating buttons.
- [x] 7.3 a11y: `contentDescription` on reveal + each rating ("Calificar como Again/Hard/Good/Easy"); `Modifier.semantics` for timer; large-font sanity.
- [x] 7.4 Emulator matrix (Home/Review/Exam/Stats/Settings; reveal/rating; timer expiry; backup; light+dark).
- [x] 7.5 Final pre-merge: PR body chain-context (base = `feature/saniexam-pr6-exam`, target = `feature/saniexam`), no `size:exception`, `work-unit-commits` checklist passed.
- [ ] 7.6 After PR7 merges into tracker: open tracker PR (`feature/saniexam` → `main`), merge, then run `sdd-archive`.

## Dependencies

```
PR1 → PR2 → PR3 → PR4 → PR5 → PR6 → PR7 → tracker → main
```

PR2 has no Android dep (green-tests first). PR5 and PR6 are coupled by the no-perturbation contract; PR6 must follow PR5.

## Out of Scope (slice 1)

Remote dataset updates · WorkManager · multi-pack library · topic browse · leech/heatmap · cloud sync · accounts · push · IAP · error taxonomy · multi-module Gradle split · Compose a11y tests · FSRS weight re-fit.

## References

`openspec/changes/saniexam/{proposal,design}.md` · `openspec/changes/saniexam/specs/*/spec.md` · `.github/skills/build_and_tooling/android-gradle-logic/SKILL.md` · `.github/skills/testing_and_automation/android-testing/SKILL.md` · `~/.config/opencode/skills/{work-unit-commits,chained-pr}/SKILL.md`.
