# Money Manager

A self-hosted personal finance / expense-tracking app: a FastAPI backend running
on a home server, with a native Android client.

## Repository layout

| Path        | What it is                                                        |
| ----------- | ----------------------------------------------------------------- |
| `backend/`  | FastAPI JSON API (Python, `uv`), packaged as a Docker container.  |
| `android/`  | Native Android app (Kotlin, Jetpack Compose).                     |

Each side has its own README with setup instructions:

- [`backend/README.md`](backend/README.md)
- [`android/README.md`](android/README.md)
