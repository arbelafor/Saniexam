# FSRS Scheduler Specification

## Purpose

Contract of the pure-Kotlin FSRS engine that computes the next review time of every question. Highest-risk piece of v1 (no first-party Kotlin FSRS library); correctness MUST be guaranteed by golden tests against a pinned reference.

## Requirements

### Requirement: Pure-Kotlin, Deterministic, Versioned Engine

The system MUST expose a pure-Kotlin FSRS engine that is deterministic for input (`currentState`, `rating`, `now`), and MUST persist `schedulerVersion` on every `CardState` it updates.

#### Scenario: Deterministic scheduling

- GIVEN a `CardState` with known `difficulty`, `stability`, `dueAt`, `lastReviewedAt`, `reps`, a `Rating=Good`, and a fixed `now`
- WHEN the scheduler is invoked
- THEN the resulting `CardState` and `dueAt` are byte-identical across repeated invocations

#### Scenario: schedulerVersion persisted

- GIVEN the engine produces a new `CardState` from a rating event
- WHEN the repository persists the new state
- THEN `CardState.schedulerVersion` equals the engine's `SCHEDULER_VERSION`

#### Scenario: Version mismatch handled

- GIVEN a `CardState` row with `schedulerVersion=1` and the engine is at `SCHEDULER_VERSION=2`
- WHEN the scheduler is invoked on that row
- THEN the system MUST either re-initialize through a documented migration OR refuse the update with a typed error
- AND MUST NOT silently mix v1 and v2 math on the same card

### Requirement: Four-Button Rating Contract

The system MUST accept exactly `Again`, `Hard`, `Good`, `Easy` and MUST satisfy `Again < Hard < Good < Easy` interval ordering for any non-empty input state.

#### Scenario: Rating ordering invariant

- GIVEN any non-empty `CardState`
- WHEN the scheduler is invoked four times with the same `now` and each rating
- THEN `dueAt - now` satisfies `Again < Hard < Good < Easy`

#### Scenario: Again and Easy shape the schedule

- GIVEN a card with `reps >= 3`, `lapses = 0`, non-trivial `stability`
- WHEN the user rates `Again`
- THEN `lapses` increases by 1, the new `stability` is strictly less than the previous, and the new `dueAt` is sooner
- AND when the user rates `Easy` on a card with `reps >= 1`, `dueAt - now` is strictly greater than the `Good` outcome for the same starting state

### Requirement: Reschedule Preview

The system MUST compute, without persisting, the next-state preview for any of the four ratings. The Review UI MUST show this preview before the user commits.

#### Scenario: Preview is read-only and matches commit

- GIVEN a card is on screen in Review mode
- WHEN the UI requests previews for all four ratings
- THEN the engine returns four candidate `CardState` values
- AND no `CardState` write, no `ReviewLog` append, and no `dueAt` change has occurred
- AND if the preview for `Good` was displayed at `now=T` and the user commits `Good` at `now=T+epsilon` within the same session, the persisted `CardState.dueAt` equals the previewed `dueAt` plus a bounded skew explained by the time delta only

### Requirement: Golden Tests Against Reference Implementation

The system MUST ship a golden-file test suite that pins engine output against a known `ts-fsrs` reference. Golden files MUST be checked in and any drift MUST fail CI.

#### Scenario: Golden match, fuzz, and drift

- GIVEN `golden/fsrs-cases.json` is checked in
- WHEN the suite runs
- THEN every case matches the reference to the documented tolerance (date fields exact, FP fields within `1e-9`)
- AND a 1000-case fuzz triple run never throws, never returns NaN/Infinity, and preserves the rating-ordering invariant
- AND if a developer change diverges, the developer MUST regenerate the golden file with a documented, reviewable command (no hand-edits)

### Requirement: No I/O, No Android Dependencies

The engine module MUST NOT depend on Android, Room, Hilt, Compose, or any network library.

#### Scenario: Plain JVM build and test

- GIVEN the `:scheduler` module is compiled
- WHEN the build runs on a plain JVM toolchain
- THEN it succeeds without any Android plugin or SDK on the classpath
- AND JUnit tests run on the same plain JVM toolchain
