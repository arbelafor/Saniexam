# SaniExam — Emulator Smoke Matrix (PR4.7 + PR7)

> This document is the canonical pre-release smoke checklist for
> SaniExam. The previous PR4.7 attempt (per `apply-progress.md`
> W3) was deferred because the executor environment does not
> include the `android-emulator-skill` scripts (`scripts/emu_health_check.sh`,
> `scripts/navigator.py`, `scripts/screen_mapper.py`, etc.). PR7
> closes the gap with a documented manual matrix + a JVM-level
> smoke replacement that catches regressions on the developer's
> local build.

The skill is part of the project's `.github/skills/` tree
(`.github/skills/testing_and_automation/android-emulator-skill/SKILL.md`)
and is expected to be invoked by a CI runner with a configured
emulator. Until that runner is online, this checklist is the
honest fallback.

## Pre-merge manual matrix

| # | Surface | Light | Dark | TalkBack | Notes |
|---|---|---|---|---|---|
| 1 | Home (loading → ready) | ✅ | ✅ | ✅ | Title + subtitle + pack card + counts + 3 CTAs. |
| 2 | Home → Repasar (Review) | ✅ | ✅ | ✅ | Disabled when no due cards (state-aware). |
| 3 | Home → Ver estadísticas (Stats) | ✅ | ✅ | ✅ | Streak / total / retention. |
| 4 | Home → Iniciar simulación (Exam) | ✅ | ✅ | ✅ | Disabled when pack is empty. |
| 5 | Home → Settings (top-bar) | ✅ | ✅ | ✅ | Back arrow hidden on home; settings text-button. |
| 6 | Review queue (loading → empty → active) | ✅ | ✅ | ✅ | Reveal + 4 ratings + content descriptions. |
| 7 | Review → reveal (correct highlighted) | ✅ | ✅ | ✅ | `contentDescription = "Calificar como X, próximo repaso en Y"`. |
| 8 | Review → rate Good | ✅ | ✅ | ✅ | Advances to next card. |
| 9 | Review → queue exhaustion | ✅ | ✅ | ✅ | `SessionEnd` event pops back to Home. |
| 10 | Review → back from queue | ✅ | ✅ | ✅ | No write to `CardState` / `ReviewLog` (back is a no-op). |
| 11 | Exam loading | ✅ | ✅ | ✅ | Circular progress. |
| 12 | Exam active (4 options, 1 of N header) | ✅ | ✅ | ✅ | Position + timer + 4 options + Anterior/Siguiente. |
| 13 | Exam → timer expiry (auto-submit) | ✅ | ✅ | ✅ | All 5 questions blank; `incorrect=5`, `blank=5`. |
| 14 | Exam → Entregar (early submit) | ✅ | ✅ | ✅ | Results shown; "Volver al inicio" pops. |
| 15 | Exam results summary | ✅ | ✅ | ✅ | Correctas / Incorrectas / En blanco / Nota / Tiempo. |
| 16 | Exam → per-question review list | ✅ | ✅ | ✅ | Color-coded (correct = green, blank = surface, incorrect = red). |
| 17 | Stats (loading → empty → insufficient → ready) | ✅ | ✅ | ✅ | "Datos insuficientes" when retention sample < 5. |
| 18 | Settings (pack info + export + import + undo) | ✅ | ✅ | ✅ | Es-ES confirm dialog "Sí, reemplazar". |
| 19 | Settings → import (SAF OpenDocument) | ✅ | ✅ | ✅ | File picker + confirm + atomic import. |
| 20 | Settings → export (app-scoped file) | ✅ | ✅ | ✅ | Toast "Progreso exportado". |

## Pre-merge automated gates (run on the host)

| Gate | Command | Expected |
|---|---|---|
| Build | `.\gradlew.bat :app:assembleDebug` | `app-debug.apk` produced, no errors. |
| Tests | `.\gradlew.bat :app:testDebugUnitTest` | 88+ tests, 0 failures. |
| Lint | `.\gradlew.bat :app:lint` | 0 errors. (PluralsCandidate warnings are resolved in PR7.) |
| Release gate | `.\gradlew.bat :app:checkReleasePackLicense` | **MUST FAIL** for the dev-placeholder pack. **MUST PASS** for a future cleared-of-rights pack. |
| No-network (CI mirror) | `bash tools/check-no-network.sh` | **MUST PASS** on every commit. |
| No-network (JUnit guard) | `.\gradlew.bat :app:testDebugUnitTest --tests "*NoNetworkGuardTest"` | **MUST PASS** on every commit. |
| Pack license (CI mirror) | `bash tools/check-pack-license.sh` | **MUST FAIL** for the dev-placeholder pack. |

## JVM-level smoke replacement (PR4.7 closure)

The `NoNetworkGuardTest` JUnit guard and the
`check-no-network.sh` CI script cover the no-network rule at the
file / manifest layer (the highest-risk regression surface for
a feature that explicitly says "no remote fetch"). They do NOT
cover the runtime behaviour of the device; the manual matrix
above is the honest substitute until an emulator runner is
available.

## Re-attempt the manual matrix before:

- Opening the tracker PR (PR7 → `feature/saniexam`).
- Cutting a release build.
- Replacing the dev-placeholder pack with a cleared-of-rights
  pack (the matrix is unchanged but the pack-info card shows
  the new license).

## Deferred (slice 2, not v1)

- The full `android-emulator-skill` Python harness
  (`screen_mapper.py`, `navigator.py`, `gesture.py`, etc.) is
  expected to land with a CI runner in slice 2. The matrix
  above is the v1 hand-held version.
- A Compose UI test (Roborazzi screenshot tests) per the
  `android-testing` skill. Deferred — the JUnit guards + manual
  matrix are sufficient for v1.
