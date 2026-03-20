#!/usr/bin/env python3
"""Test: xlsx source -> docx template fill (Shandong air quality)"""
import urllib.request
import json
import sys
import os
import time

BASE_URL = "http://localhost:18080/api/v1"
TOKEN = None

def get_token():
    global TOKEN
    data = json.dumps({"username": "drw", "password": "drw"}).encode()
    req = urllib.request.Request(f"{BASE_URL}/users/auth", data=data,
                                headers={"Content-Type": "application/json"}, method="POST")
    resp = json.loads(urllib.request.urlopen(req, timeout=10).read().decode())
    TOKEN = resp["data"]["token"]
    print(f"Login OK (userId={resp['data']['userId']})")

def api_json(method, path, data=None, timeout=120):
    headers = {"Authorization": f"Bearer {TOKEN}"}
    if data is not None:
        body = json.dumps(data).encode()
        headers["Content-Type"] = "application/json"
        req = urllib.request.Request(f"{BASE_URL}{path}", data=body, headers=headers, method=method)
    else:
        req = urllib.request.Request(f"{BASE_URL}{path}", headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(req, timeout=timeout)
        return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"  HTTP {e.code}: {body[:500]}")
        return None

def api_upload(path, file_path, field_name="file"):
    boundary = "----FormBoundary" + str(int(time.time()))
    filename = os.path.basename(file_path)
    with open(file_path, "rb") as f:
        file_data = f.read()
    parts = []
    parts.append(f"--{boundary}".encode())
    parts.append(f'Content-Disposition: form-data; name="{field_name}"; filename="{filename}"'.encode())
    parts.append(b"Content-Type: application/octet-stream")
    parts.append(b"")
    parts.append(file_data)
    parts.append(f"--{boundary}--".encode())
    body = b"\r\n".join(parts)
    headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Authorization": f"Bearer {TOKEN}"
    }
    req = urllib.request.Request(f"{BASE_URL}{path}", data=body, headers=headers, method="POST")
    try:
        resp = urllib.request.urlopen(req, timeout=60)
        return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"  HTTP {e.code}: {body[:500]}")
        return None

def main():
    print("=== DocAI Word Fill Test (Shandong Air Quality) ===\n")
    get_token()

    # Step 1: Use existing source doc (already parsed in DB)
    # id=10000030 is the Shandong xlsx, already parsed
    xlsx_doc_id = 10000030
    print(f"\n[1] Using existing source doc id={xlsx_doc_id}")
    xlsx_doc = {"id": xlsx_doc_id}

    # Step 2: Upload docx template
    print("\n[2] Upload docx template...")
    tpl_path = r"f:\DocAI\docai-pro\data\local-oss\template_files\2025山东省环境空气质量监测数据信息-模板.docx"
    if not os.path.exists(tpl_path):
        print(f"  ERROR: Template not found at {tpl_path}")
        return
    resp = api_upload("/template/upload", tpl_path)
    if not resp or resp.get("code") != 200:
        print(f"  Upload failed: {resp}")
        return
    tpl_id = resp["data"]["id"]
    print(f"  Template uploaded: id={tpl_id}")

    # Step 3: Parse slots
    print("\n[3] Parse slots...")
    resp = api_json("POST", f"/template/{tpl_id}/parse")
    if resp:
        slots = resp.get("data", [])
        print(f"  Total slots: {len(slots)}")
        header_below = [s for s in slots if s.get("slotType") == "header_below"]
        other = [s for s in slots if s.get("slotType") != "header_below"]
        print(f"  header_below: {len(header_below)}")
        print(f"  other: {len(other)}")
        for s in slots[:30]:
            print(f"    {s['label']} - {s.get('slotType', '')} @ {s['position']}")
        if len(slots) > 30:
            print(f"    ... and {len(slots)-30} more")
    else:
        print("  Parse failed!")
        return

    # Step 4: Fill
    print("\n[4] Fill template...")
    user_req = "完成填表工作，要求提取表格中对应数据"
    doc_ids = [xlsx_doc["id"]]
    start = time.time()
    resp = api_json("POST", f"/template/{tpl_id}/fill", {
        "docIds": doc_ids,
        "userRequirement": user_req
    }, timeout=600)
    elapsed = time.time() - start
    print(f"  Completed in {elapsed:.1f}s")
    if resp:
        print(f"  Response code: {resp.get('code')}")
        result_data = resp.get("data", {})
        if isinstance(result_data, dict):
            output = result_data.get("outputFile")
            print(f"  Output: {output}")
        else:
            print(f"  Result: {json.dumps(resp, ensure_ascii=False)[:500]}")
            output = None
    else:
        print("  Fill failed!")
        return

    # Step 5: Verify output docx
    if output and os.path.exists(output):
        print(f"\n[5] Verifying output: {output}")
        try:
            from docx import Document
            doc = Document(output)
            tables = doc.tables
            print(f"  Tables in output: {len(tables)}")
            for ti, table in enumerate(tables):
                rows = table.rows
                print(f"\n  --- Table {ti}: {len(rows)} rows ---")
                for ri, row in enumerate(rows):
                    cells = row.cells
                    vals = [c.text.strip()[:20] for c in cells]
                    if ri < 3 or ri >= len(rows) - 2:
                        print(f"    Row {ri:3d}: {' | '.join(vals)}")
                    elif ri == 3:
                        print(f"    ... ({len(rows) - 4} rows omitted) ...")
                # Count filled
                filled = 0
                empty = 0
                for ri in range(1, len(rows)):
                    for c in rows[ri].cells:
                        if c.text.strip():
                            filled += 1
                        else:
                            empty += 1
                print(f"  Filled cells: {filled}, Empty: {empty}")
        except ImportError:
            print("  (python-docx not installed, skipping verification)")
    else:
        print(f"\n[5] Output file not found: {output}")

    print("\n=== Test Complete ===")

if __name__ == "__main__":
    main()
