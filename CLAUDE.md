# Money Manager

A personal finance / expense-tracking app.

## Project overview

- Purpose: track income, expenses, and budgets.
- Language: Python.
- Packaging & runtime: runs as a **Docker container**.
- Deployment target: a **headless Debian home server PC**. Keep everything
  compatible with headless Linux (no GUI dependencies, no interactive prompts
  at runtime).

## Architecture & conventions

Layout (`src/money_manager/backend/` is the FastAPI app):

- `app.py` — `create_app()` factory; register every router here. `lifespan`
  calls `init_db()` on startup.
- `db/models/` — SQLAlchemy 2.0 ORM models (`Mapped` / `mapped_column`). Import
  each new model in `db/models/__init__.py` so `Base.metadata` sees it.
- `db/base.py` (`Base`) and `db/session.py` (engine, `get_session`, `init_db`).
- `deps.py` — `SessionDep = Annotated[Session, Depends(get_session)]`. Use it in
  handlers (avoids ruff `B008`); the dependency param must come **before** any
  parameter that has a default.
- `models/` — Pydantic request/response schemas. Read models set
  `ConfigDict(from_attributes=True)`.
- `routes/` — one `APIRouter` per resource.
- `src/scripts/` — standalone test UI: `test_ui.py` (stdlib proxy server) +
  `index.html`. **The backend serves the API only — it never serves HTML.** Run
  the UI with `uv run python src/scripts/test_ui.py --backend <backend-url>`.
- `tests/` — pytest against an in-memory SQLite engine via
  `app.dependency_overrides` (see `conftest.py`).

Domain model:

- **Category** and **Account** are each a single **self-referential two-layer**
  table: top-level rows have `parent_id IS NULL`; children point at a top-level
  row. There are **no separate "group" tables**. The two-level depth limit is
  enforced in the route layer (a child's parent must itself be top-level → 422).
- Deletes are guarded (409) when a row still has children or is referenced by a
  transaction.
- **SQLite gotcha**: `UniqueConstraint(parent_id, name)` does NOT stop duplicate
  top-level names, because SQLite treats NULLs as distinct. Create routes do an
  explicit duplicate check for the `parent_id IS NULL` case (→ 409).
- Only `EXPENSE` transactions are accepted for now (income/transfer → 422); the
  enum and columns leave room for the rest.

Persistence:

- SQLite at `sqlite:////var/lib/money-manager/money_manager.db` (four slashes =
  absolute path), on a mounted Docker volume so data survives restarts/rebuilds.
- `init_db()` uses `create_all()`, which does **not** alter existing tables — so
  **any schema change requires recreating the DB volume** (a data reset) until
  Alembic migrations exist. Call this out before doing it.

## Python environment

- Use **`uv`** for all Python management (dependencies, virtualenv, running).
- Install **`ruff`** and **`mypy`** as dev dependencies via `uv`.
- Full local gate: `uv run ruff check . && uv run ruff format --check . &&
  uv run mypy && uv run pytest -q`.

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
- Before creating a new branch, **fetch the latest `main`** and branch from it
  (e.g. `git fetch origin && git checkout -b <branch> origin/main`) so the
  branch always starts from up-to-date `main`.
- Only after the feature is **well tested and working** does it go to `main`
  via a **PR review**.
- **No local merge to `main`, and no direct push to `main`.** `main` is updated
  exclusively through the PR review process.
