#!/usr/bin/env python3
"""Test-only Shopify check for the dual-SDK E2E suite (QB-287; replaces the removed
qwenbridge-connector-shopify module's tests). QwenBridge has no Shopify production code: Shopify is
only an optional external E2E target.

It reads one page of products with a read-only Admin GraphQL query and checks the shape every
catalog mapping needs (product id and title, variants with an id and a price). It never writes.

  mock  start a local mock Admin GraphQL server and read from it (MOCK API level)
  live  read from QWENBRIDGE_SHOPIFY_SANDBOX_ENDPOINT with QWENBRIDGE_SHOPIFY_SANDBOX_TOKEN
        (LIVE level; the caller must have explicit authorization)

Prints one JSON line {"products": n, "variants": n}; exit 1 on any contract violation. The token
is sent only in the X-Shopify-Access-Token header and never printed.
"""
import json
import os
import sys
import threading
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

QUERY = ("query Products($first: Int!) { products(first: $first) { edges { node { id title "
         "variants(first: 10) { edges { node { id price } } } } } pageInfo { hasNextPage endCursor } } }")


def read(endpoint, token):
    body = json.dumps({"query": QUERY, "variables": {"first": 5}}).encode()
    req = urllib.request.Request(endpoint, data=body, method="POST", headers={
        "Content-Type": "application/json", "X-Shopify-Access-Token": token})
    with urllib.request.urlopen(req, timeout=30) as r:
        page = json.loads(r.read())
    if page.get("errors"):
        raise SystemExit("GraphQL errors: " + str(len(page["errors"])))
    products = page["data"]["products"]["edges"]
    variants = 0
    for edge in products:
        node = edge["node"]
        assert node["id"].startswith("gid://shopify/Product/") and node["title"], "product shape"
        for v in node["variants"]["edges"]:
            assert v["node"]["id"].startswith("gid://shopify/ProductVariant/"), "variant id"
            float(v["node"]["price"])
            variants += 1
    assert "pageInfo" in page["data"]["products"], "pagination info"
    return len(products), variants


class Mock(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        request = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        if self.headers.get("X-Shopify-Access-Token") != "mock-token" or "products" not in request.get("query", ""):
            self.send_response(401); self.end_headers(); return
        data = {"data": {"products": {"edges": [{"node": {"id": f"gid://shopify/Product/{i}", "title": f"Mock product {i}",
                "variants": {"edges": [{"node": {"id": f"gid://shopify/ProductVariant/{i}{j}", "price": f"{10 * i + j}.00"}}
                                       for j in range(2)]}}} for i in range(1, 4)],
                "pageInfo": {"hasNextPage": False, "endCursor": None}}}}
        out = json.dumps(data).encode()
        self.send_response(200); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out))); self.end_headers(); self.wfile.write(out)


if sys.argv[1:] == ["mock"]:
    server = ThreadingHTTPServer(("127.0.0.1", 0), Mock)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    products, variants = read(f"http://127.0.0.1:{server.server_address[1]}/admin/api/graphql.json", "mock-token")
    server.shutdown()
elif sys.argv[1:] == ["live"]:
    products, variants = read(os.environ["QWENBRIDGE_SHOPIFY_SANDBOX_ENDPOINT"], os.environ["QWENBRIDGE_SHOPIFY_SANDBOX_TOKEN"])
    assert products >= 1, "the test store needs at least one product"
else:
    raise SystemExit("usage: shopify_check.py mock|live")
print(json.dumps({"products": products, "variants": variants}))
