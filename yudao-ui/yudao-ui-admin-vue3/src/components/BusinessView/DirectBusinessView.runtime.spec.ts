import {defineComponent,h,nextTick} from 'vue'
import {beforeEach,expect,it,vi} from 'vitest'
import DirectBusinessView from './DirectBusinessView.vue'
import {getDirectBusinessView} from '@/api/pms/platform/business'
import {mount,textOf,type TestNode} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/platform/business',()=>({getDirectBusinessView:vi.fn()}))
const leave=vi.hoisted(()=>vi.fn(async()=>false))
vi.mock('@/components/ProjectBusiness/ProjectBusinessPage.vue',()=>({default:defineComponent({setup(_, {attrs,expose}){expose({requestLeave:leave});return()=>h('default-page',attrs)}})}))
const all=(node:TestNode):TestNode[]=>[node,...node.children.flatMap(all)]
const flush=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
beforeEach(()=>vi.resetAllMocks())
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

it('forwards the inherited page leave guard so a template switch cannot discard unsaved business data',async()=>{
 vi.mocked(getDirectBusinessView).mockResolvedValue({ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',apiBase:'/api/v1/pms/notes'})
 leave.mockResolvedValue(false)
 const mounted=mount(DirectBusinessView,{ownerModule:'IT',entityType:'note',stableCode:'IT_NOTE',projectId:20,allowedActions:['save']})
 try{await flush();expect(await (mounted.vm as any).requestLeave()).toBe(false);expect(leave).toHaveBeenCalledOnce();leave.mockResolvedValue(true);expect(await (mounted.vm as any).requestLeave()).toBe(true)}finally{mounted.app.unmount()}
})
