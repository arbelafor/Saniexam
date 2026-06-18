# Progress Backup Specification

## Purpose

User-initiated local export of all user-generated state to a single file, and an import that replaces the current state on confirmation. This is the v1 escape hatch for device changes; cloud sync is explicitly out of scope.

## Requirements

### Requirement: User-Initiated Local Export

The system MUST allow the user to export a single backup file containing the user's full progress state. The export path MUST be user-chosen (app-scoped storage by default).

#### Scenario: Successful and empty export

- GIVEN the user is on Settings and taps "Exportar progreso"
- WHEN the export completes (including a DB with zero reviews)
- THEN a single file is written containing every `CardState` row, every `ReviewLog` row, and the singleton `UserSettings` row
- AND a Spanish confirmation is shown: "Progreso exportado"
- AND the file's MIME is `application/json` and its name is `saniexam-backup-<ISO-8601 timestamp>.json`
- AND a zero-review DB still produces a valid backup with empty arrays

### Requirement: Backup File Schema and Checksum

The file MUST include a top-level `schemaVersion` and a SHA-256 `checksum` over the payload. Imports MUST be atomic.

#### Scenario: Valid import, corrupt refusal, and future schema

- GIVEN a backup file with `schemaVersion=1`
- WHEN the user confirms import
- THEN every row is written inside a single Room transaction
- AND a failure mid-import leaves the DB unchanged
- AND if the `checksum` does not match the payload, the system refuses with a clear Spanish error and applies no row
- AND if `schemaVersion` exceeds the app's current supported value, the system refuses with a Spanish message instructing the user to update the app

### Requirement: User-Confirmed Destructive Import

The system MUST treat import as destructive and require explicit confirmation. The default flow MUST take a pre-import snapshot and offer to restore it within the same session.

#### Scenario: Confirm dialog and revert

- GIVEN the user has selected a valid backup file
- WHEN the import flow is about to overwrite the current DB
- THEN a Spanish confirmation dialog lists what will be replaced (`CardState`, `ReviewLog`, `UserSettings`)
- AND requires an explicit "Sí, reemplazar" tap
- AND if the user taps "Deshacer importación" within the same session, the snapshot is restored atomically and the DB is byte-equivalent to its pre-import state

### Requirement: Excluded Data and Privacy

The system MUST exclude bundled question content, dataset version rows, and any telemetry from the backup. Only user-generated state MAY be exported, and no network activity may be a side effect of export.

#### Scenario: Bundled content excluded and no upload

- GIVEN a complete backup file
- WHEN the system inspects it
- THEN it contains no `Question`, `Option`, `Topic`, `SubjectPack`, or `DatasetVersion` rows
- AND no network identifiers or device identifiers
- AND no HTTP request, background job, or WorkManager task is triggered by export

### Requirement: Round-Trip Equivalence

The system MUST be able to export the current DB, then re-import that same file after a wipe, and obtain a functionally equivalent DB.

#### Scenario: Round-trip on same device

- GIVEN the user exports a backup, clears app data, reinstalls, and the bundled pack re-imports creating fresh `Question`/`Option` rows
- WHEN the user imports the previously exported backup
- THEN `COUNT(CardState)`, `COUNT(ReviewLog)`, and the singleton `UserSettings` row all equal the pre-export counts and values
- AND the FSRS queue computed immediately after import equals the queue that was due at export time (modulo elapsed time)

### Requirement: Backup Is the Only Cross-Device Path in v1

The system MUST NOT include any cloud backup, account-bound backup, or automatic-sync mechanism in v1.

#### Scenario: v1 has no cloud code

- GIVEN the v1 build
- WHEN the backup code is inspected
- THEN no network permission, account system, or remote endpoint is referenced
- AND a UI affordance for "cloud backup" or "sign in to sync" does not exist
