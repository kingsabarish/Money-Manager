# Money Manager

A personal finance / expense-tracking app.

## Project overview

- Purpose: track income, expenses, and budgets.
- Language: Python.
- Packaging & runtime: runs as a **Docker container**.
- Deployment target: a **headless Debian home server PC**. Keep everything
  compatible with headless Linux (no GUI dependencies, no interactive prompts
  at runtime).

## Python environment

- Use **`uv`** for all Python management (dependencies, virtualenv, running).
- Install **`ruff`** and **`mypy`** as dev dependencies via `uv`.

## Code quality (required for every change)

For **any** code you add or update, before considering it done you MUST:

1. Run **`ruff`** (lint + format) and fix all reported issues.
2. Run **`mypy`** and fix all reported type issues.

Do not leave ruff or mypy failures behind.

## Building & testing (Docker via WSL)

- Docker is installed inside **WSL** on the development PC (not native Windows).
- Whenever we need to test something we built, **build and run the container
  through WSL** and test it there.

## Workflow & git

- **I review every change.** After you make a change, stop and let me review it.
- **Do NOT commit or push** unless I explicitly tell you to. Only after I say
  "commit" / "push" may you run those git commands.

### Branching & PR flow

- Every new feature starts on a **feature branch created from `main`**. Do the
  development there.
- Only after the feature is **well tested and working** does it go to `main`
  via a **PR review**.
- **No local merge to `main`, and no direct push to `main`.** `main` is updated
  exclusively through the PR review process.
