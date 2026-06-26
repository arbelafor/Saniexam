# Delta for Dataset Import

## MODIFIED Requirements

### Requirement: Bundled Pack Ingestion on First Launch

The system MUST ingest the bundled `sanidad-v1` pack from `assets/question-packs/` into Room on first launch, and MUST NOT re-ingest the same version on subsequent launches. The ingest path MUST resolve the active pack through `user_settings.active_category` and MUST refuse to import a pack whose `category` does not match the active category.

(Previously: ingest did not filter by category and did not validate pack-level `category` field.)

#### Scenario: Cold first launch with bundled pack

- GIVEN no prior local DB and `sanidad-v1` v1 in assets and `active_category="TCAE"`
- WHEN the user completes onboarding
- THEN every `Question`, `Option`, `Topic`, `SubjectPack` row for v1 is persisted
- AND the `SubjectPack` row's `category` column equals `"TCAE"`
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

#### Scenario: Pack category does not match active category

- GIVEN the bundled `sanidad-v1` pack has `category="TCAE"` but `active_category` is set to a different category
- WHEN the import runs
- THEN the pack is rejected with a clear error
- AND the local DB is unchanged

### Requirement: Pack Validation and Rejection

The system MUST validate the pack against a strict schema before persisting any row, and MUST reject the entire pack on the first validation error. The validator MUST also reject any question with a blank or missing `officialSourceRef`.

(Previously: validator checked structural rules only; it did not reject questions missing `officialSourceRef`.)

#### Scenario: Valid pack

- GIVEN the asset matches the schema (pack id, version, checksums, referenced topics present, every question has exactly one correct option, every question has a non-blank `officialSourceRef`)
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

#### Scenario: Question missing provenance

- GIVEN a question has a blank or missing `officialSourceRef`
- WHEN validation runs
- THEN the pack is rejected with a `ProvenanceMissing` reason naming the offending `questionId`
- AND the release pipeline gate fails closed on this pack

### Requirement: Official-Source Metadata and Provenance

The system MUST store provenance fields per pack and per question, and MUST surface attribution in the UI. The release pipeline MUST fail closed when a pack's manifest license is in the refused set OR when the pack or any question lacks the required provenance fields.

(Previously: license gate failed closed only on refused license strings; pack-level `category` and per-question `officialSourceRef` were not enforced as gate inputs.)

#### Scenario: Per-pack attribution

- GIVEN a `SubjectPack` is persisted
- THEN it MUST also persist `sourceAttribution`, `publishedAt` (ISO-8601), `license`, `licenseNotes`, and `category`
- AND the Settings screen MUST show this attribution verbatim

#### Scenario: Per-question source reference

- GIVEN a `Question` is persisted
- THEN it MUST persist `officialYear` (nullable int), `officialSourceRef` (non-blank string), and `sourcePackId` (FK)
- AND the question detail MUST display `officialYear` and `officialSourceRef`

#### Scenario: License gate before public distribution

- GIVEN a build is being prepared for a public distribution channel
- WHEN the release pipeline runs
- THEN it MUST fail closed if any active `SubjectPack.license` is `unknown`, empty, null, OR matches a refused license in any case variant
- AND it MUST fail closed if the manifest is missing a `category` field
- AND it MUST fail closed if any question in the pack is missing a non-blank `officialSourceRef`

### Requirement: Immutability Within a Version

The system MUST treat questions as immutable within a given `packVersion`; corrections MUST be delivered as a new version.

#### Scenario: No silent edits

- GIVEN `sanidad-v1` v1 is applied with question `Q1` prompt "..."
- WHEN the user reopens the app
- THEN `Q1.prompt` is byte-identical to the first-import value
- AND any correction to `Q1` requires a new `SubjectPack` version (e.g. v2), never a direct row update
