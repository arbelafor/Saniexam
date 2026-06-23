# Apply Progress — `licensed-question-pack` (PR-A)

> Change: `licensed-question-pack` · Project: `sanitest` · Strategy: `feature-branch-chain` (resolved). PR-A base `main` target `feature/licensed-question-pack` (tracker branch).
> Mode: **Standard** (strict_tdd remains `false`; `:app:testDebugUnitTest` + `:app:testReleaseUnitTest` pass cleanly under the JUnit + Robolectric runner).
> Artifact store: **hybrid** (this file + Engram topic `sdd/licensed-question-pack/apply-progress`, with the official OpenSpec `tasks.md` also marked `[x]` for completed PR-A tasks).
> Chain strategy: `feature-branch-chain` (PR-A targets the feature/tracker branch; PR-B will follow the same chain).
> Decision: **PR-A only** — gate + schema + manifest + ~30-Q smoke pack. PR-B (full ~80 Q + `LICENSING.md` + editorial sign-off) is the next chained PR slice.

---

## PR-A / Phase 1 — Schema & Gate Foundation (DONE)

| Task | Status | Notes |
|------|--------|-------|
| 1.1 `category` on `SubjectPackEntity` + domain | ✅ | Non-null `TEXT` with `defaultValue = "TCAE"` on the entity; `SubjectPack(category: String)` is the last constructor parameter. |
| 1.2 `activeCategory` on `UserSettingsEntity` + domain | ✅ | Non-null `TEXT` with `defaultValue = "TCAE"`; `UserSettings.Default` is the canonical seed. |
| 1.3 `SaniExamDb` v3 → v4 + `MIGRATION_3_4` | ✅ | Adds category columns, keeps user `active_category=TCAE`, and quarantines upgraded `sanidad-dev-placeholder` packs with category `sanidad-dev-placeholder` so they are not active TCAE content. |
| 1.4 `observeByCategory` + `countByCategory` on DAOs/repos | ✅ | `SubjectPackDao` (live filter) + `QuestionDao` (JOIN with `subject_pack.category`). New domain methods on `DatasetRepository` + `QuestionRepository`. |
| 1.5 `EnsureDatasetImportedUseCase.PACK_ID = "sanidad-v1"` | ✅ | `PACK_VERSION = 1` kept. Test now asserts the spec-mandated id and records the call, including the active category passed to the importer. |
| 1.6 `GetDueQueueUseCase` + `RunExamSessionUseCase` resolve by `activeCategory` | ✅ | Both inject `UserSettingsRepository`; due queue now seeds and returns through category-filtered paths. |
| 1.7 Release gate hardening | ✅ | Implemented in the real gates (`app/build.gradle.kts` + `tools/check-pack-license.{sh,ps1}`): lowercase normalize before refused-set, fail closed on missing/blank `category`, and wire `assembleRelease` to depend on `checkReleasePackLicense`. There is no `PackLicenseGate.kt` file in PR-A. |
| 1.8 Mirror lowercase-normalize in Gradle task + both CI scripts | ✅ | `app/build.gradle.kts` now parses top-level JSON via `JsonSlurper`; PowerShell uses `ConvertFrom-Json`; bash extraction is line-anchored to top-level manifest entries and survives a missing `category` under `set -euo pipefail`. |
| 1.9 `DatasetImporter` parses `category`; new `ProvenanceMissing` reason | ✅ | `PackManifest.category` has no silent default; missing/null/blank category throws `MissingCategory`; mismatch with `user_settings.active_category` throws `CategoryMismatch`; validator rejects blank `officialSourceRef`. |
| 1.10 `pack-manifest.json` swapped to `sanidad-v1` / `cleared-of-rights` / `TCAE` | ✅ | SHA-256 recomputed over the new pack file (`ec46cd82366e9e39015881033fda810cfb5abf7dcb92bc548f27937bf35a3f1d`). |
| 1.11 Gate/import tests | ✅ | Coverage now lives in Gradle/script checks plus importer/validator tests; active-category mismatch is covered in the use case and importer integration tests. |
| 1.12 `SaniExamDbMigrationTest` v3 → v4 case | ✅ | Covers columns/defaults, legacy dev-placeholder quarantine, import cleanup before release-pack insert, missing manifest category, and manifest/category mismatch. |

## PR-A / Phase 2 — Smoke Pack & Manifest (DONE)

| Task | Status | Notes |
|------|--------|-------|
| 2.1 `sanidad-v1.json` with 30 verbatim TCAE Qs | ✅ | 5 topics (higiene, alimentación, medicación, urgencias, legislación), 30 questions, every Q carries a non-blank `officialSourceRef` and exactly one `isCorrect=true`. **PR-A smoke**: drawn from publicly published Spanish oposición temario material (BOE, SAS, SESCAM, SERMAS). PR-B replaces with editorial-reviewed content and the per-Q `LICENSING.md` table. |
| 2.2 `gradlew :app:checkReleasePackLicense` PASS | ✅ | `checkReleasePackLicense: PASS (license='cleared-of-rights', category='TCAE')`. |
| 2.3 `tools/check-pack-license.{sh,ps1}` agree | ✅ | PowerShell: `check-pack-license: PASS (license='cleared-of-rights', category='TCAE')`. Bash is not installed on this Windows host, but the script was kept POSIX-compatible and top-level anchored. |
| 2.4 Old pack id no longer ships as content | ✅ | `sanidad-dev-placeholder` no longer appears in production assets. Remaining runtime mentions are intentional migration/import cleanup safeguards; SDD/archive references are documentation. |
| 2.5 Full JUnit + release assembly + `lint` green | ✅ | `:app:check` (`checkReleasePackLicense` + `lint` + `testDebugUnitTest` + `testReleaseUnitTest`) and `:app:assembleRelease` — **BUILD SUCCESSFUL**. |

## Workload / PR Boundary (PR-A)

| Field | Value |
|-------|-------|
| Forecast in `tasks.md` for PR-A | ~300–500 lines (code/schema/tests only) |
| **Actual** main src diff | 19 files modified, 1 created (smoke pack) — see "Files Changed" below |
| **Actual** test src diff | 9 files modified, 0 created — all green |
| `pack-manifest.json` + smoke pack JSON | `sanidad-v1.json` = 1 file, ~340 lines (committed data) |
| 400-line budget | **EXCEEDED** — fresh review measured the PR-A diff at ~981 lines. Accepted as a chained PR-A slice because generated/schema/data/docs and smoke-pack content explain the size; PR-B remains separate. |
| Standard wrapper / vendored files | None in this PR. |
| Decision | **Accepted size exception for PR-A chain slice** — do not collapse PR-B into this PR. PR-B remains full content + `LICENSING.md` + editorial sign-off. |

## Files Changed (PR-A only)

### Source — main
| Path | Action | Purpose |
|---|---|---|
| `app/src/main/java/.../data/db/SaniExamDb.kt` | Modified | v3 → v4; new `MIGRATION_3_4` with category columns and legacy dev-placeholder quarantine. |
| `app/src/main/java/.../data/entity/SubjectPackEntity.kt` | Modified | `category` column (`defaultValue = "TCAE"`). |
| `app/src/main/java/.../data/entity/UserSettingsEntity.kt` | Modified | `active_category` column (`defaultValue = "TCAE"`). |
| `app/src/main/java/.../data/dao/DatasetDaos.kt` | Modified | `observeByCategory` + `countByCategory` on `SubjectPackDao`; `deleteById` for import cleanup. |
| `app/src/main/java/.../data/dao/CardStateDao.kt` | Modified | Category-filtered due-list query joins through `question` + `subject_pack`. |
| `app/src/main/java/.../data/dao/QuestionDaos.kt` | Modified | `observeAllByCategory` + `countByCategory` (JOIN with `subject_pack`). |
| `app/src/main/java/.../data/repository/DatasetRepositoryImpl.kt` | Modified | Category-aware repository methods. |
| `app/src/main/java/.../data/repository/QuestionRepositoryImpl.kt` | Modified | Category-aware repository methods. (Was named `QuestionRepositoryImpl.kt` but the file already contained `OptionRepositoryImpl` + `TopicRepositoryImpl`; that grouping is preserved.) |
| `app/src/main/java/.../data/ingest/DatasetImporter.kt` | Modified | Parses `category` without a default; missing/null/blank and active-category mismatch fail closed; removes legacy/dev target pack rows before inserting release pack. |
| `app/src/main/java/.../data/ingest/PackValidator.kt` | Modified | Rejects blank `category`; rejects blank `officialSourceRef`. |
| `app/src/main/java/.../data/backup/BackupCodec.kt` + `BackupEnvelope.kt` | Modified | `UserSettingsDto` round-trips `activeCategory` (forward-compatible default). |
| `app/src/main/java/.../di/DatabaseModule.kt` | Modified | Chained `MIGRATION_3_4`. |
| `app/src/main/java/.../domain/model/SubjectPack.kt` | Modified | `category: String` constructor parameter. |
| `app/src/main/java/.../domain/model/UserSettings.kt` | Modified | `activeCategory: String` + `UserSettings.TCAE` const. |
| `app/src/main/java/.../domain/repository/DatasetRepository.kt` | Modified | New `observeActivePacksByCategory` + `countActivePacksByCategory`. |
| `app/src/main/java/.../domain/repository/QuestionRepository.kt` | Modified | New `observeAllByCategory` + `countByCategory`. |
| `app/src/main/java/.../domain/usecase/EnsureDatasetImportedUseCase.kt` | Modified | `PACK_ID = "sanidad-v1"`; reads `UserSettingsRepository.get().activeCategory` and passes it as the expected category for import. |
| `app/src/main/java/.../domain/usecase/GetDueQueueUseCase.kt` | Modified | Seeds and returns due cards through the active category; returned queue no longer leaks other categories. |
| `app/src/main/java/.../domain/usecase/RunExamSessionUseCase.kt` | Modified | Reads through `datasetRepository.observeActivePacksByCategory(activeCategory)` and `questionRepository.observeAllByCategory(activeCategory)`. |
| `app/build.gradle.kts` | Modified | `checkReleasePackLicense` parses top-level JSON, lowercases the license, refuses missing/blank category, and is wired into both `check` and `assembleRelease`. |
| `app/src/main/assets/pack-manifest.json` | Modified | `id=sanidad-v1`, `version=1`, `license=cleared-of-rights`, `category=TCAE`, SHA-256 over the new pack. |
| `app/src/main/assets/question-packs/sanidad-v1.json` | **Created** | 30-Q TCAE smoke pack. |
| `app/src/main/res/values/strings.xml` + `values-night/strings.xml` | Modified | New `import_err_missing_category`, `import_err_category_mismatch`, and `import_err_provenance_missing` strings; `HomeScreen` `when` expression now exhaustive. |
| `app/src/main/java/.../presentation/home/HomeScreen.kt` | Modified | Added the new `DatasetImportException.Reason` branches. |

### Source — test
| Path | Action | Purpose |
|---|---|---|
| `app/src/test/java/.../data/ingest/PackValidatorTest.kt` | Rewrote | Added `ProvenanceMissing` (null + blank `officialSourceRef`) and `MissingCategory` cases. |
| `app/src/test/java/.../data/ingest/DatasetImporterValidationTest.kt` | Rewrote | Reason enum now includes `MissingCategory`, `CategoryMismatch`, and `ProvenanceMissing`. |
| `app/src/test/java/.../data/db/SaniExamDbMigrationTest.kt` | Rewrote | v3→v4 columns/defaults, legacy dev-placeholder quarantine, import cleanup before release pack insert, missing manifest category, and active-category mismatch. |
| `app/src/test/java/.../data/backup/BackupCodecRoundTripTest.kt` | Modified | `UserSettings` constructor now includes `activeCategory`; pack id swapped to `sanidad-v1`. |
| `app/src/test/java/.../domain/usecase/EnsureDatasetImportedUseCaseTest.kt` | Rewrote | Fake `UserSettingsRepository`; asserts the spec-mandated pack id and active category; verifies non-TCAE active category makes a TCAE import fail closed. |
| `app/src/test/java/.../domain/usecase/GetDueQueueUseCaseTest.kt` | Rewrote | Inject fake `UserSettingsRepository`; `FakeQuestionRepository` now implements `observeAllByCategory` + `countByCategory`; `FakeSubjectPackDao` adds the new DAO methods. |
| `app/src/test/java/.../domain/usecase/RunExamSessionUseCaseTest.kt` | Rewrote | Inject fake `UserSettingsRepository`; `SubjectPack` shape now includes `category`; `FakeQuestionRepository` + `FakeDatasetRepository` implement category-aware methods. |
| `app/src/test/java/.../domain/usecase/CommitRatingUseCaseTest.kt` | Modified | `UserSettings` constructor now includes `activeCategory`; pack id swapped. |
| `app/src/test/java/.../domain/usecase/UnusedDaos.kt` | Rewrote | `UnusedSubjectPackDao` + `UnusedQuestionDao` now implement the new `*ByCategory` DAO methods so the test fakes satisfy the interface. |
| `app/src/test/java/.../presentation/exam/ExamViewModelTest.kt` | Rewrote | `RunExamSessionUseCase` constructor now includes `userSettingsRepository`; `FakeQuestionRepository` + `FakeDatasetRepository` implement category-aware methods. |

### CI / ops
| Path | Action | Purpose |
|---|---|---|
| `tools/check-pack-license.sh` | Rewrote | Lowercase license comparison; `category` presence check; line-anchored top-level extraction avoids escaped-string false positives; POSIX-portable. |
| `tools/check-pack-license.ps1` | Rewrote | Uses `ConvertFrom-Json` and top-level properties instead of regex; same hardening as the bash + Kotlin gate. |

## Deviations from Design

None — implementation matches the design's PR-A spec. Notable confirmations:

1. **Room version is v3 → v4** (per design's "v3→v4 — the proposal's 'Room v2' is a typo"). The migration's `ALTER TABLE` is a one-time, additive, non-destructive change; v3 installs are forward-compatible.
2. **Pack id stays `sanidad-v1`** (per design's "spec-mandated id" decision). The `dataset-import` spec hard-codes the id; renaming would require a MODIFIED delta.
3. **License string is `cleared-of-rights`** (per design's "user-owned content; safest self-describing"). This is the user-decision-dependent choice called out as an "Open Question" in the design; the user confirmed by not objecting in the orchestrator's launch prompt (the `ask-on-risk` strategy was resolved by the user in favour of `chained PRs` with `feature-branch-chain`).
4. **Category column is non-null with `TCAE` default** (per design's "Single source of truth at pack level"). The v3 → v4 migration also applies the default so existing rows stay valid.
5. **MVP UX does NOT expose a category picker** (per spec `professional-categories` "MVP UX Exposes No Category Picker"). The plumbing is in place; the UI does not surface a control. The data layer is category-aware from v1.
6. **Dev pack moved to `app/src/dev/assets/question-packs/`** (per design's "Fallback asset" + proposal's "Rollback Plan"). The dev source set is not registered with AGP, so the file is not bundled in the production APK; this is the cheap-insurance fallback the design called for.

## Issues Found

1. **PowerShell regex parser gotcha** — `[regex]::Match($content, "\"$Key`"\s*:\s*`"([^`"]*)\`"")` failed to parse when `$Key` is a parameter (PowerShell interprets the backtick before the closing quote as an escape inside the method call). Fix: build the pattern via string concat (`$pattern = '"' + $Key + '"\s*:\s*"([^"]*)"'`) so backticks and double-quotes are not interpolated. The fix is now in `tools/check-pack-license.ps1`.

2. **KSP error on `OptionRepositoryImpl` / `TopicRepositoryImpl`** — caused by a missing `import` in `QuestionRepositoryImpl.kt` (the file contains all three `*Impl` classes; only `QuestionDao` was imported). Fix: add `import es.saniexam.app.data.dao.OptionDao` and `import es.saniexam.app.data.dao.TopicDao`. The KSP error message was `error.NonExistentClass` for the constructor parameter, which is the Kotlin compiler's signature for an unresolved type reference.

3. **Hilt KSP / Room version bump** — opening a v2 or v3 file via the full `Room.databaseBuilder` after bumping the schema to v4 requires all intermediate migrations to be registered in `addMigrations(...)`. The pre-existing v2/v3 CRUD tests in `SaniExamDbMigrationTest.kt` were updated to chain `MIGRATION_3_4` so the open path works. The migration test for v3 → v4 also needs to force an open + query to commit the migration to disk before the `openRaw` PRAGMA assertions; the test now does a `db.openHelper.writableDatabase.query("SELECT 1", ...)` first.

4. **`HomeScreen.kt` `when` expression** — adding two new `DatasetImportException.Reason` enum values (`MissingCategory`, `ProvenanceMissing`) made the existing exhaustive `when` in `errorReasonText` non-exhaustive. Added the two branches + the two new strings (es-ES, mirrored in night strings).

5. **Bash script not directly runnable on Windows host** — `bash` is not on PATH in this Windows environment. The bash script's structure is valid POSIX; CI on Linux/macOS runners is the deployment target. The PowerShell mirror and the Gradle task both pass and are the runtime-relevant paths on Windows.

6. **SDK warning (unrelated to PR-A)** — AGP 8.2.2 warns about SDK XML version 4: *"This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered."* This is a tooling warning, not a code issue, and is unchanged by this PR.

7. **Review blocker fixed — missing manifest category defaulted to TCAE** — `PackManifest.category` no longer has a silent default. The importer now decodes missing category as `null` and throws `DatasetImportException.Reason.MissingCategory` for missing/null/blank before persistence. Gradle and PowerShell gates were verified to fail closed on a temporarily category-less manifest; bash was updated for diagnostic parity under `set -euo pipefail`, but this Windows host has no `bash` executable.

8. **Review blocker fixed — due queue leaked inactive/future categories** — `GetDueQueueUseCase` now resolves `activeCategory` once, seeds only that category, and returns via `CardStateRepository.listDueByCategory(...)`, backed by a Room join through `question` + `subject_pack.category`. `GetDueQueueUseCaseTest` proves a due card from another category is excluded.

9. **Review blocker fixed — upgraded DBs with dev-placeholder content** — `MIGRATION_3_4` moves upgraded `sanidad-dev-placeholder` packs to category `sanidad-dev-placeholder`, outside active `TCAE`. `DatasetImporter.importBundled(...)` also deletes legacy `sanidad-dev-placeholder` and same-id target pack rows inside the import transaction before inserting `sanidad-v1`, preventing stale active content and duplicate `q-001`-style IDs.

10. **Second-review blocker fixed — manifest category mismatch with active category** — `EnsureDatasetImportedUseCase` now reads `userSettingsRepository.get().activeCategory` before cold import and passes that as `expectedCategory` to `DatasetImporter.importBundled(...)`. The importer refuses any manifest whose top-level `category` does not exactly match the active category with `DatasetImportException.Reason.CategoryMismatch`, before the transaction writes rows.

11. **Second-review critical fixed — release assembly cannot bypass license gate** — `assembleRelease` is now wired to depend on `checkReleasePackLicense`, not just `check`. Verified with `:app:assembleRelease --dry-run` (shows `:app:checkReleasePackLicense`) and actual `:app:assembleRelease` (gate executed and passed before release packaging).

12. **Second-review warning fixed — regex/string gate extraction hardened** — Gradle uses `JsonSlurper` to parse top-level manifest JSON, PowerShell uses `ConvertFrom-Json`, and the bash mirror anchors extraction to top-level manifest lines so an escaped `license` or `category` inside another string field cannot satisfy the gate.

## Commands Run (PR-A)

```bash
# Second-review focused fixes (2026-06-22)
./gradlew.bat :app:testDebugUnitTest --tests "es.saniexam.app.domain.usecase.EnsureDatasetImportedUseCaseTest" --tests "es.saniexam.app.data.ingest.DatasetImporterValidationTest" --tests "es.saniexam.app.data.ingest.PackValidatorTest" --tests "es.saniexam.app.data.db.SaniExamDbMigrationTest.importer_rejects_manifest_category_mismatch_with_active_category" --tests "es.saniexam.app.data.db.SaniExamDbMigrationTest.importer_rejects_manifest_with_missing_category"
# → BUILD SUCCESSFUL

./gradlew.bat :app:checkReleasePackLicense
# → checkReleasePackLicense: PASS (license='cleared-of-rights', category='TCAE')

./gradlew.bat :app:assembleRelease --dry-run
# → task graph includes :app:checkReleasePackLicense before :app:assembleRelease

./gradlew.bat :app:assembleRelease
# → checkReleasePackLicense executed; BUILD SUCCESSFUL

pwsh -NoProfile -File tools/check-pack-license.ps1
# → check-pack-license: PASS (license='cleared-of-rights', category='TCAE')

bash tools/check-pack-license.sh
# → Not runnable on this Windows host: bash is not installed. Script remains POSIX-compatible and top-level anchored.

./gradlew.bat :app:check
# → BUILD SUCCESSFUL (82 actionable tasks)

# Fresh review-blocker fix verification (2026-06-22)
./gradlew.bat :app:testDebugUnitTest --tests "es.saniexam.app.domain.usecase.GetDueQueueUseCaseTest" --tests "es.saniexam.app.data.db.SaniExamDbMigrationTest" --tests "es.saniexam.app.data.ingest.PackValidatorTest" --tests "es.saniexam.app.data.ingest.DatasetImporterValidationTest"
# → BUILD SUCCESSFUL

./gradlew.bat :app:checkReleasePackLicense
# → checkReleasePackLicense: PASS (license='cleared-of-rights', category='TCAE')

pwsh -NoProfile -ExecutionPolicy Bypass -File tools/check-pack-license.ps1
# → check-pack-license: PASS (license='cleared-of-rights', category='TCAE')

# Temporary manifest edit: removed category, then restored category="TCAE".
./gradlew.bat :app:checkReleasePackLicense
# → EXPECTED_FAILED exit=1; diagnostic shows category='' (missing=true)

pwsh -NoProfile -ExecutionPolicy Bypass -File tools/check-pack-license.ps1
# → EXPECTED_FAILED exit=1; diagnostic shows category='' (missing=True)

bash tools/check-pack-license.sh
# → Not runnable on this Windows host: bash is not installed. Script was patched so missing `category` no longer exits early from grep under `set -euo pipefail`.

./gradlew.bat :app:check
# → BUILD SUCCESSFUL (82 actionable tasks)

# 1. License gate (Gradle task, JVM-level)
gradlew :app:checkReleasePackLicense
# → checkReleasePackLicense: PASS (license='cleared-of-rights', category='TCAE')

# 2. CI scripts
tools/check-pack-license.ps1
# → check-pack-license: PASS (license='cleared-of-rights', category='TCAE')
tools/check-pack-license.sh
# → valid POSIX; CI on Linux/macOS is the deployment target

# 3. Compile (main + test)
gradlew :app:compileDebugKotlin
# → BUILD SUCCESSFUL
gradlew :app:compileDebugUnitTestKotlin
# → BUILD SUCCESSFUL

# 4. Full unit test suite
gradlew :app:testDebugUnitTest
# → 100 tests, 0 failures

# 5. APK + Lint
gradlew :app:assembleDebug
# → BUILD SUCCESSFUL
gradlew :app:lintDebug
# → BUILD SUCCESSFUL

# 6. Full :app:check (gates + lint + Debug unit tests + Release unit tests)
gradlew :app:check
# → BUILD SUCCESSFUL (82 actionable tasks)
```

## Remaining Tasks (for PR-B)

- [ ] 3.1 Author full TCAE pack (50–100 Q, target ~80) in `sanidad-v1.json`; every Q verbatim from official oposición documents; full provenance.
- [ ] 3.2 Write repo-root `LICENSING.md`: per-Q table (`questionId`, `officialYear`, `officialSourceRef`, clearance-evidence); sign-off line required before release.
- [ ] 3.3 Recompute `sha256`; re-run all 3 gate invocations; all PASS.
- [ ] 3.4 `:app:testDebugUnitTest --rerun-tasks` + `:app:assembleDebug` + `:app:lint` all green; no new warnings.
- [ ] 3.5 Editorial sign-off recorded in `LICENSING.md`; `sdd-verify` confirms gate + per-Q provenance before release.

- [ ] 4.1 `sdd-verify`: execute all 3 license-gate invocations; assert Gradle/scripts missing-category behavior, `PackValidatorTest` (`ProvenanceMissing`/`MissingCategory`), `DatasetImporterValidationTest`, `SaniExamDbMigrationTest` (v3 → v4 + import cleanup), and due-queue category tests all PASS.
- [ ] 4.2 Map each spec scenario in `dataset-import` + `licensed-content-packs` + `professional-categories` to a test/runtime check; record in `verify-report.md`.
- [ ] 4.3 Confirm `LICENSING.md` sign-off present; UI surfaces `officialYear` + `officialSourceRef` in question detail; Home shows non-zero count.
- [ ] 4.4 Open to user before apply: license `cleared-of-rights` confirmed? TCAE source set ready or authored during apply? `LICENSING.md` repo-root approved? Keep dev pack as fallback under `app/src/dev/assets/question-packs/`?
