#!/usr/bin/env python3
"""Actual two native SFCs and common file/delivery components on exclusive controller/MySQL fixture."""
import json, os, subprocess, time, urllib.request
from playwright.sync_api import sync_playwright, expect
import run_native_arrival_delivery_browser as base
base.FIXTURE=base.REPO/'.run/native-delivery-two-20261006/browser'
base.OUTPUT=base.REPO/'docs/generated/native-delivery-two-20261006'
base.HTTP='http://127.0.0.1:28502'
KINDS=[('BRIEFING','engineering/briefing'),('LEGACY_ACCEPTANCE','acceptance/acceptance')]
def main():
    base.prepare()
    ports=(base.FIXTURE/'ports.ts').read_text()+"""
export const getStrDictOptions=()=>[];
export const getSiteSurvey=async()=>{throw new Error('Survey shortcut not exercised')};
export const loadSurveyActionContext=async()=>{throw new Error('Survey shortcut not exercised')};
export const resolveSurveyExecution=async()=>undefined;
export const useUserStore=()=>({getUser:{id:17}});
export const useRoute=()=>({query:{}});
export const useRouter=()=>({push:async()=>{}});
export const getUserPage=async()=>({list:[{id:17,nickname:'隔离用户'}],total:1});
export const getDeviceArchiveRecord=async()=>({id:8,name:'隔离设备',sn:'TEST-8'});
export const getDeviceArchivePage=async()=>({list:[],total:0});
export const getProject=async()=>({id:20,name:'隔离项目'});
"""
    base.write('ports.ts',ports)
    main=(base.FIXTURE/'main.ts').read_text()
    imports='\n'.join(f"import Page{i} from '@/views/pms/{folder}/index.vue';" for i,(_,folder) in enumerate(KINDS))
    main=main.replace("import Arrival from '@/views/pms/engineering/arrival/index.vue';",imports)
    main=main.replace('const app=createApp(Arrival);',"const pages=["+','.join(f'Page{i}' for i in range(2))+"];const pageIndex=Number(new URLSearchParams(location.search).get('page')||0);const app=createApp(pages[pageIndex],{projectId:20});")
    base.write('main.ts',main)
    config=(base.FIXTURE/'vite.config.mjs').read_text().replace('28463','28503')
    aliases=r"""{find:/^vue-router$/,replacement:port},
 {find:/^@\/api\/pms\/engineering\/site-survey\/entity$/,replacement:port},
 {find:/^@\/views\/pms\/delivery-business\/site-survey\/(surveyActionContext|siteSurveyExecutionShortcut)$/,replacement:port},
 {find:/^@\/(store\/modules\/user|api\/system\/user|api\/pms\/asset\/device\/archive)$/,replacement:port},
 {find:/^.*\/(ProjectTag|UserTag|DeviceTag)\/index\.vue$/,replacement:'DEVICE'},
 {find:/^.*\/(ManualCollectionDialog|MaterialDevicePicker)\.vue$/,replacement:'DEVICE'},
 {find:/^@\/components\/DeviceCollection\/.*\.vue$/,replacement:'DEVICE'},
""".replace('DEVICE',str(base.FIXTURE/'Device.vue'))
    config=config.replace('resolve:{alias:[','resolve:{alias:['+aliases)
    base.write('vite.config.mjs',config)
    vite=subprocess.Popen(['node',str(base.FRONTEND/'node_modules/vite/bin/vite.js'),'--config',str(base.FIXTURE/'vite.config.mjs')],cwd=base.FIXTURE,stdout=open(base.FIXTURE/'vite.log','w'),stderr=subprocess.STDOUT)
    evidence={'scope':'actual two native pages/common SFCs; real secured controllers/services/file/material on exclusive MySQL; actor/permission/project/storage/device ports deterministic; no full login/roles/Flyway','selectedKinds':os.environ.get('NATIVE_TWO_BROWSER_KINDS',','.join(k for k,_ in KINDS)).split(','),'checks':[]}
    try:
        for _ in range(80):
            try:urllib.request.urlopen('http://127.0.0.1:28503',timeout=1);break
            except OSError:
                if vite.poll() is not None:raise RuntimeError('Vite failed')
                time.sleep(.15)
        with sync_playwright() as p:
            browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
            for i,(kind,folder) in enumerate(KINDS):
                if kind not in evidence['selectedKinds']:continue
                page=browser.new_page(viewport={'width':1440,'height':1300});errors=[];page.on('pageerror',lambda error:errors.append(str(error)))
                page.goto(f'http://127.0.0.1:28503/?page={i}',wait_until='networkidle');assert not errors,errors
                page.get_by_role('button',name='编辑',exact=True).first.click()
                dialog=page.get_by_role('dialog');dirty=dialog.locator('.el-form-item').filter(has_text='备注').get_by_role('textbox');dirty.fill('Unsaved native edit')
                base.fixture('/fixture/reject/'+kind)
                dialog.locator('input[type=file]').set_input_files({'name':'native.txt','mimeType':'text/plain','buffer':('Actual native '+kind+' file\n').encode()})
                dialog.get_by_role('button',name='上传并绑定',exact=True).click()
                retry=dialog.get_by_role('button',name='重试归集已上传附件',exact=True);expect(retry).to_be_visible(timeout=15000)
                failed=base.fixture('/fixture/evidence/'+kind);assert failed['files']==1 and failed['materials']==[] and failed['version']==0,failed
                base.fixture('/fixture/allow/'+kind);retry.click();expect(dialog.get_by_text('已满足',exact=True)).to_be_visible(timeout=15000)
                collected=base.fixture('/fixture/evidence/'+kind);assert collected['files']==1 and len(collected['materials'])==1 and collected['completed'] and collected['remark']=='Persisted native Owner',collected
                m=collected['materials'][0];assert m['requirementId'] is None and m['id'] in [x['id'] for x in collected['projectMaterials']],collected
                expect(dirty).to_have_value('Unsaved native edit')
                dialog.get_by_role('button',name='撤回材料',exact=True).click();expect(dialog.get_by_text('未满足',exact=True)).to_be_visible(timeout=12000)
                withdrawn=base.fixture('/fixture/evidence/'+kind);assert withdrawn['files']==1 and not withdrawn['completed'] and withdrawn['materials'][0]['status']=='WITHDRAWN',withdrawn
                page.screenshot(path=str(base.OUTPUT/(folder.replace('/','-')+'-browser.png')),full_page=True);assert not errors,errors
                evidence['checks'].append({'kind':kind,'passed':True,'failed':failed,'collected':collected,'withdrawn':withdrawn,'pageErrors':errors});page.close()
            browser.close()
        (base.OUTPUT/os.environ.get('NATIVE_TWO_BROWSER_EVIDENCE','native-two-browser.json')).write_text(json.dumps(evidence,ensure_ascii=False,indent=2));print(f"PASS {len(evidence['checks'])} selected native Chromium pages: actual upload/rollback/retry/shared material/dirty-form/withdrawal/public completion")
    finally:
        vite.terminate()
        try:vite.wait(timeout=10)
        except subprocess.TimeoutExpired:vite.kill()
if __name__=='__main__':main()
