#!/usr/bin/env python3
"""Chromium production legacy solution SFC/helper acceptance with explicitly mocked HTTP ports.

Complementary to SolutionCustomerDocumentServiceTest; not a real backend/database/login E2E.
"""
import json
import subprocess
import time
import urllib.request
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright, expect
from run_native_arrival_delivery_browser import prepare, FIXTURE, FRONTEND, OUTPUT


def main():
    prepare()
    main_file = FIXTURE / "main.ts"
    main_file.write_text(main_file.read_text().replace("import Arrival from '@/views/pms/engineering/arrival/index.vue'", "import Solution from '@/views/pms/engineering/solution/index.vue'").replace("createApp(Arrival)", "createApp(Solution)"))
    with (FIXTURE / "ports.ts").open("a") as ports:
        ports.write("\nexport const getProjectMembers = async () => [];\nexport const getProject = async () => ({projectCode:'FIXTURE',projectName:'隔离方案'});\nexport const getDictLabel = () => '';\n")
    row = {"id": 9, "projectId": 20, "name": "原生客户方案浏览器夹具", "code": "SOL-9", "status": 0, "version": 2,
           "solutionType": "IMPLEMENTATION", "remark": json.dumps({"hasCustomerPlan": "yes", "customerPlanUrl": "https://legacy/old.pdf", "trainingPurpose": "preserve"})}
    material = []
    counts = {"ownerSaves": 0, "initUploads": 0, "completeUploads": 0, "nativeAttachments": 0}
    requests = []
    failed_owner = None

    def respond(route):
        nonlocal failed_owner
        request = route.request
        path = urlparse(request.url).path
        data = None
        status = 200
        if request.method == "GET" and path == "/pms/sol-solution/page":
            data = {"list": [dict(row)], "total": 1}
        elif request.method == "GET" and path == "/pms/sol-solution/get":
            data = dict(row)
        elif request.method == "PUT" and path == "/pms/sol-solution/update":
            saved = request.post_data_json
            assert saved["id"] == 9 and saved["version"] == row["version"], saved
            row.update(saved)
            row["version"] += 1
            counts["ownerSaves"] += 1
            data = True
        elif path == "/api/v1/pms/delivery/types":
            data = [{"id": 1, "typeCode": "IMPLEMENTATION_PLAN", "name": "实施方案", "enabled": True,
                     "category": "BUSINESS_DOCUMENT", "maxSizeBytes": 52428800, "allowedMediaJson": '["txt"]'}]
        elif path == "/api/v1/pms/delivery/materials":
            data = material
        elif path.startswith("/api/v1/pms/delivery/"):
            data = []
        elif path == "/api/v1/pms/files:init-upload":
            saved = request.post_data_json
            assert saved["objectId"] == "SOL:solution:9" and saved["purposeCode"] == "IMPLEMENTATION_PLAN", saved
            assert saved["ownerContext"] == "PLT" and saved["fileName"] == "customer.txt", saved
            counts["initUploads"] += 1
            data = {"artifactId": 31, "sessionId": 41, "expiresAt": "2026-10-06T12:00:00"}
        elif path == "/api/v1/pms/files/31:complete-upload":
            assert "Actual selected customer file" in request.post_data, request.post_data
            counts["completeUploads"] += 1
            data = {"artifactId": 31, "referenceId": 41, "versionNo": 1, "referenceKey": "fixture-customer"}
        elif path == "/api/v1/pms/solutions/9/customer-files":
            saved = request.post_data_json
            assert saved == {"expectedVersion": 3, "referenceIds": [41]}, saved
            counts["nativeAttachments"] += 1
            if counts["nativeAttachments"] == 1:
                failed_owner = dict(row)
                status = 503
                data = None
            else:
                pointer = "/api/v1/pms/solutions/9/customer-files/51/customer.txt"
                row["version"] += 1
                envelope = json.loads(row["remark"])
                envelope["customerPlanUrl"] = pointer
                row["remark"] = json.dumps(envelope)
                material.append({"id": 51, "ownerModule": "SOL", "entityType": "solution", "entityId": 9,
                                 "typeCode": "IMPLEMENTATION_PLAN", "materialKind": "FILE", "fileName": "customer.txt",
                                 "fileVersionNo": 1, "sourceKind": "UPLOAD", "status": "ACTIVE"})
                data = {"version": 4, "customerPlanUrl": pointer, "materialIds": [51]}
        else:
            data = {"list": [], "total": 0}
        requests.append({"method": request.method, "path": path, "status": status})
        route.fulfill(status=status, content_type="application/json", body=json.dumps({"code": 0 if status == 200 else status,
                      "data": data, "msg": "" if status == 200 else "Fixture native attachment temporarily unavailable"}, ensure_ascii=False))

    with (FIXTURE / "solution-vite.log").open("w") as log:
        vite = subprocess.Popen(["node", str(FRONTEND / "node_modules/vite/bin/vite.js"), "--config", str(FIXTURE / "vite.config.mjs")], cwd=FIXTURE, stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(100):
                if vite.poll() is not None:
                    raise RuntimeError((FIXTURE / "solution-vite.log").read_text())
                try:
                    urllib.request.urlopen("http://127.0.0.1:28463", timeout=1).close()
                    break
                except OSError:
                    time.sleep(0.1)
            with sync_playwright() as playwright:
                browser = playwright.chromium.launch(executable_path="/usr/bin/chromium", headless=True, args=["--no-sandbox"])
                page = browser.new_page(viewport={"width": 1440, "height": 1100})
                errors = []
                page.on("pageerror", lambda error: errors.append(str(error)))
                page.route("http://127.0.0.1:28463/pms/**", respond)
                page.route("http://127.0.0.1:28463/api/v1/pms/**", respond)
                page.goto("http://127.0.0.1:28463", wait_until="networkidle")
                page.get_by_role("button", name="编辑", exact=True).click()
                dialog = page.locator(".el-dialog")
                picker = dialog.locator(".el-form-item").filter(has_text="上传方案文件")
                picker.locator('input[type="file"]').set_input_files({"name": "customer.txt", "mimeType": "text/plain", "buffer": b"Actual selected customer file\n"})
                save = dialog.get_by_role("button", name="保存草稿", exact=True).last
                save.click()
                expect(dialog.get_by_text("Fixture native attachment temporarily unavailable", exact=True)).to_be_visible(timeout=15000)
                assert counts == {"ownerSaves": 1, "initUploads": 1, "completeUploads": 1, "nativeAttachments": 1}, counts
                assert json.loads(failed_owner["remark"])["customerPlanUrl"] == "https://legacy/old.pdf", failed_owner
                save.click()
                expect(dialog).not_to_be_visible(timeout=15000)
                assert counts == {"ownerSaves": 1, "initUploads": 1, "completeUploads": 1, "nativeAttachments": 2}, counts
                assert json.loads(row["remark"])["trainingPurpose"] == "preserve", row
                page.get_by_role("button", name="编辑", exact=True).click()
                expect(page.locator(".el-dialog").get_by_role("button", name="customer.txt", exact=True)).to_be_visible(timeout=10000)
                expect(page.locator(".el-dialog").get_by_text("交付件（统一交付能力）", exact=True)).to_be_visible()
                page.screenshot(path=str(OUTPUT / "solution-customer-browser.png"), full_page=True)
                assert not errors, errors
                browser.close()
            evidence = {"scope": "real Chromium + production legacy solution Vue and existing customer upload/attach helper; mocked HTTP only; no production backend/database/login acceptance",
                        "passed": True, "counts": counts, "failedOwner": failed_owner, "savedOwner": row,
                        "materialFixture": material, "pageErrors": errors, "requests": requests}
            (OUTPUT / "solution-customer-browser.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")
            print("PASS native legacy solution customer file selection, original root attachment, failed attachment retry without repeated save/upload, persisted locator display")
        finally:
            vite.terminate()
            try:
                vite.wait(timeout=10)
            except subprocess.TimeoutExpired:
                vite.kill()
                vite.wait()


if __name__ == "__main__":
    main()
