import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Configuration from './with-device-logs.vue'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ users: vi.fn(), simple: vi.fn(), members: vi.fn(), resolve: vi.fn(), options: vi.fn(), page: vi.fn(), create: vi.fn(), update: vi.fn() }))
vi.mock('@/api/pms/platform/user-selection', () => ({ searchPmsUserOptions: api.options, pmsUserSelectionSource: (key: string) => ({ key, resolve: api.resolve }) }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 3, nickname: 'Self' } }) }))
vi.mock('@/utils/permission', () => ({ checkPermi: (permissions: string[]) => !permissions.includes('system:user:query') }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: api.users, getSimpleUser: api.simple }))
vi.mock('@/api/pms/project/unified-members', () => ({ getMemberPage: api.members }))
vi.mock('@/api/pms/engineering/configuration', () => ({ getConfigurationPage: api.page, createConfiguration: api.create, updateConfiguration: api.update }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/device-log-archive', () => ({ download: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: { download: vi.fn() } }))
vi.mock('@/utils/download', () => ({ default: { file: vi.fn() } }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ENG_STATUS: 'configuration' }, getIntDictOptions: () => [] }))
vi.mock('@/utils/formatTime', () => ({ dateFormatter: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn() }) }))
vi.mock('@/components/DeviceCollection/CollectionWithDeviceLogsDialog.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/DeviceCollection/ProjectDeviceLogArchiveDialog.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/DeviceCollection/BusinessCollectionLogs.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/ProjectDeviceSelect/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/DeviceTag/index.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/UserTag/index.vue', () => ({ default: defineComponent({ props: { userId: [Number, String] }, setup: props => () => h('user-tag', { userId: props.userId }) }) }))
vi.mock('@/views/system/user/components/UserSelect.vue', () => ({ default: defineComponent({
  props: { scopedSource: { type: Object, required: true }, modelValue: [Number, String], placeholder: String }, emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () => h('entity-picker', {
      modelValue: props.modelValue, placeholder: props.placeholder,
      api: (params: any) => props.scopedSource.search({ keyword: params.nickname || '', pageNo: params.pageNo, pageSize: params.pageSize }),
      select: (id: number) => emit('update:modelValue', id)
    })
  }
}) }))
const apps: { unmount: () => void }[] = []
const form = defineComponent({ setup(_, { slots, expose }) { expose({ validate: async () => true }); return () => h('form', slots.default?.()) } })
const picker = defineComponent({ inheritAttrs: false, props: { api: Function, modelValue: [Number, String], placeholder: String }, emits: ['update:modelValue'], setup(props, { emit }) {
  return () => h('entity-picker', { api: props.api, modelValue: props.modelValue, placeholder: props.placeholder, select: (id: number) => emit('update:modelValue', id) })
} })
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const text = (node: TestNode): string => `${node.text || ''}${node.children.map(text).join('')}`
const flush = async () => { for (let i = 0; i < 6; i++) await nextTick() }
const render = () => {
  const view = mount(Configuration, { projectId: 1 }, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: form, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElDatePicker: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: picker, Editor: passthrough, UploadFile: passthrough })
  apps.push(view.app)
  return { ...view, state: (view.vm as any).$.setupState }
}
const debuggerPicker = (root: TestNode) => nodes(root, 'entity-picker').find(node => node.props?.placeholder === '请选择调试人员')!
beforeEach(() => { vi.resetAllMocks(); api.page.mockResolvedValue({ list: [], total: 0 }); api.options.mockResolvedValue({ list: [{ id: 3, nickname: 'Self' }], total: 1 }); api.simple.mockResolvedValue({ nickname: 'Self' }); api.members.mockResolvedValue({ list: [], total: 0 }) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('selects only current project members, deduplicates roles and searches across pages', async () => {
  api.members.mockImplementation(async (_projectId: number, params: { pageNo: number }) => params.pageNo === 1
    ? { list: [{ userId: 3, memberName: 'Self', employeeNo: 's003' }, { userId: null, memberName: '外部人员' }, ...Array.from({ length: 98 }, () => ({ userId: 3, memberName: 'Self' }))], total: 101 }
    : { list: [{ userId: '2112914651700375582', memberName: 'k06057-康志伟', employeeNo: 'k06057', email: 'kang@example.test' }], total: 101 })
  const view = render(); view.state.openForm(); await flush()
  const select = debuggerPicker(view.root)
  expect(select.props!.modelValue).toBe(3)
  const result = await (select.props!.api as Function)({ pageNo: 1, pageSize: 20, nickname: 'k06057' })
  expect(result.list).toEqual([expect.objectContaining({ id: '2112914651700375582', nickname: 'k06057-康志伟' })])
  expect(result.total).toBe(1)
  expect(api.members).toHaveBeenCalledWith(1, { state: 'CURRENT', pageNo: 2, pageSize: 100 })
  expect(api.options).not.toHaveBeenCalled()
  ;(select.props!.select as Function)('2112914651700375582')
  Object.assign(view.state.form, { equipmentId: '123', code: 'CFG-USERS' })
  await view.state.save()
  expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ debuggerUserId: '2112914651700375582', projectId: 1 }))
})

it('defaults a new record to self without adding self to the project candidate range', async () => {
  api.members.mockResolvedValue({ list: [{ userId: 9, memberName: '项目成员' }], total: 1 })
  const view = render(); view.state.openForm(); await flush()
  const select = debuggerPicker(view.root)
  expect(select.props!.modelValue).toBe(3)
  const result = await (select.props!.api as Function)({ pageNo: 1, pageSize: 20 })
  expect(result.list.map((user: { id: number }) => user.id)).toEqual([9])
  expect(api.users).not.toHaveBeenCalled()
  expect(api.options).not.toHaveBeenCalled()
})

it('preserves an existing record with no debugger instead of replacing it with self', async () => {
  const view = render(); view.state.openForm({ id: 8, projectId: 1, status: 0, version: 3 }); await flush()
  expect(view.state.form.debuggerUserId).toBeUndefined()
})

it('fails closed when the project member query fails and preserves historical selection', async () => {
  const view = render(); view.state.openForm({ id: 8, projectId: 1, status: 0, version: 3, debuggerUserId: 9 }); await flush()
  api.members.mockRejectedValueOnce(new Error('Forbidden'))
  await expect((debuggerPicker(view.root).props!.api as Function)({ pageNo: 1, pageSize: 20 })).rejects.toThrow('Forbidden')
  expect(view.state.form.debuggerUserId).toBe(9)
  expect(api.options).not.toHaveBeenCalled()
})

it('clears the device and resets the debugger to self when the project changes', async () => {
  const view = render(); view.state.openForm(); await flush()
  Object.assign(view.state.form, { debuggerUserId: 9, equipmentId: 8 })
  view.state.onFormProjectChange()
  expect(view.state.form.debuggerUserId).toBe(3)
  expect(view.state.form.equipmentId).toBeUndefined()
})

it('keeps completed history read-only and displays the stored person', async () => {
  const view = render(); view.state.openForm({ id: 8, projectId: 1, status: 2, version: 3, debuggerUserId: 3 }); await flush()
  expect(view.state.readOnly).toBe(true)
  expect(debuggerPicker(view.root)).toBeUndefined()
  expect(text(view.root)).toContain('Self')
  await view.state.save()
  expect(api.update).not.toHaveBeenCalled()
})

it('shows the account and name for a readable selected person without altering history', async () => {
  api.resolve.mockResolvedValue([{ id: 3, username: 's003', nickname: 'Self' }])
  const view = render(); view.state.openForm({ id: 8, projectId: 1, status: 2, version: 3, debuggerUserId: 3 }); await flush()
  expect(text(view.root)).toContain('s003-Self')
  expect(api.simple).not.toHaveBeenCalled()
  expect(api.update).not.toHaveBeenCalled()
})

it('shows the exact historical project member when the system user lookup returns no data', async () => {
  api.simple.mockResolvedValue(null)
  api.members.mockImplementation(async (_projectId: number, params: { state: string }) => params.state === 'HISTORY'
    ? { list: [{ userId: '2112914651700375582', memberName: 'k06057-康志伟' }], total: 1 }
    : { list: [], total: 0 })
  const view = render(); view.state.openForm({ id: 8, projectId: 1, status: 2, version: 3, debuggerUserId: '2112914651700375582' }); await flush()
  expect(text(view.root)).toContain('k06057-康志伟')
  expect(text(view.root)).not.toContain('2112914651700375582')
})
