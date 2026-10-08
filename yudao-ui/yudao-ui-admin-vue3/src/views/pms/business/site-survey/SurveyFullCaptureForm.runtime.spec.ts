import {defineComponent,h,nextTick} from 'vue'
import {beforeEach,expect,it,vi} from 'vitest'
import {mount} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Capture from './SurveyFullCaptureForm.vue'
const mocks=vi.hoisted(()=>({header:vi.fn(),validate:vi.fn(),defaults:vi.fn()}))
vi.mock('@/components/ProjectBusiness/ProjectBusinessContentForm.vue',()=>({default:defineComponent({setup(_,{expose}){expose({buildInput:mocks.header});return()=>h('input')}})}))
vi.mock('@/views/pms/delivery-business/site-survey/SiteSurveyDynamicForm.vue',()=>({default:defineComponent({setup(_,{expose}){expose({validate:mocks.validate});return()=>h('section',{'data-testid':'original-full-capture'})}})}))
const flush=async()=>{for(let i=0;i<14;i++){await Promise.resolve();await nextTick()}}
const layout={binding:{formRevisionId:'992209220346',fieldBindings:{extra_cabinetReady:'cabinetReady',extra_powerTypes:'powerTypes',extra_requiredEndDate:'requiredEndDate'},version:0},formVersion:1,formConfJson:'{}',formRulesJson:'[]'}
const fields=['name','location','cabinetReady','powerTypes'].map(code=>({code,name:code,type:'TEXT',readable:true,writable:true,required:false}))
const input={projectId:20,name:'host',location:'site',cabinetReady:true,powerTypes:['AC'],requiredEndDate:null}
beforeEach(()=>{vi.resetAllMocks();mocks.header.mockResolvedValue({});mocks.validate.mockResolvedValue(undefined);mocks.defaults.mockResolvedValue({layout,extensions:{version:0,fields:{}},definitions:[]})})
it('uses the published full template, preserves false and arrays, and sends one binding with the body',async()=>{
 const mounted=mount(Capture,{api:{formDefaults:mocks.defaults},scopeProjectId:20,writableFields:fields,fields,initialValues:input,disabled:false})
 try{
  await flush();const state=(mounted.vm as any).$.setupState
  expect(mocks.defaults).toHaveBeenCalledWith(20);expect(state.schema.revisionId).toBe('992209220346')
  state.survey.businessValues.cabinetReady=false;state.survey.businessValues.powerTypes=['DC']
  const value=await (mounted.vm as any).buildInput()
  expect(value.cabinetReady).toBe(false);expect(value.powerTypes).toEqual(['DC'])
  expect(value.$binding).toMatchObject({formRevisionId:'992209220346',expectedVersion:0,bindRemainingFields:true})
  expect(value).not.toHaveProperty('outsourceRequestId')
 }finally{mounted.app.unmount()}
})
it('keeps the existing bound revision and carries deadline updates as a typed business command',async()=>{
 const bound={...layout,binding:{...layout.binding,formRevisionId:'old-published',version:3}}
 const mounted=mount(Capture,{api:{formDefaults:mocks.defaults},current:{ref:{entityId:'11'},concurrencyBasis:2},scopeProjectId:20,writableFields:fields,fields,initialValues:input,presentation:{layout:bound,extensions:{version:0,fields:{}},definitions:[]},disabled:false})
 try{
  await flush();expect(mocks.defaults).not.toHaveBeenCalled();const state=(mounted.vm as any).$.setupState
  expect(state.schema.revisionId).toBe('old-published')
  state.survey.businessValues.requiredEndDate='2026-11-30';state.survey.projectEndDateVersion=2
  const value=await (mounted.vm as any).buildInput();expect(value.$business).toEqual({requiredEndDate:'2026-11-30',projectVersion:2})
  expect(value).not.toHaveProperty('requiredEndDate');expect(value).not.toHaveProperty('$binding')
 }finally{mounted.app.unmount()}
})
it('read-only full templates never emit a binding or write payload',async()=>{
 const mounted=mount(Capture,{api:{formDefaults:mocks.defaults},scopeProjectId:20,writableFields:fields,fields,initialValues:input,disabled:true})
 try{await flush();expect(await (mounted.vm as any).buildInput()).toEqual({});expect(mocks.validate).not.toHaveBeenCalled()}
 finally{mounted.app.unmount()}
})
