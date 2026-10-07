#!/usr/bin/env python3
"""Real inherited business page, business HTTP and isolated SQL. No production login/migration claim."""
import json
import subprocess
import time
import urllib.request
from playwright.sync_api import sync_playwright, expect
import run_default_business_delivery_browser as shared

shared.FIX = shared.REPO / '.run/direct-business-browser/browser'
shared.OUT = shared.REPO / 'docs/generated/direct-business-browser-20261007'
FIX, OUT, UI = shared.FIX, shared.OUT, shared.UI

def field(page, label):
    return page.locator('.el-form-item').filter(has=page.locator('.el-form-item__label', has_text=label)).locator('input').first

def main():
    shared.prepare()
    (FIX / 'Fixture.vue').write_text('''<template><div>
      <button @click="mode='notes'">业务一</button><button @click="mode='others'">业务二</button><button @click="mode='collection'">归集</button>
      <Collection v-if="mode==='collection'" />
      <Page v-else :key="mode" :api-base="'/api/v1/pms/it-direct-'+mode" deliverable-type="REPORT" />
    </div></template><script setup>
      import {ref} from 'vue';import Page from '@/components/ProjectBusiness/ProjectBusinessPage.vue';
      import Collection from '@/components/ProjectBusiness/ProjectDeliveryCollection.vue';const mode=ref('notes');
    </script>''')
    # Use real confirmation controls, including cancellation, rather than an always-confirm test stub.
    ports=(FIX/'ports.ts').read_text().replace("export const useMessage=()=>({success:()=>{},error:()=>{},warning:()=>{},confirm:async()=>{}});", "import {ElMessageBox} from 'element-plus';export const useMessage=()=>({success:()=>{},error:()=>{},warning:()=>{},confirm:(text)=>ElMessageBox.confirm(text,'确认',{confirmButtonText:'确定',cancelButtonText:'取消'})});")
    (FIX/'ports.ts').write_text(ports)
    log=(FIX/'vite.log').open('w');vite=subprocess.Popen(['node',str(UI/'node_modules/vite/bin/vite.js'),'--config',str(FIX/'vite.config.mjs')],cwd=FIX,stdout=log,stderr=subprocess.STDOUT)
    result={'scope':'Actual ProjectBusinessPage and delivery/collection SFCs, inherited business APIs, production services and isolated MySQL; authentication, project and storage use fixture ports.','checks':[]};networks=[]
    try:
        for _ in range(100):
            if vite.poll() is not None:raise RuntimeError((FIX/'vite.log').read_text())
            try:urllib.request.urlopen('http://127.0.0.1:27463',timeout=1).close();break
            except OSError:time.sleep(.1)
        with sync_playwright() as p:
            browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
            page=browser.new_page(viewport={'width':1500,'height':1600});errors=[]
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.on('response',lambda r:networks.append({'url':r.url,'status':r.status,'body':r.text()}) if '/api/v1/' in r.url else None)
            page.goto('http://127.0.0.1:27463',wait_until='networkidle')
            for button,label,value in [('业务一','title','First inherited'),('业务二','description','Second inherited')]:
                if button=='业务二':page.get_by_role('button',name=button,exact=True).click()
                page.get_by_role('button',name='新建',exact=True).click()
                # Field names come from the real generated model, not a separately maintained business form.
                field(page,'项目').fill('99');field(page,label).fill(value)
                page.get_by_role('button',name='保存',exact=True).click()
                expect(page.get_by_text('尚无有效上传',exact=True)).to_be_visible(timeout=15000)
                expect(page.get_by_label('上传交付件')).to_be_enabled(timeout=15000)
                page.get_by_label('上传交付件').set_input_files({'name':label+'.txt','mimeType':'text/plain','buffer':value.encode()})
                expect(page.get_by_text('已有最新有效上传',exact=True)).to_be_visible(timeout=15000)
                page.get_by_role('button',name='返回列表',exact=True).click()
                expect(page.locator('.business-entity-list .el-table__row')).to_have_count(1)
                page.locator('.business-entity-list .el-table__row').click()
                expect(field(page,label)).to_have_value(value)
                field(page,label).fill(value+' edited')
                with page.expect_response(lambda r: r.request.method=='PUT' and '/it-direct-' in r.url and '/deliverables' not in r.url) as saved:
                    page.get_by_role('button',name='保存',exact=True).click()
                assert saved.value.json()['code']==0
                expect(page.get_by_role('button',name='保存',exact=True)).to_be_enabled()
                expect(field(page,label)).to_have_value(value+' edited')
                page.screenshot(path=str(OUT/(label+'-business.png')),full_page=True)
                result['checks'].append(button+' inherits create/list/read/update/upload/echo')
            evidence=shared.fixture('/fixture/evidence');assert len(evidence['plt_delivery_material'])==2,evidence
            assert evidence['it_direct_note'][0]['title']=='First inherited edited',evidence
            assert evidence['it_direct_other'][0]['description']=='Second inherited edited',evidence
            page.get_by_role('button',name='归集',exact=True).click();page.get_by_label('归集项目').select_option('99');page.get_by_label('归集交付件类型').fill('REPORT')
            expect(page.locator('.default-delivery-records .el-table__row')).to_have_count(2,timeout=15000)
            page.screenshot(path=str(OUT/'collection.png'),full_page=True)
            page.get_by_role('button',name='业务二',exact=True).click();page.locator('.business-entity-list .el-table__row').click()
            page.get_by_role('button',name='删除交付件',exact=True).click();page.get_by_role('button',name='取消',exact=True).click()
            expect(page.locator('.project-business-deliveries .el-table__row')).to_have_count(1)
            page.get_by_role('button',name='删除交付件',exact=True).click();page.get_by_role('button',name='确定',exact=True).click()
            expect(page.get_by_text('尚无有效上传',exact=True)).to_be_visible(timeout=15000)
            page.get_by_role('button',name='删除',exact=True).click();page.get_by_role('button',name='取消',exact=True).click()
            expect(field(page,'description')).to_have_value('Second inherited edited')
            page.get_by_role('button',name='删除',exact=True).click();page.get_by_role('button',name='确定',exact=True).click()
            expect(page.locator('.business-entity-list .el-table__row')).to_have_count(0,timeout=15000)
            final=shared.fixture('/fixture/evidence');assert final['it_direct_other'][0]['deleted'] and final['plt_delivery_material'][1]['deleted'],final
            assert len(final['plt_delivery_material'])==2,final
            assert not errors,errors
            assert all(r['status']==200 and json.loads(r['body']).get('code')==0 for r in networks),networks
            assert not any('/business-models/' in r['url'] for r in networks),networks
            result.update(passed=True,evidence=final);result['checks'].append('same material table and collection; cancel/confirm delete; logical history retained; no model dispatcher API')
            browser.close()
    finally:
        (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,default=str));(OUT/'http.json').write_text(json.dumps(networks,ensure_ascii=False,indent=2))
        vite.terminate()
        try:vite.wait(timeout=10)
        except subprocess.TimeoutExpired:vite.kill()
        log.close()
    print(json.dumps({'passed':True,'checks':len(result['checks'])}))

if __name__=='__main__':main()
