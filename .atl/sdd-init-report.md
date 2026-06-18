# SDD Init Report — SANITEST

## Detected Project State

- **Project key (Engram)**: `sanitest`
- **Project path**: `C:\Users\arbel\Desktop\APPS\SANITEST`
- **Working product name**: SaniExam (folder remains SANITEST)
- **Repository type**: Android application
- **Source code present**: ❌ no
- **Gradle wrapper present**: ❌ no
- **Test runner present**: ❌ no
- **CI config present**: ❌ no
- **Lint / type-check / formatter present**: ❌ no

## Stack (declared by `.github/skills/Agent.md`)

| Area | Target |
| --- | --- |
| Language | Kotlin 1.9+ |
| Build system | Gradle (Kotlin DSL preferred) |
| Min SDK | 24 |
| Target SDK | 34 |
| UI | Jetpack Compose (default), legacy XML as fallback |
| Navigation | Jetpack Navigation Compose |
| DI | Hilt/Dagger (preferred) or Koin |
| Async | Kotlin Coroutines (no RxJava for new code); inject Dispatchers |
| Architecture | Clean Architecture — presentation / domain (UseCases) / data (Repository) |
| State | StateFlow / SharedFlow in ViewModels (UDF) |
| Persistence | Room (local) + Retrofit (remote), offline-first |
| Min SDK override allowed | yes (if project requires) |

## Agent Skills Already On Disk

17 Android agent skills from `new-silvermoon/awesome-android-agent-skills` are installed under `.github/skills/` and are indexed in `.atl/skill-registry.md`:

- `architecture/`: android-architecture, android-data-layer, android-viewmodel
- `build_and_tooling/`: android-gradle-logic
- `concurrency_and_networking/`: android-coroutines, android-retrofit, kotlin-concurrency-expert
- `migration/`: rxjava-to-coroutines-migration, xml-to-compose-migration
- `performance/`: compose-performance-audit, gradle-build-performance
- `testing_and_automation/`: android-emulator-skill (with scripts), android-testing
- `ui/`: android-accessibility, coil-compose, compose-navigation, compose-ui

The project-level `Agent.md` is the agent marker. It also functions as the project-level `AGENTS.md` and is referenced from the skill registry.

## Testing Capabilities

| Capability | Status | Tool / Command |
| --- | --- | --- |
| Strict TDD | ❌ disabled (reason: no test runner configured) | — |
| Test runner | ❌ not present | — |
| Unit layer | ❌ not configured | JUnit4/JUnit5 + MockK + Turbine (per Agent.md, target) |
| Integration layer | ❌ not configured | Hilt tests (target) |
| UI / E2E layer | ❌ not configured | Compose Test Rule, Espresso (per Agent.md, target) |
| Coverage | ❌ not configured | — |
| Linter | ❌ not configured | — |
| Type checker | ❌ not configured | — |
| Formatter | ❌ not configured | — |
| Emulator automation | ✅ scripts present | `.github/skills/testing_and_automation/android-emulator-skill/scripts/*.py` |

## Persistence Resolution

- Mode requested: `both` (Engram + openspec)
- Engram: `capture_prompt: false` per SDD hard rule for automated artifacts
- OpenSpec skeleton: created at `openspec/{config.yaml, specs/, changes/archive/}`

## What Was Written

- `openspec/config.yaml` — context, strict_tdd flag, phases, testing block
- `openspec/specs/.gitkeep`, `openspec/changes/archive/.gitkeep` — directory seeds
- `.atl/skill-registry.md` — refreshed to include sdd-* skills; sdd-init, sdd-explore, sdd-propose, sdd-spec, sdd-design, sdd-tasks, sdd-apply, sdd-verify, sdd-archive, sdd-onboard paths appended
- Engram observations (capture_prompt=false): `sdd-init/sanitest`, `sdd/sanitest/testing-capabilities`, `skill-registry`

## Decisions

- `strict_tdd: false` — no test runner present; not guessing tooling.
- `delivery_strategy: ask-always` — interactive mode confirmed.
- `review_budget_lines: 400` — applied.
- `artifact_store.mode: both` — applied.
- No proposal/spec/design/tasks work started (per instructions).
- No app code written.

## Next Recommended Step

Run `/sdd-explore` to capture the first product idea for SaniExam, OR run `/sdd-new "<change-name>"` if the user already has a concrete change in mind. Either path will use the strict TDD-disabled mode and the open phase pipeline.
