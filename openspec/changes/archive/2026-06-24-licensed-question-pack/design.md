# Design: licensed-question-pack

## Technical Approach

Replace the dev-placeholder pack with a TCAE pack sourced from official Spanish oposición documents. Reshape the data model so future categories (Enfermería, Medicina) slot in without another Room migration. Keep `PackLicenseGate`'s refuse-closed contract and **harden** it against case-sensitivity bypass (`Dev-Placeholder` currently passes). Add a `category` column to `subject_pack` (Room v3→v4 — the proposal's "Room v2" is a typo; current schema is v3) and a single `activeCategory` in `user_settings`. MVP UI does NOT expose a category picker; TCAE is the implicit default. Gate gains two hard-fail checks: pack must carry `category`, every question must carry non-blank `officialSourceRef`. Pack id stays `sanidad-v1` (the `dataset-import` spec hard-codes that string).

## Architecture Decisions

| Decision | Choice | Alternatives | Why |
|---|---|---|---|
| Pack id | `sanidad-v1` (spec-mandated) | `tcae-v1` | Spec `dataset-import` Scenario 1 names it literally; renaming needs MODIFIED delta. |
| License string | `cleared-of-rights` | `MIT`, `CC-BY-4.0` | User-owned content; safest self-describing. |
| Category column | `TEXT NOT NULL DEFAULT 'TCAE'` | nullable, separate table | Single source of truth at pack level. |
| Active-category store | `user_settings.active_category` (singleton) | SharedPreferences, new table | Reuses the seeded singleton; no new module. |
| Active-category filter | Repository `observeByCategory(category)` | UI picker now | Filters at read time; future categories need zero migration. |
| Gate hardening | Lowercase-normalize before refused-set check; assert `Dev-Placeholder`/`DEV-PLACEHOLDER` still refused | Keep case-sensitive | User flagged bypass risk. Mirror in 3 layers (Kotlin, Gradle, sh/ps1). |
| Rollout | Single PR with chained-budget guard | Multi-pack staging | 50–100 Q + schema fits one PR; chain triggers at >300 Q. |

## Data Flow

```
assets/question-packs/sanidad-v1.json  (TCAE, SHA-256)
        v
pack-manifest.json  (id=sanidad-v1, license=cleared-of-rights, category=TCAE, sha256)
        v
DatasetImporter.importBundled()
  -> checksum -> deserialize -> validateProvenance(category, per-Q officialSourceRef)
  -> PackValidator.validate (NEW: reject blank officialSourceRef)
  -> db.withTransaction { insert pack (+category) | topics | Qs | options | dataset_version }
        v
SubjectPackEntity(category="TCAE", license="cleared-of-rights", ...)
  ^                                       ^
  |                                       |
user_settings.active_category="TCAE"   GetDueQueueUseCase + RunExamSessionUseCase pick by active category
```

## File Changes

**Create**: `assets/question-packs/sanidad-v1.json` (TCAE, 50–100 Q, every Q with `officialSourceRef`); `LICENSING.md` at repo root (per-Q source table); 3 spec files under `openspec/changes/.../specs/` (1 delta on `dataset-import` + 2 new domains).

**Modify**: `pack-manifest.json` (`id`, `license`, `category`, recomputed `sha256`); `PackLicenseGate.kt` (lowercase normalize; add `validateManifest` for `category`; case-variant JUnit cases); `DatasetImporter.kt` (parse `category`; add `ProvenanceMissing` reason); `PackValidator.kt` (reject blank `officialSourceRef`); `SubjectPackEntity` + `SubjectPack` (+`category`); `UserSettingsEntity` + `UserSettings` (+`activeCategory` default `"TCAE"`); `SaniExamDb.kt` (`version=4`; new `MIGRATION_3_4`: 2× ALTER + seed); DAOs + repos (+`observeByCategory`, `countByCategory`); `EnsureDatasetImportedUseCase.kt` (`PACK_ID="sanidad-v1"`); `GetDueQueueUseCase` + `RunExamSessionUseCase` (resolve pack by `userSettings.activeCategory`; inject `UserSettingsRepository` into exam); `app/build.gradle.kts:64-92` + `tools/check-pack-license.{sh,ps1}` (mirror Kotlin gate); 4 test files with old id + `PackLicenseGateTest` + `PackValidatorTest` + `DatasetImporterValidationTest` + `SaniExamDbMigrationTest` (new migration test mirroring v2→v3 pattern).

## Interfaces / Contracts

```kotlin
data class SubjectPack(..., val category: String)        // NEW
data class UserSettings(..., val activeCategory: String)  // NEW, default "TCAE"
sealed class ProvenanceResult { object Ok; data class Missing/Blanked(val field: String) }
fun PackLicenseGate.validateManifest(manifest: PackManifest): ProvenanceResult
// Pack JSON: { ..., "category": "TCAE" }
```

## Testing Strategy

| Layer | What | How |
|---|---|---|
| Unit | Gate refusal incl. `Dev-Placeholder`/`DEV-PLACEHOLDER`/blank/null | `PackLicenseGateTest` + 3 cases |
| Unit | `ProvenanceMissing` on Q with blank `officialSourceRef` | `PackValidatorTest` + `DatasetImporterValidationTest` |
| Unit | `SubjectPack.category` round-trip; `activeCategory` default | entity-layer unit tests |
| Migration | `MIGRATION_3_4` adds columns + seeds TCAE | `SaniExamDbMigrationTest` (mirror v2→v3 hand-rolled DDL) |
| Pipeline | SHA-256 match; gate invocations PASS | `:app:checkReleasePackLicense` + `tools/check-pack-license.{sh,ps1}` |
| Grep | Old id only in immutable archive | `rg "sanidad-dev-placeholder"` → only `openspec/changes/archive/2026-06-19-saniexam/**` |

## Migration / Rollout

- **DB v3→v4**: `MIGRATION_3_4` runs `ALTER TABLE subject_pack ADD COLUMN category TEXT NOT NULL DEFAULT 'TCAE'` + same on `user_settings` (adds `active_category`); seeds singleton. No data loss. Chain in `DatabaseModule.provideDatabase().addMigrations(...)`.
- **Active-category v1**: TCAE is the only registered category; all existing pack rows match. No re-tenant.
- **Fallback asset**: `sanidad-dev-placeholder-v1.json` moves to `app/src/dev/assets/question-packs/` (release sourceSet exclusion) for emergency revert.
- **Rollback**: revert PR. Users on v4 must clear app data (Room refuses to downgrade; acceptable for unreleased beta).
- **Chained-PR guard**: if TCAE pack > 300 Q, split PR-A (gate/schema/manifest + 30-Q smoke) / PR-B (full content). `sdd-tasks` MUST surface `Decision needed before apply: Yes|No`, `Chained PRs recommended: Yes|No`, `400-line budget risk: Low|Medium|High`.

## Open Questions

- [ ] License string confirmed as `cleared-of-rights` (vs. `MIT`/`CC-BY-4.0`)?
- [ ] TCAE pack content ready, or authored during apply?
- [ ] `LICENSING.md` at repo root approved?
- [ ] Editorial review of TCAE pack (correct options, sources) — out of code scope, blocks release.
- [ ] Keep dev pack as non-shipped fallback asset?
- [ ] When a second category lands, should `RunExamSessionUseCase.start` default to active or require explicit pick? (Affects future schema.)
