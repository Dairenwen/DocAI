#!/usr/bin/env python3
"""Debug: test direct ai-service and gateway routes"""
import urllib.request
import json

def test_url(label, url, headers=None):
    try:
        req = urllib.request.Request(url, headers=headers or {})
        resp = urllib.request.urlopen(req, timeout=10)
        body = resp.read().decode()[:200]
        print(f"  [{label}] OK {resp.status}: {body}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()[:200]
        print(f"  [{label}] HTTP {e.code}: {body}")
    except Exception as e:
        print(f"  [{label}] Error: {e}")

# Login
data = json.dumps({"username":"drw","password":"drw"}).encode()
req = urllib.request.Request("http://localhost:18080/api/v1/users/auth", data=data,
    headers={"Content-Type":"application/json"}, method="POST")
resp = json.loads(urllib.request.urlopen(req, timeout=10).read().decode())
token = resp["data"]["token"]
print(f"Token: {token[:40]}...")
auth = {"Authorization": f"Bearer {token}"}

# Direct ai-service (port 9002) - no auth needed
print("\nDirect ai-service (9002):")
test_url("no-auth", "http://localhost:9002/source/documents")
test_url("with-auth", "http://localhost:9002/source/documents", auth)

# Gateway (port 18080)
print("\nVia gateway (18080):")
test_url("source", "http://localhost:18080/api/v1/source/documents", auth)
test_url("template", "http://localhost:18080/api/v1/template/list", auth)

# Check if gateway filter has extra CORS/auth issue
print("\nGateway with X-Gateway-Token:")
test_url("x-gw", "http://localhost:18080/api/v1/source/documents", 
         {**auth, "X-Gateway-Token": "docai-gateway-token"})
