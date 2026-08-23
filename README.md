# Money Manager

A native Android personal finance / expense-tracking app. The app is
**on-device only**: a local **Room** database is the single source of truth,
with an optional **Google Drive backup** (export/restore of a JSON snapshot) for
durability. There is no backend or server dependency at runtime.

## Repository layout

| Path        | What it is                                          |
| ----------- | --------------------------------------------------- |
| `android/`  | Native Android app (Kotlin, Jetpack Compose).       |

See [`android/README.md`](android/README.md) for setup, toolchain, and build
instructions.
