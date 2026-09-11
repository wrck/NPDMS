import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount, passthrough, tableColumn, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Panel from './TaskMaintenancePanel.vue'
const api = vi.hoisted(() => ({ getMaintenance: vi.fn(), changeRole: vi.fn(), saveDescription: vi.fn(), getMembers: vi.fn(), getRoleHistory: vi.fn() }))
const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn() }))
vi.mock('@/api/pms/project/task-maintenance', () => ({ ...api, TASK_ROLE_OPTIONS: [{ value: 'RESPONSIBLE', label: '负责人' }, { value: 'EXECUTOR', label: '执行人' }] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/components/Editor', () => ({ Editor: { render: () => null } }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i=0;i<8;i++) { await nextTick(); await Promise.resolve() } }
const render = async (showResponsibilities = false) => {
  const child=ref<any>(), changed=vi.fn()
  const { app,root }=mount(defineComponent({ setup:()=>()=>h(Panel,{ projectId:8,taskId:'2098269306807468033',taskVersion:2,showResponsibilities,ref:child,onChanged:changed }) }),{},
    { PmsEntitySelect:passthrough,ElDescriptions:passthrough,ElDescriptionsItem:passthrough,ElTable:passthrough,ElTableColumn:tableColumn,ElRadioGroup:passthrough,ElRadioButton:passthrough })
  apps.push(app); await flush(); return { state:child.value.$.setupState,changed,root }
}
beforeEach(()=>{
  vi.clearAllMocks()
  api.getMaintenance.mockResolvedValue({ taskId:'2098269306807468033',version:2,description:{ description:'原说明 <标签>',descriptionFormat:'PLAIN' },descriptionLimit:2000,
    currentRoles:[{ role:'RESPONSIBLE',userId:11,name:'负责人甲' },{ role:'EXECUTOR',userId:12,name:'执行人乙' }],canAssign:true,canEdit:true })
  api.changeRole.mockResolvedValue({ taskVersion:3 }); api.saveDescription.mockResolvedValue({ taskVersion:3 })
})
afterEach(()=>apps.splice(0).forEach(app=>app.unmount()))
it('keeps responsible and executor commands distinct and retries one intent with the same key',async()=>{
  const { state,changed }=await render(true); state.openRole('RESPONSIBLE'); state.userId=15; state.reason='调整协调职责'
  api.changeRole.mockRejectedValueOnce(new Error('uncertain'))
  await state.saveRole(); const first=api.changeRole.mock.calls[0]
  expect(first[0]).toBe('2098269306807468033'); expect(first[2].role).toBe('RESPONSIBLE'); expect(changed).not.toHaveBeenCalled()
  await state.saveRole(); expect(api.changeRole.mock.calls[1]).toEqual(first)
  state.openRole('EXECUTOR'); state.userId=15; state.reason='调整执行职责'; await state.saveRole()
  expect(api.changeRole.mock.calls[2][2].role).toBe('EXECUTOR')
})
it('hides responsibilities by default while retaining their data and the description editor',async()=>{
  const {state,root}=await render()
  expect(textOf(root)).not.toContain('任务职责'); expect(textOf(root)).not.toContain('负责人甲')
  expect(textOf(root)).toContain('任务说明'); expect(state.view.currentRoles).toHaveLength(2)
  expect(api.changeRole).not.toHaveBeenCalled()
})
it('escapes existing plain text before editing and saves rich text with the current version',async()=>{
  const { state }=await render(); state.openDescription()
  expect(state.html).toBe('<p>原说明 &lt;标签&gt;</p>')
  state.html='<p><strong>格式说明</strong></p>'; await state.saveText()
  expect(api.saveDescription).toHaveBeenCalledWith('2098269306807468033',2,'<p><strong>格式说明</strong></p>',expect.any(String))
})
it('uses the server description limit instead of the old 500-character textarea limit',async()=>{
  const { state }=await render(); state.openDescription(); state.html='x'.repeat(600); await state.saveText()
  expect(api.saveDescription).toHaveBeenCalledTimes(1)
  state.html='x'.repeat(2001); await state.saveText(); expect(api.saveDescription).toHaveBeenCalledTimes(1); expect(message.warning).toHaveBeenCalled()
})
