import { beforeEach, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, inject, nextTick, provide, type Ref } from 'vue'
import Panel from './DeliveryPanel.vue'
import * as api from '@/api/pms/platform/delivery'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/platform/delivery',()=>({ getDeliveryAllowedActions:vi.fn(),getDeliveryTypes:vi.fn(),listMaterials:vi.fn(),listRequirements:vi.fn(),
 getCompletion:vi.fn(),listSubmissions:vi.fn(),withdrawMaterial:vi.fn(),submitDelivery:vi.fn(),confirmRequirement:vi.fn(),withdrawSubmission:vi.fn() }))
vi.mock('@/components/PmsFileArtifact/PmsFileReferenceList.vue',()=>({default:{render:()=>null}}))
vi.mock('@/components/DeliveryArtifact/DeliveryUploader.vue',()=>({default:{render:()=>null}}))
const Card=defineComponent({setup(_, {slots}){return()=>h('section',[slots.header?.(),slots.default?.()])}})
const Table=defineComponent({setup(_, {attrs,slots}) {
 provide('rows',computed(()=>attrs.data as any[]));return()=>h('section',slots.default?.())
}})
const Column=defineComponent({setup(_, {attrs,slots}) {
 const rows=inject<Ref<any[]>>('rows')!;return()=>h('section',rows.value.flatMap(row=>
  slots.default ? slots.default({row}) : [h('span',String(row[String(attrs.prop)]??''))]))
}})
const find=(root:TestNode,predicate:(node:TestNode)=>boolean):TestNode|undefined=>predicate(root)?root:root.children.map(child=>find(child,predicate)).find(Boolean)
const flush=async()=>{for(let i=0;i<30;i++){await Promise.resolve();await nextTick()}}
beforeEach(()=>{
 vi.clearAllMocks();vi.mocked(api.getDeliveryAllowedActions).mockResolvedValue(['REGISTER_MATERIAL','WITHDRAW_MATERIAL','SUBMIT','CONFIRM','WITHDRAW_SUBMISSION']);vi.mocked(api.getDeliveryTypes).mockResolvedValue([])
 vi.mocked(api.listMaterials).mockResolvedValue([{id:20,typeCode:'A',title:'问题材料',status:'ACTIVE',materialKind:'FILE'}] as any)
 vi.mocked(api.listRequirements).mockResolvedValue([{id:1,typeCode:'A',status:'CONFIRMED',minimumQuantity:1,count:1},
 {id:2,typeCode:'B',status:'OPEN',minimumQuantity:1,count:0}] as any)
 vi.mocked(api.getCompletion).mockImplementation(async id=>{
  if(id===1)throw new Error('文件证据已失效')
  return {requirementId:2,count:1,minimumQuantity:1,satisfied:true,confirmed:false} as any
 })
 vi.mocked(api.listSubmissions).mockResolvedValue([])
})
it('keeps independently authorized materials and valid requirements when one completion fails',async()=>{
 const view=mount(Panel,{ownerModule:'IMP',entityType:'training',entityId:7},{ElCard:Card,ElTable:Table,ElTableColumn:Column})
 await flush()
 expect(textOf(view.root)).toContain('问题材料')
 expect(textOf(view.root)).toContain('判定失败：文件证据已失效')
 expect(textOf(view.root)).toContain('已满足')
 await (find(view.root,node=>node.type==='button'&&textOf(node).trim()==='撤回')!.props!.onClick as Function)()
 await flush();expect(api.withdrawMaterial).toHaveBeenCalledWith(20)
 view.app.unmount()
})
it('shows authorization failure explicitly without retaining a stale material list',async()=>{
 vi.mocked(api.listMaterials).mockRejectedValue(new Error('无来源对象读取权限'))
 const view=mount(Panel,{ownerModule:'IMP',entityType:'training',entityId:7},{ElCard:Card,ElTable:Table,ElTableColumn:Column})
 await flush();expect(textOf(view.root)).toContain('无来源对象读取权限')
 expect(textOf(view.root)).not.toContain('问题材料');expect(textOf(view.root)).toContain('已满足')
 view.app.unmount()
})

for(const entityType of ['acceptanceActivity','satisfactionCollectionTask'])for(const role of ['query-only','operator'])it(`hides generic writes for ${entityType} ${role} while retaining actual material reads`,async()=>{
 vi.mocked(api.getDeliveryAllowedActions).mockResolvedValue([])
 const view=mount(Panel,{ownerModule:'ACC',entityType,entityId:7},{ElCard:Card,ElTable:Table,ElTableColumn:Column});await flush()
 expect(textOf(view.root)).toContain('问题材料');expect(find(view.root,node=>node.type==='button'&&textOf(node).trim()==='撤回')).toBeUndefined()
 expect(textOf(view.root)).not.toContain('提交已选');expect(textOf(view.root)).not.toContain('撤回提交')
 expect(api.withdrawMaterial).not.toHaveBeenCalled();expect(api.submitDelivery).not.toHaveBeenCalled();view.app.unmount()
})
it('fails closed when allowed actions are revoked on reload',async()=>{
 const view=mount(Panel,{ownerModule:'IMP',entityType:'training',entityId:7},{ElCard:Card,ElTable:Table,ElTableColumn:Column});await flush()
 vi.mocked(api.getDeliveryAllowedActions).mockRejectedValue(new Error('操作权限已撤回'))
 await (find(view.root,node=>node.type==='button'&&textOf(node).trim()==='刷新')!.props!.onClick as Function)();await flush()
 expect(textOf(view.root)).toContain('操作权限已撤回');expect(find(view.root,node=>node.type==='button'&&textOf(node).trim()==='撤回')).toBeUndefined();view.app.unmount()
})
