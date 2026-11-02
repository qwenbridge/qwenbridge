#!/usr/bin/env python3
"""Local provider-neutral catalog provider (QB-295, LOCAL_PROVIDER_INTEGRATION).

Serves the QwenBridge http-json catalog change feed (GET /feed?cursor=..., pages of `changes`
with `nextCursor` and `hasMore`) from a fixture catalog, behind a bearer token. The behaviour is
switched at run time with POST /control {"mode": ...}, so one configured endpoint can exercise:

  ok            pages of 4 records, cursor pagination
  unauthorized  401 for every request
  malformed     200 with a body that is not JSON
  rate-limited  429 for the next two requests, then ok (the connector retries)
  error         500 for every request
  empty         a valid page with no changes
  shutdown      the provider stops listening (unavailability)

Every request is logged in GET /requests (method, path, status, authorized) for evidence.
Usage: catalog_provider.py PORT CATALOG_JSON TOKEN
"""
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT, CATALOG, TOKEN = int(sys.argv[1]), sys.argv[2], sys.argv[3]
RECORDS = json.load(open(CATALOG, encoding="utf-8"))["records"]
PAGE = 4
STATE = {"mode": "ok", "limited": 0}
LOG = []
LOCK = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def reply(self, status, body, raw=False):
        data = body.encode("utf-8") if raw else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        with LOCK:
            LOG.append({"path": urlparse(self.path).path, "status": status,
                        "authorized": self.headers.get("Authorization") == "Bearer " + TOKEN})

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/requests":
            with LOCK:
                snapshot = list(LOG)
            return self.reply(200, snapshot)
        if url.path != "/feed":
            return self.reply(404, {"error": "not found"})
        with LOCK:
            mode = STATE["mode"]
            if mode == "rate-limited" and STATE["limited"] < 2:
                STATE["limited"] += 1
                limited = True
            else:
                limited = False
        if self.headers.get("Authorization") != "Bearer " + TOKEN or mode == "unauthorized":
            return self.reply(401, {"error": "unauthorized"})
        if limited:
            return self.reply(429, {"error": "rate limited"})
        if mode == "error":
            return self.reply(500, {"error": "provider failure"})
        if mode == "malformed":
            return self.reply(200, "<html>not json", raw=True)
        cursor = parse_qs(url.query).get("cursor", [""])[0]
        start = int(cursor) if cursor.isdigit() else 0
        rows = [] if mode == "empty" else RECORDS[start:start + PAGE]
        has_more = mode != "empty" and start + PAGE < len(RECORDS)
        changes = [{"id": r["id"], "deleted": False, "document": r} for r in rows]
        self.reply(200, {"changes": changes, "nextCursor": str(start + PAGE) if has_more else "",
                         "hasMore": has_more})

    def do_POST(self):
        if urlparse(self.path).path != "/control":
            return self.reply(404, {"error": "not found"})
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or "{}")
        with LOCK:
            STATE["mode"] = body.get("mode", "ok")
            STATE["limited"] = 0
        self.reply(200, {"mode": STATE["mode"]})
        if STATE["mode"] == "shutdown":
            threading.Thread(target=SERVER.shutdown, daemon=True).start()


ThreadingHTTPServer.daemon_threads = True
SERVER = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
SERVER.serve_forever()
SERVER.server_close()
