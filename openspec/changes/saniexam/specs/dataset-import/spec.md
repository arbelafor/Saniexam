# Dataset Import Specification

## Purpose

Ingest a versioned, checksummed question pack from a bundled asset into the local Room database on first launch. Provenance is a hard product gate.

## Requirements

### Requirement: Bundled Pack Ingestion on First Launch

The system MUST ingest the bundled `sanidad-v1` pack from `assets/question-packs/` into Room on first launch, and MUST NOT re-ingest the same version on subsequent launches.

#### Scenario: Cold first launch with bundled pack

- GIVEN no prior local DB and `sanidad-v1` v1 in assets
- WHEN the user completes onboarding
- THEN every `Question`, `Option`, `Topic`, `SubjectPack` row for v1 is persisted
- AND a `DatasetVersion` row is recorded with `status=applied`, `bytes=assetSize`, `checksum=SHA-256(asset)`
- AND Home shows a non-zero question count

#### Scenario: Subsequent launch with same version

- GIVEN local DB already contains `sanidad-v1` v1
- WHEN the app launches
- THEN the ingest path is a no-op (no duplicate rows, no extra `DatasetVersion`)

#### Scenario: Bundled asset missing or unreadable

- GIVEN the asset is absent or fails checksum verification
- WHEN the import runs
- THEN a blocking Spanish error is shown
- AND the local DB is unchanged (transaction rolls back)

### Requirement: Pack Validation and Rejection

The system MUST validate the pack against a strict schema before persisting any row, and MUST reject the entire pack on the first validation error.

#### Scenario: Valid pack

- GIVEN the asset matches the schema (pack id, version, checksums, referenced topics present, every question has exactly one correct option)
- WHEN validation runs
- THEN every row is persisted and `DatasetVersion.status=applied`

#### Scenario: Question with zero or multiple correct options

- GIVEN a question has 0 or >1 `isCorrect=true` options
- WHEN validation runs
- THEN the pack is rejected and the offending `questionId` is reported

#### Scenario: Orphan topic reference

- GIVEN a question references a `topicId` that does not exist in the pack
- WHEN validation runs
- THEN the pack is rejected with a clear error

### Requirement: Official-Source Metadata and Provenance

The system MUST store provenance fields per pack and per question, and MUST surface attribution in the UI.

#### Scenario: Per-pack attribution

- GIVEN a `SubjectPack` is persisted
- THEN it MUST also persist `sourceAttribution`, `publishedAt` (ISO-8601), `license`, and `licenseNotes`
- AND the Settings screen MUST show this attribution verbatim

#### Scenario: Per-question source reference

- GIVEN a `Question` is persisted
- THEN it MUST persist `officialYear` (nullable int), `officialSourceRef` (nullable string), and `sourcePackId` (FK)
- AND the question detail MUST display `officialYear` and `officialSourceRef` when present

#### Scenario: License gate before public distribution

- GIVEN a build is being prepared for a public distribution channel
- WHEN the release pipeline runs
- THEN it MUST fail closed if any active `SubjectPack.license` is `unknown`, empty, or null

### Requirement: Immutability Within a Version

The system MUST treat questions as immutable within a given `packVersion`; corrections MUST be delivered as a new version.

#### Scenario: No silent edits

- GIVEN `sanidad-v1` v1 is applied with question `Q1` prompt "..."
- WHEN the user reopens the app
- THEN `Q1.prompt` is byte-identical to the first-import value
- AND any correction to `Q1` requires a new `SubjectPack` version (e.g. v2), never a direct row update

### Requirement: Remote Dataset Update (OUT OF MVP)

The system MUST NOT include any network-based pack fetch path in v1. Any future update flow MUST be a separate change.

#### Scenario: v1 has no network code

- GIVEN the v1 build is shipped
- WHEN the app runs
- THEN no `WorkManager` job, HTTP client, or manifest endpoint exists for fetching a newer pack
- AND the only pack source is the bundled asset
