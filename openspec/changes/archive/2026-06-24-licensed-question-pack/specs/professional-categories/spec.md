# Professional Categories Specification

## Purpose

Define a pack- and database-level taxonomy for oposición professional categories so the data model can host multiple categories long-term while the MVP UX exposes only one active category at a time. TCAE is the first registered category.

## Requirements

### Requirement: Pack-Level Category Field

The system MUST persist a non-null `category` field on every `SubjectPack` row, and the bundled pack manifest MUST declare the category the pack belongs to.

#### Scenario: TCAE pack carries TCAE category

- GIVEN the bundled `sanidad-v1` pack is the TCAE pack
- WHEN the manifest is read
- THEN the manifest contains a `category` field whose value is the string `"TCAE"`
- AND the persisted `SubjectPack.category` column equals `"TCAE"`

#### Scenario: Pack missing category is rejected

- GIVEN a manifest that omits the `category` field
- WHEN validation runs
- THEN the pack is rejected
- AND the release pipeline license gate fails closed with a `MissingCategory` reason

### Requirement: Active Category in User Settings

The system MUST persist a single `active_category` value in the singleton `user_settings` row, and the ingest path MUST filter imports through that active category.

#### Scenario: Default active category is TCAE

- GIVEN a fresh install with no prior `user_settings` row
- WHEN the singleton is seeded
- THEN `active_category` equals `"TCAE"`

#### Scenario: Reading uses the active category

- GIVEN `active_category="TCAE"` and a persisted TCAE pack
- WHEN `GetDueQueueUseCase` or `RunExamSessionUseCase` reads questions
- THEN the repository filter `observeByCategory("TCAE")` is used
- AND questions from other categories are not returned

### Requirement: Multi-Category Future-Proofing

The data model MUST be designed so that adding a new professional category does NOT require a Room schema migration. Categories are values, not structural changes.

#### Scenario: Adding a second category requires no migration

- GIVEN a new category (e.g. `"Enfermeria"`) is registered
- WHEN the new pack is imported
- THEN the existing Room schema is reused
- AND no `version` bump and no `MIGRATION` is required
- AND the new pack row's `category` column holds the new category value

#### Scenario: Category is a value, not a column

- GIVEN the `SubjectPackEntity` and `UserSettingsEntity` schemas
- WHEN reviewed
- THEN `category` is a single `TEXT` column (not a separate join table) and `active_category` is a single `TEXT` column
- AND the migration to add these columns is a one-time `v3→v4` change

### Requirement: MVP UX Exposes No Category Picker

The MVP release MUST NOT surface a category-picker UI; the only active category is the seeded default and the user has no in-app control over it.

#### Scenario: No category picker in MVP

- GIVEN the MVP build is shipped
- WHEN the user navigates Settings or Home
- THEN no UI control allows changing `active_category`
- AND `active_category` remains the seeded default

#### Scenario: Category plumbing is present even though UI is hidden

- GIVEN the MVP code is reviewed
- WHEN the data layer is inspected
- THEN `UserSettings.activeCategory` and `SubjectPack.category` are wired through repositories, DAOs, and use cases
- AND the UI simply does not expose a control for them
