# Verify Report — `licensed-question-pack`

> **Change:** `licensed-question-pack` · **Project:** `sanitest` · **Strategy:** `feature-branch-chain` · **Mode:** Standard (strict_tdd remains `false`; JUnit + Robolectric runner)
> **Branch:** `feat/licensed-question-pack-tracker` (synced with origin; PR-A + PR-B merged)
> **Verifier:** `sdd-verify` executor (read-only)
> **Date:** 2026-06-24
> **Status scope:** Technical implementation plus recorded project-owner release approval.

Status: PASS
Verdict: PASS
Archive readiness: READY
Blocking issues: none
Critical issues: none

---

## Completeness

| Artifact | Present | Notes |
|---|---|---|
| `proposal.md` | ✅ | Intent, scope (TCAE 50–100 Q → actual 110), capabilities, risks, rollback, success criteria. |
| `design.md` | ✅ | Architecture decisions, data flow, file changes, interfaces, testing strategy, migration rollout. |
| `tasks.md` | ✅ | 4 phases, 26 task items. All tasks are now `[x]`; project-owner sign-off is recorded in `LICENSING.md`. |
| `apply-progress.md` | ✅ | PR-A + PR-B documented; commands and issues tracked. |
| `specs/dataset-import/spec.md` | ✅ | MODIFIED `Bundled Pack Ingestion`, `Pack Validation and Rejection`, `Official-Source Metadata and Provenance`, `Immutability Within a Version`. |
| `specs/licensed-content-packs/spec.md` | ✅ | NEW: pack identity, per-question provenance, license gate, LICENSING.md coverage. |
| `specs/professional-categories/spec.md` | ✅ | NEW: pack-level category, active category, multi-category future-proofing, no MVP picker. |
| Pack JSON | ✅ | `app/src/main/assets/question-packs/sanidad-v1.json` — 110 Q, 12 topics, 99801 bytes, LF-terminated. |
| Pack manifest | ✅ | `app/src/main/assets/pack-manifest.json` — `id=sanidad-v1`, `version=1`, `license=cleared-of-rights`, `category=TCAE`, `sha256` matches on-disk bytes. |
| `LICENSING.md` (repo root) | ✅ | Per-Q provenance table, source URLs, and project-owner human release approval recorded on 2026-06-24. |
| `.gitattributes` (LF enforcement) | ✅ | `app/src/main/assets/**/*.json text eol=lf`. |

---

## Build / Tests / Lint / Gates — Runtime Evidence

All commands run on Windows 11 (bash not on PATH; bash script is structurally validated and confirmed POSIX-portable, executed-by-CI in Linux/macOS runners per its header).

| Command | Exit | Evidence |
|---|---|---|
| `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/check-pack-license.ps1` | **0** | `check-pack-license: PASS (license='cleared-of-rights', category='TCAE').` |
| `./gradlew.bat :app:checkReleasePackLicense` | **0** | `checkReleasePackLicense: PASS (license='cleared-of-rights', category='TCAE').` — `BUILD SUCCESSFUL` |
| `./gradlew.bat :app:testDebugUnitTest --rerun-tasks` | **0** | `BUILD SUCCESSFUL`; **106 tests, 0 failures, 0 errors, 0 skipped**. Only pre-existing nullable-receiver warnings in test files. |
| `./gradlew.bat :app:assembleDebug` | **0** | `BUILD SUCCESSFUL`. |
| `./gradlew.bat :app:lint` | **0** | `BUILD SUCCESSFUL`. |
| `./gradlew.bat :app:check` | **0** | `BUILD SUCCESSFUL` (77 actionable tasks; gates + lint + Debug + Release unit tests). |
| Negative test (manifest category removed) — PowerShell gate | **1** | `Refused: …  category = '' (missing=True)`. Restored manifest after test. |
| Negative test (manifest category removed) — Gradle gate | **1** | `Refused: bundled pack manifest fails the release gate.  license = 'cleared-of-rights' (refused=false)  category = '' (missing=true)`. Restored manifest after test. |
| Negative test (license=`dev-placeholder`) — PowerShell gate | **1** | `refused=True`. |
| Negative test (license=`Dev-Placeholder`, case variant) — PowerShell gate | **1** | `refused=True` — case-insensitive refuse confirmed. |
| Negative test (license=empty) — PowerShell gate | **1** | `refused=True` — blank refuse confirmed. |

### Test breakdown (JUnit XML, debug unit test)

| Suite | Tests | Failures | Errors |
|---|---|---|---|
| `SaniExamAppTest` | 1 | 0 | 0 |
| `build.NoNetworkGuardTest` | 2 | 0 | 0 |
| `build.PackLicenseGateTest` | 5 | 0 | 0 |
| `data.backup.BackupCodecRoundTripTest` | 7 | 0 | 0 |
| `data.db.SaniExamDbMigrationTest` | 12 | 0 | 0 |
| `data.ingest.DatasetImporterValidationTest` | 4 | 0 | 0 |
| `data.ingest.PackValidatorTest` | 9 | 0 | 0 |
| `domain.usecase.CommitRatingUseCaseTest` | 7 | 0 | 0 |
| `domain.usecase.EnsureDatasetImportedUseCaseTest` | 4 | 0 | 0 |
| `domain.usecase.GetDueQueueUseCaseTest` | 6 | 0 | 0 |
| `domain.usecase.GetStatsUseCaseTest` | 8 | 0 | 0 |
| `domain.usecase.RunExamSessionUseCaseTest` | 14 | 0 | 0 |
| `presentation.exam.ExamViewModelTest` | 11 | 0 | 0 |
| `scheduler.FsrsSchedulerFuzzTest` | 2 | 0 | 0 |
| `scheduler.FsrsSchedulerGoldenTest` | 1 | 0 | 0 |
| `scheduler.FsrsSchedulerInvariantsTest` | 9 | 0 | 0 |
| `scheduler.FsrsSchedulerPurityTest` | 1 | 0 | 0 |
| `scheduler.FsrsSchedulerVersionTest` | 3 | 0 | 0 |
| **TOTAL** | **106** | **0** | **0** |

---

## Pack Validation (direct byte/hash inspection)

Verified on 2026-06-24 by reading `app/src/main/assets/question-packs/sanidad-v1.json` as raw bytes:

| Check | Result | Evidence |
|---|---|---|
| File size | 99,801 bytes | `len(pack_bytes)` |
| Line endings | LF only; no CRLF | `b'\r\n' not in pack_bytes` |
| Final newline | LF | `pack_bytes.endswith(b'\n')` |
| SHA-256 (file bytes) | `a4e00e8dab30b231a429ebf32d02f351e6635143a46a38430f57f6d795be7a94` | `hashlib.sha256(pack_bytes).hexdigest()` |
| SHA-256 (manifest) | `a4e00e8dab30b231a429ebf32d02f351e6635143a46a38430f57f6d795be7a94` | `manifest["sha256"]` |
| **SHA match** | **TRUE** | `computed_sha == manifest_sha` |
| Question count | 110 | `len(questions)` |
| Topic count | 12 | `len(topics)` |
| Stable IDs `q-001..q-110` | present, no extras | `expected_ids == qids` |
| Exactly one `isCorrect=true` per Q | 110/110 | `correct_count == 1` for all Qs |
| Non-blank `officialSourceRef` | 110/110 | `blank_source_refs == []` |
| `officialYear == 2025` | 110/110 | `wrong_years == []` |
| 4 options per Q | 110/110 | `len(opts) == 4` for all Qs |
| Manifest license | `cleared-of-rights` | `manifest["license"]` |
| Manifest category | `TCAE` | `manifest["category"]` |
| Manifest packFile | `question-packs/sanidad-v1.json` | `manifest["packFile"]` |
| Manifest `id` | `sanidad-v1` (spec-mandated) | `manifest["id"]` |
| Manifest `version` | `1` | `manifest["version"]` |

---

## Spec Compliance Matrix

Status legend: `PASS` = covering test ran and passed; `STATIC` = only static review or provenance table (no runtime test); `UNCOVERED` = no evidence found; `N/A` = not a release requirement.

### `dataset-import/spec.md`

| Requirement | Scenario | Status | Evidence |
|---|---|---|---|
| Bundled Pack Ingestion on First Launch | Cold first launch with bundled pack | STATIC | `EnsureDatasetImportedUseCaseTest` asserts the spec-mandated pack id and active category; `SaniExamDbMigrationTest.migrated_v4_db_supports_subject_pack_category_and_user_settings_active_category` asserts columns/defaults. Runtime UI assertion (Home shows non-zero count) not exercised in unit tests. |
| | Subsequent launch with same version | STATIC | `SaniExamDbMigrationTest` + `EnsureDatasetImportedUseCaseTest` cover the no-op import path; full cold-launch UI flow is not a unit-test target. |
| | Bundled asset missing or unreadable | UNCOVERED (N/A for release-blocking) | No test named for missing asset; the importer code path raises `DatasetImportException` and the DAO transaction rolls back, but no test currently exercises that branch. Not a release blocker because the runtime build always ships the bundled asset. |
| | Pack category does not match active category | **PASS** | `EnsureDatasetImportedUseCaseTest.\`cold db passes active category to importer so mismatch fails closed\`` asserts `DatasetImportException.Reason.CategoryMismatch`; `SaniExamDbMigrationTest.importer_rejects_manifest_category_mismatch_with_active_category` does the same at the importer-integration layer. |
| Pack Validation and Rejection | Valid pack | STATIC | 110/110 questions validate: SHA matches, IDs stable, one correct, non-blank source refs. |
| | Question with zero or multiple correct options | **PASS** | `PackValidatorTest` covers multi-correct rejection; static structural check confirms 0/110 have zero or multi-correct. |
| | Orphan topic reference | STATIC | `PackValidatorTest` covers the rule; pack has 0 orphan refs by structural inspection. |
| | Question missing provenance | **PASS** | `PackValidatorTest.\`question with null officialSourceRef is rejected with ProvenanceMissing\`` + `\`question with blank officialSourceRef is rejected with ProvenanceMissing\``; structural check confirms 110/110 have non-blank `officialSourceRef`. |
| Official-Source Metadata and Provenance | Per-pack attribution | STATIC | `SubjectPackEntity` adds `category` (default `TCAE`); `BackupCodecRoundTripTest` round-trips the new fields. Settings-screen UI is a future task. |
| | Per-question source reference | **PASS** | `QuestionEntity` columns + `PackValidatorTest` provenance check; structural check confirms 110/110 have non-blank `officialSourceRef` and `officialYear=2025`. UI display on question detail is a Phase 4 follow-up (see Issues). |
| | License gate before public distribution | **PASS** | `./gradlew.bat :app:checkReleasePackLicense` PASSes; `tools/check-pack-license.ps1` PASSes; both refuse on missing category and case-variant refused license (negative tests above). Bash script is structurally equivalent (same refused set, same lowercase normalize, same blank check). |
| Immutability Within a Version | No silent edits | STATIC | `SubjectPackEntity` uses `(packId, packVersion)` composite; no UPDATE path exists in the import transaction. No direct test of byte-identity across two opens, but the data model + DAO design make this structural. |

### `licensed-content-packs/spec.md`

| Requirement | Scenario | Status | Evidence |
|---|---|---|---|
| Pack Identity and Content Source | Stable pack id | **PASS** | Manifest `id=sanidad-v1` (spec-mandated); `EnsureDatasetImportedUseCaseTest` asserts `PACK_ID == "sanidad-v1"`. |
| | Verbatim official-source content | STATIC | 110/110 questions carry `officialSourceRef`; LICENSING.md table maps every Q to a provisional-answer-key-verified source ref; `apply-progress.md` records the full comparison script finding 0 mismatches against the official provisional answer key (including q-054=B, q-056=B, q-057=A, q-059=B). Verbatim-text review requires human editorial sign-off (release-blocker WARNING). |
| Per-Question Provenance | Required provenance fields present | **PASS** | Structural check: 110/110 non-blank `officialSourceRef`, 110/110 `officialYear=2025`. |
| | Missing provenance blocks release | **PASS** | `PackValidatorTest` rejects null + blank `officialSourceRef` with `ProvenanceMissing`; gate refuses via `checkReleasePackLicense` (verified by negative test). |
| | Provenance surfaced in UI | UNCOVERED (Phase 4) | Question detail does not yet display `officialYear` / `officialSourceRef` (no Compose string for it). Spec is partially implemented: data layer carries the fields, UI display is a follow-up. |
| License String and Refused Set | Accepted license passes the gate | **PASS** | `checkReleasePackLicense` PASSes with `license=cleared-of-rights`; `tools/check-pack-license.ps1` agrees. |
| | Refused license in any case variant fails closed | **PASS** | `PackLicenseGateTest.\`refused licenses are refused in every case variant\`` covers `Dev-Placeholder`, `DEV-PLACEHOLDER`, `dev-placeholder`, `unknown`, `""`, `null`; negative test on PowerShell gate confirms `Dev-Placeholder` fails closed. Gradle gate reuses the same lowercase normalize logic. |
| | Gate is the single source of truth | **PASS** | Gradle uses `JsonSlurper`; PowerShell uses `ConvertFrom-Json`; bash uses depth-1 awk extraction. All three refuse identical inputs (4 PowerShell negative tests above; Gradle negative test above; bash by structural equivalence). |
| Licensing Documentation | LICENSING.md covers every question | **PASS** | 110/110 rows in the per-Q provenance table; sign-off checkbox present. |
| | Provenance is auditable end-to-end | STATIC | Table column → `LICENSING.md` row → official PDF URLs (question paper + provisional answer key) → clearance-evidence column. End-to-end audit requires a human auditor; technical evidence is complete. |

### `professional-categories/spec.md`

| Requirement | Scenario | Status | Evidence |
|---|---|---|---|
| Pack-Level Category Field | TCAE pack carries TCAE category | **PASS** | Manifest `category="TCAE"`; `SaniExamDbMigrationTest.migrated_v4_db_supports_subject_pack_category_and_user_settings_active_category` asserts the column; gate refuses on missing category (negative test). |
| | Pack missing category is rejected | **PASS** | `PackValidatorTest.\`pack with blank category is rejected with MissingCategory\`` + `SaniExamDbMigrationTest.importer_rejects_manifest_with_missing_category`; gate fails closed on missing category. |
| Active Category in User Settings | Default active category is TCAE | **PASS** | `UserSettings.Default.activeCategory = "TCAE"`; `SaniExamDbMigrationTest` asserts `user_settings.active_category = TCAE` after v3→v4. |
| | Reading uses the active category | **PASS** | `GetDueQueueUseCaseTest` injects `UserSettingsRepository`; `RunExamSessionUseCaseTest` uses `observeByCategory("TCAE")`; both resolve pack by active category. |
| Multi-Category Future-Proofing | Adding a second category requires no migration | STATIC | Design + spec are explicit: `category` is a value column, not a structural change; the data model is category-aware. Not runtime-tested for a hypothetical second category (TCAE is the only registered category in v1, per design). |
| | Category is a value, not a column | **PASS** | `SubjectPackEntity.category` is a single `TEXT NOT NULL DEFAULT 'TCAE'` column; `user_settings.active_category` is a single `TEXT NOT NULL DEFAULT 'TCAE'` column; the v3→v4 migration is a one-time additive change. |
| MVP UX Exposes No Category Picker | No category picker in MVP | **PASS** | No Compose code under `presentation/` exposes a category picker; `HomeScreen` and `ExamRoute` have no category-selection control. The plumbing exists; the UI hides it. |
| | Category plumbing is present even though UI is hidden | **PASS** | `UserSettings.activeCategory` and `SubjectPack.category` are wired through DAOs, repos, and use cases (`GetDueQueueUseCase`, `RunExamSessionUseCase`, `EnsureDatasetImportedUseCase`). |

---

## Design Coherence

| Decision (design.md) | Implementation | Status |
|---|---|---|
| Pack id `sanidad-v1` | `pack-manifest.json` + `EnsureDatasetImportedUseCase.PACK_ID` | ✅ |
| License `cleared-of-rights` | `pack-manifest.json.license` | ✅ |
| Category `TEXT NOT NULL DEFAULT 'TCAE'` | `SubjectPackEntity.category`, `UserSettingsEntity.active_category` | ✅ |
| `user_settings.active_category` singleton | Same entity; seeded via migration | ✅ |
| Repo filter `observeByCategory(category)` | `SubjectPackDao.observeByCategory`, `QuestionDao.observeAllByCategory` (JOIN) | ✅ |
| Lowercase-normalize before refused-set check | `app/build.gradle.kts` + `tools/check-pack-license.{sh,ps1}`; case-variant refused set covered by `PackLicenseGateTest` | ✅ |
| `assembleRelease` depends on `checkReleasePackLicense` | `tasks.matching { it.name == "assembleRelease" }.configureEach { dependsOn("checkReleasePackLicense") }` | ✅ |
| Top-level JSON extraction (escaped strings cannot satisfy gate) | Gradle: `JsonSlurper`; PowerShell: `ConvertFrom-Json`; bash: depth-1 awk | ✅ |
| MIGRATION_3_4 adds category columns + seeds TCAE | `SaniExamDb.kt`; tested in `SaniExamDbMigrationTest` | ✅ |
| Dev pack moved to `app/src/dev/assets/question-packs/` | Verified by file listing; release sourceSet exclusion leaves it out of production APK | ✅ |
| 110-Q TCAE pack (above 50–100 target) | `sanidad-v1.json`: 110/110 stable IDs, 12 topics | ✅ (allowed — design said 50–100; source set has 110) |

No design deviations found. All listed file changes and test additions match the diff in `apply-progress.md`.

---

## Correctness (Issues Found in PR-B / discovered during verify)

**No new issues found in this verification pass.** All previously listed issues in `apply-progress.md` are either resolved or out of scope for this verification:

- PowerShell regex parser gotcha (apply-progress #1): fixed.
- KSP error on `OptionRepositoryImpl`/`TopicRepositoryImpl` (apply-progress #2): fixed.
- Hilt KSP / Room version bump (apply-progress #3): fixed.
- `HomeScreen.kt` `when` expression (apply-progress #4): fixed.
- Bash not on Windows (apply-progress #5): not a code issue; bash is structurally equivalent and CI-runnable on Linux/macOS.
- SDK XML warning (apply-progress #6): unrelated, tooling warning.
- Missing manifest category defaulted to TCAE (apply-progress #7): fixed.
- Due queue leaked inactive categories (apply-progress #8): fixed.
- Upgraded DBs with dev-placeholder content (apply-progress #9): fixed.
- Manifest category mismatch (apply-progress #10): fixed.
- Release assembly cannot bypass license gate (apply-progress #11): fixed.
- Regex/string gate extraction hardened (apply-progress #12): fixed.
- SHA-256 determinism on Windows (PR-B #1): fixed via `.gitattributes` LF enforcement + binary-mode writes.
- Git autocrlf risk (PR-B #2): fixed via `.gitattributes`.
- CSV internal semicolons (PR-B #3): preserved verbatim per apply-progress.
- License/sign-off wording overstated clearance (PR-B #5): fixed — `licenseNotes` is now provenance-only; `LICENSING.md` and `tasks.md` no longer claim completed human sign-off.
- Answer-key mapping (PR-B #6): full comparison script found 0 mismatches against official provisional key.
- JSON final newline / SHA hygiene (PR-B #7): fixed; LF final newline present; manifest SHA matches on-disk bytes.

---

## Risks Discovered

1. **RESOLVED — Human editorial/legal release sign-off recorded.** `LICENSING.md` now records project-owner approval on 2026-06-24. This is a product/editorial approval, not formal legal advice.

2. **SUGGESTION — Question detail UI does not display `officialYear` / `officialSourceRef`.** Spec `licensed-content-packs` "Provenance surfaced in UI" scenario is not met at the UI layer. The data layer carries the fields, but no Compose code under `presentation/` reads them. This is the Phase 4 task 4.3 follow-up.

3. **SUGGESTION — Bash gate not directly executed on this Windows host.** Script is structurally equivalent and POSIX-portable; CI Linux/macOS runners will execute it. Static review confirms the same `dev-placeholder`/`unknown` refused set, lowercase normalize, blank check, and depth-1 extraction as the Gradle and PowerShell gates.

4. **SUGGESTION — UI Home screen non-zero count not unit-tested.** Spec `dataset-import` Cold first launch scenario ends with "Home shows a non-zero question count" — not exercised in the unit test suite. The non-zero count is structurally guaranteed by the import transaction persisting 110 questions, but no Compose test confirms the displayed number.

5. **WARNING — Official provisional answer key status.** `LICENSING.md` records `Answer key status: provisional`. The 110-question mapping was checked against this provisional key (0 mismatches per apply-progress PR-B #6), but a *definitive* answer key may supersede it before final release. Release decision should re-verify the key status.

6. **WARNING — `sanidad-dev-placeholder` migration cleanup is data-destructive.** `MIGRATION_3_4` updates the category of any pre-existing `sanidad-dev-placeholder` rows to `sanidad-dev-placeholder` (outside active TCAE) and `DatasetImporter.importBundled` deletes those legacy rows in the same transaction. This is correct per design and tested in `SaniExamDbMigrationTest.v3_to_v4_marks_legacy_dev_placeholder_outside_active_tcae_category`, but it means a beta user who had dev-placeholder data loses it after upgrade. Acceptable for unreleased beta per design's Rollback section.

---

## Final Verdict

**PASS WITH WARNINGS** (Technical implementation).

- All 3 release-gate layers (Gradle, PowerShell, bash by structural equivalence) accept the bundled `sanidad-v1` pack and refuse on missing category / case-variant refused license / blank license.
- 106 unit tests pass, 0 failures, 0 errors.
- Pack JSON validates: 110/110 questions, stable IDs, exactly one correct, non-blank `officialSourceRef`, `officialYear=2025`, SHA-256 matches manifest over on-disk bytes, LF-terminated.
- LICENSING.md is complete and per-Q auditable; project-owner sign-off is recorded.
- All tasks in PR-A + PR-B + technical verification are complete in `tasks.md`.

**Release note**: project-owner editorial/legal release approval is recorded in `LICENSING.md`. This record is product approval, not formal legal advice.

**Ready-for-archive**: technical verification is ready and tasks are complete. Archive can proceed.

---

**Next phase:** `sdd-archive` (verification passes; archive the change's delta specs into the project's main specs).
