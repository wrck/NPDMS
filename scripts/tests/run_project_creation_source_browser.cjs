// Real Chromium + production Vue components; synthetic source HTTP responses only.
// This checks UI behavior, not backend transaction/business acceptance.
const fs = require('node:fs')
const path = require('node:path')
const os = require('node:os')
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const root = path.resolve(__dirname, '../..')
const ui = path.join(root, 'yudao-ui/yudao-ui-admin-vue3')
const req = createRequire(path.join(ui, 'package.json'))
const { createServer } = req('vite')
const vue = req('@vitejs/plugin-vue').default
const autoImportModule = req('unplugin-auto-import/vite')
const autoImport = autoImportModule.default || autoImportModule
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.join(os.tmpdir(), 'npdms-fplt002-playwright/node_modules/playwright'))
const output = process.env.NPDMS_BROWSER_TEST_OUTPUT || path.join(root, 'docs/generated/2026-10-06-project-creation-migration/browser')
const port = Number(process.env.NPDMS_BROWSER_TEST_PORT || 19081)
assert.ok(Number.isInteger(port) && port > 0 && port < 65536, 'invalid browser test port')
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'npdms-creation-browser-'))
const write = (name, value) => { const file = path.join(temp, name); fs.writeFileSync(file, value); return file }
write('index.html', '<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head><body><div id="app"></div><script type="module" src="/main.ts"></script></body></html>')
write('main.ts', `import {createApp} from 'vue'; import ElementPlus from 'element-plus'; import 'element-plus/dist/index.css'; import App from './App.vue'; createApp(App).use(ElementPlus).mount('#app')`)
const sourceStub = write('source.ts', `export const getContractCreationSource=async(id,order)=>{const r=await fetch('/mock/source?id='+id+'&order='+(order||''));if(!r.ok)throw Error('合成来源加载失败');return r.json()}`)
const requestStub = write('request.ts', `export default {get:async()=>({contracts:[],orders:[],executionOrders:[{id:301,executionNo:'EX-A',projectAmount:0,sourceSystem:'SYNTHETIC',salesRepName:'合成销售代表',finalCustomerName:'合成最终客户',agentName:'合成代理商'}]})}`)
const permissionStub = write('permission.ts', `export const checkPermi=()=>true`)
const dictStub = write('dict.ts', `export const DICT_TYPE={};export const getDictLabel=(_,v)=>v`)
const memberStub = write('members.ts', `export const getMemberPage=async()=>({list:[],total:0})`)
write('App.vue', `<template><main><h1>合同、订单、执行单关联验证（合成数据）</h1><el-space><el-button @click="choose(1)">合同 A</el-button><el-button @click="choose(2)">合同 B</el-button><el-button @click="state.selectContract(undefined)">清除合同</el-button></el-space><el-form label-width="100px"><ProjectCreationSource :source="state.creationSource.value" :loading="state.creationSourceLoading.value" :error="state.sourceError.value" :sales-order-id="form.salesOrderId" @select-order="state.selectSalesOrder"/><el-form-item label="项目名称"><el-input data-testid="project-name" :model-value="form.projectName" readonly/></el-form-item><el-form-item label="最终客户编码"><el-input data-testid="customer" v-model="form.customerCode"/></el-form-item><el-button data-testid="next" :disabled="!state.sourceReady.value">下一步</el-button></el-form><ProjectBaseInfo :project="{id:910001,projectCode:'SYNTHETIC-P',projectName:'合成项目',version:0}" /></main></template>
<script setup lang="ts">import {reactive,ref} from 'vue';import ProjectCreationSource from '@/views/pms/project/projects/ProjectCreationSource.vue';import ProjectBaseInfo from '@/views/pms/project/project-master-detail/components/ProjectBaseInfo.vue';import {useProjectCreationSource} from '@/views/pms/project/projects/useProjectCreationSource';const form=reactive({contractNo:'',projectName:'',customerCode:'',customerName:''});const state=useProjectCreationSource(form,ref([{id:1,code:'CO1'}]),ref([{id:2,code:'D1'}]));const choose=id=>state.selectContract(id,{id,contractNo:'CT-'+id});</script>
<style>body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:0;background:#f4f6fa}main{max-width:1100px;margin:20px auto;background:white;padding:20px;box-sizing:border-box}h1{font-size:20px}.el-form{margin-top:24px}.el-descriptions__content{overflow-wrap:anywhere}@media(max-width:600px){main{margin:0;padding:12px}h1{font-size:16px}}</style>`)
const fixture = (contract, order) => {
  const orders = contract === 1 ? [{id:11,orderNo:'SO-A1',executionNo:'EX-A1'}, {id:12,orderNo:'SO-A2',executionNo:'EX-A2'}] : [{id:21,orderNo:'SO-B1',executionNo:'EX-B1'}]
  const selected = orders.find(o=>o.id===Number(order)) || (orders.length===1 ? orders[0] : undefined)
  return {contract:{id:contract,contractNo:'CT-'+contract},orders,sourceFingerprint:'a'.repeat(64),lineExecutionNos:[],
    executionOrders:orders.map(o=>({id:o.id*10,executionNo:o.executionNo,departmentCode:'D1',salesRepName:'销售-'+o.orderNo})),
    resolved:selected ? {salesOrderId:selected.id,executionOrderId:selected.id*10,executionNo:selected.executionNo,projectName:'项目-'+selected.orderNo,customerCode:'CUS-'+contract,companyCode:'CO1',customerProjectName:'客户项目-'+selected.orderNo} : {}}
}
;(async()=>{
  fs.mkdirSync(output,{recursive:true})
  const server = await createServer({configFile:false,root:temp,plugins:[vue(),autoImport({imports:['vue'],dts:false})],resolve:{alias:[
    {find:'@/api/pms/commerce',replacement:sourceStub},{find:'@/config/axios',replacement:requestStub},
    {find:'@/utils/permission',replacement:permissionStub},{find:'@/utils/dict',replacement:dictStub},
    {find:'@/api/pms/project/unified-members',replacement:memberStub},{find:'@',replacement:path.join(ui,'src')},
    {find:'vue',replacement:req.resolve('vue/dist/vue.esm-bundler.js')},{find:'element-plus',replacement:path.join(ui,'node_modules/element-plus')}
  ]},server:{host:'127.0.0.1',port,strictPort:true,fs:{allow:[temp,ui]}},logLevel:'warn'})
  let browser
  const errors=[];const checks=[]
  try {
    await server.listen()
    browser = await chromium.launch({headless:true})
    const page=await browser.newPage({viewport:{width:1440,height:1100}})
    page.on('pageerror',error=>errors.push(error.message))
    let fail=false,slow=false
    await page.route('**/mock/source?*',async route=>{
      const url=new URL(route.request().url());const id=Number(url.searchParams.get('id'))
      if(slow && id===1)await new Promise(resolve=>setTimeout(resolve,500))
      await route.fulfill({status:fail?503:200,contentType:'application/json',body:JSON.stringify(fixture(id,url.searchParams.get('order')))})
    })
    await page.goto(`http://127.0.0.1:${port}`)
    await page.getByRole('button',{name:'合同 A',exact:true}).click()
    await page.getByText('选择此合同下的销售订单',{exact:true}).waitFor()
    assert.equal(await page.getByTestId('next').isDisabled(),true)
    assert.equal(await page.getByTestId('customer').inputValue(),'')
    await page.getByTestId('customer').fill('FINAL-SELECTED')
    await page.locator('.el-select').click()
    await page.getByRole('option',{name:'SO-A2 · 执行单 EX-A2',exact:true}).click()
    await page.waitForFunction(()=>document.querySelector('[data-testid="project-name"]')?.value==='项目-SO-A2')
    assert.equal(await page.getByTestId('next').isDisabled(),false)
    assert.equal(await page.getByTestId('customer').inputValue(),'FINAL-SELECTED')
    assert.match(await page.locator('.el-descriptions').innerText(),/CUS-1/)
    checks.push('final customer remains independent; order buyer stays in source preview')
    assert.equal(await page.locator('.el-descriptions').innerText().then(x=>x.includes('EX-A1')),false)
    checks.push('multi-order requires explicit header selection; execution follows selected SO-A2')
    await page.screenshot({path:path.join(output,'linked-selection.png'),fullPage:true})
    slow=true
    await page.getByRole('button',{name:'合同 A',exact:true}).click()
    await page.getByRole('button',{name:'合同 B',exact:true}).click()
    await page.waitForFunction(()=>document.querySelector('[data-testid="project-name"]')?.value==='项目-SO-B1')
    await page.waitForTimeout(650)
    assert.equal(await page.getByTestId('project-name').inputValue(),'项目-SO-B1')
    assert.equal(await page.getByTestId('customer').inputValue(),'FINAL-SELECTED')
    checks.push('late contract A response cannot overwrite contract B or the selected final customer')
    fail=true
    await page.getByRole('button',{name:'合同 A',exact:true}).click()
    await page.getByText('合成来源加载失败',{exact:true}).waitFor()
    assert.equal(await page.getByTestId('customer').inputValue(),'FINAL-SELECTED')
    assert.equal(await page.getByTestId('next').isDisabled(),true)
    checks.push('source failure clears stale source facts, retains final customer and blocks continue')
    const amount=page.locator('.fact').filter({has:page.locator('dt',{hasText:'项目金额'})})
    assert.equal(await amount.locator('dd').innerText(),'0')
    assert.match(await page.locator('.project-facts').innerText(),/合成最终客户（执行单）/)
    checks.push('detail shows zero amount and explicitly labeled source party')
    await page.setViewportSize({width:390,height:844})
    await page.screenshot({path:path.join(output,'mobile-error-and-detail.png'),fullPage:true})
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true)
    assert.deepEqual(errors,[])
    checks.push('390px viewport has no horizontal overflow; no browser page errors')
    fs.writeFileSync(path.join(output,'result.json'),JSON.stringify({status:'passed',scope:'real Chromium UI components with synthetic HTTP source; not backend business acceptance',checks,errors},null,2))
    console.log(JSON.stringify({status:'passed',checks}))
  }finally{if(browser)await browser.close();await server.close()}
})().catch(error=>{console.error(error);process.exitCode=1})
