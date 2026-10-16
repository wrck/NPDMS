import {defineComponent,h,nextTick,ref} from 'vue'
import {beforeEach,expect,it,vi} from 'vitest'
import Approvals from './ProjectBusinessApprovals.vue'
import {mount as rawMount,passthrough,tableColumn,textOf,type TestNode} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const mount=(component:any,props:Record<string,unknown>={})=>rawMount(component,props,{ElTable:passthrough,ElTableColumn:tableColumn,ElSelect:passthrough,ElOption:passthrough})
const mocks=vi.hoisted(()=>({users:vi.fn(),push:vi.fn(),prompt:vi.fn()}))
vi.mock('vue-router',()=>({useRouter:()=>({push:mocks.push})}))
vi.mock('@/hooks/web/useMessage',()=>({useMessage:()=>({prompt:mocks.prompt})}))
vi.mock('@/utils/auth',()=>({getCurrentUserId:()=>9}))
vi.mock('@/api/system/user',()=>({getSimpleUserList:mocks.users}))
vi.mock('@/utils',()=>({generateUUID:()=>`approval-${Math.random()}`}))
vi.mock('@/utils/formCreate',()=>({setConfAndFields2:(value:any)=>{value.value={rule:[],option:{},value:{}}}}))
vi.mock('@/components/Dialog',()=>({Dialog:defineComponent({props:['modelValue'],setup:(props,{slots})=>()=>props.modelValue?h('dialog',[slots.default?.(),slots.footer?.()]):null})}))
const flush=async()=>{for(let i=0;i<15;i++){await Promise.resolve();await nextTick()}}
const all=(node:TestNode):TestNode[]=>[node,...node.children.flatMap(all)]
const api=()=>({base:'/api/v1/pms/arrival-business',approvals:vi.fn().mockResolvedValue([]),approvalDefinitions:vi.fn().mockResolvedValue([{id:'approval:1',name:'业务审批',version:1,available:true,formFields:[]}]),approvalPreview:vi.fn().mockResolvedValue({nodes:[],fieldPermissions:{}}),startApproval:vi.fn().mockResolvedValue({id:'55'}),cancelApproval:vi.fn(),approval:vi.fn()})
beforeEach(()=>{vi.clearAllMocks();mocks.users.mockResolvedValue([])})
it('retries an uncertain start with the original version, fields and idempotency key',async()=>{
  const calls=api();calls.startApproval.mockRejectedValueOnce(new Error('connection lost'))
  const mounted=mount(Approvals,{api:calls,entityId:'90071992547409930',version:7});const state=(mounted.vm as any).$.setupState
  try{await flush();await state.openStart();state.definitionId='approval:1';await state.selectDefinition();state.form.value={comment:'original'};await state.submit();await flush()
    expect(calls.startApproval.mock.calls[0][0]).toBe('90071992547409930');expect(calls.startApproval.mock.calls[0][1].version).toBe(7)
    const done=vi.fn();state.closeStart(done);expect(done).not.toHaveBeenCalled();expect(textOf(mounted.root)).toContain('重试并确认发起结果')
    state.form.value={comment:'changed after timeout'};await state.submit()
    expect(calls.startApproval.mock.calls[1]).toEqual(calls.startApproval.mock.calls[0]);expect(state.starting).toBe(false)
  }finally{mounted.app.unmount()}
})
it('does not open the old entity workflow picker after the record changes',async()=>{
  let resolve!:(value:any)=>void;const calls=api(),id=ref('11')
  calls.approvalDefinitions.mockImplementationOnce(()=>new Promise(done=>{resolve=done}))
  const mounted=mount(defineComponent({setup:()=>()=>h(Approvals,{api:calls as any,entityId:id.value,version:7})}))
  try{await flush();const button=all(mounted.root).find(node=>node.type==='button' && textOf(node)==='发起审批')!
    const opening=(button.props!.onClick as Function)();await flush();id.value='12';await flush();resolve([{id:'old',available:true}]);await opening;await flush()
    expect(all(mounted.root).some(node=>node.type==='dialog')).toBe(false);expect(calls.startApproval).not.toHaveBeenCalled();expect(calls.approvals).toHaveBeenLastCalledWith('12')
  }finally{mounted.app.unmount()}
})
it('keeps readonly approval history accessible without workflow write actions',async()=>{
  const calls=api();const mounted=mount(Approvals,{api:calls,entityId:'11',version:7,readonly:true});const state=(mounted.vm as any).$.setupState
  try{await flush();await state.openStart();await state.submit();await state.cancel({id:'55',status:'PENDING'})
    expect(calls.approvals).toHaveBeenCalledWith('11');expect(calls.approvalDefinitions).not.toHaveBeenCalled();expect(calls.startApproval).not.toHaveBeenCalled();expect(calls.cancelApproval).not.toHaveBeenCalled()
  }finally{mounted.app.unmount()}
})
it('requires saved business content and refuses to silently switch the submission version',async()=>{
  const calls=api(),beforeStart=vi.fn().mockResolvedValueOnce(false).mockResolvedValue(true),version=ref(7)
  const mounted=mount(defineComponent({setup:()=>()=>h(Approvals,{api:calls as any,entityId:'11',version:version.value,beforeStart})}))
  try{await flush();let button=all(mounted.root).find(node=>node.type==='button'&&textOf(node)==='发起审批')!;await (button.props!.onClick as Function)();await flush();expect(calls.approvalDefinitions).not.toHaveBeenCalled()
    await (button.props!.onClick as Function)();await flush();version.value=8;await flush();expect(calls.startApproval).not.toHaveBeenCalled()
  }finally{mounted.app.unmount()}
})
