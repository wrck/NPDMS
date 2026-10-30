"""Real Chromium picker interaction check; API fixtures, not business acceptance.

Run with the existing frontend Vite server on port 18081.
"""
import json
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[2]
FRONTEND = ROOT / "yudao-ui/yudao-ui-admin-vue3"
OUTPUT = ROOT / "output/material-exchange-browser"
HARNESS = FRONTEND / "material-exchange-picker.acceptance.html"

HTML = '''<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><title>换货设备组件验收</title>
<body style="margin:24px"><div id="app"></div><script type="module">
import { createApp, h, ref } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Picker from '/src/views/pms/engineering/material-exch/MaterialDevicePicker.vue'
import Pagination from '/src/components/Pagination/index.vue'
const app = createApp({ setup() {
  const serials = ref([])
  return () => h('main', { style: 'max-width:1000px;margin:auto' }, [
    h('h2', '换货申请 · 设备选择'),
    h(Picker, { projectId: 10, modelValue: serials.value, 'onUpdate:modelValue': value => serials.value = value }),
    h('pre', { id: 'selected-result', style: 'white-space:pre-wrap;overflow-wrap:anywhere' }, JSON.stringify(serials.value))
  ])
}})
app.use(createPinia()).use(ElementPlus).component('Pagination', Pagination).mount('#app')
</script></body></html>'''


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if HARNESS.exists():
        raise RuntimeError(f"Refusing to overwrite {HARNESS}")
    HARNESS.write_text(HTML, encoding="utf-8")
    requests, errors = [], []
    devices = [{"id": i, "sn": f"SN-{i:04}", "name": f"测试设备 {i}",
                "productModel": "SW-100", "contractNo": "TEST-CONTRACT"} for i in range(1, 126)]
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(viewport={"width": 1280, "height": 1000})
            page.on("pageerror", lambda error: errors.append(str(error)))

            def candidates(route):
                query = {key: values[0] for key, values in parse_qs(urlparse(route.request.url).query).items()}
                requests.append(query)
                assert query.get("selectionProjectId") == "10", query
                rows = devices
                for field in ["sn", "name", "productModel", "contractNo"]:
                    if query.get(field):
                        rows = [row for row in rows if query[field] in row[field]]
                size, number = int(query["pageSize"]), int(query["pageNo"])
                route.fulfill(json={"code": 0, "data": {"list": rows[(number-1)*size:number*size], "total": len(rows)}})

            page.route("**/admin-api/pms/asset/devices/archive-page*", candidates)
            page.goto("http://127.0.0.1:18081/" + HARNESS.name)
            page.wait_for_load_state("networkidle")
            table = page.get_by_test_id("device-candidates")
            expect(table.get_by_text("SN-0001", exact=True)).to_be_visible(timeout=30000)
            table.locator(".el-table__body-wrapper tbody tr").first.locator(".el-checkbox").click()
            expect(page.locator(".selection-summary")).to_contain_text("已选择 1 台")
            page.get_by_test_id("device-pagination").locator(".btn-next").click()
            expect(table.get_by_text("SN-0021", exact=True)).to_be_visible()
            table.locator(".el-table__body-wrapper tbody tr").first.locator(".el-checkbox").click()
            expect(page.locator(".selection-summary")).to_contain_text("已选择 2 台")
            page.get_by_label("筛选序列号", exact=True).fill("SN-0125")
            page.get_by_role("button", name="筛选", exact=True).click()
            expect(table.get_by_text("SN-0125", exact=True)).to_be_visible()
            table.locator(".el-table__body-wrapper tbody tr").first.locator(".el-checkbox").click()
            expect(page.locator(".selection-summary")).to_contain_text("已选择 3 台")
            expect(table.locator(".el-table__body-wrapper tbody tr").first.get_by_role("checkbox")).to_be_checked()
            selected = json.loads(page.locator("#selected-result").inner_text())
            assert [row["equipmentId"] for row in selected] == [1, 21, 125]
            page.get_by_role("button", name="移除", exact=True).first.click()
            expect(page.locator(".selection-summary")).to_contain_text("已选择 2 台")
            expect(table.locator(".el-table__body-wrapper tbody tr").first.get_by_role("checkbox")).to_be_checked()
            page.screenshot(path=str(OUTPUT / "picker.png"), full_page=True)
            page.set_viewport_size({"width": 768, "height": 1000})
            page.screenshot(path=str(OUTPUT / "picker-768.png"), full_page=True)
            assert not errors, errors
            result = {"browser": "Chromium", "scope": "real component with API fixtures",
                      "checks": ["server-side filter parameters", "cross-page selection", "selection beyond first 100", "remove"],
                      "selected": [row["equipmentId"] for row in json.loads(page.locator("#selected-result").inner_text())],
                      "requests": requests, "pageErrors": errors}
            (OUTPUT / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            print(json.dumps({"passed": True, "checks": result["checks"], "pageErrors": errors}, ensure_ascii=False))
            browser.close()
    finally:
        HARNESS.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
