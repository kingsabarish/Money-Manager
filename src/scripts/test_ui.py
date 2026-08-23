"""Standalone test UI for the Money Manager API.

Serves the single-page test UI (``index.html`` next to this file) and proxies
every other request to a running backend. Because the page and the API it calls
share this server's origin, no CORS setup is needed and the backend itself never
has to serve any page.

Start the backend first (Docker or ``uv run money-manager``), then run::

    uv run python src/scripts/test_ui.py
    uv run python src/scripts/test_ui.py --port 8080 --backend http://localhost:8001

and open the printed URL in a browser.
"""

from __future__ import annotations

import argparse
import contextlib
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

INDEX_HTML = Path(__file__).parent / "index.html"

# Backend base URL; set from CLI args in main() before the server starts.
BACKEND = "http://localhost:8000"


class Handler(BaseHTTPRequestHandler):
    """Serve the UI at ``/`` and proxy everything else to the backend."""

    def _serve_index(self) -> None:
        body = INDEX_HTML.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _proxy(self) -> None:
        length_header = self.headers.get("Content-Length")
        length = int(length_header) if length_header else 0
        payload = self.rfile.read(length) if length else None

        request = Request(  # noqa: S310 - fixed, operator-supplied backend
            BACKEND.rstrip("/") + self.path,
            data=payload,
            method=self.command,
        )
        content_type = self.headers.get("Content-Type")
        if content_type:
            request.add_header("Content-Type", content_type)

        status: int
        body: bytes
        out_type: str
        try:
            with urlopen(request) as resp:  # noqa: S310 - see above
                status = resp.status
                body = resp.read()
                out_type = resp.headers.get("Content-Type", "application/json")
        except HTTPError as exc:  # backend responded with 4xx/5xx
            status = exc.code
            body = exc.read()
            out_type = exc.headers.get("Content-Type", "application/json")
        except URLError as exc:  # backend unreachable
            status = 502
            body = (
                f'{{"detail": "Cannot reach backend at {BACKEND}: {exc.reason}"}}'
            ).encode()
            out_type = "application/json"

        self.send_response(status)
        self.send_header("Content-Type", out_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _route(self) -> None:
        if self.path in ("", "/"):
            self._serve_index()
        else:
            self._proxy()

    def do_GET(self) -> None:
        self._route()

    def do_POST(self) -> None:
        self._route()

    def do_PATCH(self) -> None:
        self._route()

    def do_DELETE(self) -> None:
        self._route()

    def log_message(self, format: str, *args: Any) -> None:
        """Log one compact line per request."""
        print(f"{self.command} {self.path} -> {args[1] if len(args) > 1 else ''}")


def main() -> None:
    """Parse arguments and serve the test UI until interrupted."""
    global BACKEND
    parser = argparse.ArgumentParser(description="Money Manager test UI server")
    parser.add_argument("--port", type=int, default=8080, help="port to serve on")
    parser.add_argument(
        "--backend",
        default="http://localhost:8000",
        help="base URL of the running backend API",
    )
    parser.add_argument(
        "--no-open", action="store_true", help="do not open a browser automatically"
    )
    args = parser.parse_args()
    BACKEND = args.backend

    url = f"http://localhost:{args.port}/"
    server = HTTPServer(("127.0.0.1", args.port), Handler)
    print(f"Test UI on {url}  ->  proxying API to {BACKEND}")
    print("Press Ctrl+C to stop.")
    if not args.no_open:
        with contextlib.suppress(Exception):
            webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
