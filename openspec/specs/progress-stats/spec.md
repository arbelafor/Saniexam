# Progress Stats Specification

## Purpose

Define the v1 Stats screen: the three numbers a user needs to see at a glance — current streak, total reviews, and 30-day rolling retention — all derived from the append-only `ReviewLog`. Stats are read-only; they never modify state.

## Requirements

### Requirement: Read-Only Derivation From ReviewLog

The system MUST compute every stat exclusively from `ReviewLog` rows. The Stats screen MUST NOT write to `CardState`, `ReviewLog`, or any other table, and MUST NOT trigger any background mutation as a side effect of being opened.

#### Scenario: Opening the Stats screen

- GIVEN the user opens the Stats screen
- WHEN the screen renders
- THEN the displayed numbers equal the result of the documented aggregation queries over `ReviewLog`
- AND no `CardState` row, `ReviewLog` row, or `UserSettings` field has changed

### Requirement: Streak (Días consecutivos)

The system MUST display the current consecutive-day review streak, defined as the number of consecutive days ending today (local time) on which the user committed at least one rating. A day with zero commits breaks the streak.

#### Scenario: Streak with activity today

- GIVEN the user committed at least one rating today and at least one rating on each of the previous 6 days, with zero on the 7th-previous day
- WHEN the Stats screen renders
- THEN the streak shows `7 días`

#### Scenario: Streak broken by a missed day

- GIVEN the user committed ratings on day D-1, missed day D-2, and committed at least one today (D)
- WHEN the Stats screen renders
- THEN the streak shows `1 día` (today only), not `2`

#### Scenario: No activity ever

- GIVEN `ReviewLog` is empty
- WHEN the Stats screen renders
- THEN the streak shows `0 días` and a friendly empty-state message in Spanish

### Requirement: Total Reviews (Total de repasos)

The system MUST display the total count of committed ratings as `COUNT(*)` over `ReviewLog`.

#### Scenario: Total after N reviews

- GIVEN `ReviewLog` contains exactly N rows
- WHEN the Stats screen renders
- THEN the total shows `N repasos` and the number is presented in the user's locale-appropriate thousands separator

### Requirement: 30-Day Rolling Retention

The system MUST display the rolling 30-day retention, defined as the percentage of `ReviewLog` rows in the last 30 days whose rating was `Good` or `Easy`. The window is inclusive of today and uses the device's local timezone.

#### Scenario: All Good or Easy in window

- GIVEN the last 30 days contain 100 `ReviewLog` rows, all rated `Good` or `Easy`
- WHEN the Stats screen renders
- THEN the retention shows `100%` (or `100,0 %` per Spanish locale)

#### Scenario: Mixed ratings

- GIVEN the last 30 days contain 100 `ReviewLog` rows, 70 rated `Good` or `Easy`, 20 `Hard`, 10 `Again`
- WHEN the Stats screen renders
- THEN the retention shows `70%`

#### Scenario: Insufficient history

- GIVEN the user has fewer than 5 `ReviewLog` rows total
- WHEN the Stats screen renders
- THEN the retention area shows a "Datos insuficientes" message and no percentage

### Requirement: Reconciliation With ReviewLog

The system MUST be able to recompute every stat from scratch given the full `ReviewLog`, and the recomputed values MUST equal the live-displayed values within a documented tolerance (zero in v1, since stats are not cached).

#### Scenario: Manual recompute

- GIVEN a debug or test build can invoke the recompute function
- WHEN it runs against the live DB
- THEN the three stats equal the values shown on screen
- AND no row is written by the recompute
