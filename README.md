# Money Manager

A native Android personal finance / expense-tracking app for tracking income,
expenses, and budgets.

The app is **on-device only**: a local **Room** database is the single source of
truth, with an optional **Google Drive backup** (export/restore of a JSON
snapshot) for durability. There is no backend or server dependency at runtime.

## Features

- Track income, expenses, and budgets on-device.
- Two-level, self-referential **categories** and **accounts** (top-level groups
  with child items) — no separate "group" tables.
- JSON snapshot backups to Google Drive's hidden `appDataFolder`, with optional
  scheduled backups (daily / weekly / monthly).
- Material 3 UI with dynamic color (Material You) on Android 12+.
- Home-screen widget (Glance).

## Repository layout

| Path        | What it is                                          |
| ----------- | --------------------------------------------------- |
| `android/`  | Native Android app (Kotlin, Jetpack Compose).       |

The `android/` module is a single-module Gradle project (the `:app` module),
organized package-by-feature under
`app/src/main/java/com/moneymanager/`.

See [`android/README.md`](android/README.md) for setup, toolchain, and build
instructions.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Room** — local database, single source of truth (amounts stored as `TEXT`
  via a `BigDecimal` ↔ `String` converter; dates as ISO `yyyy-MM-dd`)
- **DataStore** — settings
- **Hilt** — dependency injection
- **Navigation Compose** — type-safe routes
- **kotlinx.serialization** — JSON backup snapshot
- **Glance** — home-screen widget
- **WorkManager** — background/scheduled backups
- **Google Drive** (`appDataFolder`, REST v3) — backup target

## Architecture

```
UI (Compose, package-by-feature)
   │  depends only on repository interfaces
   ▼
Domain (model + repository interfaces)  — no Android/Room imports
   │
   ▼
Data (Room entities, repository impls, backup)
   │
   ▼
Room DB (single source of truth)
```

- The UI depends only on repository **interfaces** in `domain/repository/`;
  implementations in `data/repository/` map Room entities ↔ domain models.
- `ui/**` never imports `data/**`. Errors cross the boundary as a sealed
  `AppResult`, not exceptions.
- Invariants (two-level depth limit, delete guards, duplicate-name checks) are
  enforced in the repository layer, since there is no server to backstop them.

## Building & running

Built SDK-only (no Android Studio required). See
[`android/README.md`](android/README.md) for the full toolchain and build
steps. In short:

```bash
cd android
./gradlew installDebug   # builds and installs to the connected device
```

## Backups

- Manual *Back up to Drive* grants consent (required once before scheduled
  backups can run).
- Scheduled backups are configured via `BackupFrequency`
  (MANUAL / DAILY / WEEKLY / MONTHLY) in app settings.

## License

See repository for license details.
