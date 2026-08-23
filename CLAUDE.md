# Money Manager

A personal finance / expense-tracking app.

## Project overview

- Purpose: track income, expenses, and budgets.
- Languages: **Python** (backend) and **Kotlin** (Android app).
- Backend packaging & runtime: runs as a **Docker container**.
- Backend deployment target: a **headless Debian home server PC**. Keep
  everything compatible with headless Linux (no GUI dependencies, no interactive
  prompts at runtime).
- The Android app talks to the backend over the network (currently reached via
  Tailscale); the server base URL is user-configurable in the app.

## Repository layout (monorepo)

- `backend/` — the Python FastAPI project (its own `pyproject.toml`, `uv.lock`,
  `Dockerfile`, `docker-compose.yml`, `src/`, `tests/`). Run all `uv` commands
  from inside `backend/`.
- `android/` — the native Android app (Kotlin, Jetpack Compose).
- Root holds only shared files: `CLAUDE.md`, `README.md`, `.gitignore`.

## Backend architecture & conventions

Layout (`backend/src/money_manager/` is the FastAPI app):

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
- `backend/scripts/` — standalone test UI, kept **parallel to `src/`** (it is a
  test script, not part of the app code): `test_ui.py` (stdlib proxy server) +
  `index.html`. **The backend serves the API only — it never serves HTML.** Run
  the UI with `uv run python scripts/test_ui.py --backend <backend-url>` (from
  `backend/`).
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

## Android app architecture & conventions

Layout (`android/` is a single-module Gradle project — the `:app` module):

- Native **Kotlin + Jetpack Compose** (Material 3), organized
  **package-by-feature** under `app/src/main/java/com/moneymanager/`:
  `data/{remote,local,repository}`, `domain/{model,repository}`,
  `ui/{theme,components,navigation,feature/*}`, `widget/`, `di/`. Empty layers
  are held by `.gitkeep` until filled in.
- The UI depends only on repository **interfaces** in `domain/repository/`;
  concrete implementations live in `data/repository/`.
- Stack: Retrofit + OkHttp + kotlinx.serialization (API), Room (offline cache),
  DataStore (settings), Hilt (DI), Glance (widget), WorkManager (sync).
- Versions are centralized in `android/gradle/libs.versions.toml`. Pins: AGP
  **9.3**, Gradle **9.5.0**, Kotlin **2.4.10**, compileSdk/targetSdk **36**,
  minSdk **26**, JVM target **17**.
- Android blocks cleartext HTTP by default (API 28+). Reaching the backend over
  plain `http://` (Tailscale) needs a network security config — added in the
  first networking slice.

## Python environment

- Use **`uv`** for all Python management (dependencies, virtualenv, running).
  Run `uv` commands from the `backend/` directory.
- Install **`ruff`** and **`mypy`** as dev dependencies via `uv`.
- Full local gate (from `backend/`): `uv run ruff check . && uv run ruff format
  --check . && uv run mypy && uv run pytest -q`.

## Android environment

- Built **SDK-only, without Android Studio** — the Android command-line tools +
  a JDK, driven from VS Code / a terminal, deployed to a **physical device**
  over USB/Wi-Fi debugging (no emulator). Full install steps live in
  `android/README.md`.
- Toolchain on the dev PC: a **JDK** (via `JAVA_HOME`) and the **Android SDK**
  at `C:\Android\Sdk` (via `ANDROID_HOME` / `ANDROID_SDK_ROOT`), with
  `cmdline-tools\latest\bin` and `platform-tools` on `PATH`.
- The build's JVM **target** is 17 (AGP 9.3 baseline); the JDK that *runs*
  Gradle may be newer (this machine uses JDK 26).
- `android/local.properties` (holds `sdk.dir`) is **machine-local and
  gitignored** — never commit it. Every other `android/` config is committed.
- Gradle runs via the wrapper (`./gradlew` from `android/`), generated once
  with `gradle wrapper`.

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
- **Modular commits.** When I ask you to commit, do NOT dump everything into one
  commit. Split the changes into reasonable, logically-grouped commits (e.g.
  restructure vs. feature vs. docs) each with its own clear message.

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
