# Money Manager — Backend

FastAPI JSON API for the Money Manager app. Runs as a Docker container on a
headless Debian home server; the Android app talks to it over the network.

## Development

Uses [`uv`](https://docs.astral.sh/uv/) for all Python management.

```bash
# from this backend/ directory
uv sync                     # create the venv and install deps (incl. dev)
uv run money-manager        # run the API locally (http://localhost:8000)
```

### Test UI

A standalone stdlib proxy UI for exercising the API by hand:

```bash
uv run python scripts/test_ui.py --backend http://localhost:8000
```

### Quality gate

Run before considering any change done:

```bash
uv run ruff check . && uv run ruff format --check . && uv run mypy && uv run pytest -q
```

## Docker

```bash
docker compose up --build
```

Data persists in the `/var/lib/money-manager` bind mount (SQLite database).
See `docker-compose.yml` for the one-time host directory setup.
