"""Entry point for the Money Manager server."""

import os

import uvicorn


def main() -> None:
    """Run the ASGI server."""
    host = os.getenv("MONEY_MANAGER_HOST", "0.0.0.0")
    port = int(os.getenv("MONEY_MANAGER_PORT", "8000"))
    uvicorn.run("money_manager.app:app", host=host, port=port)


if __name__ == "__main__":
    main()
