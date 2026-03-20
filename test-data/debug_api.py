#!/usr/bin/env python3
"""Quick debug script to test API auth"""
import urllib.request, json

data = json.dumps({"username":"drw","password":"drw"}).encode()
req = urllib.request.Request("http://localhost:18080/api/v1/users/auth", data=data, 
    headers={"Content-Type":"application/json"}, method="POST")
resp = json.loads(urllib.request.urlopen(req, timeout=10).read().decode())
token = resp["data"]["token"]
print(f"Token OK: {token[:30]}...")

# Test various endpoints
endpoints = [
    ("GET", "/source/documents"),
    ("GET", "/ai/source/documents"),  
    ("GET", "/template/list"),
    ("GET", "/ai/template/list"),
]
for method, path in endpoints:
    try:
        req2 = urllib.request.Request(f"http://localhost:18080/api/v1{path}", 
            headers={"Authorization": f"Bearer {token}"}, method=method)
        resp2 = urllib.request.urlopen(req2, timeout=10)
        body = resp2.read().decode()[:100]
        print(f"  {method} {path} -> OK: {body}")
    except urllib.error.HTTPError as e:
        print(f"  {method} {path} -> HTTP {e.code}")
    except Exception as e:
        print(f"  {method} {path} -> Error: {e}")
