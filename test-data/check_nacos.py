#!/usr/bin/env python3
"""Query nacos configs"""
import urllib.request
import json

nacos = "http://localhost:8848/nacos/v1/cs/configs"
groups = ["DEFAULT_GROUP"]
data_ids = ["application.properties", "ai-service.properties", "gateway-service.properties", 
            "ai-service.yml", "gateway-service.yml", "application.yml"]

for did in data_ids:
    for g in groups:
        url = f"{nacos}?dataId={did}&group={g}"
        try:
            resp = urllib.request.urlopen(url, timeout=5)
            body = resp.read().decode()
            if body.strip():
                print(f"\n=== {did} ({g}) ===")
                print(body[:1000])
        except:
            pass

# Also check for all configs
print("\n=== ALL CONFIGS ===")
url2 = f"http://localhost:8848/nacos/v1/cs/configs?search=accurate&dataId=&group=&pageNo=1&pageSize=50"
try:
    resp = urllib.request.urlopen(url2, timeout=5)
    data = json.loads(resp.read().decode())
    for item in data.get("pageItems", []):
        print(f"  dataId={item['dataId']}, group={item['group']}")
        content = item.get("content", "")
        if "gateway" in content.lower() or "security" in content.lower():
            print(f"    CONTENT: {content[:300]}")
except Exception as e:
    print(f"Error: {e}")
