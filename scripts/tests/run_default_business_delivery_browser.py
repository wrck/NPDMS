#!/usr/bin/env python3
"""Actual default Host/upload/collection SFCs against production MVC/services and an exclusive MySQL fixture."""
import json
import subprocess
import time
import urllib.request
import urllib.parse
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

REPO = Path(__file__).resolve().parents[2]
UI = REPO / 'yudao-ui/yudao-ui-admin-vue3'
FIX = REPO / '.run/default-business-delivery-20261007/browser'
OUT = REPO / 'docs/generated/default-business-delivery-20261007'
HTTP = 'http://127.0.0.1:27462'

def fixture(path):
    with urllib.request.urlopen(HTTP + path, timeout=10) as response:
        return json.load(response)

def prepare():
    FIX.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    if not (FIX / 'node_modules').exists():
        (FIX / 'node_modules').symlink_to(UI / 'node_modules', target_is_directory=True)
    def write(name, data):
        (FIX / name).write_text(data)
    write('index.html', '<html lang="zh-CN"><body><div id="app"></div><script type="module" src="/main.ts"></script></body></html>')
    write('http.ts', '''async function send(method, options) {
      const params = new URLSearchParams(); Object.entries(options.params || {}).forEach(([k,v]) => { if(v != null) params.set(k,String(v)) });
      const headers = {...options.headers}; const multipart = options.data instanceof FormData;
      if(options.data && !multipart) headers['Content-Type']='application/json';
      const response = await fetch(options.url + (params.size ? '?' + params : ''), {method,headers,body:options.data ? multipart ? options.data : JSON.stringify(options.data) : undefined});
      const body = await response.json(); if(!response.ok || body.code !== 0) throw new Error(body.msg || 'HTTP ' + response.status); return body.data;
    }
    export default Object.fromEntries(['get','post','put','delete'].map(method=>[method,options=>send(method.toUpperCase(),options)]));''')
    write('ports.ts', '''export const getCurrentUserId=()=>880001; export const getTenantId=()=>7; export const getVisitTenantId=()=>undefined;
      export const getProjectPage=async()=>({list:[{id:99,projectName:'隔离项目99'},{id:101,projectName:'隔离项目101'}],total:2});
      export const getProject=async(id)=>({id,projectName:'隔离项目'+id});
      export const resolveStandaloneBusinessEntityView=()=>undefined;
      export const useMessage=()=>({success:()=>{},error:()=>{},warning:()=>{},confirm:async()=>{}});''')
    write('Select.vue', '''<template><select :value="modelValue" aria-label="归集项目" @change="$emit('update:modelValue',$event.target.value)">
      <option value="">选择项目</option><option value="99">隔离项目99</option><option value="101">隔离项目101</option></select></template>
      <script setup>defineProps(['modelValue','api','labelField','valueField','queryField']);defineEmits(['update:modelValue'])</script>''')
    write('Fixture.vue', '''<template><div>
      <button @click="mode='declaredNote'">业务一</button><button @click="mode='secondDelivery'">业务二</button><button @click="mode='collection'">归集</button>
      <Collection v-if="mode==='collection'" />
      <Host v-else-if="ids" :key="mode" owner-module="IT" :entity-type="mode" :initial-entity-id="mode==='declaredNote'?ids.first.entityId:ids.second.entityId" deliverable-type="REPORT" />
    </div></template><script setup>
      import {ref,onMounted} from 'vue'; import Host from '@/components/BusinessEntity/BusinessEntityHost.vue';
      import Collection from '@/views/pms/platform/deliverables/index.vue';
      const mode=ref('declaredNote'),ids=ref();onMounted(async()=>ids.value=await (await fetch('/fixture/context')).json());
    </script>''')
    write('main.ts', '''import {createApp,defineComponent,h,resolveComponent} from 'vue';import {createRouter,createWebHistory} from 'vue-router';
      import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import Fixture from './Fixture.vue';
      const router=createRouter({history:createWebHistory(),routes:[{path:'/:pathMatch(.*)*',component:Fixture}]});
      const app=createApp(defineComponent({render:()=>h(resolveComponent('router-view'))}));app.use(router);app.use(ElementPlus);
      app.component('ContentWrap',defineComponent({setup(_,ctx){return()=>h('section',{style:'margin:16px'},ctx.slots.default?.())}}));
      app.component('Icon',defineComponent({setup(){return()=>h('span')}}));app.mount('#app');''')
    write('vite.config.mjs', f'''import {{defineConfig}} from 'vite';import vue from '@vitejs/plugin-vue';import AutoImport from 'unplugin-auto-import/vite';
      export default defineConfig({{cacheDir:'{FIX}/.vite',plugins:[vue(),AutoImport({{imports:['vue',{{'@/hooks/web/useMessage':['useMessage']}}],dts:false}})],
        resolve:{{alias:[
          {{find:/^@\\/config\\/axios$/,replacement:'{FIX}/http.ts'}},
          {{find:/^@\\/(utils\\/auth|api\\/pms\\/project\\/projects|components\\/BusinessView\\/registry|hooks\\/web\\/useMessage)$/,replacement:'{FIX}/ports.ts'}},
          {{find:/^@\\/components\\/PmsEntitySelect\\/index.vue$/,replacement:'{FIX}/Select.vue'}},
          {{find:'@',replacement:'{UI}/src'}}]}},
        server:{{host:'127.0.0.1',port:27463,strictPort:true,fs:{{allow:['{REPO}']}},proxy:{{'/api':'{HTTP}','/fixture':'{HTTP}'}}}}
      }});''')

def main():
    prepare()
    log = (FIX / 'vite.log').open('w')
    vite = subprocess.Popen(['node', str(UI / 'node_modules/vite/bin/vite.js'), '--config', str(FIX / 'vite.config.mjs')], cwd=FIX, stdout=log, stderr=subprocess.STDOUT)
    result = {'scope':'Actual default Host, upload and collection SFCs; production MVC/method-security/default services/MySQL. Authenticated transport, project scope and storage are fixture ports; no full boot/login/migration claim.', 'checks':[]}
    networks = []
    try:
        for _ in range(100):
            if vite.poll() is not None:
                raise RuntimeError((FIX / 'vite.log').read_text())
            try:
                urllib.request.urlopen('http://127.0.0.1:27463', timeout=1).close(); break
            except OSError:
                time.sleep(.1)
        with sync_playwright() as p:
            browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
            page=browser.new_page(viewport={'width':1500,'height':1600}); errors=[]
            page.on('pageerror',lambda error:errors.append(str(error)))
            page.on('response',lambda response:networks.append({'url':response.url,'status':response.status,'body':response.text()}) if '/api/v1/' in response.url else None)
            page.goto('http://127.0.0.1:27463',wait_until='networkidle')
            expect(page.get_by_text('尚无有效上传',exact=True)).to_be_visible(timeout=15000)
            page.get_by_label('上传交付件').set_input_files({'name':'first.txt','mimeType':'text/plain','buffer':b'First ordinary upload\n'})
            expect(page.get_by_text('已有最新上传记录',exact=True)).to_be_visible(timeout=15000)
            first=fixture('/fixture/evidence');assert len(first['materials'])==first['files']==1 and first['requirements']==first['submissions']==0,first
            row=page.locator('.default-delivery-records .el-table__row');row.get_by_role('textbox').fill('Edited in default view');row.get_by_role('button',name='保存标题',exact=True).click()
            expect(row.get_by_role('textbox')).to_have_value('Edited in default view');page.screenshot(path=str(OUT/'first-default-business.png'),full_page=True)
            result['checks'].append({'name':'first default business upload / material / common completion / title edit','passed':True,'database':fixture('/fixture/evidence')})
            page.get_by_role('button',name='业务二',exact=True).click();expect(page.get_by_text('尚无有效上传',exact=True)).to_be_visible(timeout=15000)
            fixture('/fixture/reject-material')
            page.get_by_label('上传交付件').set_input_files({'name':'second.txt','mimeType':'text/plain','buffer':b'Second ordinary upload\n'})
            expect(page.get_by_role('button',name='重试上传',exact=True)).to_be_visible(timeout=15000)
            failed=fixture('/fixture/evidence');assert len(failed['materials'])==failed['files']==1 and not failed['secondCompletion']['completed'],failed
            fixture('/fixture/allow-material');page.get_by_role('button',name='重试上传',exact=True).click()
            expect(page.get_by_text('已有最新上传记录',exact=True)).to_be_visible(timeout=15000)
            second=fixture('/fixture/evidence');assert len(second['materials'])==second['files']==2 and second['secondCompletion']['completed'],second
            assert len(page.locator('.default-delivery-records .el-table__row').all())==1
            page.screenshot(path=str(OUT/'second-default-business.png'),full_page=True)
            result['checks'].append({'name':'second ordinary business with zero adapter/service/API; real registration failure rollback and same-request retry','passed':True,'failed':failed,'database':second})
            page.get_by_role('button',name='归集',exact=True).click();page.get_by_label('归集项目').select_option('99');page.get_by_label('归集交付件类型').fill('REPORT')
            expect(page.locator('.default-delivery-records .el-table__row')).to_have_count(2,timeout=15000)
            page.screenshot(path=str(OUT/'project-collection.png'),full_page=True)
            page.get_by_label('归集项目').select_option('101');expect(page.locator('.default-delivery-records .el-table__row')).to_have_count(0)
            result['checks'].append({'name':'project/type collection displays the same two records and isolates project101','passed':True})
            page.get_by_role('button',name='业务二',exact=True).click();expect(page.get_by_text('已有最新上传记录',exact=True)).to_be_visible(timeout=15000)
            expect(page.locator('.default-delivery-records .el-table__row')).to_have_count(1)
            page.get_by_role('button',name='删除',exact=True).click();expect(page.get_by_text('尚无有效上传',exact=True)).to_be_visible(timeout=15000)
            deleted=fixture('/fixture/evidence');assert len(deleted['materials'])==deleted['files']==2 and deleted['materials'][1]['deleted'] and not deleted['secondCompletion']['completed'],deleted
            page.get_by_label('上传交付件').set_input_files({'name':'second-new.txt','mimeType':'text/plain','buffer':b'Second reupload\n'})
            expect(page.get_by_text('已有最新上传记录',exact=True)).to_be_visible(timeout=15000)
            final=fixture('/fixture/evidence');assert len(final['materials'])==final['files']==3 and final['firstTitle']=='first',final
            assert str(final['materials'][-1]['id'])==final['secondCompletion']['latest']['id']
            page.screenshot(path=str(OUT/'second-reupload.png'),full_page=True)
            result['checks'].append({'name':'exact echo / logical delete / common completion invalidation / reupload chooses latest without overwriting business values','passed':True,'deleted':deleted,'database':final})
            assert not errors,errors
            # Opening an initial entity can hide a failed list request; do not count that as a working default page.
            pages=[response for response in networks if urllib.parse.urlparse(response['url']).path.endswith('/page')]
            assert pages,'Default business list was never requested'
            assert all(response['status']==200 and json.loads(response['body']).get('code')==0 for response in pages),pages
            result['checks'].append({'name':'shared default page queries succeed before and after business switching','passed':True})
            browser.close();result['passed']=True
    finally:
        (OUT/'browser-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
        (OUT/'browser-http.json').write_text(json.dumps(networks,ensure_ascii=False,indent=2))
        vite.terminate()
        try: vite.wait(timeout=10)
        except subprocess.TimeoutExpired: vite.kill()
        log.close()
    print(json.dumps({'passed':True,'checks':len(result['checks'])},ensure_ascii=False))

if __name__=='__main__': main()
