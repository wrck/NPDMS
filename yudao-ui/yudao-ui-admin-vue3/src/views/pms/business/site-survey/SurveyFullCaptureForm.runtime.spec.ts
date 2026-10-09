import {defineComponent,h,nextTick} from 'vue'
import {beforeEach,expect,it,vi} from 'vitest'
import {mount} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Capture from './SurveyFullCaptureForm.vue'
const mocks=vi.hoisted(()=>({header:vi.fn(),validate:vi.fn(),defaults:vi.fn()}))
vi.mock('./SurveyLocationSelector.vue',()=>({default:{render:()=>null}}))
vi.mock('@/components/ProjectBusiness/ProjectBusinessContentForm.vue',()=>({default:defineComponent({setup(_,{expose}){expose({buildInput:mocks.header});return()=>h('input')}})}))
vi.mock('@/views/pms/delivery-business/site-survey/SiteSurveyDynamicForm.vue',()=>({default:defineComponent({setup(_,{expose}){expose({validate:mocks.validate});return()=>h('section',{'data-testid':'original-full-capture'})}})}))
const flush=async()=>{for(let i=0;i<14;i++){await Promise.resolve();await nextTick()}}
const layout={binding:{formRevisionId:'992209220346',fieldBindings:{extra_cabinetReady:'cabinetReady',extra_powerTypes:'powerTypes',extra_requiredEndDate:'requiredEndDate'},version:0},formVersion:1,formConfJson:'{}',formRulesJson:'[]'}
const fields=['name','location','cabinetReady','powerTypes'].map(code=>({code,name:code,type:'TEXT',readable:true,writable:true,required:false}))
const input={projectId:20,name:'host',location:'site',cabinetReady:true,powerTypes:['AC'],requiredEndDate:null}
beforeEach(()=>{vi.resetAllMocks();mocks.header.mockResolvedValue({});mocks.validate.mockResolvedValue(undefined);mocks.defaults.mockResolvedValue({layout,extensions:{version:0,fields:{}},definitions:[]})})
it('uses the layout supplied by the common form and preserves false and arrays',async()=>{
 const mounted=mount(Capture,{scopeProjectId:20,writableFields:fields,fields,initialValues:input,presentation:{layout,extensions:{version:0,fields:{}},definitions:[]},disabled:false})
 try{
  await flush();const state=(mounted.vm as any).$.setupState
  expect(mocks.defaults).not.toHaveBeenCalled();expect(state.schema.revisionId).toBe('992209220346')
  state.survey.businessValues.cabinetReady=false;state.survey.businessValues.powerTypes=['DC']
  const value=await (mounted.vm as any).readValues()
  expect(value.fields.cabinetReady).toBe(false);expect(value.fields.powerTypes).toEqual(['DC'])
  expect(value).not.toHaveProperty('$binding')
  expect(value.fields).not.toHaveProperty('outsourceRequestId')
 }finally{mounted.app.unmount()}
})
it('keeps the existing bound revision and carries deadline updates as a typed business command',async()=>{
 const bound={...layout,binding:{...layout.binding,formRevisionId:'old-published',version:3}}
 const mounted=mount(Capture,{api:{formDefaults:mocks.defaults},current:{ref:{entityId:'11'},concurrencyBasis:2},scopeProjectId:20,writableFields:fields,fields,initialValues:input,presentation:{layout:bound,extensions:{version:0,fields:{}},definitions:[]},disabled:false})
 try{
  await flush();expect(mocks.defaults).not.toHaveBeenCalled();const state=(mounted.vm as any).$.setupState
  expect(state.schema.revisionId).toBe('old-published')
  state.survey.businessValues.requiredEndDate='2026-11-30';state.survey.projectEndDateVersion=2
  const value=await (mounted.vm as any).readValues();expect(value.business).toEqual({requiredEndDate:'2026-11-30',projectVersion:2})
  expect(value.fields).not.toHaveProperty('requiredEndDate');expect(value).not.toHaveProperty('$binding')
 }finally{mounted.app.unmount()}
})
it('read-only full templates never emit a binding or write payload',async()=>{
 const mounted=mount(Capture,{api:{formDefaults:mocks.defaults},scopeProjectId:20,writableFields:fields,fields,initialValues:input,disabled:true})
 try{await flush();expect(await (mounted.vm as any).readValues()).toEqual({fields:{},extensions:{},business:{}});expect(mocks.validate).not.toHaveBeenCalled()}
 finally{mounted.app.unmount()}
})
it('sends structured location changes with the ordinary save and leaves raw location out of the body patch',async()=>{
 const mounted=mount(Capture,{current:{ref:{entityId:'11'},concurrencyBasis:2},scopeProjectId:20,writableFields:fields,fields,initialValues:input,presentation:{layout,extensions:{version:0,fields:{}},definitions:[],context:{addressId:21,addressVersion:1}},disabled:false})
 try{await flush();const state=(mounted.vm as any).$.setupState
  state.location={...state.location,fallbackLocation:'site',extraSiteLocations:[]}
  expect((await (mounted.vm as any).readValues()).business).toEqual({})
  state.description='new site';state.location={projectId:20,address:{id:22,expectedVersion:3}}
  const value=await (mounted.vm as any).readValues();expect(value.fields).not.toHaveProperty('location');expect(value.business).toMatchObject({locationDescription:'new site',location:{address:{id:22,expectedVersion:3}}})
 }finally{mounted.app.unmount()}
})
it('preserves the original fallback location and normalizes a newly maintained site before saving',async()=>{
 const mounted=mount(Capture,{current:{ref:{entityId:'11'},concurrencyBasis:2},scopeProjectId:20,writableFields:fields,fields,initialValues:input,presentation:{layout,extensions:{version:0,fields:{}},definitions:[]},disabled:false})
 try{await flush();const state=(mounted.vm as any).$.setupState
  expect(state.location.fallbackLocation).toBe('site')
  state.location={projectId:20,fallbackLocation:'现场兼容地点'}
  expect((await (mounted.vm as any).readValues()).fields.location).toBe('现场兼容地点')
  state.location={projectId:20,address:{countryName:'中国',provinceCode:'110000',provinceName:'北京市',detailAddress:'验收地址'},site:{name:'验收站点'},siteLocation:{name:'',code:''}}
  const value=await (mounted.vm as any).readValues()
  expect(value.business.location.address.fullAddress).toBe('中国北京市验收地址')
  expect(value.business.location.siteLocation).toBeUndefined()
  expect(value.business.locationDescription).toBe('验收站点（中国北京市验收地址）')
 }finally{mounted.app.unmount()}
})
