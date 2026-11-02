#!/usr/bin/env python3
"""Exit 0 when a Redis server answers PING at the given redis://host:port URL (QB-295)."""
import socket
import sys
from urllib.parse import urlparse

url = urlparse(sys.argv[1])
try:
    with socket.create_connection((url.hostname or "127.0.0.1", url.port or 6379), timeout=2) as s:
        s.sendall(b"*1\r\n$4\r\nPING\r\n")
        sys.exit(0 if s.recv(16).startswith(b"+PONG") else 1)
except OSError:
    sys.exit(1)
