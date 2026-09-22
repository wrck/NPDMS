"""Domain-test (19191/59191) live acceptance. Own fixtures only; no production data rewrites.

Credentials: MATERIAL_EXCHANGE_TEST_PASSWORD environment variable; DB credentials use .env.
"""
import json
import os
import re
import sys
import time
from pathlib import Path
import pymysql
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "output/material-exchange-live"
FRONTEND = "http://127.0.0.1:19191"
BACKEND = "http://127.0.0.1:59191/admin-api"


def database():
    values = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return pymysql.connect(host="127.0.0.1", port=24306, database="npdms_domain_test",
                           user=values["NPDMS_DB_USER"], password=values["NPDMS_DB_PASSWORD"], autocommit=True)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    password = os.environ["MATERIAL_EXCHANGE_TEST_PASSWORD"]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    run_id = str(int(time.time()))
    base = int(run_id) * 10000
    prefix = "ME-SCOPE-" + run_id
    project_id = 1001
    device_ids = list(range(base + 1, base + 29))
    with database() as connection, connection.cursor() as cursor:
        cursor.execute("SELECT project_name, contract_no FROM proj_project WHERE id=%s AND tenant_id=1 AND deleted=0", (project_id,))
        project_name, contract_no = cursor.fetchone()
        cursor.execute("SELECT COUNT(*) FROM ast_device WHERE id BETWEEN %s AND %s", (device_ids[0], device_ids[-1]))
        assert cursor.fetchone()[0] == 0, "fixture ID collision"
        rows = []
        for i in range(1, 29):
            # 24 direct, one contract-only, then unrelated/cross-tenant/deleted devices.
            project = project_id if i <= 24 or i >= 27 else 1002
            contract = contract_no if i in (25, 27) else prefix + "-OTHER"
            rows.append((base+i, f"{prefix}-{i:02}", f"换货验收设备 {i:02}", "ME-TEST", "ME-MODEL",
                         project, contract, "IN_STOCK", "MANUAL", 2 if i == 27 else 1, i == 28, "codex-material-exchange-test"))
        cursor.executemany("INSERT INTO ast_device (id,sn,name,product_code,product_model,project_id,contract_no,status,source_system,tenant_id,deleted,creator) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", rows)

    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 1100})
        page.set_default_timeout(20000)
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(FRONTEND + "/login", wait_until="networkidle")
        page.get_by_placeholder("请输入用户名", exact=True).fill("admin")
        page.get_by_placeholder("请输入密码", exact=True).first.fill(password)
        page.get_by_role("button", name="登录", exact=True).click()
        try:
            page.wait_for_url("**/index")
        except Exception:
            print("Login result:", page.url, page.locator("body").inner_text()[:1600])
            raise
        page.goto(FRONTEND + "/pms/engineering/procurement/imp-material-exch", wait_until="networkidle")
        page.get_by_role("button", name="新建换货申请", exact=True).click()
        dialog = page.get_by_role("dialog").filter(has_text="新建换货申请")

        def field(name):
            return dialog.locator(".el-form-item").filter(has=page.locator(".el-form-item__label", has_text=re.compile("^" + name + "$")))

        field("项目").locator(".el-select").click()
        field("项目").locator("input").fill(project_name)
        page.get_by_role("option", name=project_name, exact=True).click()
        dialog.get_by_label("筛选序列号", exact=True).fill(prefix)
        dialog.get_by_role("button", name="筛选", exact=True).click()
        table = dialog.get_by_test_id("device-candidates")
        expect(table.get_by_text(prefix + "-25", exact=True)).to_be_visible()
        table.locator(".el-table__body-wrapper tbody tr").filter(has_text=prefix + "-25").locator(".el-checkbox").click()
        dialog.get_by_test_id("device-pagination").locator(".btn-next").click()
        expect(table.get_by_text(prefix + "-01", exact=True)).to_be_visible()
        table.locator(".el-table__body-wrapper tbody tr").filter(has_text=prefix + "-01").locator(".el-checkbox").click()
        expect(dialog.locator(".selection-summary")).to_contain_text("已选择 2 台")
        field("单号").locator("input").fill(prefix)
        field("名称").locator("input").fill("项目或合同设备换货验收 " + run_id)
        field("换货原因").locator('[contenteditable="true"]').fill("专用验收记录：项目设备和关联合同设备，跨页多选后保存序列号子表。")
        field("申请人").locator(".el-select").click()
        field("申请人").locator("input").fill("管理员")
        page.get_by_role("option", name="管理员", exact=True).click()
        field("申请时间").locator("input").fill(time.strftime("%Y-%m-%d %H:%M:%S"))
        field("申请时间").locator("input").press("Tab")
        with page.expect_response(lambda response: "/pms/imp-material-exch/create" in response.url) as created:
            dialog.get_by_role("button", name="保存", exact=True).click()
        create_response = created.value.json()
        assert create_response["code"] == 0, create_response
        application_id = create_response["data"]
        original_payload = created.value.request.post_data_json
        expect(dialog).not_to_be_visible()
        row = page.get_by_role("row").filter(has_text=prefix)
        row.get_by_role("button", name="编辑", exact=True).click()
        dialog = page.get_by_role("dialog").filter(has_text="编辑换货申请")
        expect(dialog.locator(".selection-summary")).to_contain_text("已选择 2 台")
        expect(dialog.locator('.el-table').filter(has_text='已选序列号').get_by_text(prefix + "-25", exact=True)).to_be_visible()
        expect(dialog.locator('.el-table').filter(has_text='已选序列号').get_by_text(prefix + "-01", exact=True)).to_be_visible()
        page.screenshot(path=str(OUTPUT / "saved-reopened.png"), full_page=True, animations="disabled")
        dialog.locator('.el-table').filter(has_text='已选序列号').get_by_role('row').filter(has_text=prefix + '-01').get_by_role('button', name='移除').click()
        dialog.get_by_label('筛选序列号', exact=True).fill(prefix + '-02')
        dialog.get_by_role('button', name='筛选', exact=True).click()
        table = dialog.get_by_test_id('device-candidates')
        expect(table.get_by_text(prefix + '-02', exact=True)).to_be_visible()
        table.locator('.el-table__body-wrapper tbody tr').filter(has_text=prefix + '-02').locator('.el-checkbox').click()
        with page.expect_response(lambda response: '/pms/imp-material-exch/update' in response.url) as updated:
            dialog.get_by_role('button', name='保存', exact=True).click()
        assert updated.value.json()['code'] == 0, updated.value.json()
        expect(dialog).not_to_be_visible()
        row.get_by_role('button', name='编辑', exact=True).click()
        dialog = page.get_by_role('dialog').filter(has_text='编辑换货申请')
        expect(dialog.locator('.selection-summary')).to_contain_text('已选择 2 台')
        expect(dialog.locator('.el-table').filter(has_text='已选序列号').get_by_text(prefix + '-02', exact=True)).to_be_visible()
        expect(dialog.locator('.el-table').filter(has_text='已选序列号').get_by_text(prefix + '-25', exact=True)).to_be_visible()
        page.screenshot(path=str(OUTPUT / 'edited-reopened.png'), full_page=True, animations="disabled")
        dialog.get_by_role("button", name="取消", exact=True).click()

        # Actual authenticated HTTP failure paths, without bypassing the server.
        token = page.evaluate("async()=> (await import('/src/utils/auth.ts')).getAccessToken()")
        headers = {"Authorization": "Bearer " + token, "tenant-id": "1"}
        def request(method, path, data=None):
            response = page.request.fetch(BACKEND + path, method=method, data=data, headers=headers)
            return response.json()

        candidates = request("GET", "/pms/asset/devices/archive-page?selectionProjectId=1001&pageNo=1&pageSize=100&sn=" + prefix)
        assert candidates["code"] == 0 and candidates["data"]["total"] == 25, candidates
        visible = {item["id"] for item in candidates["data"]["list"]}
        assert base+25 in visible and all(base+i not in visible for i in [26, 27, 28])
        failures = []
        for suffix, ids in [("unrelated", [base+26]), ("cross-tenant", [base+27]), ("deleted", [base+28]), ("duplicate", [base+1, base+1])]:
            payload = {**original_payload, "code": prefix + "-" + suffix, "quantity": len(ids),
                       "serials": [{"equipmentId": value, "sn": "FORGED"} for value in ids]}
            result = request("POST", "/pms/imp-material-exch/create", payload)
            assert result["code"] == 1015006000, (suffix, result)
            failures.append({"case": suffix, "code": result["code"]})
        module_failures = []
        for module in ['imp-arrival', 'imp-installation', 'imp-configuration', 'imp-joint-test', 'imp-material-req', 'sol-resource']:
            payload = {**original_payload, 'equipmentId': base+26, 'arrivalTime': int(time.time()*1000),
                       'testCase': '设备范围验收', 'materialName': '验收设备', 'quantity': 1}
            result = request('POST', '/pms/' + module + '/create', payload)
            assert result['code'] == 1015006000, (module, result)
            module_failures.append({'module': module, 'code': result['code']})
        detail = request("GET", f"/pms/imp-material-exch/get?id={application_id}")["data"]
        assert {item["sn"] for item in detail["serials"]} == {prefix+"-02", prefix+"-25"}
        stale = {**original_payload, "id": application_id, "version": detail["version"] - 1}
        stale_result = request("PUT", "/pms/imp-material-exch/update", stale)
        assert stale_result["code"] != 0, stale_result
        with database() as connection, connection.cursor() as cursor:
            cursor.execute("SELECT equipment_id,sn FROM imp_eng_material_exchange_serial WHERE exchange_id=%s AND tenant_id=1 AND deleted=0 ORDER BY equipment_id", (application_id,))
            saved_serials = cursor.fetchall()
            assert saved_serials == ((base+2, prefix+"-02"), (base+25, prefix+"-25")), saved_serials
            cursor.execute('SELECT COUNT(*) FROM imp_eng_material_exchange_serial WHERE exchange_id=%s AND deleted=1', (application_id,))
            assert cursor.fetchone()[0] == 2, 'previous child rows were not retained as deleted history'
            cursor.execute("SELECT COUNT(*) FROM imp_eng_material_exchange WHERE code LIKE %s", (prefix+"%",))
            assert cursor.fetchone()[0] == 1, "rejected request left an application"
        assert not errors, errors
        result = {"applicationId": application_id, "code": prefix, "projectId": project_id,
                  "candidateCount": 25, "savedSerials": saved_serials, "rejected": failures,
                  "otherModulesRejected": module_failures, "editedAndReopened": True,
                  "staleVersionCode": stale_result["code"], "pageErrors": errors,
                  "runtime": {"frontend": 19191, "backend": 59191, "database": "npdms_domain_test"}}
        (OUTPUT / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(result, ensure_ascii=False))
        browser.close()


if __name__ == "__main__":
    main()
