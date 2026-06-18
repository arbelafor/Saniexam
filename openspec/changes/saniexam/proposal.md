# Proposal: SaniExam — Healthcare Public-Exam Prep (Android, FSRS, Offline-First)

> Change: `saniexam` · Project: `sanitest` · Stack: Kotlin 1.9+, Android minSdk 24 / targetSdk 34, Compose, Hilt, Coroutines + StateFlow, Room, Clean Architecture, offline-first.
> Pre-locked gates (Engram #420 / #421): product name = **SaniExam**; dataset source = **public official-exam PDFs, questions extracted per exam, then added to the local app**. Multi-device sync and remote dataset updates are deferred. Initial answer tracking is binary correct/incorrect; no error taxonomy in v1. Practice-test mode is out of v1.

## Intent

Candidates preparing Spanish healthcare ("sanidad") public-sector exams need to internalize a large, mostly stable corpus of official past-exam questions during short offline blocks (commute, breaks). Generic flashcard tools lack a curated official dataset; web-only exam sites have no offline review loop. SaniExam pairs a curated local question database with an FSRS scheduler so the right question reappears at the right moment, fully on-device, with an exam-simulation mode for mock tests.

## Scope

### In Scope (first slice, shippable end-to-end)

- Single-module `:app`, strict internal packages (`presentation/`, `domain/`, `data/`, `scheduler/`). Multi-module deferred.
- 1 question pack from a bundled JSON asset. No network, no WorkManager, no auth, no analytics.
- Pure-Kotlin FSRS engine, 4-button rating (Again / Hard / Good / Easy), `schedulerVersion` on `CardState`.
- **Exam Simulation mode** — timed mock exam, binary scoring, **no** FSRS perturbation.
- **FSRS Review mode** — daily due queue, reveal-on-tap, reschedule preview, append-only `ReviewLog`.
- One Stats screen: streak, total reviews, 30-day rolling retention.
- Spanish UI; light + dark; accessibility on rating controls.
- Local backup/restore of progress as a single user-chosen file (device-change escape hatch).
- JUnit test layer enabled **only** for the FSRS engine and core use cases. `strict_tdd` stays off elsewhere.

### Out of Scope (deferred)

- Remote / dataset updates over network — slice 3.
- Non-FSRS practice-test study surface — slice 4.
- Multi-pack library, topic browse, in-app pack switcher, leech list, weak-topic heatmap — slices 2 / 4.
- Cloud sync, accounts, push, IAP, web/iOS clients — post-launch.
- Detailed per-question error taxonomy — deferred until v1.x content-team need is proven.

### Future Scope (named, not for v1)

- Dataset update flow (`WorkManager`, `UNMETERED` constraint, diff preview, version pinning in `CardState`).
- Per-topic mastery heatmap and leech handling policy.
- Optional opt-in anonymous telemetry.
- Localized Basque / Catalan / Galician co-official strings.

## Capabilities

> Contract with `sdd-spec`. Research `openspec/specs/` first when authoring; none exist yet.

### New Capabilities

- `dataset-import` — versioned, checksummed, immutable-per-version pack from a bundled asset. Owns `Question`, `Option`, `Topic`, `SubjectPack`, `DatasetVersion`.
- `fsrs-scheduler` — pure-Kotlin FSRS engine: update rules, deterministic, versioned, reschedule preview.
- `exam-simulation` — timed mock-exam sessions over a fixed set, binary correct/incorrect, never writes `CardState` / `ReviewLog`.
- `review-session` — FSRS daily queue, reveal flow, append-only `ReviewLog`, `CardState` updates.
- `progress-stats` — streak, total reviews, 30-day rolling retention from `ReviewLog`.
- `progress-backup` — local export/import of `CardState` + `ReviewLog` + `UserSettings` as a single file.

### Modified Capabilities

- None (no prior main specs exist).

## Approach

1. **Data first, network never (v1).** Room is the source of truth. On first launch the bundled JSON is imported; after that the app works with airplane mode on.
2. **Own the FSRS engine.** From-scratch Kotlin port with **golden-file tests** pinned against a known `ts-fsrs` reference output plus a small fuzz suite. No third-party FSRS dependency in v1.
3. **Modes stay independent.** Exam Simulation is pure read-and-score over a fixed subset; it does **not** write `CardState` or `ReviewLog`. Only Review does.
4. **Schedule durability.** `CardState` carries `packVersion` + `schedulerVersion` so future pack/algorithm updates are non-destructive. Room migrations are a spec requirement from day one.
5. **Binary answer tracking.** v1 stores only `correct | incorrect`. No partial credit, no error tags, no user-driven difficulty hints.
6. **Single module, clean packages.** Internal layering matches Clean Architecture; documented migration to multi-module (`:core:scheduler`, `:core:data`, `:feature:*`) is planned for slice 2.

## Target Users

| Persona | Need addressed by v1 |
|---|---|
| Opositor/a en activo (adult, 25–55, 30–90 min/day, commutes on metro) | Reliable offline review, predictable daily queue, retention analytics. |
| Re-aparejado/a (re-attempting, weak-topic drilling) | FSRS-driven re-exposure of unstable items. |
| Estudiante sin temario propio (uses the app as primary practice source) | Trust signal via source attribution, exam-simulation mode. |
| Preparador/a de academia (cohort content) | Deferred to slice 2+; v1 ships single-user local only. |

## Goals & Non-Goals

| Goals (v1) | Non-Goals (v1) |
|---|---|
| Offline-first end-to-end after first launch | Cloud sync, accounts, multi-device |
| FSRS-driven daily review with verified correctness | FSRS weight re-fitting |
| Exam Simulation mode (timed, binary scoring) | Detailed error taxonomy / per-question error tags |
| Local backup/restore of progress | Server-side backup, cross-device merge |
| Spanish UI, light + dark, accessible | Basque / Catalan / Galician localization |
| Single Android module, clean internal layers | Multi-module Gradle setup |

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `app/` | New | Single Gradle module, Kotlin DSL, Hilt, Compose, Room. |
| `app/src/main/java/.../domain/` | New | `Question`, `CardState`, `ReviewLog`, `UserSettings`, `DatasetVersion`, repository interfaces, use cases. |
| `app/src/main/java/.../data/` | New | Room entities/DAOs, mappers, first-launch bundled-asset import. |
| `app/src/main/java/.../scheduler/` | New | Pure-Kotlin FSRS engine + golden tests. |
| `app/src/main/java/.../presentation/` | New | Compose: Home, Review, Exam, Stats, Settings; `StateFlow<UiState>` + `SharedFlow<UiEvent>`. |
| `app/src/main/assets/question-packs/sanidad-v1.json` | New | One bundled pack (cleared of rights). |
| `app/src/test/.../scheduler/` | New | JUnit + golden-file FSRS tests. |
| `openspec/specs/{dataset-import,fsrs-scheduler,exam-simulation,review-session,progress-stats,progress-backup}/` | New | Delta specs from `sdd-spec`. |

## Assumptions

- **A1.** Android-first is fixed (declared in `.github/skills/Agent.md`).
- **A2.** Spanish UI is acceptable for v1; technical artifacts default to English.
- **A3.** Offline-first is a hard requirement.
- **A4.** v1 may add the JUnit unit-test layer for `:scheduler` + use cases, even with `strict_tdd: false`.
- **A5.** No server, no auth, no multi-device sync in v1. Local backup is the v1 escape hatch.
- **A6.** Practice-test mode is deferred to slice 4; exam-simulation mode covers the v1 "test myself" need.
- **A7.** Binary correct/incorrect is the v1 answer signal; richer signals are deferred.
- **A8.** One bundled pack (`sanidad-v1`) is the v1 dataset; a content team extracts questions from official PDFs and a per-pack license is cleared before any public distribution.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| **R1 — Dataset provenance / legal.** Extracted questions from official PDFs may carry third-party copyright (publishing academies). | High | Hard gate before publishing any dataset: per-pack license + source attribution recorded in `DatasetVersion`; in-app attribution shown; takedown procedure documented. The bundled `sanidad-v1` ships only with sources cleared of rights. v1 ships offline; this gate blocks distribution, not compilation. |
| **R2 — FSRS correctness without an upstream Kotlin lib.** | High | Golden-file tests pinned to a known `ts-fsrs` output; `schedulerVersion` persisted so re-tuning is non-destructive. |
| **R3 — Schema evolution breaks user history.** | Med | Room migrations are a spec requirement; migration tests live next to FSRS tests. |
| **R4 — Single-module becomes a wall at slice 2.** | Med | Documented migration path to multi-module; package boundaries enforced by review checklist. |
| **R5 — Content staleness with no update flow.** | Med | Show "last updated" badge on the pack; document slice 3 as the in-app update plan. |
| **R6 — Review budget overflow.** First slice easily exceeds 400 lines. | High | Apply phase must propose chained PRs; `review_budget_lines: 400` is active. |
| **R7 — `strict_tdd: false`, no test runner.** | Med | Wire JUnit for `:scheduler` and use-case tests; emulator scripts for UI smoke. |
| **R8 — Binary correct/incorrect loses learning signal.** | Low | Accepted trade-off; `ReviewLog` is append-only so a future taxonomy can be derived without data loss. |
| **R9 — Naming drift (folder `SANITEST` vs product `SaniExam`).** | Med | Lock package id in the design phase; revisit if a final brand name changes. |

## Rollback Plan

- v1 is a fresh app in an empty repo. Rollback = remove the `:app` module + the bundled asset + revert the Room schema. No external data loss.
- The local DB is wiped by uninstall. No remote state to roll back.
- `LocalBackup` exports written by a user are inert for a rollback build; import is opt-in.
- If a distributable dataset is found to violate licensing (R1), the bundling step is the rollback point: replace the asset, bump the pack version, ship a corrective update. No code change required.

## Dependencies

- Android target stack per `.github/skills/Agent.md` (Kotlin 1.9+, Compose, Hilt, Room, Coroutines + StateFlow, Navigation Compose).
- Android Agent Skills already installed under `.github/skills/`.
- External `ts-fsrs` reference output (public) for FSRS golden tests.
- Per-pack license cleared for every question in `sanidad-v1.json` (legal gate, not code).

## Success Criteria

- [ ] App installs on `minSdk 24` and runs the bundled pack **fully offline** after first launch.
- [ ] FSRS golden tests pass against pinned `ts-fsrs` reference output; `schedulerVersion` is stored on every `CardState`.
- [ ] Exam Simulation mode runs a timed mock, scores binary correct/incorrect, writes **no** `CardState` / `ReviewLog` changes.
- [ ] Review mode reschedules cards per FSRS (Again < Hard < Good < Easy) and persists each rating as an append-only `ReviewLog` row.
- [ ] Stats screen reports streak, total reviews, 30-day retention; numbers reconcile with `ReviewLog`.
- [ ] A Room migration test shows that bumping `schedulerVersion` does not destroy user history.
- [ ] Local backup exports and re-imports preserve `CardState` + `ReviewLog` byte-for-byte.
- [ ] Spanish UI copy is complete (es-ES) and accessible (content descriptions on rating controls).
- [ ] First slice is delivered as a **chained PR sequence**, each PR ≤ 400 changed lines.

## Proposal Question Round (for user review)

Per the proposal skill's interactive-mode requirement, these product questions are surfaced **before** `sdd-spec` locks anything irreversible. The proposal above uses the **bolded assumption** if the user does not reply.

1. **v1 scope.** Is the slice above (bundled single pack + Exam Simulation + Review + Stats + local backup) the right first shippable surface, or do you want a non-FSRS Practice Test in v1 even at the cost of less polish on Review? **Assumed: as written; Practice Test is slice 4.**
2. **Pack content for `sanidad-v1`.** Which oposición call(s) (e.g. "Enfermería SAS", "Médico interno residente", "Celador SACYL"), how many questions, and is extraction done by an internal team or a partner? **Assumed: internal team, per-pack license cleared before public distribution; compilation is not blocked.**
3. **Binary vs 3-state answer tracking.** Keep v1 binary, or add Correct / Unsure / Incorrect at near-zero cost to improve FSRS signal? **Assumed: binary in v1; 3-state deferred.**
4. **Brand & package id.** Lock the package id now (`com.sanitest.saniexam` vs. `es.saniexam.app`) and confirm `SaniExam` as the final product name, app icon, and store handle. **Assumed: `SaniExam` final; package id chosen in design phase.**
5. **Chained PRs in slice 1.** Confirm the first slice is split into chained PRs (Gradle skeleton → FSRS engine → data layer + bundled pack → Exam Simulation UI → Review UI → Stats + backup → polish) so each PR stays under 400 lines. **Assumed: yes, chained PRs.**

Reply with corrections or `OK to proceed` and the orchestrator will advance to `sdd-spec` against this proposal.
