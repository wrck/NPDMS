#!/usr/bin/env python3
"""Actual native checklist SFC/file components against the exclusive MySQL/controller fixture."""
import json
import subprocess
import time
import urllib.request
from playwright.sync_api import sync_playwright, expect
from run_native_arrival_delivery_browser import prepare, write, FIXTURE, OUTPUT, fixture


def main():
    prepare()
    original = (FIXTURE / 'main.ts').read_text()
    write('main.ts', original.replace("@/views/pms/engineering/arrival/index.vue", "@/views/pms/acceptance/deliverable-checklist/index.vue"))
    write('ProjectPanel.vue', '<template><span>项目模板面板（本夹具不验收）</span></template>')
    config = (FIXTURE / 'vite.config.mjs').read_text()
    write('vite.config.mjs', config.replace("resolve:{alias:[", "resolve:{alias:[{find:/^@\\/views\\/pms\\/project\\/project-master-detail\\/components\\/ProjectDeliverablesPanel.vue$/,replacement:'" + str(FIXTURE / 'ProjectPanel.vue') + "'},"))
    ports = (FIXTURE / 'ports.ts').read_text()
    write('ports.ts', ports + "\nexport const getStrDictOptions = () => [{value:'REQUIRED',label:'必交'}];\n")
    vite = subprocess.Popen(['node', str(FIXTURE / 'node_modules/vite/bin/vite.js'), '--config', str(FIXTURE / 'vite.config.mjs'), str(FIXTURE)], stdout=open(FIXTURE / 'checklist-vite.log', 'w'), stderr=subprocess.STDOUT)
    evidence = {'scope':'actual native checklist/file/delivery SFCs + production secured controllers/services + exclusive MySQL; deterministic actor/project/permission/storage ports; not full boot/login/migrations','checks':[]}
    try:
        for _ in range(80):
            try:
                urllib.request.urlopen('http://127.0.0.1:28463', timeout=1); break
            except Exception:
                if vite.poll() is not None: raise RuntimeError('Vite failed')
                time.sleep(.25)
        with sync_playwright() as p:
            browser = p.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])
            page = browser.new_page(viewport={'width':1440,'height':1200})
            errors=[]; page.on('pageerror',lambda error:errors.append(str(error)))
            page.goto('http://127.0.0.1:28463',wait_until='networkidle')
            page.get_by_role('button',name='编辑',exact=True).click()
            dialog=page.get_by_role('dialog')
            dialog.locator('textarea').fill('Unsaved checklist edit')
            fixture('/fixture/reject-checklist')
            dialog.locator('input[type=file]').set_input_files({'name':'checklist.txt','mimeType':'text/plain','buffer':b'Actual native checklist attachment\n'})
            dialog.get_by_role('button',name='上传并绑定',exact=True).click()
            retry=dialog.get_by_role('button',name='重试归集已上传附件',exact=True)
            expect(retry).to_be_visible(timeout=15000)
            failed=fixture('/fixture/checklist-evidence')
            assert failed['ownerVersion']==0 and failed['ownerMaterials']==[] and failed['fileVersions']==1,failed
            fixture('/fixture/allow-checklist'); retry.click()
            expect(dialog.get_by_text('已满足',exact=True)).to_be_visible(timeout=15000)
            collected=fixture('/fixture/checklist-evidence')
            assert collected['ownerVersion']==1 and collected['ownerRemark']=='Persisted checklist' and collected['fileVersions']==1,collected
            assert len(collected['ownerMaterials'])==1,collected
            material=collected['ownerMaterials'][0]
            assert material['id'] in [row['id'] for row in collected['projectMaterials']],collected
            assert material['typeCode']=='ACC.CHECKLIST_ATTACHMENT' and material['requirementId'] is None,material
            expect(dialog.locator('textarea')).to_have_value('Unsaved checklist edit')
            evidence['checks'].append({'name':'real upload; failed native registration rolls back Owner; retry reuses one file and one material in entity/project ledger; dirty form not saved','passed':True,'failed':failed,'collected':collected})
            dialog.get_by_role('button',name='撤回材料',exact=True).click()
            expect(dialog.get_by_text('未满足',exact=True)).to_be_visible(timeout=10000)
            withdrawn=fixture('/fixture/checklist-evidence')
            assert withdrawn['ownerMaterials'][0]['status']=='WITHDRAWN' and withdrawn['fileVersions']==1 and withdrawn['ownerVersion']==1,withdrawn
            evidence['checks'].append({'name':'native-authorized public withdrawal invalidates completion and preserves actual file history','passed':True,'database':withdrawn})
            page.screenshot(path=str(OUTPUT/'checklist-browser.png'),full_page=True)
            assert errors==[],errors
            evidence['pageErrors']=errors; browser.close()
        (OUTPUT/'checklist-browser.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2))
        print('PASS native checklist Chromium/MySQL/upload/rollback/retry/shared-identity/withdrawal')
    finally:
        vite.terminate()
        try: vite.wait(timeout=10)
        except subprocess.TimeoutExpired: vite.kill()

if __name__=='__main__': main()
