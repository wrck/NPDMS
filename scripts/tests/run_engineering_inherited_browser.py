#!/usr/bin/env python3
"""Actual inherited survey/requirement pages with production entity, revision, file and material services."""
import json
import subprocess
import time
import urllib.request
from playwright.sync_api import sync_playwright, expect
import run_default_business_delivery_browser as shared
shared.FIX=shared.REPO/'.run/engineering-inherited-browser/browser'
shared.OUT=shared.REPO/'docs/generated/engineering-inherited-browser-20261007'
FIX,OUT,UI=shared.FIX,shared.OUT,shared.UI

def field(page,label):
    return page.locator('.el-form-item').filter(has=page.locator('.el-form-item__label',has_text=label)).locator('input,textarea').first

def act(page,label):
    page.get_by_role('button',name=label,exact=True).click()
    page.get_by_role('button',name='确定',exact=True).click()

def upload(page,name,body):
    expect(page.get_by_label('上传交付件')).to_be_enabled(timeout=15000)
    page.get_by_label('上传交付件').set_input_files({'name':name,'mimeType':'text/plain','buffer':body.encode()})
    expect(page.get_by_text('已有最新有效上传',exact=True)).to_be_visible(timeout=15000)

def main():
    shared.prepare()
    main_path=FIX/'main.ts'
    main_path.write_text(main_path.read_text().replace("import ElementPlus", "import formCreate from '@form-create/element-ui';import ElementPlus").replace('app.use(ElementPlus);','app.use(ElementPlus);app.use(formCreate);'))
    (FIX/'Fixture.vue').write_text('''<template><div>
      <button @click="mode='survey'">工勘业务</button><button @click="mode='requirement'">需求分析业务</button><button @click="mode='collection'">交付件归集</button>
      <Survey v-if="mode==='survey'"/><Requirement v-else-if="mode==='requirement'"/><Collection v-else/>
    </div></template><script setup>import {ref} from 'vue';
      import Survey from '@/views/pms/business/site-survey/index.vue';import Requirement from '@/views/pms/business/requirement-analysis/index.vue';
      import Collection from '@/components/ProjectBusiness/ProjectDeliveryCollection.vue';const mode=ref('survey');</script>''')
    ports=(FIX/'ports.ts').read_text().replace('()=>880001','()=>17').replace('id:99','id:20').replace('隔离项目99','隔离项目20')
    ports=ports.replace("export const useMessage=()=>({success:()=>{},error:()=>{},warning:()=>{},confirm:async()=>{}});", "import {ElMessageBox} from 'element-plus';export const useMessage=()=>({success:()=>{},error:()=>{},warning:()=>{},confirm:(text)=>ElMessageBox.confirm(text,'确认',{confirmButtonText:'确定',cancelButtonText:'取消'})});")
    (FIX/'ports.ts').write_text(ports)
    (FIX/'Select.vue').write_text((FIX/'Select.vue').read_text().replace('value="99"','value="20"').replace('隔离项目99','隔离项目20'))
    log=(FIX/'vite.log').open('w');vite=subprocess.Popen(['node',str(UI/'node_modules/vite/bin/vite.js'),'--config',str(FIX/'vite.config.mjs')],cwd=FIX,stdout=log,stderr=subprocess.STDOUT)
    result={'scope':'Actual production survey/requirement inherited pages, own controllers, entity/current/revision/child/file/material SQL and V399. Authentication, project facts, technical storage and legacy layout/file-slot ports are fixture boundaries.','checks':[]};responses=[];networks=[]
    try:
        for _ in range(100):
            if vite.poll() is not None:raise RuntimeError((FIX/'vite.log').read_text())
            try:urllib.request.urlopen('http://127.0.0.1:27463',timeout=1).close();break
            except OSError:time.sleep(.1)
        with sync_playwright() as p:
            browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
            page=browser.new_page(viewport={'width':1550,'height':1800});errors=[]
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.on('response',lambda r:responses.append(r) if '/api/v1/' in r.url else None)
            page.on('requestfailed',lambda r:errors.append(r.url+': '+str(r.failure)) if '/api/v1/' in r.url else None)
            try:
                page.goto('http://127.0.0.1:27463',wait_until='networkidle')
                page.get_by_role('button',name='新建',exact=True).click();field(page,'项目').fill('20');field(page,'工勘名称').fill('继承工勘');field(page,'工勘地点').fill('机房')
                field(page,'选定材料').fill('[{"projectId":20,"sn":"SN-BROWSER","reason":"验收"}]')
                page.get_by_role('button',name='保存',exact=True).click();expect(page.get_by_label('上传交付件')).to_be_enabled(timeout=15000)
                created=shared.fixture('/fixture/evidence');assert len(created['sol_site_survey'])==1 and len(created['sol_site_survey_material'])==1,created
                assert int(created['sol_site_survey'][0]['id'])>9007199254740991
                page.get_by_role('button',name='删除',exact=True).click();page.get_by_role('button',name='取消',exact=True).click()
                field(page,'工勘名称').fill('继承工勘已编辑');page.locator('.el-form-item').filter(has=page.locator('.el-form-item__label',has_text='工勘扩展标志')).locator('.el-switch').click()
                with page.expect_response(lambda r:r.request.method=='POST' and '/save-form' in r.url and '/site-survey-business/' in r.url) as save:
                    page.get_by_role('button',name='保存',exact=True).click()
                assert save.value.json()['code']==0;expect(page.get_by_role('button',name='保存',exact=True)).to_be_enabled()
                upload(page,'survey.txt','Survey record\n');act(page,'确认工勘')
                expect(page.get_by_role('button',name='保存',exact=True)).to_be_disabled(timeout=15000)
                expect(page.get_by_label('上传交付件')).to_have_count(0)
                act(page,'归档工勘');expect(page.get_by_role('button',name='归档工勘',exact=True)).to_be_disabled(timeout=15000)
                survey=shared.fixture('/fixture/evidence');assert survey['sol_site_survey'][0]['status']==3 and survey['sol_site_survey'][0]['name']=='继承工勘已编辑',survey
                assert len(survey['sol_site_survey_material'])==1
                assert json.loads(survey['plt_entity_extension_value'][0]['values_json'])['extra_flag'] is True
                page.screenshot(path=str(OUT/'survey.png'),full_page=True);result['checks'].append('survey existing table/bigint identity/child material/create/edit/cancel delete/upload/confirm/archive/read-only state')
                page.get_by_role('button',name='需求分析业务',exact=True).click();page.get_by_role('button',name='新建',exact=True).click();field(page,'项目').fill('20')
                page.get_by_role('button',name='保存',exact=True).click();expect(page.get_by_label('上传交付件')).to_be_enabled(timeout=15000)
                draft=shared.fixture('/fixture/evidence');assert len(draft['sol_requirement_analysis'])==0 and len(draft['sol_requirement_analysis_revision'])==1,draft
                page.locator('.el-form-item').filter(has=page.locator('.el-form-item__label',has_text='需求扩展标志')).locator('.el-switch').click();field(page,'项目背景').fill('项目背景');field(page,'项目目标').fill('项目目标');field(page,'网络拓扑').fill('网络拓扑')
                field(page,'业务设备明细').fill('[{"deviceName":"设备","serialNumber":"SN-RA","businessName":"业务"}]')
                with page.expect_response(lambda r:r.request.method=='POST' and '/save-form' in r.url and '/requirement-analysis-business/' in r.url) as save:
                    page.get_by_role('button',name='保存',exact=True).click()
                assert save.value.json()['code']==0;expect(page.get_by_role('button',name='保存',exact=True)).to_be_enabled()
                upload(page,'requirement.txt','Requirement report\n');act(page,'完成并生效')
                expect(page.get_by_role('button',name='保存',exact=True)).to_be_disabled(timeout=15000);expect(page.get_by_label('上传交付件')).to_have_count(0)
                frozen=shared.fixture('/fixture/evidence');assert len(frozen['sol_requirement_analysis'])==1 and frozen['sol_requirement_analysis_revision'][0]['revision_state']=='FROZEN',frozen
                act(page,'复制为新草稿');expect(page.get_by_label('上传交付件')).to_be_enabled(timeout=15000)
                expect(page.get_by_text('已有最新有效上传',exact=True)).to_be_visible(timeout=15000)
                copied=shared.fixture('/fixture/evidence');revisions=copied['sol_requirement_analysis_revision'];assert len(revisions)==2 and revisions[1]['source_revision_id']==revisions[0]['id'],copied
                assert revisions[0]['entity_id']==revisions[1]['entity_id'] and revisions[1]['revision_state']=='DRAFT'
                requirement_extensions=[row for row in copied['plt_entity_extension_value'] if row['entity_type']=='REQUIREMENT_ANALYSIS']
                assert len(requirement_extensions)==3 and all(json.loads(row['values_json'])['CUSTOM_FLAG'] is True for row in requirement_extensions)
                assert {int(row['revision_id']) for row in requirement_extensions}=={0,int(revisions[0]['id']),int(revisions[1]['id'])}
                assert all(int(row['entity_id'])==int(revisions[0]['entity_id']) for row in requirement_extensions)
                assert copied['storedObjects']==copied['fileVersions']==2 and len(copied['plt_delivery_material'])==3,copied
                source,copied_material=copied['plt_delivery_material'][1:];assert source['file_artifact_id']==copied_material['file_artifact_id'] and source['file_reference_id']!=copied_material['file_reference_id']
                assert copied_material['source_kind']=='ASSOCIATED'
                page.screenshot(path=str(OUT/'requirement-copy.png'),full_page=True);result['checks'].append('requirement empty draft/body/typed detail/complete/current projection/frozen read-only/copy preserves logical identity and immutable file version')
                page.get_by_role('button',name='删除交付件',exact=True).click();page.get_by_role('button',name='取消',exact=True).click()
                expect(page.locator('.project-business-deliveries .el-table__row')).to_have_count(1)
                act(page,'删除交付件');expect(page.get_by_text('尚无有效上传',exact=True)).to_be_visible(timeout=15000)
                act(page,'删除');expect(page.locator('.business-entity-list .el-table__row')).to_have_count(1,timeout=15000)
                page.locator('.business-entity-list .el-table__row').click();act(page,'复制为新草稿')
                expect(page.get_by_label('上传交付件')).to_be_enabled(timeout=15000);expect(page.get_by_text('已有最新有效上传',exact=True)).to_be_visible(timeout=15000)
                final=shared.fixture('/fixture/evidence');revisions=final['sol_requirement_analysis_revision'];assert len(revisions)==3 and revisions[1]['deleted'] and not revisions[2]['deleted'],final
                assert revisions[1]['revision_no']==revisions[2]['revision_no']==2 and final['storedObjects']==final['fileVersions']==2
                page.get_by_role('button',name='交付件归集',exact=True).click();page.get_by_label('归集项目').select_option('20');page.get_by_label('归集交付件类型').fill('ATTACHMENT')
                expect(page.locator('.default-delivery-records .el-table__row')).to_have_count(3,timeout=15000)
                page.screenshot(path=str(OUT/'collection.png'),full_page=True);result['checks'].append('cancel/confirm material and draft deletion; V399 active slot reuse without rewriting history; project collection shares one material entity')
                page.wait_for_load_state('networkidle');networks.extend({'url':r.url,'status':r.status,'body':r.text()} for r in responses)
                assert not errors,errors;assert all(r['status']==200 and json.loads(r['body']).get('code')==0 for r in networks),networks
                assert not any('/business-models/' in r['url'] for r in networks)
                result.update(passed=True,evidence=final)
            finally:
                if not networks:
                    for response in responses:
                        try:networks.append({'url':response.url,'status':response.status,'body':response.text()})
                        except Exception as failure:networks.append({'url':response.url,'status':response.status,'captureError':str(failure)})
                if not result.get('passed'):
                    page.screenshot(path=str(OUT/'failure.png'),full_page=True)
                    result['failureEvidence']=shared.fixture('/fixture/evidence')
                browser.close()

    finally:
        if not networks:
            for response in responses:
                try:networks.append({'url':response.url,'status':response.status,'body':response.text()})
                except Exception as failure:networks.append({'url':response.url,'status':response.status,'captureError':str(failure)})
        (OUT/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2,default=str));(OUT/'http.json').write_text(json.dumps(networks,ensure_ascii=False,indent=2))
        vite.terminate()
        try:vite.wait(timeout=10)
        except subprocess.TimeoutExpired:vite.kill()
        log.close()
    print(json.dumps({'passed':True,'checks':len(result['checks'])}))

if __name__=='__main__':main()
