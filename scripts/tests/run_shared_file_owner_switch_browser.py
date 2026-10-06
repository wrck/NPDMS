#!/usr/bin/env python3
"""Real shared SFC/ElementPlus Owner-switch regression; deterministic file HTTP ports only."""
import json, subprocess, time, urllib.request
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
REPO=Path(__file__).resolve().parents[2]
UI=REPO/'yudao-ui/yudao-ui-admin-vue3'
FIXTURE=REPO/'.run/owner-followup-20261006/browser'
PORT=29563

def write(name,text):
    (FIXTURE/name).write_text(text)

def main():
    FIXTURE.mkdir(parents=True,exist_ok=True)
    if not (FIXTURE/'node_modules').exists():
        (FIXTURE/'node_modules').symlink_to(UI/'node_modules',target_is_directory=True)
    write('index.html','<html><body><div id="app"></div><script type="module" src="/main.ts"></script></body></html>')
    write('ports.ts', '''
export const generateUUID=()=>crypto.randomUUID();
export const useMessage=()=>({success:()=>{},warning:()=>{},error:()=>{},prompt:async()=>({value:'重复材料'})});
export default {file:()=>{throw new Error('Download is outside this fixture')}};
''')
    write('file.ts','''
export const fixture={uploads:[],detach:[],initialized:[],completed:[],model:[],owner:'41'};
window.fixture=fixture;
export const initializeUpload=async (input,key)=>{fixture.initialized.push({...input,key});return {artifactId:input.objectId,sessionId:input.objectId+'-session',expiresAt:'2026-10-06'}};
export const completeUpload=(artifactId,sessionId,file,key,progress)=>new Promise((resolve,reject)=>fixture.uploads.push({artifactId,resolve,reject,progress}));
export const detachReference=(...args)=>new Promise((resolve,reject)=>fixture.detach.push({args,resolve,reject}));
export const getReference=async key=>({...key,artifactId:key.objectId,referenceId:key.objectId+'-ref',referenceVersion:1,versionNo:1,status:'ACTIVE'});
export const getArtifact=async (id,key)=>({artifactId:id,name:'材料-'+key.objectId+'.txt',categoryCode:'EVIDENCE',allowedActions:['DETACH'],reference:{...key,referenceId:key.objectId+'-ref',referenceVersion:1,versionNo:1,status:'ACTIVE'}});
export const getVersions=async()=>({items:[]});
export const createAccessTicket=async()=>{throw new Error('Access is outside this fixture')};
''')
    write('main.ts','''
import {createApp,defineComponent,h,ref} from 'vue';
import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';
import Uploader from '@/components/PmsFileArtifact/PmsFileUploader.vue';
import Reference from '@/components/PmsFileArtifact/PmsFileReferenceList.vue';
import Field from '@/views/pms/platform/dynamic-form/components/PmsFileArtifactField.vue';
import {fixture} from './file';
const mode=new URLSearchParams(location.search).get('mode');
const host=defineComponent({setup(){const owner=ref('41'),model=ref([]),completed=ref([]),facts=ref([]);
 const key=()=>({ownerContext:'PLATFORM',objectType:'DYNAMIC_FORM_INSTANCE',objectId:owner.value,purposeCode:'FORM_FIELD_ATTACHMENT/evidence',referenceKey:'slot'});
 return()=>h('main',[
 h('h1','公共文件组件 Owner 切换验收'),h('p',{'data-testid':'owner'},'实例 '+owner.value),
 h('button',{'data-testid':'switch',onClick:()=>{owner.value='42';fixture.owner='42';model.value=[];facts.value=[]}},'切换实例42'),
 h('pre',{'data-testid':'model'},JSON.stringify(model.value)),
 h('pre',{'data-testid':'completed'},JSON.stringify(completed.value)),
 mode==='reference'?h(Reference,{...key(),editable:true,onDetached:r=>completed.value.push(r)}):
 mode==='upload'?h(Uploader,{...key(),categoryCode:'EVIDENCE',onCompleted:r=>completed.value.push(r)}):
 h(Field,{instanceId:Number(owner.value),fieldKey:'evidence',currentFacts:facts.value,allowedActions:['PATCH_INSTANCE'],
 'onUpdate:modelValue':value=>{model.value=value;fixture.model=value}})
 ]);
}});
const app=createApp(host);app.use(ElementPlus);app.directive('hasPermi',()=>{});app.component('Icon',defineComponent({setup:()=>()=>h('span')}));app.mount('#app');
''')
    write('vite.config.mjs',f'''
import {{defineConfig}} from 'vite';import vue from '@vitejs/plugin-vue';import AutoImport from 'unplugin-auto-import/vite';
export default defineConfig({{cacheDir:'{FIXTURE/'.vite'}',plugins:[vue(),AutoImport({{imports:['vue'],dts:false}})],resolve:{{alias:[
{{find:/^@\\/api\\/pms\\/platform\\/file$/,replacement:'{FIXTURE/'file.ts'}'}},
{{find:/^@\\/(utils|utils\\/download|hooks\\/web\\/useMessage)$/,replacement:'{FIXTURE/'ports.ts'}'}},
{{find:'@',replacement:'{UI/'src'}'}}]}},server:{{host:'127.0.0.1',port:{PORT},strictPort:true,fs:{{allow:['{REPO}']}}}}}});
''')
    log=open(FIXTURE/'vite.log','w')
    vite=subprocess.Popen(['node',str(UI/'node_modules/vite/bin/vite.js'),'--config',str(FIXTURE/'vite.config.mjs')],cwd=FIXTURE,stdout=log,stderr=subprocess.STDOUT)
    result={'scope':'Actual shared Vue SFCs and ElementPlus, same component instance across Owner changes; file API promises controlled. No server authorization conclusion.','checks':[]}
    try:
        for _ in range(100):
            try:urllib.request.urlopen(f'http://127.0.0.1:{PORT}',timeout=1);break
            except OSError:
                if vite.poll() is not None:raise RuntimeError('Vite failed')
                time.sleep(.1)
        with sync_playwright() as p:
            browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
            for mode in ['reference','upload','form']:
                page=browser.new_page(viewport={'width':1200,'height':900});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
                page.goto(f'http://127.0.0.1:{PORT}/?mode={mode}',wait_until='networkidle')
                if mode=='reference':
                    expect(page.get_by_text('材料-41.txt',exact=True)).to_be_visible()
                    page.get_by_role('button',name='解绑',exact=True).click()
                    page.wait_for_function('fixture.detach.length===1')
                    page.get_by_test_id('switch').click()
                    expect(page.get_by_text('材料-42.txt',exact=True)).to_be_visible()
                    page.evaluate("fixture.detach[0].resolve({referenceId:'41-ref',status:'DETACHED'})")
                    page.wait_for_timeout(100)
                    expect(page.get_by_text('材料-42.txt',exact=True)).to_be_visible()
                    expect(page.get_by_test_id('completed')).to_have_text('[]')
                else:
                    page.locator('input[type=file]').first.set_input_files({'name':'old.txt','mimeType':'text/plain','buffer':b'old evidence'})
                    page.get_by_role('button',name='上传并绑定',exact=True).click()
                    page.wait_for_function('fixture.uploads.length===1')
                    page.get_by_test_id('switch').click()
                    if mode=='upload':
                        page.locator('input[type=file]').first.set_input_files({'name':'new.txt','mimeType':'text/plain','buffer':b'new evidence'})
                        page.get_by_role('button',name='上传并绑定',exact=True).click()
                        page.wait_for_function('fixture.uploads.length===2')
                    page.evaluate("fixture.uploads[0].progress(100);fixture.uploads[0].resolve({artifactId:'41',versionNo:1,referenceId:'old-ref',referenceKey:fixture.initialized[0].referenceKey,sha256:'old'})")
                    page.wait_for_timeout(100)
                    expect(page.get_by_test_id('completed')).to_have_text('[]')
                    expect(page.get_by_test_id('model')).to_have_text('[]')
                    if mode=='upload':
                        expect(page.get_by_text('正在上传文件',exact=True)).to_be_visible()
                        page.evaluate("fixture.uploads[1].resolve({artifactId:'42',versionNo:1,referenceId:'new-ref',referenceKey:'slot',sha256:'new'})")
                        expect(page.get_by_test_id('completed')).to_contain_text('new-ref')
                    else:
                        page.locator('input[type=file]').first.set_input_files({'name':'current.txt','mimeType':'text/plain','buffer':b'current evidence'})
                        page.get_by_role('button',name='上传并绑定',exact=True).click()
                        page.wait_for_function('fixture.uploads.length===2')
                        page.evaluate("fixture.uploads[1].resolve({artifactId:'42',versionNo:1,referenceId:'new-ref',referenceKey:fixture.initialized[1].referenceKey,sha256:'new'})")
                        page.wait_for_function('fixture.model.length===1')
                        assert page.evaluate('fixture.initialized[1].objectId')=='42'
                assert not errors,errors
                page.screenshot(path=str(FIXTURE/f'{mode}.png'),full_page=True)
                result['checks'].append({'mode':mode,'status':'PASS','pageErrors':errors})
                page.close()
            browser.close()
        result['status']='PASS'
    except Exception as ex:
        result['status']='FAIL';result['failure']=str(ex)
        if 'errors' in locals():result['pageErrors']=errors
        if 'page' in locals():
            try:
                result['bodyText']=page.locator('body').inner_text(timeout=2000)
                page.screenshot(path=str(FIXTURE/'failure.png'),full_page=True)
            except Exception:pass
        raise
    finally:
        (FIXTURE/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
        vite.terminate();vite.wait(timeout=10);log.close()
if __name__=='__main__':main()
