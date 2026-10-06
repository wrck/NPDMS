#!/usr/bin/env python3
"""Real native Vue/file/delivery components against the exclusive MySQL JUnit HTTP fixture.

This deliberately does not claim full app login, production deployment, or Flyway acceptance.
Only unrelated layout/project lookup ports and the authenticated HTTP transport are fixtures.
"""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request

from playwright.sync_api import sync_playwright, expect

REPO = Path(__file__).resolve().parents[2]
FRONTEND = REPO / "yudao-ui/yudao-ui-admin-vue3"
FIXTURE = REPO / ".run/native-delivery-20261006/browser"
OUTPUT = REPO / "docs/generated/native-delivery-20261006"
HTTP = "http://127.0.0.1:28462"


def fixture(path):
    with urllib.request.urlopen(HTTP + path, timeout=10) as response:
        return json.load(response)


def write(name, content):
    (FIXTURE / name).write_text(content, encoding="utf-8")


def prepare():
    FIXTURE.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    modules = FIXTURE / "node_modules"
    if not modules.exists():
        modules.symlink_to(FRONTEND / "node_modules", target_is_directory=True)
    write("index.html", '<html lang="zh-CN"><head><meta charset="utf-8"></head><body><div id="app"></div><script type="module" src="/main.ts"></script></body></html>')
    write("http.ts", """
async function send(method, options) {
  const params = new URLSearchParams();
  Object.entries(options.params || {}).forEach(([key,value]) => { if(value !== undefined && value !== null) params.set(key,String(value)) });
  const query = params.toString(); const headers = {...options.headers};
  const multipart = options.data instanceof FormData;
  if(options.data && !multipart) headers['Content-Type']='application/json';
  const response = await fetch(options.url + (query ? '?' + query : ''), { method, headers,
    body: options.data ? (multipart ? options.data : JSON.stringify(options.data)) : undefined });
  const body = await response.json();
  if(!response.ok || body.code !== 0) throw new Error(body.msg || `HTTP ${response.status}`);
  return body.data;
}
export default Object.fromEntries(['get','post','put','delete'].map(name => [name, options => send(name.toUpperCase(),options)]));
""")
    write("ports.ts", """
import { ElMessage, ElMessageBox } from 'element-plus';
export const checkPermi = () => true;
export const useMessage = () => ({ success: ElMessage.success, error: ElMessage.error, warning: ElMessage.warning,
  confirm: (message) => ElMessageBox.confirm(message), delConfirm: () => ElMessageBox.confirm('确认删除?') });
export const DICT_TYPE = { PMS_ARRIVAL_STATUS: 'PMS_ARRIVAL_STATUS' };
export const getIntDictOptions = () => [{value:0,label:'待签收'},{value:1,label:'已签收'},{value:2,label:'异常'}];
export const getProjectPage = async () => ({list:[{id:20,projectName:'隔离验收项目'}],total:1});
export const generateUUID = () => crypto.randomUUID();
export default { url: () => { throw new Error('Download not exercised by this browser fixture'); } };
""")
    write("Device.vue", '<template><span>设备选择（独立模块）</span></template>')
    write("main.ts", """
import {createApp, defineComponent, h} from 'vue';
import ElementPlus, {ElDialog} from 'element-plus';
import 'element-plus/dist/index.css';
import Arrival from '@/views/pms/engineering/arrival/index.vue';
const app=createApp(Arrival); app.use(ElementPlus);
app.directive('hasPermi',()=>{});
app.component('ContentWrap',defineComponent({setup(_,ctx){return()=>h('section',{style:'margin:16px'},ctx.slots.default?.())}}));
app.component('Dialog',defineComponent({props:['modelValue','title','width'],emits:['update:modelValue'],setup(props,ctx){
  return()=>h(ElDialog,{...props,'onUpdate:modelValue':value=>ctx.emit('update:modelValue',value)},ctx.slots)}}));
app.component('Icon',defineComponent({setup(){return()=>h('span')}}));
app.component('Pagination',defineComponent({setup(){return()=>h('span')}}));
app.component('DictTag',defineComponent({props:['value'],setup(props){return()=>h('span', ['待签收','已签收','异常'][props.value])}}));
app.component('PmsEntitySelect',defineComponent({props:['modelValue'],setup(props){return()=>h('span',`隔离验收项目 #${props.modelValue || 20}`)}}));
app.component('Editor',defineComponent({props:['modelValue','readonly'],emits:['update:modelValue'],setup(props,ctx){return()=>h('textarea',{
  value:props.modelValue,readonly:props.readonly,onInput:event=>ctx.emit('update:modelValue',event.target.value),style:'width:100%;min-height:50px'})}}));
app.mount('#app');
""")
    write("vite.config.mjs", f"""
import {{defineConfig}} from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
const port='{(FIXTURE / 'ports.ts').as_posix()}';
export default defineConfig({{cacheDir:'{(FIXTURE / '.vite').as_posix()}',plugins:[vue(),AutoImport({{imports:['vue'],dts:false}})],
 resolve:{{alias:[
 {{find:/^@\\/config\\/axios$/,replacement:'{(FIXTURE / 'http.ts').as_posix()}'}},
 {{find:/^@\\/(utils\\/(permission|dict|download)|hooks\\/web\\/useMessage|api\\/pms\\/project\\/projects)$/,replacement:port}},
 {{find:/^@\\/utils$/,replacement:port}},
 {{find:/^@\\/components\\/ProjectDeviceSelect\\/index.vue$/,replacement:'{(FIXTURE / 'Device.vue').as_posix()}'}},
 {{find:'@',replacement:'{(FRONTEND / 'src').as_posix()}'}}]}},
 server:{{host:'127.0.0.1',port:28463,strictPort:true,fs:{{allow:['{REPO.as_posix()}']}},
 proxy:{{'/api':'{HTTP}','/pms':'{HTTP}'}}}} }});
""")


def main():
    prepare()
    log = (FIXTURE / "vite.log").open("w")
    vite = subprocess.Popen(["node", str(FRONTEND / "node_modules/vite/bin/vite.js"), "--config", str(FIXTURE / "vite.config.mjs")], cwd=FIXTURE, stdout=log, stderr=subprocess.STDOUT)
    evidence = {"scope": "native SFC + real controllers/method permissions/services + exclusive MySQL; fixture actor/project scope/storage; no full boot/login/migrations", "checks": []}
    networks = []
    try:
        for _ in range(100):
            if vite.poll() is not None:
                raise RuntimeError((FIXTURE / "vite.log").read_text())
            try:
                urllib.request.urlopen("http://127.0.0.1:28463", timeout=1).close()
                break
            except OSError:
                time.sleep(0.1)
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(executable_path="/usr/bin/chromium", headless=True, args=["--no-sandbox"])
            page = browser.new_page(viewport={"width": 1440, "height": 1300})
            errors = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.on("response", lambda response: networks.append({"url": response.url, "status": response.status, "body": response.text()}) if "/api/" in response.url or "/pms/" in response.url and "/src/" not in response.url else None)
            page.goto("http://127.0.0.1:28463", wait_until="networkidle")
            page.get_by_role("button", name="编辑", exact=True).click()
            dialog = page.locator(".el-dialog")
            expect(dialog.get_by_text("交付件（统一交付能力）", exact=True)).to_be_visible()
            remark = dialog.locator(".el-form-item").filter(has_text="备注").get_by_role("textbox")
            remark.fill("LOCAL UNSAVED CHANGE")
            uploader = dialog.locator(".pms-file-uploader")
            uploader.locator('input[type="file"]').set_input_files({"name": "receipt.txt", "mimeType": "text/plain", "buffer": b"Actual browser receipt\n"})
            uploader.get_by_role("button", name="上传并绑定", exact=True).click()
            expect(dialog.get_by_text("已满足", exact=True)).to_be_visible(timeout=15000)
            first = fixture("/fixture/evidence")
            assert first["plt_delivery_material"] == first["plt_file_version"] == first["plt_file_upload_session"] == 1, first
            assert first["ownerMaterials"][0]["id"] == first["projectMaterials"][0]["id"], first
            assert first["ownerRemark"] == "Persisted receipt", first
            expect(remark).to_have_value("LOCAL UNSAVED CHANGE")
            evidence["checks"].append({"name": "upload auto-collects same material without persisting local dirty fields", "passed": True, "database": first})
            dialog.get_by_role("button", name="取消", exact=True).click()
            page.get_by_role("button", name="编辑", exact=True).click()
            expect(page.locator('.sign-document-section .pms-file-uploader').get_by_role("button", name="上传新版本", exact=True)).to_be_visible(timeout=10000)
            evidence["checks"].append({"name": "reopen restores the actual native reference and selects ADD_VERSION", "passed": True})
            fixture("/fixture/reject-material")
            uploader = dialog.locator(".pms-file-uploader")
            uploader.locator('input[type="file"]').set_input_files({"name": "receipt.txt", "mimeType": "text/plain", "buffer": b"New browser receipt\n"})
            uploader.get_by_role("button", name="上传新版本", exact=True).click()
            retry = dialog.get_by_role("button", name="重试归集已上传附件", exact=True)
            expect(retry).to_be_visible(timeout=15000)
            failed = fixture("/fixture/evidence")
            assert failed["plt_file_version"] == 2 and failed["plt_delivery_material"] == 1, failed
            assert failed["ownerVersion"] == first["ownerVersion"], failed
            fixture("/fixture/allow-material")
            retry.click()
            expect(retry).not_to_be_visible(timeout=15000)
            expect(dialog.get_by_text("判定失败：", exact=False)).to_be_visible(timeout=10000)
            recovered = fixture("/fixture/evidence")
            assert recovered["plt_file_version"] == recovered["plt_file_upload_session"] == recovered["plt_delivery_material"] == 2, recovered
            assert recovered["ownerVersion"] == first["ownerVersion"] + 1, recovered
            assert recovered["ownerRemark"] == "Persisted receipt", recovered
            evidence["checks"].append({"name": "failed registration rolls back Owner; retry collects existing upload; replaced evidence cannot report completed", "passed": True, "failed": failed, "recovered": recovered})
            dialog.get_by_role("button", name="撤回已失效旧版本材料", exact=True).click()
            expect(dialog.get_by_text("已满足", exact=True)).to_be_visible(timeout=10000)
            withdrawn = fixture("/fixture/evidence")
            assert withdrawn["plt_file_version"] == withdrawn["plt_file_upload_session"] == withdrawn["plt_delivery_material"] == 2, withdrawn
            assert sorted(item["status"] for item in withdrawn["ownerMaterials"]) == ["ACTIVE", "WITHDRAWN"], withdrawn
            assert withdrawn["ownerVersion"] == recovered["ownerVersion"], withdrawn
            assert any(item["fileVersionNo"] == 2 and item["status"] == "ACTIVE" for item in withdrawn["ownerMaterials"]), withdrawn
            evidence["checks"].append({"name": "native-authorized public withdrawal preserves both versions and restores public completion from the new material", "passed": True, "database": withdrawn})
            page.screenshot(path=str(OUTPUT / "arrival-browser.png"), full_page=True)
            assert not errors, errors
            evidence["pageErrors"] = errors
            browser.close()
        (OUTPUT / "arrival-browser.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        print("PASS native arrival Chromium upload/auto-collection/rollback/retry/shared-identity/withdrawal/completion-recovery")
    finally:
        (FIXTURE / "network.json").write_text(json.dumps(networks, ensure_ascii=False, indent=2), encoding="utf-8")
        vite.terminate()
        try:
            vite.wait(timeout=10)
        except subprocess.TimeoutExpired:
            vite.kill()
            vite.wait()
        log.close()


if __name__ == "__main__":
    main()
