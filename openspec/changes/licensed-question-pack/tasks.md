# Tasks: licensed-question-pack

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 1200–2500 (TCAE JSON alone ~1000–2000; code/schema/tests ~300–500) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR-A (gate/schema/manifest + ~30-Q smoke) → PR-B (full content + LICENSING.md) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| PR-A | Gate hardening, Room v3→v4, manifest swap, pack schema + ~30-Q smoke, all tests green | PR 1 | Base = main; gates must now PASS |
| PR-B | Full TCAE (~80 Q), LICENSING.md, editorial sign-off, all 3 gates PASS | PR 2 | Base = main after PR-A; can also stack on PR-A branch |

## Phase 1: Schema & Gate Foundation (PR-A)

- [ ] 1.1 Add `category: String` (default `"TCAE"`) to `SubjectPackEntity.kt` and `SubjectPack` domain model.
- [ ] 1.2 Add `activeCategory: String` (default `"TCAE"`) to `UserSettingsEntity.kt` + `UserSettings`; update repo impl + stub.
- [ ] 1.3 Bump `SaniExamDb.kt` to `version=4`; add `MIGRATION_3_4` (ALTER `subject_pack` ADD `category`, ALTER `user_settings` ADD `active_category`, seed singleton); chain in `DatabaseModule`.
- [ ] 1.4 Add `observeByCategory` + `countByCategory` to `SubjectPackDao` + repo; matching filter on `QuestionDao`.
- [ ] 1.5 Change `EnsureDatasetImportedUseCase.kt` `PACK_ID` to `"sanidad-v1"`; refresh its test.
- [ ] 1.6 Update `GetDueQueueUseCase` + `RunExamSessionUseCase` to resolve pack by `userSettings.activeCategory`; inject `UserSettingsRepository`; refresh tests.
- [ ] 1.7 Harden `PackLicenseGate.kt`: lowercase-normalize before refused-set; add `validateManifest(manifest)` returning `ProvenanceResult` (Ok / `MissingCategory` / blank); add 3 case-variant JUnit cases.
- [ ] 1.8 Mirror lowercase-normalize in `app/build.gradle.kts` `checkReleasePackLicense` and `tools/check-pack-license.{sh,ps1}`; update refused-set message.
- [ ] 1.9 Add `category` to `DatasetImporter.kt` parse; add `ProvenanceMissing(offendingQuestionId)` reason; reject blank `officialSourceRef` in `PackValidator.kt`.
- [ ] 1.10 Update `pack-manifest.json` to `id=sanidad-v1`, `license=cleared-of-rights`, `category=TCAE`; recompute `sha256` (placeholder).
- [ ] 1.11 Rewrite `PackLicenseGateTest` test (b) to assert accepted license; Kdoc points to `LICENSING.md`.
- [ ] 1.12 Add `SaniExamDbMigrationTest` v3→v4 case mirroring v2→v3 hand-rolled DDL pattern; assert columns + seed.

## Phase 2: Smoke Pack & Manifest (PR-A close-out)

- [ ] 2.1 Create `app/src/main/assets/question-packs/sanidad-v1.json` with ~30 verbatim TCAE Qs (full provenance, exactly one `isCorrect=true`, top-level `category: "TCAE"`).
- [ ] 2.2 Recompute `sha256` in `pack-manifest.json`; verify `gradlew :app:checkReleasePackLicense` PASSes.
- [ ] 2.3 Run `tools/check-pack-license.sh` + `tools/check-pack-license.ps1`; both must agree with Kotlin gate.
- [ ] 2.4 `rg "sanidad-dev-placeholder"` returns hits only in `openspec/changes/archive/2026-06-19-saniexam/**`.
- [ ] 2.5 Full JUnit suite + `:app:assembleDebug` + `:app:lint` all green.

## Phase 3: Full TCAE Content (PR-B)

- [ ] 3.1 Author full TCAE pack (50–100 Q, target ~80) in `sanidad-v1.json`; every Q verbatim from official oposición documents; full provenance.
- [ ] 3.2 Write repo-root `LICENSING.md`: per-Q table (`questionId`, `officialYear`, `officialSourceRef`, clearance-evidence); sign-off line required before release.
- [ ] 3.3 Recompute `sha256`; re-run all 3 gate invocations; all PASS.
- [ ] 3.4 `:app:testDebugUnitTest --rerun-tasks` + `:app:assembleDebug` + `:app:lint` all green; no new warnings.
- [ ] 3.5 Editorial sign-off recorded in `LICENSING.md`; `sdd-verify` confirms gate + per-Q provenance before release.

## Phase 4: Verification & Documentation

- [ ] 4.1 `sdd-verify`: execute all 3 license-gate invocations; assert `PackLicenseGateTest` (incl. 3 case-variants), `PackValidatorTest` (`ProvenanceMissing`), `DatasetImporterValidationTest`, `SaniExamDbMigrationTest` (v3→v4) all PASS.
- [ ] 4.2 Map each spec scenario in `dataset-import` + `licensed-content-packs` + `professional-categories` to a test/runtime check; record in `verify-report.md`.
- [ ] 4.3 Confirm `LICENSING.md` sign-off present; UI surfaces `officialYear` + `officialSourceRef` in question detail; Home shows non-zero count.
- [ ] 4.4 Open to user before apply: license `cleared-of-rights` confirmed? TCAE source set ready or authored during apply? `LICENSING.md` repo-root approved? Keep dev pack as fallback under `app/src/dev/assets/question-packs/`?
