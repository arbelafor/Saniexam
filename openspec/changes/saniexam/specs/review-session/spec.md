# Review Session Specification

## Purpose

The daily FSRS review flow: a queue of due cards, reveal-on-tap, the four-button rating, reschedule preview, and the append-only `ReviewLog` that records every rating.

## Requirements

### Requirement: Daily Due Queue

The system MUST present a "due today" queue as `CardState.dueAt <= now() AND suspended = false`, ordered most-overdue first.

#### Scenario: Queue on open

- GIVEN N cards with `dueAt <= now` and M with `dueAt > now`
- WHEN the user opens Review mode
- THEN the queue contains exactly the N due cards
- AND the first card has the smallest `dueAt`

#### Scenario: Suspended excluded

- GIVEN a card is `suspended = true`
- WHEN the queue is computed
- THEN that card is excluded and absent from the Home due count

#### Scenario: Empty queue

- GIVEN no card is due
- WHEN the user opens Review mode
- THEN the system shows "no hay repasos pendientes" in Spanish
- AND does NOT auto-advance to a future-dated card

### Requirement: Reveal-on-Tap and Rating Flow

The system MUST hide the correct option(s) until the user taps "Mostrar respuesta", and MUST NOT show rating buttons until reveal.

#### Scenario: Pre-reveal state

- GIVEN a card is on screen
- WHEN the user has not tapped reveal
- THEN the prompt and options are visible, the correct option is not visually marked, and the four rating buttons are hidden or disabled

#### Scenario: Post-reveal state and a11y

- GIVEN the user has tapped reveal
- WHEN the screen updates
- THEN the correct option(s) are visually marked, the optional `explanation` is shown, and the four rating buttons are enabled
- AND under TalkBack each button has a Spanish `contentDescription` of the form `Calificar como <Again|Hard|Good|Easy>`
- AND the reveal action has `contentDescription = "Mostrar respuesta"`

### Requirement: Reschedule Preview

The system MUST show, for each of the four ratings, the resulting next-due interval before commit. The preview MUST come from the same engine that persists the update.

#### Scenario: Preview shown and read-only

- GIVEN a revealed card
- WHEN the rating row renders
- THEN each button shows a short Spanish hint of the resulting interval
- AND the four hints respect the `Again < Hard < Good < Easy` ordering
- AND no `CardState` write, no `ReviewLog` append, and no `dueAt` change has occurred yet

### Requirement: Persisted Rating and Append-Only ReviewLog

The system MUST persist every committed rating by (a) updating `CardState` through the FSRS engine, and (b) appending one immutable `ReviewLog` row. The system MUST NOT update or delete any existing `ReviewLog` row.

#### Scenario: Commit Good

- GIVEN a revealed card and the user taps `Good`
- WHEN the rating is committed
- THEN `CardState` is replaced with the engine's output (new `dueAt`, `stability`, `difficulty`, `reps`, `lastReviewedAt = now`)
- AND exactly one `ReviewLog` row is appended with `rating=Good`, `reviewedAt=now`, `elapsedDays`, `scheduledDays`, `previousIntervalDays`, `newIntervalDays`

#### Scenario: Append-only and queue advance

- GIVEN `ReviewLog` contains rows `R1..Rn` and the user commits any rating
- WHEN the commit completes
- THEN `ReviewLog` contains exactly `R1..Rn` plus one new row `Rn+1`
- AND no existing row `Ri` (i <= n) has been mutated or deleted
- AND the UI advances to the next due card or shows the empty state
- AND the just-rated card does not reappear in the same session

### Requirement: Interrupt and Resume

The system MUST survive process death mid-session: queue position, reveal state, and unsaved draft answers MUST be recoverable on next launch.

#### Scenario: Process killed mid-card

- GIVEN the user is on card C, has revealed it, and has not committed a rating
- WHEN the OS kills the app process and the user reopens the app
- THEN Review mode resumes on card C with the same reveal state and any draft selection intact
- AND no `ReviewLog` row is appended for the un-committed interaction
