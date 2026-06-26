# Licensed Content Packs Specification

## Purpose

Define the rules for sourcing, licensing, and packaging question content for SaniExam so the release-pipeline license gate can pass and every shipped question has auditable provenance. This spec is the single source of truth for what makes a pack "publishable" under the license gate.

## Requirements

### Requirement: Pack Identity and Content Source

The system MUST ship question packs whose questions are copied literally from official Spanish oposición documents, and MUST identify the pack with a stable `id` and `version` carried in both the pack file and the manifest.

#### Scenario: Stable pack id

- GIVEN the app is built for release
- WHEN the bundled pack is read
- THEN the manifest `id` equals the value the dataset-import spec mandates (`sanidad-v1`)
- AND the manifest `version` is a non-decreasing integer

#### Scenario: Verbatim official-source content

- GIVEN a question `Q` in the pack
- WHEN editorial review audits `Q`
- THEN the `prompt` and every `option.text` match the official document character-for-character
- AND any rephrasing, translation, or paraphrase MUST be rejected at editorial review

### Requirement: Per-Question Provenance

The system MUST require every question to carry a non-blank `officialSourceRef` and a valid `officialYear`, and MUST refuse to import any pack that omits these fields.

#### Scenario: Required provenance fields present

- GIVEN a pack with 80 questions
- WHEN validation runs
- THEN every question has a non-blank `officialSourceRef` and an integer `officialYear`

#### Scenario: Missing provenance blocks release

- GIVEN a pack with one question whose `officialSourceRef` is blank or missing
- WHEN validation runs
- THEN the pack is rejected with a `ProvenanceMissing` reason
- AND the release pipeline license gate fails closed
- AND the local DB is unchanged

#### Scenario: Provenance surfaced in UI

- GIVEN a persisted question with `officialYear=2024` and `officialSourceRef="BOE-A-2024-12345, pregunta 17"`
- WHEN the user opens question detail
- THEN both values are displayed

### Requirement: License String and Refused Set

The system MUST accept a pack whose `license` is not in the refused set, and MUST refuse any pack whose `license` is null, blank, or matches a refused value in any case variant.

#### Scenario: Accepted license passes the gate

- GIVEN a manifest with `license="cleared-of-rights"`
- WHEN the release pipeline license gate runs
- THEN the gate passes

#### Scenario: Refused license in any case variant fails closed

- GIVEN a manifest with `license="Dev-Placeholder"`, `license="DEV-PLACEHOLDER"`, `license="dev-placeholder"`, `license="unknown"`, or `license=""`
- WHEN the release pipeline license gate runs
- THEN the gate fails closed
- AND the error message points the maintainer at the fix path

#### Scenario: Gate is the single source of truth

- GIVEN the pure-Kotlin `PackLicenseGate`, the Gradle task, and the two CI scripts (POSIX bash and PowerShell)
- WHEN any of them runs against the same manifest
- THEN they MUST all reach the same accept/reject decision
- AND the `PackLicenseGate` REFUSED set MUST be mirrored in the Gradle task and both scripts

### Requirement: Licensing Documentation

The system MUST include a repo-root `LICENSING.md` that lists every question, its `officialSourceRef`, the source document, and the rights clearance evidence.

#### Scenario: LICENSING.md covers every question

- GIVEN a pack with N questions
- WHEN the release is prepared
- THEN `LICENSING.md` lists N rows (one per question) with `questionId`, `officialYear`, `officialSourceRef`, and a clearance-evidence column
- AND `LICENSING.md` carries a sign-off line (date + reviewer name) before the release can ship

#### Scenario: Provenance is auditable end-to-end

- GIVEN any question in the pack
- WHEN an auditor opens `LICENSING.md` and locates the row for that question
- THEN they MUST be able to trace from question → `officialSourceRef` → official document → clearance evidence
