# Money Manager

A personal finance / expense-tracking app.

## Project overview

- Purpose: track income, expenses, and budgets.
- Language: **Kotlin** (native Android app, Jetpack Compose).
- The app is **on-device only**: a local **Room** database is the single source
  of truth, with a **Google Drive backup** (export/restore of a JSON snapshot)
  for durability. There is **no backend / no server dependency at runtime**.

## Repository layout

- `android/` — the native Android app (Kotlin, Jetpack Compose). Single-module
  Gradle project (the `:app` module).
- Root holds only shared files: `AGENTS.md`, `README.md`, `.gitignore`.

> History: the repo previously carried a decoupled FastAPI `backend/`. It was
> removed once the app went fully on-device; it still lives in git history if a
> future web layer ever needs it.

## Android app architecture & conventions

Layout (`android/` is a single-module Gradle project — the `:app` module):

- Native **Kotlin + Jetpack Compose** (Material 3), organized
  **package-by-feature** under `app/src/main/java/com/moneymanager/`:
  `data/{local,repository,backup}`, `domain/{model,repository}`,
  `ui/{theme,components,navigation,feature/*}`, `widget/`, `di/`. Empty layers
  are held by `.gitkeep` until filled in.
- **On-device architecture:** Room is the single source of truth (no server at
  runtime). The UI depends only on repository **interfaces** in
  `domain/repository/`; implementations in `data/repository/` map Room entities ↔
  domain models. `domain/**` has no Android/Room imports; `ui/**` never imports
  `data/**`. Errors cross the boundary as a sealed `AppResult`, not exceptions.

Domain model & invariants (enforced in the **repository layer**, since there is
no server to backstop them):

- **Category** and **Account** are each a single **self-referential two-layer**
  table: top-level rows have `parentId IS NULL`; children point at a top-level
  row. There are **no separate "group" tables**. The two-level depth limit is
  enforced in the repository (a child's parent must itself be top-level →
  `AppError.Validation`).
- Deletes are guarded (`AppError.Conflict`) when a row still has children or is
  referenced by a transaction.
- The self-referential FK uses `onDelete = RESTRICT`. Room enables
  `PRAGMA foreign_keys = ON`, and SQLite enforces RESTRICT **immediately, per
  row** — so any bulk clear (e.g. restore) must delete child rows before their
  top-level parents, or a single `DELETE FROM …` aborts the moment it removes a
  parent that still has a child.
- **SQLite gotcha**: a unique index on `(parentId, name)` does NOT stop duplicate
  top-level names, because SQLite treats NULLs as distinct. The repository does
  an explicit duplicate check for the `parentId IS NULL` case (→ `Conflict`).
- Only `EXPENSE` transactions are wired up end to end for now; `INCOME` /
  `TRANSFER` exist in the enum so the schema has room to grow.

Persistence:

- Room database on-device; amounts stored as **TEXT** (`BigDecimal` ↔ String
  converter — never REAL/Double), dates as ISO `yyyy-MM-dd`.
- Database version is **3** (version 3 added internal `merchant TEXT` column via
  `MIGRATION_2_3`). `merchant` is used strictly for internal ML payee/vendor
  learning and is never shown in place of user notes in the UI.
- Room `exportSchema = true` writes schema JSON to `app/schemas/` (committed) so
  migrations are possible later.

## Transaction ingestion & categorization

- **Ingestion**:
  - `SmsTransactionReceiver` intercepts bank debit SMS messages.
  - `TransactionNotificationListenerService` intercepts Google Pay push notifications
    (including split requests like `Food - for-eggs`).
  - `TransactionParser` parses debit amount, account ref, and payee merchant/notes.
    Account numbers and raw reference strings are filtered out from the human note
    via `isReasonableNote()`.
- **Categorization priority hierarchy** (`CategorizationEngine`):
  - **Priority 1: Explicit Category in Group / Note (Highest)**: Direct name match
    (ignoring emoji prefixes). Subcategories are checked strictly: explicit meal
    keywords (`breakfast`, `lunch`, `dinner`, `snacks`) or transport keywords
    assign a subcategory; generic food items (`egg`, `idly`, `dosa`, `biryani`)
    remain at the top-level category (`Food`) with `subCategoryId = null`.
  - **Priority 2: User Transaction History & Online Learned Weights (Medium)**:
    Checks `category_ml_weights` for `merchant:` (friend bank account / UPI ID)
    or confirmed tokens. Whenever a transaction is saved/edited (even with a blank
    note), `EntryViewModel` trains the engine on the merchant payee.
  - **Priority 3: Seeded Keyword Dictionary & Fallback Heuristics (Lowest)**:
    Pre-seeded keywords from 1,980+ past transactions and business suffix heuristics
    (`...foods`, `...bakes`, `...fuels`). Fallback to `Other` / `Others`.
- **Category consolidation**:
  - `cab`, `auto`, and `Rapido` are combined into **`Cab / Auto`**.
  - `Bus` is converted to **`Public Transport`**.
  - `DatabaseSeeder` and `SnapshotCodec` normalize these categories automatically.

Stack & tooling:

- Stack: Room (local DB = source of truth), DataStore (settings), Hilt (DI),
  Navigation Compose (type-safe routes), kotlinx.serialization (JSON backup
  snapshot), Glance (widget), WorkManager (background backup). Backup target is
  Google Drive **appDataFolder**; Retrofit/OkHttp are available and may be used
  for the Drive REST API.
- Versions are centralized in `android/gradle/libs.versions.toml`. Pins: AGP
  **9.3.0**, Gradle **9.5.0**, Kotlin **2.4.10**, KSP **2.3.11** (KSP uses
  *decoupled* versioning — not `<kotlin>-<ksp>`), Glance **1.1.1**,
  compileSdk/targetSdk **37**, minSdk **26**, JVM target **17**.
- AGP 9 provides **built-in Kotlin**: do **not** apply the
  `org.jetbrains.kotlin.android` plugin (it errors). The compose, serialization,
  and KSP plugins still apply on top; Kotlin compiler options go in the
  `kotlin { compilerOptions { } }` DSL (jvmTarget defaults to
  `compileOptions.targetCompatibility`).
- Config cache is temporarily **off** (`org.gradle.configuration-cache=false`):
  AGP 9.3's `ProcessNavigationXmlTask` fails to serialize into it. Re-enable once
  on an AGP version that fixes it.
- **Dynamic color** (Material You) only on API 31+ — guard with
  `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`, else fall back to the static
  scheme (crashes on 26–30 without the guard).

## Google Drive backup

- Auth uses **Google Identity Services** (`com.google.android.gms.auth.api.identity
  .AuthorizationClient`) with the `drive.appdata` scope, then raw Drive REST v3
  (OkHttp) — no Firebase, no Google Sign-In button. The OAuth client ID lives in
  `app/src/main/res/values/strings.xml` as `default_web_client_id`; GIS reads it
  automatically (there is **no** `setServerClientId` call).
- **OAuth consent gotcha:** with an *External* app in *Testing* mode, the Google
  account **must be added as a Test User** in the consent screen's *Audience* tab,
  and the debug signing SHA-1 must be registered in the Android OAuth client.
  Without the test user, `authorize()` fails with `12500` ("Google sign in
  failed"). The 12500 is **not** a client-id-type issue (an Android-type client ID
  in `default_web_client_id` works once the test user exists).
- Backups are stored in Drive's hidden **`appDataFolder`** (file
  `money-manager-backup.json`), which is **invisible in the normal Drive UI**.
- **Consent must be granted once** via the manual *Back up to Drive* action before
  scheduled backups can run; `BackupWorker` treats `ConsentRequired` as a skip (it
  does not auto-retry), so a periodic run with no token just no-ops.
- **Periodic backup** (`BackupFrequency` MANUAL/DAILY/WEEKLY/MONTHLY, stored in
  `AppSettings.backupFrequency`): `BackupScheduler` enqueues an immediate
  `OneTimeWorkRequest` + a `PeriodicWorkRequest` (interval 1/7/30 days, initial
  delay = interval, constraints `NetworkType.CONNECTED` + `RequiresBatteryNotLow`).
  `BackupScheduler.arm()` (called at app startup from `MoneyManagerApp`) re-arms
  only the periodic job; `schedule()` (called when the user changes frequency)
  also fires the immediate one. MANUAL cancels both.
- **Hilt 2.60.1 removed `@HiltWorker`**, so `BackupWorker` obtains its deps via a
  Hilt `@EntryPoint` (`BackupWorkerEntryPoint`, `@InstallIn(SingletonComponent::
  class)`) rather than constructor injection. Do **not** switch to
  `HiltWorkerFactory`/`Configuration.Provider` unless the Hilt version is bumped.

## Android environment

- Built **SDK-only, without Android Studio** — the Android command-line tools +
  a JDK, driven from VS Code / a terminal, deployed to a **physical device**
  over USB/Wi-Fi debugging (no emulator). Full install steps live in
  `android/README.md`.
- Toolchain on the dev PC: a **JDK** (via `JAVA_HOME`) and the **Android SDK**
  at `C:\Android\Sdk` (via `ANDROID_HOME` / `ANDROID_SDK_ROOT`), with
  `cmdline-tools\latest\bin` and `platform-tools` on `PATH`. Installed SDK
  packages include **platforms;android-37** and **build-tools;37.0.0** (Compose
  BOM `2026.08.00` pulls Compose 1.12 and requires compileSdk 37).
- The build's JVM **target** is 17 (AGP 9.3 baseline); the JDK that *runs*
  Gradle may be newer (this machine uses JDK 26 — verified working with Gradle
  9.5.0).
- Build & run **natively on Windows** (no WSL/Docker): `./gradlew installDebug`
  from `android/` builds and installs to the connected phone.
- `android/local.properties` (holds `sdk.dir`) is **machine-local and
  gitignored** — never commit it. Every other `android/` config is committed.
- Gradle runs via the wrapper (`./gradlew` from `android/`). The wrapper files
  (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` +
  `.properties`) are **committed** — clone and run, no `gradle wrapper` step.
- **This dev machine's toolchain** (non-standard paths, captured for
  reproducibility): the JDK lives at `C:\Android\jdk\...` (a write-permission
  workaround for `C:\Program Files\Java`), the Android SDK at `C:\Android\Sdk`,
  and the debug keystore (`C:\Users\sabar\.android\debug.keystore`, alias
  `androiddebugkey`) has SHA-1
  `A4:CD:61:74:60:D2:33:AC:0B:70:75:6A:F2:34:56:53:B6:22:72:94`. `local.properties`
  points `sdk.dir` at `C:\Android\Sdk`. The OAuth client ID in
  `default_web_client_id` is an **Android**-type client.
- **Installation prompts:** when `./gradlew installDebug` pushes the APK, the
  device may show an **"Install app"** or **"Open app"** system prompt. Tap
  **Install** / **Open** on the device to continue. The Gradle task only
  transfers the APK; it cannot dismiss those dialogs for you.

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
