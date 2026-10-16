import {defineComponent,h,nextTick,reactive,ref} from 'vue'
import {beforeEach,expect,it,vi} from 'vitest'
import DirectBusinessView from './DirectBusinessView.vue'
import {getDirectBusinessView} from '@/api/pms/platform/business'
import {mount,textOf,type TestNode} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/platform/business',()=>({getDirectBusinessView:vi.fn()}))
vi.mock('@/utils/auth',()=>({getCurrentUserId:()=>42,getTenantId:()=>7,getVisitTenantId:()=>undefined}))
const leave=vi.hoisted(()=>vi.fn(async()=>false))
const native=vi.hoisted(()=>({mounted:0}))
vi.mock('@/components/ProjectBusiness/ProjectBusinessPage.vue',()=>({default:defineComponent({setup(_, {attrs,expose}){expose({requestLeave:leave});return()=>h('default-page',attrs)}})}))
vi.mock('@/components/ProjectBusiness/native/NativeBusinessHost.vue',()=>({__esModule:true,default:defineComponent({setup(_, {attrs,expose}){
 native.mounted++;expose({requestLeave:leave,canAdoptAssociatedObject:(id:unknown)=>String(id)==='42'});return()=>h('native-page',attrs)
}})}))
const all=(node:TestNode):TestNode[]=>[node,...node.children.flatMap(all)]
const flush=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
beforeEach(()=>{vi.resetAllMocks();native.mounted=0})
const initial=(scope:{ownerModule:string;entityType:string;stableCode:string;projectId:number},apiBase='/api/v1/pms/notes')=>({
 view:{ownerModule:scope.ownerModule,entityType:scope.entityType,stableCode:scope.stableCode,apiBase},
 data:{request:{projectId:scope.projectId},model:{stableCode:scope.stableCode},configuration:{},records:{list:[],total:0},forms:[],relatedPages:[],context:{}}
})
it('uses resolved initialization while keeping business content closed during capability checks',async()=>{
 const scope={ownerModule:'SOL',entityType:'stagePlanBatch',stableCode:'SOL_STAGE_PLAN_BATCH',projectId:20}
 const initialization=initial(scope,'/api/v1/pms/stage-plan-business'),state=reactive({deferContent:true}),child=ref<any>()
 const mounted=mount(defineComponent({setup:()=>()=>h(DirectBusinessView,{ref:child,...scope,initialization:initialization as any,allowedActions:['QUERY'],...state})}))
 try{
  await flush();await vi.dynamicImportSettled();await flush()
  expect(getDirectBusinessView).not.toHaveBeenCalled();expect(native.mounted).toBe(0);expect(await child.value.requestLeave()).toBe(true)
  state.deferContent=false;await flush();await vi.dynamicImportSettled();await flush()
  expect(native.mounted).toBe(1);expect(getDirectBusinessView).not.toHaveBeenCalled()
  expect(all(mounted.root).find(node=>node.type==='native-page')?.props?.initialization).toEqual(initialization.data)
 }finally{mounted.app.unmount()}
})
it('reports unavailable metadata and reads it fresh on reopening',async()=>{
 const scope={ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',projectId:20},state=reactive({opening:1})
 vi.mocked(getDirectBusinessView).mockRejectedValueOnce(new Error('视图定义暂不可用'))
 const mounted=mount(defineComponent({setup:()=>()=>h(DirectBusinessView,{...scope,key:state.opening,allowedActions:['QUERY']})}))
 try{
  await flush();expect(textOf(mounted.root)).toContain('视图定义暂不可用')
  expect(all(mounted.root).some(node=>node.type==='default-page')).toBe(false);expect(getDirectBusinessView).toHaveBeenCalledOnce()
  vi.mocked(getDirectBusinessView).mockResolvedValue({...scope,apiBase:'/api/v1/pms/notes'})
  state.opening++;await flush()
  expect(all(mounted.root).some(node=>node.type==='default-page')).toBe(true);expect(getDirectBusinessView).toHaveBeenCalledTimes(2)
 }finally{mounted.app.unmount()}
})
it('rejects an initial page belonging to another project',async()=>{
 const scope={ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',projectId:20}
 const mounted=mount(DirectBusinessView,{...scope,projectId:21,initialization:initial(scope),allowedActions:['QUERY']})
 try{await flush();expect(textOf(mounted.root)).toContain('初始化范围不匹配');expect(all(mounted.root).some(node=>node.type==='default-page')).toBe(false);expect(getDirectBusinessView).not.toHaveBeenCalled()}finally{mounted.app.unmount()}
})
it('validates the Owner identity of resolved initialization',async()=>{
 const scope={ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',projectId:20}
 const initialization=initial({...scope,ownerModule:'OTHER'})
 const mounted=mount(DirectBusinessView,{...scope,initialization,allowedActions:['QUERY']})
 try{await flush();expect(textOf(mounted.root)).toContain('身份不匹配');expect(all(mounted.root).some(node=>node.type==='default-page')).toBe(false);expect(getDirectBusinessView).not.toHaveBeenCalled()}finally{mounted.app.unmount()}
})
it('discovers an inherited API route and preserves exact project/entity IDs plus allowed actions',async()=>{
 vi.mocked(getDirectBusinessView).mockResolvedValue({ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',apiBase:'/api/v1/pms/notes'})
 const mounted=mount(DirectBusinessView,{ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',projectId:'9007199254740993',entityId:'9007199254740994',readonly:true,allowedActions:[]})
 try{await flush();expect(all(mounted.root).find(node=>node.type==='default-page')?.props).toMatchObject({'api-base':'/api/v1/pms/notes','scope-project-id':'9007199254740993','initial-entity-id':'9007199254740994',readonly:true,'allowed-actions':[]})}finally{mounted.app.unmount()}
})
it('a mismatched metadata identity never loads a business page',async()=>{
 vi.mocked(getDirectBusinessView).mockResolvedValue({ownerModule:'OTHER',entityType:'note',stableCode:'IT_NOTE',apiBase:'/api/v1/pms/other'})
 const mounted=mount(DirectBusinessView,{ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',projectId:20,allowedActions:['save']})
 try{await flush();expect(textOf(mounted.root)).toContain('身份不匹配');expect(all(mounted.root).some(node=>node.type==='default-page')).toBe(false)}finally{mounted.app.unmount()}
})

it('passes the enclosing authorized project through to its native page without changing its exact ID',async()=>{
 vi.mocked(getDirectBusinessView).mockResolvedValue({ownerModule:'SOL',entityType:'requirementAnalysisRevision',stableCode:'SOL_REQUIREMENT_ANALYSIS_REVISION',apiBase:'/api/v1/pms/requirement-analysis-business'})
 const project={id:'9007199254740993',projectName:'已读取的项目'}
 const mounted=mount(DirectBusinessView,{ownerModule:'SOL',entityType:'requirementAnalysisRevision',stableCode:'SOL_REQUIREMENT_ANALYSIS_REVISION',projectId:project.id,initialProject:project,allowedActions:['QUERY']})
 try{
  await flush();await vi.dynamicImportSettled();await flush()
  expect(all(mounted.root).find(node=>node.type==='native-page')?.props?.['initial-project']).toEqual(project)
  expect(getDirectBusinessView).toHaveBeenCalledOnce()
 }finally{mounted.app.unmount()}
})

it('forwards the inherited page leave guard so a template switch cannot discard unsaved business data',async()=>{
 vi.mocked(getDirectBusinessView).mockResolvedValue({ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',apiBase:'/api/v1/pms/notes'})
 leave.mockResolvedValue(false)
 const mounted=mount(DirectBusinessView,{ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',projectId:20,allowedActions:['save']})
 try{await flush();expect(await (mounted.vm as any).requestLeave()).toBe(false);expect(leave).toHaveBeenCalledOnce();leave.mockResolvedValue(true);expect(await (mounted.vm as any).requestLeave()).toBe(true)}finally{mounted.app.unmount()}
})

it('keeps native workspace metadata and delegates receipt identity when its first association arrives',async()=>{
 vi.mocked(getDirectBusinessView).mockResolvedValue({ownerModule:'ACC',entityType:'acceptanceActivity',stableCode:'ACC_ACCEPTANCE_ACTIVITY',apiBase:'/api/v1/pms/acceptance-business'})
 const state=reactive({ownerModule:'ACC',entityType:'acceptanceActivity',stableCode:'ACC_ACCEPTANCE_ACTIVITY',projectId:20,entityId:undefined as string|undefined,allowedActions:['create']})
 const child=ref<any>()
 const mounted=mount(defineComponent({setup:()=>()=>h(DirectBusinessView,{...state,ref:child})}))
 try{
  await flush();await vi.dynamicImportSettled();await flush();expect(native.mounted).toBe(1)
  expect(child.value.canAdoptAssociatedObject('42')).toBe(true)
  expect(child.value.canAdoptAssociatedObject('43')).toBe(false)
  state.entityId='42';state.allowedActions=['report-publish'];await flush()
  expect(native.mounted).toBe(1);expect(getDirectBusinessView).toHaveBeenCalledOnce()
  expect(all(mounted.root).find(node=>node.type==='native-page')?.props).toMatchObject({'initial-entity-id':'42','allowed-actions':['report-publish']})
 }finally{mounted.app.unmount()}
})
