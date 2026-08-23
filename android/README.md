# Money Manager — Android app

Native Android app for tracking income, expenses, and budgets. The app is
**on-device only**: a local **Room** database is the single source of truth and
it has **no runtime dependency on the backend**. Data durability comes from an
optional **Google Drive backup** (export/restore of a JSON snapshot).

> **Status:** app shell + Material 3 theme + navigation in place; on-device data
> layer and features landing incrementally.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3, dynamic color on API 31+)
- **Coroutines / Flow** for async
- **Room** — the local database, single source of truth
- **DataStore** for settings
- **kotlinx.serialization** for the JSON backup snapshot
- **Hilt** for dependency injection
- **Navigation Compose** (type-safe routes)
- **Glance** for the home-screen widget, **App Shortcuts** for quick actions
- **WorkManager** for the background backup

Versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Requirements

- **JDK 17+** — 17 is AGP's baseline; a newer JDK works too (this project was
  set up with **JDK 26**, verified against Gradle 9.5.0)
- **Android SDK 37** — Platform 37 + Build-Tools 37.0.0 + Platform-Tools (`adb`)
- Gradle **9.5.0** via the committed wrapper (AGP **9.3.0**, built-in Kotlin)
- Either **Android Studio** *or* the **command-line tools only** — see
  [Installing the build toolchain](#installing-the-build-toolchain-sdk-only-no-android-studio)
  for the SDK-only setup used here.

## Installing the build toolchain (SDK-only, no Android Studio)

The setup used for this project: the Android command-line tools + a JDK, driven
from VS Code / a terminal and deployed to a physical phone — no Android Studio,
no emulator.

> Paths below are the Windows locations used here (SDK at `C:\Android\Sdk`);
> adjust for your OS. Everything is installed from downloads/zip (`winget` was
> unusable in this environment).

### 1. JDK

Install a JDK (17+ — this project used **JDK 26**) and note its path, e.g.
`C:\Program Files\Java\jdk-26.0.2.1`.

### 2. Android command-line tools

1. Download **"Command line tools only"** from
   <https://developer.android.com/studio#command-line-tools-only>.
2. Unzip so the layout is **exactly** this (the zip's `cmdline-tools` contents
   must sit inside a folder literally named `latest`):

   ```text
   C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
   ```

### 3. Environment variables (User scope)

| Variable | Value |
| --- | --- |
| `JAVA_HOME` | your JDK path (e.g. `C:\Program Files\Java\jdk-26.0.2.1`) |
| `ANDROID_HOME` | `C:\Android\Sdk` |
| `ANDROID_SDK_ROOT` | `C:\Android\Sdk` |

Then add to `PATH`: `%JAVA_HOME%\bin`, `%ANDROID_HOME%\cmdline-tools\latest\bin`,
and `%ANDROID_HOME%\platform-tools`. **Open a new terminal** so they load.

### 4. SDK packages

```bash
sdkmanager --licenses                                             # accept all (y)
sdkmanager "platform-tools" "platforms;android-37" "build-tools;37.0.0"
```

- `platform-tools` provides **`adb`** (needed to talk to the physical device).
- `platforms;android-37` + `build-tools;37.0.0` match `compileSdk` /
  `targetSdk 37` (Compose BOM `2026.08.00` requires SDK 37).

### 5. `local.properties`

Point Gradle at the SDK. This file is **machine-local and gitignored — never
commit it**:

```properties
sdk.dir=C\:\\Android\\Sdk
```

### 6. Verify

```bash
java -version         # the JDK
sdkmanager --version
adb version           # from platform-tools
```

## Getting started

The Gradle wrapper is **committed**, so no generation step is needed — clone and
build. From `android/`:

```bash
./gradlew assembleDebug
```

(If opening in Android Studio instead, it generates `local.properties` on first
sync.)

## Running during development

The Android app builds and runs **natively on Windows** (no WSL/Docker) and
deploys to a physical phone (recommended) or an emulator. No network/server
setup is required — the app is fully on-device.

### Build & install from the command line

From `android/`, with a device connected (`adb devices` lists it):

```bash
./gradlew installDebug          # build + install the debug APK
adb shell monkey -p com.moneymanager 1   # launch it
```

### Run on a physical phone (recommended)

Best for testing widgets, quick actions, and battery on real hardware.

1. On the phone: Developer Options → **USB debugging** (cable) or **Wireless
   debugging** (Android 11+). Authorize the RSA prompt on first connect.
2. `./gradlew installDebug` builds, installs, and you launch from the app
   drawer or via `adb`.

The app needs no network to run. The **Google Drive backup** (in Settings) is
the only online feature and is optional.

### Run on the emulator (fast UI iteration)

1. **Device Manager → Create Device** → pick a phone + an **API 37** system
   image (downloads once).
2. Press **▶ Run** (Android Studio) or `./gradlew installDebug` against the
   running emulator.

### Inner loop

- `./gradlew installDebug` (or **▶ Run / Debug** in the IDE) builds, installs,
  and launches on the selected target.
- **Logcat** (`adb logcat`) shows logs.
- Compose **`@Preview` + Live Edit** render UI without a full deploy.

## Project structure

Single Gradle module (`:app`), layered and organized by feature:

```text
app/src/main/java/com/moneymanager/
├── data/
│   ├── local/         Room entities/DAOs + DataStore (settings)
│   ├── backup/        Google Drive JSON snapshot export/restore
│   └── repository/    repository implementations (map Room ↔ domain)
├── domain/
│   ├── model/         clean Kotlin domain models + AppResult
│   └── repository/    repository interfaces (UI depends on these)
├── ui/
│   ├── theme/         Material 3 theme
│   ├── components/    reusable composables
│   ├── navigation/    Compose navigation graph
│   └── feature/       one package per screen (screen + ViewModel):
│       ├── transactions/   expense list (home) + filters
│       ├── entry/          add / edit expense
│       ├── categories/     manage categories + subcategories
│       ├── accounts/       manage accounts + subaccounts
│       └── settings/       backup + preferences
├── widget/            Glance home-screen widget(s)
├── di/                Hilt modules
├── MainActivity.kt    single-activity Compose host
└── MoneyManagerApp.kt Application (Hilt entry point)
```

Empty package directories are held by `.gitkeep` files that describe each
layer's purpose. The UI depends only on repository **interfaces**
(`domain/repository/`); `domain/**` has no Android/Room imports, and errors cross
the boundary as a sealed `AppResult`. Because there is no server to backstop
them, the two-level category/account depth, duplicate-name, and guarded-delete
invariants are enforced in the repository layer.
