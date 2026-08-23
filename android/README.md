# Money Manager — Android app

Native Android client for the Money Manager backend. Talks to the self-hosted
FastAPI server over the network (currently reached via Tailscale); the server
base URL is configurable in the app's settings.

> **Status:** project structure only — no feature logic yet.

## Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Coroutines / Flow** for async
- **Retrofit + OkHttp + kotlinx.serialization** for the API
- **Room** for the offline cache, **DataStore** for settings
- **Hilt** for dependency injection
- **Glance** for the home-screen widget, **App Shortcuts** for quick actions
- **WorkManager** for background sync

Versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Requirements

- **JDK 17+** — 17 is AGP's baseline; a newer JDK works too (this project was
  set up with **JDK 26**)
- **Android SDK 36** — Platform 36 + Build-Tools 36 + Platform-Tools (`adb`)
- Gradle **9.5.0** via the wrapper (AGP **9.3**)
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
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

- `platform-tools` provides **`adb`** (needed to talk to the physical device).
- `platforms;android-36` + `build-tools;36.0.0` match `compileSdk` /
  `targetSdk 36`.

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

Open the `android/` folder in Android Studio, which generates
`local.properties` (SDK path) and the Gradle wrapper on first sync.

To generate the Gradle wrapper from the command line instead (SDK-only setup):

```bash
cd android
gradle wrapper --gradle-version 9.5.0
```

## Running during development

Unlike the backend (Docker in WSL), the Android app builds and runs **natively
on Windows** (no WSL/Docker) and deploys to a physical phone or an emulator.

### Two install paths

- **SDK-only (used here):** the command-line tools + a JDK, driven from VS Code
  — see [Installing the build toolchain](#installing-the-build-toolchain-sdk-only-no-android-studio)
  above. Best if you don't want the IDE; pair it with a physical device.
- **Android Studio (all-in-one):** its first-run setup downloads everything the
  build needs — a bundled JDK (JetBrains Runtime 17+), the Android SDK Platform
  36 + Build-Tools 36, Platform-Tools (`adb`), and the Emulator; Gradle comes
  from the project wrapper. Add anything the wizard skipped under **SDK Manager**
  (Settings → Languages & Frameworks → Android SDK).

Either way, the only extra you need is a run target — a physical phone
(recommended) or an emulator (below).

### Run on a physical phone (recommended)

Best for testing widgets, quick actions, and battery on real hardware.

1. On the phone: Developer Options → **USB debugging** (cable) or **Wireless
   debugging** (Android 11+). No PC-side install — `adb` ships with Platform-Tools.
2. Keep the phone on your **tailnet** (Tailscale app running).
3. In the app's settings, set the server URL to the server's Tailscale address,
   e.g. `http://<server-name>:8000` or `http://100.x.y.z:8000`.
4. Select the phone in the target dropdown and press **▶ Run**.

### Run on the emulator (fast UI iteration)

1. **Device Manager → Create Device** → pick a phone + an **API 36** system
   image (downloads once).
2. Keep **Tailscale running on Windows** — the emulator reaches the home server
   through the host's network at the same Tailscale address.
3. Press **▶ Run**.

> `10.0.2.2` maps to the *Windows host's* localhost, so it is **not** how to
> reach the backend here (the backend runs on the home server, not your PC). Use
> the Tailscale address.

### Inner loop

- **▶ Run / Debug** builds, installs, and launches on the selected target.
- **Logcat** shows logs.
- Compose **`@Preview` + Live Edit** render UI without a full deploy.

> Android blocks plaintext HTTP by default (API 28+). Reaching the backend over
> plain `http://` (Tailscale) will need a network security config; that is added
> in the first networking slice so the connection test works.

## Project structure

Single Gradle module (`:app`), layered and organized by feature:

```text
app/src/main/java/com/moneymanager/
├── data/
│   ├── remote/        Retrofit service + DTOs (mirror the FastAPI JSON)
│   ├── local/         Room entities/DAOs + DataStore (settings)
│   └── repository/    repository implementations (remote + local)
├── domain/
│   ├── model/         clean Kotlin domain models
│   └── repository/    repository interfaces (UI depends on these)
├── ui/
│   ├── theme/         Material 3 theme
│   ├── components/     reusable composables
│   ├── navigation/    Compose navigation graph
│   └── feature/       one package per screen (screen + ViewModel):
│       ├── transactions/   expense list (home) + filters
│       ├── entry/          add / edit expense
│       ├── categories/     manage categories + subcategories
│       ├── accounts/       manage accounts + subaccounts
│       └── settings/       server URL + connection test
├── widget/            Glance home-screen widget(s)
├── di/                Hilt modules
├── MainActivity.kt    single-activity Compose host
└── MoneyManagerApp.kt Application (Hilt entry point)
```

Empty package directories are held by `.gitkeep` files that describe each
layer's purpose. Each feature screen depends only on a repository **interface**
(`domain/repository/`), so data sources can change or be tested without
touching the UI.
