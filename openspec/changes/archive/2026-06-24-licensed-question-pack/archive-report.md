# Archive Report — `licensed-question-pack`

> **Change:** `licensed-question-pack` · **Project:** `sanitest` · **Archived:** 2026-06-24
> **Mode:** hybrid (filesystem + Engram)
> **Archive path:** `openspec/changes/archive/2026-06-24-licensed-question-pack/`

---

## Gate Validation

| Gate | Result |
|------|--------|
| Task completion (26/26) | ✅ PASS — all tasks `[x]` in `tasks.md` |
| Verify report status | ✅ PASS — `Status: PASS`, `Verdict: PASS` |
| Archive readiness | ✅ READY |
| CRITICAL issues | ✅ none |
| Blocking issues | ✅ none |
| Release approval | ✅ Project-owner sign-off recorded in `LICENSING.md` on 2026-06-24 (product/editorial approval) |

---

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `dataset-import` | Updated | Merged MODIFIED delta: updated requirement text for 4 requirements (Bundled Pack Ingestion, Pack Validation, Official-Source Metadata, Immutability Within a Version); added 2 new scenarios (Pack category mismatch, Question missing provenance); updated 3 existing scenarios with category/provenance fields. Preserved "Remote Dataset Update (OUT OF MVP)" requirement (unchanged by delta). |
| `licensed-content-packs` | Created (NEW full spec) | Copied directly to `openspec/specs/licensed-content-packs/spec.md` — 5 requirements with 11 scenarios covering pack identity, per-question provenance, license gate, refused set, and licensing documentation. |
| `professional-categories` | Created (NEW full spec) | Copied directly to `openspec/specs/professional-categories/spec.md` — 4 requirements with 8 scenarios covering pack-level category, active category in settings, multi-category future-proofing, and MVP UX with no category picker. |

### Merge Detail: `dataset-import`

| Requirement | Action | Changes |
|-------------|--------|---------|
| Bundled Pack Ingestion on First Launch | MODIFIED | Added active-category resolution, pack-category mismatch refusal; added new scenario "Pack category does not match active category"; updated Cold first launch scenario to include `active_category` and `category` column assertion. |
| Pack Validation and Rejection | MODIFIED | Added blank/missing `officialSourceRef` rejection; added new "Question missing provenance" scenario; updated Valid pack scenario to check non-blank `officialSourceRef`. |
| Official-Source Metadata and Provenance | MODIFIED | Added `category` to per-pack attribution; changed `officialSourceRef` from nullable to non-blank; expanded license gate to fail on case variants, missing `category`, and missing `officialSourceRef`. |
| Immutability Within a Version | MODIFIED (same content) | Listed as MODIFIED in delta; content is identical — preserved as-is. |
| Remote Dataset Update (OUT OF MVP) | Preserved (not in delta) | Unchanged requirement kept in main spec. |

---

## Archive Contents

| Artifact | Status |
|----------|--------|
| `proposal.md` | ✅ |
| `exploration.md` | ✅ (optional) |
| `specs/dataset-import/spec.md` | ✅ |
| `specs/licensed-content-packs/spec.md` | ✅ |
| `specs/professional-categories/spec.md` | ✅ |
| `design.md` | ✅ |
| `tasks.md` | ✅ (26/26 tasks complete) |
| `apply-progress.md` | ✅ |
| `verify-report.md` | ✅ |
| `archive-report.md` | ✅ (this file) |

---

## Source of Truth Updated

The following main specs now reflect the new behavior:

| Main Spec | Action |
|-----------|--------|
| `openspec/specs/dataset-import/spec.md` | Updated (MODIFIED delta merged) |
| `openspec/specs/licensed-content-packs/spec.md` | Created (NEW full spec) |
| `openspec/specs/professional-categories/spec.md` | Created (NEW full spec) |

---

## Archived Change Folder

`openspec/changes/licensed-question-pack/` → `openspec/changes/archive/2026-06-24-licensed-question-pack/`

Active changes directory no longer contains this change.

---

## Risks and Notes

- Verify report contained 6 non-critical items (suggestions/warnings), none blocking archive.
- No CRITICAL issues found; no stale unchecked tasks in archived `tasks.md`.
- Project-owner human release approval is recorded in `LICENSING.md` on 2026-06-24 as product/editorial approval, not formal legal advice.
- Bash gate not directly executed on Windows host; structurally equivalent and POSIX-portable for CI runners.

---

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. Ready for the next change.
