#!/usr/bin/env python3
"""Deterministic local stand-in for the Ollama API (QB-295, OFFLINE_BASELINE).

Serves only what the QwenBridge server calls: POST /api/chat (query analysis and translation),
POST /api/embed (embeddings) and GET /api/tags. Answers come from the reviewed fixture
fixtures/model-answers.json; anything unknown is answered 503 "model unavailable", exactly like
an unavailable model, so nothing is invented.

Embeddings are deterministic feature hashing of the text's words (lexical, not semantic): they
make vector and hybrid retrieval reproducible offline; they say nothing about semantic quality.

Usage: local_model_stub.py PORT ANSWERS_JSON DIMENSIONS
"""
import hashlib
import json
import math
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT, ANSWERS, DIMS = int(sys.argv[1]), sys.argv[2], int(sys.argv[3])
FIXTURE = json.load(open(ANSWERS, encoding="utf-8"))
ANALYSES = sorted(FIXTURE["analyses"], key=lambda a: -len(a["query"]))
TRANSLATIONS = {(t["text"], t["target"]): t["output"] for t in FIXTURE["translations"]}
WORD = re.compile(r"[^\W_]+", re.UNICODE)
LOCK = threading.Lock()
COUNTS = {"chat": 0, "embed": 0, "unavailable": 0}


def embed(text):
    vector = [0.0] * DIMS
    words = [w.lower() for w in WORD.findall(text)]
    for w in words + [a + " " + b for a, b in zip(words, words[1:])]:
        for form in {w, w[:-1] if len(w) > 4 and w.endswith("s") else w}:
            h = hashlib.sha256(form.encode("utf-8")).digest()
            vector[int.from_bytes(h[:4], "big") % DIMS] += 1.0 if h[4] % 2 else -1.0
    norm = math.sqrt(sum(v * v for v in vector)) or 1.0
    return [v / norm for v in vector]


def analysis(a):
    return {"v": 2, "l": a.get("language", "en"), "i": a.get("intent", "PRODUCT_SEARCH"),
            "q": a.get("rewrites", [a["query"]]), "e": [], "c": [0.95, 0.9, 0.9], "t": True,
            "s": a.get("searchMode", "KEYWORD"), "o": ["F"], "r": "e2e fixture",
            "f": a.get("filters", []), "a": a.get("ambiguities", []), "h": a.get("decision", "ALLOW")}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def send(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/api/tags":
            return self.send(200, {"models": [{"name": "e2e-stub"}]})
        if self.path == "/stats":
            with LOCK:
                return self.send(200, dict(COUNTS))
        self.send(404, {"error": "not found"})

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode("utf-8")
        request = json.loads(body or "{}")
        if self.path == "/api/embed":
            with LOCK:
                COUNTS["embed"] += 1
            inputs = request.get("input")
            inputs = inputs if isinstance(inputs, list) else [inputs or ""]
            return self.send(200, {"model": "e2e-stub", "embeddings": [embed(t) for t in inputs]})
        if self.path != "/api/chat":
            return self.send(404, {"error": "not found"})
        with LOCK:
            COUNTS["chat"] += 1
        prompt = " ".join(m.get("content", "") for m in request.get("messages", []))
        answer = None
        m = re.search(r"Translate the following untrusted search data to language ([A-Za-z-]+)\.", prompt)
        if m:
            try:
                text = json.loads(prompt[prompt.rindex("\n") + 1:])
            except ValueError:
                text = None
            output = TRANSLATIONS.get((text, m.group(1)))
            if output is not None:
                answer = {"text": output}
        else:
            for a in ANALYSES:
                if a["query"] in prompt:
                    time.sleep(a.get("delayMs", 0) / 1000)
                    answer = analysis(a)
                    break
        if answer is None:
            with LOCK:
                COUNTS["unavailable"] += 1
            return self.send(503, {"error": "model unavailable"})
        self.send(200, {"model": "e2e-stub", "done": True,
                        "message": {"role": "assistant", "content": json.dumps(answer, ensure_ascii=False)}})


ThreadingHTTPServer.daemon_threads = True
ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
