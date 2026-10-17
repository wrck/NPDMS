import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Drawer from './ProjectCustomerExtensionDrawer.vue'
import * as Api from '@/api/pms/customer'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/customer', () => ({ getProjectCustomerOverview: vi.fn(), updateProjectCustomerExtension: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: vi.fn() }) }))
const dialog=defineComponent({props:['modelValue'],setup(props,{slots}) {return()=>props.modelValue?h('section',[slots.default?.(),slots.footer?.()]):null}})
const find=(node:TestNode,predicate:(node:TestNode)=>boolean):TestNode|undefined=>predicate(node)?node:node.children.map(child=>find(child,predicate)).find(Boolean)
const settle=async()=>{for(let i=0;i<6;i++)await nextTick()}
beforeEach(()=>{
 vi.clearAllMocks()
 vi.mocked(Api.getProjectCustomerOverview).mockResolvedValue({id:'2102227613952667649',name:'项目客户',code:'C-200',remark:'原备注',version:7} as unknown as Api.CustomerRespVO)
 vi.mocked(Api.updateProjectCustomerExtension).mockResolvedValue({} as any)
})
const components={Dialog:dialog,ElDescriptions:passthrough,ElDescriptionsItem:passthrough,ElInput:passthrough}
it('edits only the current project customer remark using its original version and raw project ID',async()=>{
 const mounted=mount(Drawer,{projectId:'992203060198'},components)
 try{
  await (mounted.vm as any).open();await settle()
  expect(Api.getProjectCustomerOverview).toHaveBeenCalledWith('992203060198')
  const input=find(mounted.root,node=>node.props?.['aria-label']==='平台备注')!
  input.props!['onUpdate:modelValue']('新备注');await settle()
  await find(mounted.root,node=>node.type==='button'&&textOf(node)==='保存')!.props!.onClick()
  expect(Api.updateProjectCustomerExtension).toHaveBeenCalledWith('992203060198',{remark:'新备注'},7,expect.any(String))
  expect(textOf(mounted.root)).not.toContain('平台备注')
 }finally{mounted.app.unmount()}
})
it('keeps entered data visible when the server refuses the project write',async()=>{
 vi.mocked(Api.updateProjectCustomerExtension).mockRejectedValue(new Error('project scope denied'))
 const mounted=mount(Drawer,{projectId:'992203060198'},components)
 try{
  await (mounted.vm as any).open();await settle()
  await find(mounted.root,node=>node.type==='button'&&textOf(node)==='保存')!.props!.onClick();await settle()
  expect(textOf(mounted.root)).toContain('客户备注保存失败')
  expect(find(mounted.root,node=>node.props?.['aria-label']==='平台备注')!.props!.modelValue).toBe('原备注')
 }finally{mounted.app.unmount()}
})
