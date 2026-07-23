# spec/ — decision log for future development

This folder records **design decisions and feature specs** for the plugin, so
the reasoning behind future changes is captured before code is written and
survives beyond any single conversation.

It complements the other docs, it does not replace them:

- `CLAUDE.md` — the *current* architecture, invariants and gotchas (what the
  plugin **is** right now).
- `keymaps/keys.md` — the German-layout keymap reference (facts about keys).
- `spec/` — **why** we might change things, and the plan for doing so (what the
  plugin **could become**, and the trade-offs weighed).

## Conventions

- One file per topic, numbered `NNNN-short-slug.md` (ADR-style, chronological).
- Each spec carries a **Status** (`Proposed` · `Accepted` · `Rejected` ·
  `Deferred` · `Implemented`) and a **Decision** section, so a stale idea is
  never mistaken for committed work.
- When a spec is implemented, note the commit and flip Status to `Implemented`;
  keep the file as the historical record rather than deleting it.
- If a spec sources an external issue (YouTrack, GitHub), link it and pin the
  facts we read, so we don't depend on the issue staying reachable/unchanged.

## Index

| # | Title | Source | Status |
|---|-------|--------|--------|
| [0001](0001-modified-shortcuts-filter.md) | Filter / view for modified shortcuts | [IJPL-228176](https://youtrack.jetbrains.com/issue/IJPL-228176) | Implemented |
| [0002](0002-plugin-scope-and-name.md) | Plugin scope and name | internal (triggered by 0001) | Accepted |

## Backlog

Candidate features not yet scheduled live in [BACKLOG.md](BACKLOG.md). When one
is picked up it graduates to a numbered spec here.
