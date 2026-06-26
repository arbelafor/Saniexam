## Exploration: licensed-question-pack

> Phase: `sdd-explore`. Change name: `licensed-question-pack`. Project: `sanitest`. Branch: `feature/licensed-question-pack` (currently identical to `main`, working tree clean).
> Prior change `saniexam` archived 2026-06-19; main specs live in `openspec/specs/*` and are the source of truth. Strict TDD inactive (`strict_tdd=false`); verification gate is the JUnit unit-test suite + the release-pipeline license gate.
> Goal: replace the dev-placeholder question pack with a legally usable/licensed content pack so SaniExam passes the release pack license gate and becomes publishable/beta-ready.

---

### 1. Current State (what the system does today)

**Bundled content (the problem).** The shipped APK contains exactly one question pack, declared in `app/src/main/assets/pack-manifest.json` with `id: "sanidad-dev-placeholder"`, `version: 1`, `license: "dev-placeholder"`, and a SHA-256 over `app/src/main/assets/question-packs/sanidad-dev-placeholder-v1.json`. The pack has 5 clearly-marked `[DEV PLACEHOLDER]` questions across two topics (`t-cardio`, `t-legis`). The `licenseNotes` field literally says: *"Dev placeholder pack; release pipeline MUST fail closed while this license is active. Replace with a cleared-of-rights official pack before any public distribution."* So the placeholder is a deliberate stub, intentionally refused by the release gate.

**Release-pipeline license gate (4 layers, all wired in `saniexam` PR7).**

1. **Pure-Kotlin core** at `app/src/main/java/es/saniexam/app/build/PackLicenseGate.kt`. `REFUSED_LICENSES = {"dev-placeholder", "unknown"}`. `isRefused(license)` returns `true` for `null`, blank, whitespace-only, or any value in the refused set. Single source of truth for the rule.
2. **Gradle task** in `app/build.gradle.kts` (lines 64–92). `:app:checkReleasePackLicense` reads `app/src/main/assets/pack-manifest.json`, regex-extracts the `license` field, and throws `GradleException` if refused. The message points the maintainer at the fix path. Wired into `tasks.named("check") { dependsOn("checkReleasePackLicense") }` so any `./gradlew :app:check` invocation runs it.
3. **CI mirror** at `tools/check-pack-license.sh` (POSIX bash) and `tools/check-pack-license.ps1` (PowerShell). Both contain a hand-rolled `REFUSED_LICENSES=("dev-placeholder" "unknown")` list and the same accept/reject logic. The Kdoc on `PackLicenseGate` and the header comment on both scripts call out the need to keep them in sync.
4. **JUnit test** at `app/src/test/java/es/saniexam/app/build/PackLicenseGateTest.kt`. Three tests: (a) manifest parses, (b) bundled license is currently `dev-placeholder` and is in the refused set, (c) the gate's refusal logic covers every spec scenario. The test's Kdoc explicitly says the test must always pass on the *current* bundled pack because the bundled license is intentionally refused — a future maintainer swapping in a real pack will need to update the assertion in test (b).

**Ingest path (the pack loader).** `EnsureDatasetImportedUseCase` (idempotent) calls `DatasetImporter.importBundled(packId="sanidad-dev-placeholder", packVersion=1)`. The importer:
- reads the manifest via `PackAssetSource` (interface + `AndroidPackAssetSource` wrapping `AssetManager`),
- verifies the SHA-256 of the pack bytes matches the manifest,
- runs `PackValidator` (pure-Kotlin schema + FK validator; rejects orphan topic refs, zero/multiple correct options, duplicate ids, missing fields),
- inside a `db.withTransaction { ... }` writes `SubjectPackEntity` (with `license` + `licenseNotes` columns), topics, questions, options, and a `DatasetVersionEntity` with `status="applied"`.

Spec source: `openspec/specs/dataset-import/spec.md` — Requirement: "Official-Source Metadata and Provenance" / Scenario: "License gate before public distribution" states the pipeline MUST fail closed if any active `SubjectPack.license` is `unknown`, empty, or null.

**Surface where the license is shown to the user.** `HomeScreen.kt:216-218` and `SettingsScreen.kt:150` both display the license string verbatim from the active `SubjectPack`. The UI is already license-aware; only the data needs to change.

**Test state baseline.** From the `saniexam` PR7 verify-report: 18 test classes, **93 tests passing**; `app-debug.apk` builds; `lint` 0 errors / 53 warnings; both license-gate scripts and the Gradle task correctly fail-closed on the dev-placeholder (this is the *desired* state pre-replacement, and a regression of that refusal would be a spec violation, not a fix).

---

### 2. Affected Areas

The change is *content-heavy* (replace the pack + manifest + a couple of test data references) but code-light (the gate, importer, validator, and most tests are content-agnostic and need no changes). The hard contract is: every layer that mentions `sanidad-dev-placeholder` by string literal must be updated, and the new content must (a) pass the existing validator, (b) be under a license string not in the refused set, and (c) leave the gate's refusal logic intact.

| File | Why affected | Type of change |
|---|---|---|
| `app/src/main/assets/question-packs/sanidad-dev-placeholder-v1.json` | The dev content itself (5 placeholder questions). | **Replace** with a real, licensed pack (new filename, e.g. `sanidad-v1.json` or topic-specific filename) OR keep the structure and rewrite content with cleared-of-rights / MIT-licensed questions. |
| `app/src/main/assets/pack-manifest.json` | Declares the active pack id, version, license, and SHA-256. | **Update** `id`, `version`, `publishedAt`, `license`, `licenseNotes`, `sourceAttribution`, `packFile`, and recompute `sha256` over the new pack bytes. |
| `app/src/main/java/es/saniexam/app/domain/usecase/EnsureDatasetImportedUseCase.kt` | Hard-codes `PACK_ID = "sanidad-dev-placeholder"` and `PACK_VERSION = 1`. | **Update** the two `const val` lines to the new id/version. The use case logic stays identical. |
| `app/src/test/java/es/saniexam/app/build/PackLicenseGateTest.kt` | Test (b) asserts the bundled license equals `"dev-placeholder"`. | **Update** test (b): either (i) assert the new license is in the accepted set, or (ii) restructure into a parameterized test that documents the refusal contract independently of the bundled file. The Kdoc must be rewritten. The other two tests (manifest parses, gate logic covers every spec scenario) stay as-is. |
| `app/src/test/java/es/saniexam/app/data/ingest/PackValidatorTest.kt` | Uses `id = "sanidad-dev-placeholder"`, `license = "dev-placeholder"` in the test fixture. | **Optional**: update the literal to match the new id (cosmetic, no behavior change). |
| `app/src/test/java/es/saniexam/app/domain/usecase/GetDueQueueUseCaseTest.kt` | `packId = "sanidad-dev-placeholder"` appears at lines 104, 134, 197. | **Optional**: cosmetic update if the test expects a specific id. |
| `app/src/test/java/es/saniexam/app/domain/usecase/CommitRatingUseCaseTest.kt` | `packId = "sanidad-dev-placeholder"` at lines 235, 252. | **Optional**: cosmetic. |
| `app/src/test/java/es/saniexam/app/data/backup/BackupCodecRoundTripTest.kt` | `packId = "sanidad-dev-placeholder"` at line 194. | **Optional**: cosmetic. |
| `app/src/test/java/es/saniexam/app/data/db/SaniExamDbMigrationTest.kt` | `license = "dev-placeholder"` at line 264. | **Optional**: cosmetic. |
| `app/build.gradle.kts` | `checkReleasePackLicense` reads the same manifest; no behavior change needed if the manifest is updated. | **No change** (gate stays strict). |
| `tools/check-pack-license.sh`, `tools/check-pack-license.ps1` | CI mirrors of the gate. | **No change** (they re-read the manifest at run time). |
| `openspec/changes/archive/2026-06-19-saniexam/*` | Historical context only. | **No change** (archive is immutable per OpenSpec convention). |
| `openspec/specs/dataset-import/spec.md` | Main spec — "License gate before public distribution" scenario. | **No change** required (the spec already mandates what we are about to deliver). Optionally add a clarifying note in the change's archive. |
| `app/src/main/res/values/strings.xml` | License string is shown verbatim to the user. | **No change** required — the UI already renders whatever license string the pack carries. |

**Key insight: the gate itself is not the bug. It works correctly and the refusal behavior must be preserved exactly.** A common mistake would be to "fix" the gate to be more permissive (e.g. dropping `dev-placeholder` from the refused set) — that would satisfy CI but ship non-licensed content. The right move is to *feed the gate a license it can accept*.

---

### 3. Approaches

#### Approach A — Curated cleared-of-rights / public-domain pack (recommended)

Author or commission a new pack of real exam-style questions where the source is in the public domain (e.g. official Spanish "oposiciones" past-exam questions published in the BOE under open licenses, or academy material the user has written permission to ship). The pack uses an explicit license string such as `"cleared-of-rights"`, `"MIT"`, `"CC-BY-4.0"`, or `"Apache-2.0"` — any value in the gate's accepted set.

- **Pros:** Legally clean, satisfies the existing spec, no gate changes, no new infrastructure, ships in a single PR, debug and release builds both pass. Tests updated to reflect the new content. Real exam prep value from day one.
- **Cons:** Requires the user to either already have cleared-of-rights content in hand OR to author/curate it. Time/effort to find or write 50–500 quality questions with topic taxonomy, official-year metadata, and source references. The biggest cost is content production, not code.
- **Effort:** **Medium** (content production: 1–3 days; code: 30 minutes — manifest + literal updates + test fixture refresh).

#### Approach B — Empty/skeleton pack ("publication-grade shape, zero content")

Ship a pack with the *schema* in place but zero or near-zero questions, license `"cleared-of-rights"`, and a `sourceAttribution` that says "Content pending — placeholder structure for release build." Gate accepts; release build passes. The app is technically "publishable" but has no real content for the user to study.

- **Pros:** Lowest content cost; gate passes; ships immediately. Useful as a stepping stone if real content is on a longer timeline.
- **Cons:** The product is empty — a user who installs from the Play Store sees a "0 questions" experience. **Borderline unethical** for an exam-prep product. Spec risk: the `dataset-import` "Bundled Pack Ingestion on First Launch" scenario requires "Home shows a non-zero question count" — a zero-question pack violates the spec. The placeholder must be replaced with content, not a content-shaped void.
- **Effort:** **Low** (one file edit + manifest + ~5 test updates).
- **Verdict:** Possible only if Approach A is blocked. Carries spec risk and a product-quality risk.

#### Approach C — Generate content procedurally from public sources

Scrape or import from a public source (e.g. BOE-published questions, official oposición tribunales that publish under open licenses) and convert to the pack JSON schema in a build-time generator.

- **Pros:** Cheapest at scale once the pipeline exists. Defensible provenance.
- **Cons:** Pipeline work is large (scraper, license verifier, content normalizer, generator). Not a "ship a pack" change — it's a "build a content pipeline" change. Out of scope for the goal.
- **Effort:** **High** (multi-day, probably its own SDD change). Wrong scope for this slice.

#### Approach D — License every question individually + add a "license per question" field

Add a new column `option_license` or `question_license` so the manifest license can stay generic (e.g. `"mixed"`) while each question carries its own license.

- **Pros:** Maximum flexibility for heterogeneous sources.
- **Cons:** Schema migration (Room version bump, migration test), new gate logic, new failure modes. Spec change required. The current gate is a single-string check; per-question licensing is a much larger architectural change. Overkill for v1 beta.
- **Effort:** **High**. Wrong slice.

---

### 4. Recommendation

**Approach A**, with the content sourced from material the user has cleared rights to ship. Concretely:

1. **Decide the license string** in the manifest. The gate accepts anything that is not in `{"dev-placeholder", "unknown"}` and not blank. Realistic values:
   - `"cleared-of-rights"` — clean, self-describing, recommended when the user owns the content or has explicit permission but does not want to ship under a specific OSI license.
   - `"CC-BY-4.0"` — when content is sourced from a Creative Commons BY 4.0 work and attribution is in `sourceAttribution`.
   - `"MIT"` / `"Apache-2.0"` — when content is shipped under those licenses.
   - Avoid `"unknown"` and `"dev-placeholder"` (refused).
2. **Decide the pack id.** `sanidad-v1` is the name the `saniexam` design used for the real pack. Recommend `sanidad-v1` (or a more specific `sanidad-2026-v1` once content scope is defined).
3. **Author / curate the questions.** Minimum-viable beta: 50–100 questions across 3–5 topics, with `officialYear`, `officialSourceRef`, exactly one `isCorrect=true` per question, real explanations. The validator enforces all of this.
4. **Recompute the SHA-256** of the new pack file (PowerShell: `Get-FileHash -Algorithm SHA256 path/to/pack.json`; or `sha256sum` on Linux).
5. **Update the two `const val` lines** in `EnsureDatasetImportedUseCase` and the test data references in the four test files.
6. **Rewrite test (b) in `PackLicenseGateTest.kt`** to assert the new bundled license is in the accepted set, with a Kdoc explaining what license the new pack uses and where the cleared-of-rights documentation lives.
7. **Run the verification gate**:
   - `:app:testDebugUnitTest` — 93+ tests still green (some assertions change wording but no behavior regression).
   - `:app:assembleDebug` — debug APK still builds.
   - `:app:lint` — should be at-or-below current 53 warnings.
   - `:app:checkReleasePackLicense` — **must now PASS** (it was failing before; the change is the first time the release build is allowed to succeed).
   - `pwsh tools/check-pack-license.ps1` — same, must PASS.
   - `bash tools/check-pack-license.sh` — same, must PASS.

**Why this approach over the others:** Approach A is the only one that delivers real product value (questions to study), keeps the gate's contract intact, requires no schema or architectural changes, and fits comfortably inside the 400-line review budget. The hard part is content, and that is a user-decision, not a code-decision.

---

### 5. Risks

1. **R1 — Content license clarity.** If the license string is set to `"cleared-of-rights"` without a real `licenseNotes` describing what was cleared, the manifest becomes "trust me bro" — the gate accepts it but the project has no auditable record. **Mitigation:** `licenseNotes` must describe the cleared source (e.g. "Questions authored in-house by SaniExam team, 2026-06" or "Extracted from BOE XXX/2024, public-domain official exam questions"). Add a `LICENSING.md` at the repo root that lists the source of every question and the rights clearance evidence.
2. **R2 — Content quality.** Real exam-prep questions need correct options, real explanations, and accurate `officialYear` / `officialSourceRef` fields. The validator only checks *structure*, not *correctness*. **Mitigation:** Editorial review pass before merging. Optionally add a `quality_check` field to questions (post-v1, would be a schema change).
3. **R3 — Test fixture sprawl.** Six test files reference the literal string `"sanidad-dev-placeholder"`. Forgetting one means a test passes structurally but a future maintainer searches for the old id and finds it. **Mitigation:** run a repo-wide grep for `"sanidad-dev-placeholder"` before opening the PR; the only remaining hit should be in `openspec/changes/archive/2026-06-19-saniexam/` (which is immutable history).
4. **R4 — SHA-256 mismatch on rebuild.** If the manifest's `sha256` is hand-computed once and then the pack JSON is reformatted on a later commit, the import will fail at `ChecksumMismatch` and the app refuses to start. **Mitigation:** compute the hash at PR-merge time and verify on the dev machine before commit; add a comment in the manifest explaining that the hash must be recomputed on any pack change.
5. **R5 — Room version / migration impact.** None expected — `SubjectPackEntity` columns stay identical (just different values). The change is content-only. **Mitigation:** confirm by reading the Room schema in `app/schemas/es.saniexam.app.data.db.SaniExamDb/*.json`; if a new field is needed, the change is bigger than a content swap.
6. **R6 — Size budget.** The replace is small (one new pack file + one manifest + ~5 test edits + Kdoc rewrites). Likely < 400 lines unless the new pack is enormous (10k+ questions). If the pack is large, consider moving the data file to a sibling PR (chained-PR strategy) to keep the license-gate change reviewable on its own. **Mitigation:** if the new pack exceeds ~300 questions, split into PR-A (license gate + minimal pack, e.g. 30 questions) and PR-B (full content drop).
7. **R7 — Downgrade path.** If the new pack has a content problem post-merge, the recovery is to revert the manifest + pack file. Ensure the previous dev-placeholder content is still available in git history (it will be, the file is just being modified, not deleted, by `git mv`). **Mitigation:** keep the dev pack file checked in *somewhere* (e.g. `app/src/dev/assets/question-packs/sanidad-dev-placeholder-v1.json`) as a known-good fallback that is *not* shipped in the release APK (add an `android.sourceSets` exclusion to keep it out of the production assets). This is optional but cheap insurance.
8. **R8 — Gate regression.** A well-meaning future change could "simplify" the gate by removing `dev-placeholder` from the refused list, or by adding an `--allow-dev` flag, and the new content would mask the regression. **Mitigation:** the `PackLicenseGateTest` covers the refused set directly; keep that test strict. Optionally add a CI job that runs the gate against a fixture with `dev-placeholder` and asserts the gate refuses — that would catch any future relaxation immediately.
9. **R9 — License string case sensitivity.** The gate is case-sensitive (`dev-placeholder` is refused; `Dev-Placeholder` is accepted). A future maintainer who typoes the license could accidentally bypass the gate. **Mitigation:** keep the accepted set tiny and well-named; the test should also assert a couple of near-miss strings (e.g. `"Dev-Placeholder"`, `"DEV-PLACEHOLDER"`) are accepted, to make the case-sensitivity explicit and to fail loud if the gate is ever changed to be case-insensitive in a way that re-rejects real licenses.
10. **R10 — No-network guard is unaffected but co-located.** The PR7 `NoNetworkGuardTest` and `app/src/main/AndroidManifest.xml` lack of `INTERNET` permission are independent of this change. Confirm no manifest changes are required.

---

### 6. Open Questions for the Proposal Phase

1. **Source of the content.** Is there real content ready, or does the user need to author/curate it first? This decides the PR timeline.
2. **License string choice.** `cleared-of-rights` vs. `MIT` vs. `CC-BY-4.0`? The right answer depends on what the source is and what legal review the user wants.
3. **Pack id.** `sanidad-v1` (matches design) or a more specific id (e.g. `sanidad-2026-v1`)? Affects the upgrade path for future content.
4. **Content scope.** How many questions for the beta? 30 (smoke), 100 (real MVP), 500+ (premium)? Affects the PR split and the review budget.
5. **`licenseNotes` content.** One-sentence summary? Multi-line provenance block? Affects what shows up in the Settings screen verbatim.
6. **`sourceAttribution` content.** Same — what string the user sees on Home and Settings.
7. **LICENSING.md at the repo root?** Recommended but not required; confirm with the user.
8. **Editorial review.** Who reviews the questions for correctness before merge? Out of code scope but in scope for the change.
9. **Downgrade posture.** Keep the dev pack as a non-shipped asset for emergency fallback? (See R7.)

---

### 7. Ready for Proposal

**Yes.** The change is well-scoped, the gate contract is preserved, the code surface is tiny (one use case const + ~5 test edits + a manifest), and the only real cost is content production — which is a user decision, not a code decision.

**Orchestrator should ask the user the questions in section 6 before running `sdd-propose`.** Specifically:

- "Do you have cleared-of-rights content ready, or do you need to author/curate it?"
- "What license string do you want on the manifest? (`cleared-of-rights` is the safest default.)"
- "How many questions for the first licensed pack?"

If the user has content in hand, the proposal can be drafted in the next slot. If not, the proposal can still be drafted but should call out content production as a prerequisite and the apply phase will not start until content is available.
