# Exam Simulation Specification

## Purpose

A timed mock-exam mode over a fixed question set, binary-scored, intentionally decoupled from the FSRS scheduler — it MUST NOT perturb the user's review schedule.

## Requirements

### Requirement: Timed Mock Exam Session

The system MUST allow the user to start a mock exam over a fixed, pre-defined set with a countdown timer and a results screen.

#### Scenario: Start an exam

- GIVEN the user is on Home and a bundled pack is installed
- WHEN the user taps "Iniciar simulación"
- THEN the system opens an exam session with a deterministic question set, ordered presentation, and a running timer
- AND the FSRS due-queue view is not shown during the session

#### Scenario: Timer expires

- GIVEN an exam session is in progress and the timer reaches zero
- WHEN the user has not submitted
- THEN the system auto-submits with whatever answers are currently selected
- AND unanswered questions are scored as incorrect

#### Scenario: User submits early

- GIVEN an exam session is in progress
- WHEN the user taps "Entregar"
- THEN the timer stops, the session is scored, and the results screen is shown

### Requirement: Binary Correct/Incorrect Scoring

The system MUST score each question as `correct` or `incorrect` only, with no partial credit or confidence field.

#### Scenario: Single-correct scoring

- GIVEN a question with exactly one `isCorrect=true` option
- WHEN the user selects that option and submits
- THEN the question is scored `correct`; otherwise `incorrect`

#### Scenario: Multi-correct schema

- GIVEN a question with multiple `isCorrect=true` options
- WHEN the user submits
- THEN the question is scored `correct` only if the user's selected set equals the full correct set, else `incorrect`

### Requirement: No FSRS Perturbation

The system MUST NOT write to `CardState` and MUST NOT append to `ReviewLog` as a result of an exam session, regardless of answers.

#### Scenario: CardState untouched

- GIVEN a card has `CardState` with `dueAt=D` and `reps=N` before an exam
- WHEN the user submits the exam
- THEN the same card still has `dueAt=D` and `reps=N` immediately after

#### Scenario: ReviewLog untouched

- GIVEN `ReviewLog` contains `R` rows before an exam
- WHEN the user completes a 50-question exam
- THEN `ReviewLog` still contains exactly `R` rows after the results screen

### Requirement: Results Screen

The system MUST show a results screen with total correct, total incorrect, percentage, elapsed time, and a per-question review list with selected vs correct answer.

#### Scenario: Results summary

- GIVEN the user submits a 50-question exam with 38 correct, 12 incorrect, in 42 minutes
- WHEN the results screen renders
- THEN it shows `Correctas: 38`, `Incorrectas: 12`, `Nota: 76%`, `Tiempo: 42:00`
- AND a scrollable list of 50 rows, each with the prompt, the user's selection, and the correct answer

#### Scenario: Re-attempt guard

- GIVEN a user just completed an exam
- WHEN the user returns to Home
- THEN the FSRS due-queue view is unchanged
- AND no rating affordance is offered for any exam question
