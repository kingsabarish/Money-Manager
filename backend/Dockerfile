# syntax=docker/dockerfile:1

# uv image bundles the required Python 3.14 and the uv package manager.
FROM ghcr.io/astral-sh/uv:python3.14-bookworm-slim

# Run as a fixed non-root UID so the bind-mounted data dir has predictable
# ownership on the host (see docker-compose.yml).
ARG APP_UID=10001
ARG APP_GID=10001
RUN groupadd --gid "${APP_GID}" app \
    && useradd --uid "${APP_UID}" --gid "${APP_GID}" --create-home app

WORKDIR /app

# Persistent application data lives here; the host mounts over this path.
ENV DATA_DIR=/var/lib/money-manager
RUN mkdir -p "${DATA_DIR}" && chown "${APP_UID}:${APP_GID}" "${DATA_DIR}"

# Install dependencies first (cached) using only the lockfile + manifest.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

# Install the project itself.
COPY README.md ./
COPY src ./src
RUN uv sync --frozen --no-dev

ENV PATH="/app/.venv/bin:${PATH}" \
    MONEY_MANAGER_HOST=0.0.0.0 \
    MONEY_MANAGER_PORT=8000 \
    MONEY_MANAGER_DATABASE_URL="sqlite:////var/lib/money-manager/money_manager.db"

USER ${APP_UID}:${APP_GID}

EXPOSE 8000

CMD ["money-manager"]
