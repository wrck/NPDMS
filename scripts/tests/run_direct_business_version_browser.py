#!/usr/bin/env python3
"""Production inherited page/history/forms against actual isolated versioned business HTTP and SQL."""
import json, subprocess, time, urllib.request
from playwright.sync_api import sync_playwright, expect
import run_default_business_delivery_browser as shared
shared.FIX=shared.REPO/'.run/direct-version-browser/browser'
shared.OUT=shared.REPO/'docs/generated/direct-business-version-20261007'
FIX,OUT,UI=shared.FIX,shared.OUT,shared.UI

def field(scope,label):
    return scope.locator('.el-form-item').filter(has=scope.page.locator('.el-form-item__label',has_text=label) if hasattr(scope,'page') else scope.locator('.el-form-item__label',has_text=label)).locator('input,textarea').first

def confirm(page,name):
    page.get_by_role('button',name=name,exact=True).click();page.get_by_role('button',name='确定',exact=True).click()

def main():
    shared.prepare()
    (FIX/'Fixture.vue').write_text('''<template><Page api-base="/api/v1/pms/version-notes" title="版本业务" /></template><script setup>import Page from '@/components/ProjectBusiness/ProjectBusinessPage.vue'</script>''')
    ports=(FIX/'ports.ts').read_text().replace('()=>880001','()=>9').replace('()=>7','()=>1')
    ports=ports.replace("export const useMessage=()=>({success:()=>{},error:()=>{},warning:()=>{},confirm:async()=>{}});", "import {ElMessageBox} from 'element-plus';export const useMessage=()=>({success:()=>{},error:()=>{},warning:()=>{},confirm:(text)=>ElMessageBox.confirm(text,'确认',{confirmButtonText:'确定',cancelButtonText:'取消'})});")
    (FIX/'ports.ts').write_text(ports)
    log=(FIX/'vite.log').open('w');vite=subprocess.Popen(['node',str(UI/'node_modules/vite/bin/vite.js'),'--config',str(FIX/'vite.config.mjs')],cwd=FIX,stdout=log,stderr=subprocess.STDOUT)
    result={'scope':'Actual inherited Controller/Service/Mapper/current/revision/extension SQL and production history/form SFCs. Authentication/project/form-layout policy and delivery port are fixture boundaries.','checks':[]};responses=[];network=[]
    try:
        for _ in range(100):
            if vite.poll() is not None:raise RuntimeError((FIX/'vite.log').read_text())
            try:urllib.request.urlopen('http://127.0.0.1:27463',timeout=1).close();break
            except OSError:time.sleep(.1)
        with sync_playwright() as playwright:
            browser=playwright.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
            page=browser.new_page(viewport={'width':1500,'height':1800});errors=[]
            page.on('pageerror',lambda error:errors.append(str(error)));page.on('response',lambda response:responses.append(response) if '/api/v1/' in response.url else None)
            page.on('requestfailed',lambda request:errors.append(request.url+': '+str(request.failure)) if '/api/v1/' in request.url else None)
            try:
                page.goto('http://127.0.0.1:27463',wait_until='networkidle');page.get_by_role('button',name='新建',exact=True).click()
                field(page,'项目').fill('20');field(page,'标题').fill('Original');page.get_by_role('button',name='保存',exact=True).click()
                panel=page.locator('.project-business-history');expect(panel).to_be_visible(timeout=15000);expect(panel.get_by_role('button',name='发起修订',exact=True)).to_be_enabled()
                evidence=shared.fixture('/fixture/evidence');entity_id=str(evidence['it_version_note'][0]['id']);definition=shared.fixture('/fixture/context')['definitionId']
                # Configure an extension definition through the real inherited form API before any revision exists.
                configured=page.evaluate('''async ({id,definition})=>{const response=await fetch('/api/v1/pms/version-notes/'+id+'/save-form',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({version:0,idempotencyKey:'browser-config',values:{$extensions:{definitionRevisionId:definition,expectedVersion:0,values:{flag:true,memo:'keep'}}}})});return response.json()}''',{'id':entity_id,'definition':definition})
                assert configured['code']==0,configured
                page.get_by_role('button',name='重新读取',exact=True).click();expect(panel.get_by_role('button',name='发起修订',exact=True)).to_be_enabled()
                panel.get_by_label('修订原因').fill('First');confirm(page,'发起修订');expect(panel.locator('h4')).to_contain_text('草稿',timeout=15000)
                field(panel,'标题').fill('Revised');field(panel,'扩展标志').click();page.get_by_role('option',name='否',exact=True).click()
                confirm(page,'保存修订');expect(panel.get_by_role('button',name='冻结并生效',exact=True)).to_be_enabled(timeout=15000)
                assert shared.fixture('/fixture/evidence')['it_version_note'][0]['title']=='Original'
                confirm(page,'冻结并生效');expect(panel.locator('h4')).to_contain_text('冻结只读',timeout=15000)
                expect(field(panel,'标题')).to_be_disabled();evidence=shared.fixture('/fixture/evidence');assert evidence['it_version_note'][0]['title']=='Revised'
                current_extension=[row for row in evidence['plt_entity_extension_value'] if row['entity_type']=='versionNote' and row['revision_id']==0][0]
                assert json.loads(current_extension['values_json'])=={'flag':False,'memo':'keep'}
                page.screenshot(path=str(OUT/'frozen.png'),full_page=True);result['checks'].append('empty business classes inherit actual revision APIs, form/extension save, immutable freeze and current activation')
                confirm(page,'从此版本复制');expect(panel.locator('h4')).to_contain_text('草稿',timeout=15000)
                field(panel,'标题').fill('Alternative');confirm(page,'保存修订');expect(panel.get_by_role('button',name='放弃修订',exact=True)).to_be_enabled(timeout=15000)
                panel.get_by_label('比较左版本').click();page.get_by_role('option',name='#1',exact=True).click()
                panel.get_by_label('比较右版本').click();page.get_by_role('option',name='#2',exact=True).click();panel.get_by_role('button',name='比较',exact=True).click()
                expect(panel.get_by_text('"Alternative"',exact=True)).to_be_visible(timeout=15000)
                panel.get_by_role('button',name='放弃修订',exact=True).click();page.get_by_role('button',name='取消',exact=True).click();assert len(shared.fixture('/fixture/evidence')['it_version_note_revision'])==2
                confirm(page,'放弃修订');expect(panel.locator('h4')).to_contain_text('冻结只读',timeout=15000)
                evidence=shared.fixture('/fixture/evidence');assert evidence['it_version_note_revision'][1]['deleted'] and evidence['it_version_note_revision'][1]['revision_no']==2
                confirm(page,'从此版本复制');expect(panel.locator('h4')).to_contain_text('#3',timeout=15000)
                field(panel,'标题').fill('Unsaved');page.get_by_role('button',name='返回列表',exact=True).click();page.get_by_role('button',name='取消',exact=True).click();expect(field(panel,'标题')).to_have_value('Unsaved')
                page.get_by_role('button',name='返回列表',exact=True).click();page.get_by_role('button',name='确定',exact=True).click();expect(page.locator('.business-entity-list')).to_be_visible(timeout=15000)
                page.locator('.business-entity-list .el-table__row').click();expect(panel.locator('h4')).to_contain_text('#3',timeout=15000);expect(field(panel,'标题')).to_have_value('Revised')
                page.screenshot(path=str(OUT/'history.png'),full_page=True);result['checks'].append('copy and comparison, cancel/confirm discard, immutable deleted revision number, monotonically increasing next revision, cancel/confirm unsaved navigation')
                page.wait_for_load_state('networkidle');network.extend({'url':response.url,'status':response.status,'body':response.text()} for response in responses)
                assert not errors,errors;assert all(response['status']==200 and json.loads(response['body']).get('code')==0 for response in network),network
                assert not any('/business-models/' in response['url'] or '/pms/entities/' in response['url'] for response in network)
                result.update(passed=True,evidence=shared.fixture('/fixture/evidence'))
            finally:
                if not network:
                    for response in responses:
                        try:network.append({'url':response.url,'status':response.status,'body':response.text()})
                        except Exception as error:network.append({'url':response.url,'status':response.status,'captureError':str(error)})
                if not result.get('passed'):page.screenshot(path=str(OUT/'failure.png'),full_page=True);result['failureEvidence']=shared.fixture('/fixture/evidence')
                browser.close()
    finally:
        (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,default=str));(OUT/'http.json').write_text(json.dumps(network,ensure_ascii=False,indent=2))
        vite.terminate()
        try:vite.wait(timeout=10)
        except subprocess.TimeoutExpired:vite.kill()
        log.close()
    print(json.dumps({'passed':True,'checks':len(result['checks'])}))
if __name__=='__main__':main()
