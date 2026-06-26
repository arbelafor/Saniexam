# Proposal: licensed-question-pack

## Intent

Replace the `sanidad-dev-placeholder` bundled pack with a publishable TCAE pack sourced from official Spanish oposición documents, so the release-pipeline license gate passes and the app becomes beta-shippable. Treat official-document licensing as an UNKNOWN publishing risk: every question MUST carry provenance, and the release gate MUST block any pack missing it.

## Scope

**In scope.** TCAE pack (50–100 Q, target ~80, verbatim, full provenance on every Q); updated `pack-manifest.json`; multi-category pack + DB schema (TCAE only active in v1); hardened license gate (fail closed on refused license OR missing pack/per-Q provenance); repo-root `LICENSING.md`; updated use-case constants and test fixture refresh; new gate-test cases.

**Out of scope.** Network/remote fetch; per-Q license granularity; procedural generator; UI category redesign; non-TCAE content.

## Capabilities

### New
- `licensed-content-packs`: sourcing, licensing, provenance, and pack-shape rules.
- `professional-categories`: pack and DB taxonomy for oposición categories (TCAE first).

### Modified
- `dataset-import`: provenance MANDATORY at pack and question level; gate fails on missing pack or per-Q provenance; ingest reads `activeCategory`.

## Approach

Author TCAE pack JSON; Qs copied verbatim, every Q records `officialYear` and `officialSourceRef`, exactly one `isCorrect=true`. Extend pack schema with category, active category, and categories registry; `SubjectPackEntity` gains a non-null category column (Room v2). Update `pack-manifest.json` to the new id, user-chosen license, and `licenseNotes`. Harden `PackLicenseGate` to validate provenance (pack + per-Q) and add case-sensitivity JUnit cases. Write `LICENSING.md`. Update use-case constants, refresh test fixtures, run full verification plus all three license-gate invocations.

## Affected Areas

New: TCAE pack JSON, `LICENSING.md`. Modified: `pack-manifest.json`, `PackLicenseGate.kt`, `SubjectPackEntity.kt` + `SaniExamDb.kt` (Room v2), `EnsureDatasetImportedUseCase.kt`, `PackLicenseGateTest.kt`, test files, `dataset-import/spec.md` (mandatory provenance, category rules).

## Risks

- **Official-doc redistribution rights unclear (High).** `LICENSING.md` documents per-Q clearance evidence.
- **SHA-256 mismatch and test fixture sprawl (Med).** Hash at PR-merge; repo-wide grep pre-PR — only allowed hit is the immutable archive.
- **Pack size > 400-line budget (Med).** Chain PRs: PR-A = gate/schema/manifest + ~30-Q smoke; PR-B = full.
- **Future categories need another migration (Low).** Pack + DB column category-aware from v1.
- **Gate case-sensitivity slip (Low).** New test asserts near-miss strings refused.
- **Editorial review skipped (Med).** `LICENSING.md` sign-off is a release prerequisite.

## Rollback Plan

Revert the PR. Keep the dev placeholder pack in `app/src/dev/assets/question-packs/` (excluded from production) as a fallback. If Room v2 shipped to beta users, bump to v3, ship a no-op migration.

## Dependencies

User decision on license string (`cleared-of-rights` recommended); user-supplied TCAE question source set; editorial review pass.

## Success Criteria

TCAE pack 50–100 Q with full provenance; `LICENSING.md` with per-Q source + sign-off; license-gate invocations PASS; JUnit green; pack + DB schema ready for more categories without future Room migration; grep for the old id returns only the immutable archive.
