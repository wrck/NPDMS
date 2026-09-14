import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Panel from './TaskMaintenancePanel.vue'
const api = vi.hoisted(() => ({ getMaintenance: vi.fn(), changeRole: vi.fn(), saveDescription: vi.fn(), getMembers: vi.fn(), getRoleHistory: vi.fn() }))
const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn() }))
vi.mock('@/api/pms/project/task-maintenance', () => ({ ...api, TASK_ROLE_OPTIONS: [{ value: 'RESPONSIBLE', label: '负责人' }, { value: 'EXECUTOR', label: '执行人' }] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/components/Editor', () => ({ Editor: { render: () => null } }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i=0;i<8;i++) { await nextTick(); await Promise.resolve() } }
const render = async (showResponsibilities = false, showDescription = true) => {
  const child=ref<any>(), changed=vi.fn()
  const { app,root }=mount(defineComponent({ setup:()=>()=>h(Panel,{ projectId:8,taskId:'2098269306807468033',taskVersion:2,showResponsibilities,showDescription,ref:child,onChanged:changed }) }),{},
    { PmsEntitySelect:passthrough,ElDescriptions:passthrough,ElDescriptionsItem:passthrough,ElTable:passthrough,ElTableColumn:tableColumn,ElRadioGroup:passthrough,ElRadioButton:passthrough })
  apps.push(app); await flush(); return { state:child.value.$.setupState,exposed:child.value,changed,root }
}
beforeEach(()=>{
  vi.clearAllMocks()
  api.getMaintenance.mockResolvedValue({ taskId:'2098269306807468033',version:2,description:{ description:'原说明 <标签>',descriptionFormat:'PLAIN' },descriptionLimit:2000,
    currentRoles:[{ role:'RESPONSIBLE',userId:11,name:'负责人甲' },{ role:'EXECUTOR',userId:12,name:'执行人乙' }],canAssign:true,canEdit:true })
  api.changeRole.mockResolvedValue({ taskVersion:3 }); api.saveDescription.mockResolvedValue({ taskVersion:3 })
})
afterEach(()=>apps.splice(0).forEach(app=>app.unmount()))
const descriptionDialog = (node: TestNode): TestNode | undefined =>
  node.props?.title === '编辑任务说明' ? node : node.children.map(descriptionDialog).find(Boolean)
const closeDescription = (root: TestNode, done: () => void) => {
  const props = descriptionDialog(root)!.props!
  const handler = props['before-close'] || props.beforeClose
  expect(handler).toBeTypeOf('function')
  ;(handler as (done: () => void) => void)(done)
}

it('keeps unsaved description when its own dialog receives a backdrop or close request', async () => {
  const { state, root, exposed } = await render()
  state.openDescription()
  state.html = '<p>本次尚未保存的说明</p>'
  await flush()
  const done = vi.fn()
  closeDescription(root, done)
  expect(done).not.toHaveBeenCalled()
  expect(state.descriptionVisible).toBe(true)
  expect(state.html).toBe('<p>本次尚未保存的说明</p>')
  expect(exposed.requestLeave()).toBe(false)
  expect(api.saveDescription).not.toHaveBeenCalled()
})

it('allows normal close without changes and blocks close while a save is pending or failed', async () => {
  const { state, root } = await render()
  state.openDescription()
  await flush()
  const untouched = vi.fn()
  closeDescription(root, untouched)
  expect(untouched).toHaveBeenCalledOnce()
  state.html = '<p>等待保存</p>'
  let rejectSave!: (error: Error) => void
  api.saveDescription.mockReturnValueOnce(new Promise((_, reject) => { rejectSave = reject }))
  const saving = state.saveText()
  const done = vi.fn()
  closeDescription(root, done)
  expect(done).not.toHaveBeenCalled()
  rejectSave(new Error('version conflict'))
  await saving
  closeDescription(root, done)
  expect(done).not.toHaveBeenCalled()
  expect(state.html).toBe('<p>等待保存</p>')
  expect(state.descriptionVisible).toBe(true)
})
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

it('shares the existing description editor without rendering a duplicate description section', async () => {
  const { exposed, state, root } = await render(false, false)
  expect(textOf(root)).not.toContain('原说明 <标签>')
  expect(exposed.description.description).toBe('原说明 <标签>')
  expect(exposed.canEditDescription).toBe(true)
  exposed.openDescription()
  expect(state.descriptionVisible).toBe(true)
  expect(exposed.requestLeave()).toBe(false)
  state.html = '<p>表格中的新说明</p>'
  await state.saveText()
  expect(api.saveDescription).toHaveBeenCalledWith('2098269306807468033', 2, state.html, expect.any(String))
  expect(exposed.requestLeave()).toBe(true)
})

it('does not expose an edit action when description permission is denied', async () => {
  api.getMaintenance.mockResolvedValueOnce({ version: 2, description: { description: '只读说明', descriptionFormat: 'PLAIN' }, currentRoles: [], canEdit: false })
  const { exposed, state } = await render(false, false)
  expect(exposed.description.description).toBe('只读说明')
  expect(exposed.canEditDescription).toBe(false)
  exposed.openDescription()
  expect(state.descriptionVisible).toBe(false)
  expect(api.saveDescription).not.toHaveBeenCalled()
})
