## Exploration: SaniExam — Official-Exam Prep with FSRS Spaced Repetition

> Phase: `sdd-explore` (entry step for a new change concept).
> Change name: `saniexam`. Project key (Engram): `sanitest`. Folder: `C:\Users\arbel\Desktop\APPS\SANITEST`.
> Working product name: **SaniExam**. Repository is a near-empty Android skeleton (no source, no Gradle wrapper, no test runner). SDD init completed; `strict_tdd: false`.
> Stack target (declared by `.github/skills/Agent.md`): Kotlin 1.9+, Android minSdk 24 / targetSdk 34, Jetpack Compose + Navigation Compose, Hilt, Coroutines + StateFlow, Room + Retrofit, offline-first, Clean Architecture (presentation / domain / data).

---

### 1. Problem Statement

Aspirants preparing for Spanish public-sector competitive exams ("oposiciones") must internalize a large, mostly stable corpus of official past-exam questions and their correct answers, often across multiple subjects and years. They study in fragmented blocks of free time (commute, breaks, between work shifts) and frequently without reliable connectivity (underground transit, rural academies). Existing generic flashcard tools lack a curated official-exam dataset, and exam-specific sites are usually web-only and have no offline review loop. SaniExam closes the gap by combining a curated database of official past-exam questions with a modern spaced-repetition scheduler (FSRS) so the right question reappears at the right moment, fully on-device, with optional progress sync.

### 2. Target Users

| Persona | Profile | Primary need |
| --- | --- | --- |
| "Opositor/a en activo" | Adult, 25–55, working full-time, studying 30–90 min/day, commutes on metro/regional rail. | Reliable offline study, predictable daily queue, retention analytics. |
| "Preparador/a de academia" | Trainer who curates content for a cohort; may want to push subject packs to students. | Authoring/packaging of question banks; per-cohort progress visibility. |
| "Re-aparejado/a" | Candidate re-attempting the exam, already familiar with the syllabus, uses targeted weak-topic drilling. | Topic/taxonomy filtering, weak-area heatmap, high review density on unstable items. |
| "Estudiante sin temario propio" | Candidate using the app as the primary source of practice questions for a specific oposición. | Trust signal on question provenance, official-source attribution, periodic dataset refreshes. |

Geographic and language context: Spanish-speaking market; UI copy and content will be in Spanish (es-ES by default), but technical artifacts in this SDD process default to English per the language contract.

### 3. Core Use Cases

1. **Onboarding & dataset selection** — Pick a target oposición / subject pack, see estimated card count, last-updated timestamp, and source attribution. Confirm the local install.
2. **Daily review session** — The app shows today's due queue (ordered by FSRS priority). For each item: present the question (with optional image, official-year stamp, source PDF reference), collect a self-rating, reveal the correct answer with explanation, schedule the next review.
3. **Targeted practice (no scheduling)** — User picks a topic/subject block and runs a non-FSRS practice test (e.g. 20 random items) with optional timer. Does not perturb the FSRS state of those items, or perturbs it lightly via a "leech" mode.
4. **Browse & manage questions** — Search by text, topic, official-year, or "marked/unmarked" status. Flag, report, or annotate a question.
5. **Stats & weak-topic insights** — Streak, daily reviews, retention rate (rolling 30-day), leech list (highest-lapse items), per-topic mastery heatmap.
6. **Dataset update** — Pull a newer version of a question pack (over Wi-Fi only by default). Show a diff of "new / changed / removed" questions before applying. Preserve the user's per-card FSRS state for unchanged questions.
7. **Backup & restore** — Local export/import of progress (FSRS state) as a file, to recover after a device change without a server.

Out of scope for the first slice: social features, multi-device real-time sync, in-app purchases, push notifications, web client.

### 4. Domain Model Candidates

Pure-Kotlin domain (no Android types). Mapping to entities is deferred to the design phase.

```
Topic                    (id, parentId?, name, slug, description)
                         — hierarchical taxonomy (e.g. "Derecho Constitucional" > "Título I").

SubjectPack              (id, slug, displayName, description, version, sourceAttribution,
                          publishedAt, totalQuestions)
                         — a curated, versioned corpus for one oposición/subject.

Question                 (id, packId, topicId, officialYear?, officialSourceRef?,
                          prompt, explanation?, mediaUrls[], difficultyHint?,
                          isActive)
                         — stable, immutable per pack version; FSRS state lives elsewhere.

Option                   (id, questionId, ordinal, text, isCorrect, rationale?)

CardState (FSRS)         (id, questionId, packVersion, schedulerVersion,
                          difficulty, stability, retrievability, dueAt, lastReviewedAt,
                          reps, lapses, suspended)
                         — one row per (user, question, pack version). Mutable over time.
                         — Indexes: dueAt for the daily queue; questionId+packVersion unique.

ReviewLog                (id, questionId, reviewedAt, rating, elapsedDays, scheduledDays,
                          previousIntervalDays, newIntervalDays)
                         — append-only history. Powers stats, retention rate, and
                           future FSRS parameter re-fitting.

UserSettings             (id, dailyNewLimit, dailyReviewLimit, learningStepsMinutes[],
                          ratingButtons, theme, language, notificationsEnabled)

DatasetVersion           (id, packId, version, fetchedAt, appliedAt, bytes, checksum,
                          questionCount, status)
                         — supports the update-with-diff flow.
```

Notes for the proposal phase:
- `CardState` carries `packVersion` so updating a pack can preserve the user's review history on unchanged questions.
- `Question` is treated as immutable per version; corrections ship as a new version, not a silent overwrite.
- `ReviewLog` is the long-term analytical asset; designing it as append-only from day one avoids painful migrations later.

### 5. FSRS / Spaced-Repetition Implications

**Algorithm choice.** Use FSRS (Free Spaced Repetition Scheduler, the SSP-MMC family; available in Anki from 23.10 and in RemNote from 1.16). FSRS models per-card `difficulty` (D), `stability` (S), and `retrievability` (R), and uses a small set of user-tuned weights (`w[]`, 17 or 19 numbers) that can be re-fit from a user's own `ReviewLog` history.

**Kotlin/JVM library reality check (important).** There is no first-party FSRS library from the upstream authors. The reference implementation is `ts-fsrs` (TypeScript); `py-fsrs` and Rust ports exist. For Android the realistic options are:
- (a) Port the algorithm directly in Kotlin (it is small: a handful of update formulas). Lowest dependency, full control, easiest to test deterministically.
- (b) Use a community port (e.g. `fsrs4j` style projects). Risk: small community, possible bit-rot, ambiguous license.
- (c) Embed a Rust core via JNI. Overkill for v1; revisit if the team needs to share the core with an iOS or server build.

**Recommended for the first slice:** option (a) — a pure-Kotlin module `:core:scheduler` with a tiny, well-tested FSRS engine. The algorithm is small enough that ownership beats reuse here.

**Rating scale.** Adopt the 4-button scale (Again / Hard / Good / Easy) rather than SM-2's 0–5. It matches modern FSRS inputs and is what Anki users expect.

**Re-fitting.** Defer to v1.1: ship default weights, persist `ReviewLog` from day one so a future release can fit user-specific weights server-side or on-device.

**Scheduler version field.** Store `schedulerVersion` on `CardState` so the algorithm can evolve without invalidating stored state.

**"No perturb" practice tests.** When a user runs a non-FSRS practice round, the simplest correct behavior is to NOT write a `ReviewLog` and NOT touch `CardState` (so the FSRS schedule stays intact). This needs an explicit spec decision in the proposal phase.

### 6. Offline-First / Local Database Implications

- **Room is mandatory.** The app's source of truth is the on-device DB. The repository pattern (per `android-data-layer` skill) exposes Flows from DAOs; the UI never reads from the network directly.
- **Stale-while-revalidate** for the dataset manifest (pack list, latest version), but the question/options data is fully local after first install — the user should be able to study with airplane mode on day one after the install.
- **Dataset ingest is a background task.** Use `WorkManager` with a constraint of `NetworkType.UNMETERED` for pack downloads, so the user does not burn mobile data by accident.
- **Migrations are a first-class concern.** Room migrations are needed when `CardState` evolves (e.g., scheduler weight changes, new columns). The spec must call this out so we do not "lock" early users out of their history.
- **Backups are local-only for v1.** A `WorkManager` job exports an encrypted JSON (or SQLite snapshot) to user-chosen storage (app-scoped storage by default). Real cloud sync is post-v1.
- **No outbox pattern needed in v1.** There is no server to sync writes to; reviews are purely local. When sync is added later, the append-only `ReviewLog` design will be the basis.

### 7. Official-Exam Question Dataset Implications

This is the single largest real-world risk for the product. It must be solved at the product/policy layer, not the code layer.

- **Provenance and licensing.** Spanish public-sector exams are typically published by the corresponding body (INAP, ministries, autonomous communities, local councils). Copyright status varies: many are "open" by law (BOE, official bulletins), others are owned by the publishing academy. The dataset policy must say: (1) which sources are ingested, (2) the license they are released under, (3) attribution shown in-app, and (4) opt-out / takedown procedure.
- **Import pipeline, not hand-typed content.** The architecture must support a versioned, importable dataset (JSON/SQLite/CSV). Sources should be referenced by a stable `sourceRef` and `checksum`. This is what makes "update a pack without losing user state" possible.
- **Editorial layer.** A question entry needs more than a prompt + answer: topic, official year, source PDF page, an explanation (rationale), and at least one citation. Without these, the product is just another quiz app.
- **Content quality feedback loop.** Users must be able to report a wrong answer or a broken source link. The spec must define what happens to a "reported" question (badge only? auto-suspend? review queue?).
- **Leech handling.** FSRS already surfaces leeches (items with many lapses), but a product decision is needed: cap consecutive lapses before auto-suspending? Offer a "doctor" / "rewrite explanation" affordance?
- **Size estimation.** A typical oposición subject pack is on the order of 1k–10k questions. Across multiple packs, the DB stays well under 100 MB, which is fine for an Android app. The concern is not size; it is update bandwidth and perceived freshness.

### 8. Android Native Architecture Implications

Aligned with the project-declared stack (`.github/skills/Agent.md`) and the existing `android-architecture` / `android-data-layer` / `android-viewmodel` skills.

**Module layout (proposed for the design phase, not enforced here).**
```
:app                       — entry point, navigation graph, theming
:core:model                — pure-Kotlin domain types
:core:domain               — use cases + repository interfaces
:core:data                 — Room, DAOs, repositories, WorkManager workers
:core:scheduler            — pure-Kotlin FSRS engine (no Android deps)
:core:ui                   — theme, common composables
:feature:home              — daily queue, due count, streak
:feature:review            — single-card review screen
:feature:practice          — non-scheduled practice tests
:feature:library           — browse packs, topics, search
:feature:stats             — analytics & weak-topic heatmap
:feature:settings          — daily limits, scheduler knobs, backup
```
Trade-off: this is a multi-module setup. For a brand-new project with no code yet, it is the right long-term shape but adds Gradle/Convention-Plugin work upfront. An acceptable intermediate is a single-module version with strict internal package boundaries (`presentation/`, `domain/`, `data/`, `scheduler/`) and a documented migration path to multi-module.

**Concurrency.** All FSRS computation is CPU-bound and microsecond-scale; it can run on `Dispatchers.Default`. Room/IO stays on `Dispatchers.IO` (injected, per the agent marker). ViewModels expose `StateFlow<UiState>` and `SharedFlow<UiEvent>` (per `android-viewmodel` skill). A "due queue" is naturally a `Flow` from a `WHERE dueAt <= now` DAO query.

**UI specifics for review screens.**
- Single-card composable with reveal-on-tap; the rating buttons (Again/Hard/Good/Easy) appear only after reveal.
- A "reschedule" preview (e.g. "Good → in 4 days") is a high-value, low-cost UX touch that Anki users expect.
- Compose state hoisting for the per-card state. Avoid side effects in composables.
- Accessibility (per `android-accessibility` skill): every rating button must have a content description, and the question prompt must expose itself to screen readers.

**Background work.** `WorkManager` periodic worker for dataset version checks (constraint: `UNMETERED`, backoff). One-shot worker for dataset import. Constraints and idempotency are spec items, not implementation details.

**Testing reality (must be flagged).** `openspec/config.yaml` has `strict_tdd: false` and no runner. This means the proposal phase must decide: do we add JUnit + Turbine + MockK + Compose Test Rule as part of the first slice, or do we ship the FSRS engine unit-tested only and defer the rest? The cheapest safe path is to enable the unit-test layer for `:core:scheduler` and `:core:domain` first, since that is where the highest-risk logic lives. The full pyramid comes in slice 2.

### 9. First-Slice Scope Candidates

The product is large. The first slice must be small, end-to-end, and shippable. The candidate below is one valid choice; the proposal phase should confirm or re-scope with the user.

**First-slice: "Single-pack, offline, FSRS-driven daily review."**
- 1 Gradle module (`:app`) with strict internal package boundaries, no multi-module.
- 1 question pack ingested from a bundled JSON asset (offline, no network, no WorkManager).
- Pure-Kotlin FSRS engine, unit-tested with JUnit + a hand-rolled golden-file test against a known `ts-fsrs` output.
- Daily review screen with the 4-button rating and a reschedule preview.
- One stats screen (streak, total reviews, retention rate over the last 30 days).
- Theme + light/dark + Spanish UI strings.
- No dataset updates, no cloud, no auth, no analytics, no practice tests beyond review.

Acceptance gate for the slice: a user can install the app, run ~30 reviews, see the schedule adapt (later intervals for Good, shorter for Again), and the DB survives a process kill.

**Next slices (sketch only, not for implementation now).**
- Slice 2: Multi-pack library, topic browsing, in-app pack switcher, Room migrations, JUnit/Turbine coverage on ViewModels.
- Slice 3: Dataset update flow with `WorkManager`, diff preview, in-app pack download from a manifest endpoint.
- Slice 4: Practice-test mode, leech list, weak-topic heatmap.
- Slice 5: Local backup/restore, optional opt-in cloud sync (likely out-of-band, post-launch).

### 10. Unknowns / Questions for the Proposal Phase

1. **Source of truth for the dataset.** Who curates the official questions? Is it the user, an internal team, a partner academy, or a public scrape? This determines licensing, attribution, and the import pipeline shape.
2. **V1 scope confirmation.** Is the "single-pack, offline, FSRS-driven daily review" slice the right v1, or does the user want the practice-test mode in v1 even at the cost of less polish on review?
3. **Multi-module from day one, or single-module then split?** Multi-module is cleaner long-term but adds Gradle overhead. The user is the right person to pick.
4. **Scheduler weights.** Ship with FSRS defaults? Allow user override? Re-fit on-device later? Default plan is: ship defaults, persist `ReviewLog`, defer re-fit.
5. **Localization.** Spanish only for v1, or also Basque/Catalan/Galician co-official? Affects string strategy and content policy.
6. **Telemetry.** Strictly local, or opt-in anonymous telemetry from day one? Affects permission/manifest choices.
7. **Testing depth for v1.** Confirm the proposal should add the unit-test layer (JUnit + Turbine) for the FSRS engine and use cases, even with `strict_tdd: false`.
8. **Device minimum.** Is `minSdk 24` firm, or can we go higher (e.g. 26) to drop some compatibility shims? Affects how aggressively we use modern APIs.
9. **Brand and naming.** "SaniExam" is the working product name in `openspec/config.yaml`. The user must confirm this is the final brand and decide on app icon, package id (`com.sanitest.saniexam`?), and store listing.
10. **Review budget.** With 400-line PR budget and an empty repo, the first slice will almost certainly need chained PRs (Gradle skeleton, FSRS engine, data layer, review screen, stats, polish). The proposal should call this out so the apply phase can plan slicing from the start.

### 11. Risks and Assumptions

**Assumptions.**
- A1. The user wants Android-first. (Stack is already declared Android.)
- A2. Spanish UI is acceptable for v1; English-only technical artifacts are acceptable (per the language contract).
- A3. Offline-first is a hard requirement, not a nice-to-have. (Stack says offline-first.)
- A4. The user is willing to add the unit-test layer (JUnit + Turbine) for the FSRS engine in the first slice, even though `strict_tdd: false`.
- A5. No server, no auth, no multi-device sync in v1. Local backup/restore is acceptable as a v1 escape hatch.

**Risks.**
- R1. **Dataset provenance / legal.** If questions are taken from copyrighted prep academies without a license, the product can be taken down. The first action in the proposal phase must be a content-licensing policy, not a coding task.
- R2. **No first-party FSRS library for Kotlin.** A from-scratch Kotlin port is the realistic path, but it means we own the algorithm's correctness. Mitigation: golden-file tests against a known `ts-fsrs` output and a small fuzz-test suite.
- R3. **Schema evolution breaks user history.** `CardState` will evolve (new fields, new scheduler version). If migrations are sloppy, returning users lose their schedule. Mitigation: design migrations as a hard requirement from day one and write migration tests.
- R4. **Empty repo + multi-module temptation.** Trying to set up the full module layout and the FSRS engine in one PR blows the 400-line budget and degrades review quality. Mitigation: scope first slice as single-module with strict package boundaries, defer multi-module to slice 2.
- R5. **Content staleness.** Spanish oposición syllabi change. If pack updates are deferred, users lose trust. Mitigation: at minimum, a "last updated" badge and a clear roadmap date for the update flow.
- R6. **Review budget churn.** A full first slice (Gradle + FSRS + data + review screen + stats) easily exceeds 400 lines. Mitigation: apply phase must propose chained PRs.
- R7. **TDD is off.** `strict_tdd: false` means the verification phase cannot rely on a full test run. Mitigation: at minimum, gate the FSRS engine on unit tests; treat the rest as "manual smoke + emulator scripts" per `android-emulator-skill`.
- R8. **FSRS defaults may be a poor fit.** A new user with no history gets default weights, which are tuned for an "average" Anki user. Mitigation: ship with defaults, persist `ReviewLog` from day one, document a v1.1 re-fit.
- R9. **"Practice test" feature confusion.** If practice tests also write to `CardState` / `ReviewLog`, the user's FSRS schedule gets perturbed by study mode, which is the wrong default. Mitigation: explicit "no perturb" behavior, to be locked in the spec.
- R10. **Naming drift.** "SaniExam" is a working name and the project folder is `SANITEST`. Without a final naming decision, package ids, build configs, and store listings will churn. Mitigation: name-locking question is item #9 in the unknowns list.

---

### Ready for Proposal

**Yes, with conditions.** This exploration is complete enough to enter `sdd-propose`, but the proposal phase should NOT skip these two gates:

1. **Naming + branding gate.** Confirm "SaniExam" as the final product name, app package id, and store-listing identifier. Without this, the proposal's scope items will be wrong.
2. **Dataset-licensing gate.** Decide who owns the question content and under what license it is shipped. The proposal must call this out as a precondition, not as a code task; otherwise the build schedule is fiction.

The orchestrator should hand the user the 10 unknowns in section 10 and the 2 gates above, then run `sdd-propose` once at least the two gates are answered.
