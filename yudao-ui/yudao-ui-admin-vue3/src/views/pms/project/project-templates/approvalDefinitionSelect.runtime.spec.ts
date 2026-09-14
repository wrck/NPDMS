import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import Select from './ApprovalDefinitionSelect.vue'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ getProcessDefinitionPage: vi.fn(), getProcessDefinition: vi.fn() }))
const permissions = vi.hoisted(() => ({ hasPermission: vi.fn() }))
vi.mock('@/api/bpm/definition', () => api)
vi.mock('@/directives/permission/hasPermi', () => permissions)
vi.mock('@/utils/constants', () => ({ BpmModelFormType: { NORMAL: 10 } }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
const definition = (version = 1) => ({ id: `review:${version}`, key: 'review', name: '交付复核', version, suspensionState: 1, formType: 10 })
const render = async (readonly = false) => {
  const child = ref<any>(), choose = vi.fn()
  const props = reactive({ readonly, binding: { type: 'APPROVAL', approvalDefinitionKey: 'review', parameters: { processDefinitionId: 'review:1' } },
    bindingPermission: 'pms:project-plan:manage' })
  const view = mount(defineComponent({ setup: () => () => h(Select, { ...props, ref: child, onChoose: choose } as any) }), {},
    { ElSelect: passthrough, ElOption: passthrough, ElInput: passthrough })
  apps.push(view.app); await flush()
  return { ...view, choose, props, state: () => child.value.$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks(); permissions.hasPermission.mockReturnValue(true)
  api.getProcessDefinitionPage.mockResolvedValue({ list: [definition(2), definition(1)], total: 2 })
  api.getProcessDefinition.mockResolvedValue(definition(1))
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it('does not automatically replace an old pin and selects the exact version after owner revalidation', async () => {
  const view = await render()
  expect(view.choose).not.toHaveBeenCalled()
  expect(view.state().pinnedId).toBe('review:1')
  await view.state().choose('review:1')
  expect(api.getProcessDefinition).toHaveBeenCalledWith('review:1')
  expect(view.choose).toHaveBeenCalledWith({ id: 'review:1', key: 'review', name: '交付复核', version: 1 })
  expect(permissions.hasPermission).toHaveBeenCalledWith(['pms:project-plan:manage'])
})
it('keeps frozen identity visible in read-only mode without requiring a live catalog', async () => {
  const view = await render(true)
  expect(textOf(view.root)).toContain('review:1')
  expect(api.getProcessDefinitionPage).not.toHaveBeenCalled()
  await view.state().choose('review:1'); expect(view.choose).not.toHaveBeenCalled()
})
it.each([{ suspensionState: 2 }, { formType: 20 }, { id: 'review:2' }])('refuses an unavailable changed definition %j', async patch => {
  const view = await render()
  api.getProcessDefinition.mockResolvedValueOnce({ ...definition(), ...patch })
  await view.state().choose('review:1')
  expect(view.choose).not.toHaveBeenCalled()
  expect(view.state().pinnedId).toBe('review:1')
  expect(textOf(view.root)).toContain('当前绑定保持不变')
})
it('ignores late results after changing filters and preserves the pin on catalog failures', async () => {
  let finish!: (value: unknown) => void
  api.getProcessDefinitionPage.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const view = await render()
  view.state().queryKey = 'other'
  api.getProcessDefinitionPage.mockResolvedValueOnce({ list: [], total: 0 })
  await view.state().search()
  finish({ list: [definition()], total: 1 }); await flush()
  expect(view.state().rows).toEqual([])
  expect(api.getProcessDefinitionPage).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 20, key: 'other' })
  api.getProcessDefinitionPage.mockRejectedValueOnce(new Error('403'))
  await view.state().search()
  expect(textOf(view.root)).toContain('当前冻结配置未改变')
  expect(view.state().pinnedId).toBe('review:1')
})
it('does not publish a selection after unmount or permission loss', async () => {
  const view = await render()
  let finish!: (value: unknown) => void
  api.getProcessDefinition.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const pending = view.state().choose('review:1')
  view.props.readonly = true; await flush()
  finish(definition()); await pending
  expect(view.choose).not.toHaveBeenCalled()
  expect(view.props.binding.parameters.processDefinitionId).toBe('review:1')
})
