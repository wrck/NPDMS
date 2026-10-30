import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Configuration from './with-device-logs.vue'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ users: vi.fn(), page: vi.fn(), create: vi.fn(), update: vi.fn(), get: vi.fn(), saveAttachments: vi.fn() }))
vi.mock('../attachment/NativeAttachments.vue', () => ({ default: defineComponent({ setup(_, { attrs, expose }) {
  expose({ savePending: api.saveAttachments }); return () => h('native-attachments', attrs)
} }) }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 3, nickname: 'Self' } }) }))
vi.mock('@/utils/permission', () => ({ checkPermi: (permissions: string[]) => !permissions.includes('system:user:query') }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: api.users }))
vi.mock('@/api/pms/engineering/configuration', () => ({ getConfigurationPage: api.page, createConfiguration: api.create, updateConfiguration: api.update, getConfiguration: api.get }))
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
vi.mock('@/components/UserTag/index.vue', () => ({ default: defineComponent({ props: ['userId'], setup: props => () => h('user-tag', { userId: props.userId }) }) }))

const apps: { unmount: () => void }[] = []
const form = defineComponent({ setup(_, { slots, expose }) { expose({ validate: async () => true }); return () => h('form', slots.default?.()) } })
const picker = defineComponent({ inheritAttrs: false, props: ['api', 'modelValue', 'placeholder'], emits: ['update:modelValue'], setup(props, { emit }) {
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
beforeEach(() => {
  vi.resetAllMocks(); api.page.mockResolvedValue({ list: [], total: 0 }); api.users.mockResolvedValue([{ id: 3, nickname: 'Self' }])
  api.create.mockResolvedValue(91); api.get.mockImplementation(async id => ({ id, projectId: 1, status: 0, version: 4 }))
  api.saveAttachments.mockResolvedValue(true)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('saves stage-entry attachments after creating the Owner and retries without a second creation', async () => {
  const view = render(); view.state.openForm(); await flush()
  Object.assign(view.state.form, { equipmentId: '123' })
  api.saveAttachments.mockResolvedValueOnce(false)
  await view.state.save()
  expect(view.state.formVisible).toBe(true)
  expect(view.state.form.id).toBe(91)
  await view.state.save()
  expect(api.create).toHaveBeenCalledTimes(1)
  expect(api.saveAttachments).toHaveBeenLastCalledWith(91)
  expect(view.state.formVisible).toBe(false)
})

it('queries the authorized user API despite a missing menu permission snapshot and saves the selected Self user', async () => {
  const view = render(); view.state.openForm(); await flush()
  const select = debuggerPicker(view.root)
  expect(select).toBeDefined()
  const params = { pageNo: 1, pageSize: 20, nickname: 'Self' }
  await expect((select.props!.api as Function)(params)).resolves.toEqual({ list: [{ id: 3, nickname: 'Self' }], total: 1 })
  expect(api.users).toHaveBeenCalledWith()
  expect(select.props!.modelValue).toBe(3)
  Object.assign(view.state.form, { equipmentId: '123', code: 'CFG-USERS' })
  await view.state.save()
  expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ debuggerUserId: 3, projectId: 1 }))
})

it('contains an actual user query rejection, displays its error, and recovers after retry', async () => {
  const view = render(); view.state.openForm(); await flush()
  const select = debuggerPicker(view.root)
  expect(select).toBeDefined()
  api.users.mockRejectedValueOnce(new Error('Forbidden'))
  await expect((select.props!.api as Function)({ pageNo: 1, pageSize: 20 })).resolves.toEqual({ list: [{ id: 3, nickname: 'Self' }], total: 1 })
  await flush()
  expect(text(view.root)).toContain('其他人员暂时无法查询，可选择本人或保留已选人员；查询其他人员请重试或确认权限。')
  await expect((select.props!.api as Function)({ pageNo: 1, pageSize: 20 })).resolves.toEqual({ list: [{ id: 3, nickname: 'Self' }], total: 1 })
  await flush()
  expect(text(view.root)).not.toContain('其他人员暂时无法查询，可选择本人或保留已选人员；查询其他人员请重试或确认权限。')
})

it('keeps completed history read-only without querying personnel', async () => {
  const view = render(); view.state.openForm({ id: 8, projectId: 1, status: 2, version: 3, debuggerUserId: 3 }); await flush()
  expect(view.state.readOnly).toBe(true)
  expect(debuggerPicker(view.root)).toBeUndefined()
  expect(nodes(view.root, 'user-tag')[0].props?.userId).toBe(3)
  expect(api.users).not.toHaveBeenCalled()
  await view.state.save()
  expect(api.update).not.toHaveBeenCalled()
})


it('saves the known Self user after a 403 without inventing any other user options', async () => {
  const view = render(); view.state.openForm(); await flush()
  api.users.mockRejectedValueOnce(new Error('403'))
  const select = debuggerPicker(view.root)
  const result = await (select.props!.api as Function)({ pageNo: 1, pageSize: 20 })
  expect(result).toEqual({ list: [{ id: 3, nickname: 'Self' }], total: 1 })
  ;(select.props!.select as Function)(result.list[0].id)
  Object.assign(view.state.form, { equipmentId: '123', code: 'CFG-SELF' })
  await view.state.save()
  expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ debuggerUserId: 3 }))
})

it('retains an existing non-Self debugger after personnel query failure', async () => {
  const view = render(); view.state.openForm({ id: 8, projectId: 1, status: 0, version: 3, debuggerUserId: 9 }); await flush()
  api.users.mockRejectedValueOnce(new Error('403'))
  const result = await (debuggerPicker(view.root).props!.api as Function)({ pageNo: 1, pageSize: 20 })
  expect(result.list.map((user: { id: number }) => user.id)).toEqual([3, 9])
  expect(view.state.form.debuggerUserId).toBe(9)
  await view.state.save()
  expect(api.update).toHaveBeenCalledWith(expect.objectContaining({ debuggerUserId: 9 }))
})

it('filters and pages the simple user list for the personnel picker', async () => {
  api.users.mockResolvedValue([{ id: 3, nickname: 'Self' }, { id: 9, nickname: 'Other' }])
  const view = render(); view.state.openForm(); await flush()
  const query = debuggerPicker(view.root).props!.api as Function
  await expect(query({ pageNo: 1, pageSize: 1, nickname: 'o' })).resolves.toEqual({ list: [{ id: 9, nickname: 'Other' }], total: 1 })
  await expect(query({ pageNo: 2, pageSize: 1 })).resolves.toEqual({ list: [{ id: 9, nickname: 'Other' }], total: 2 })
  await expect(query({ pageNo: 1, pageSize: 20, nickname: 'missing' })).resolves.toEqual({ list: [], total: 0 })
})

it('preserves an empty historical debugger and resets a new form to Self', async () => {
  const view = render()
  view.state.openForm({ id: 8, projectId: 1, status: 0, version: 3 }); await flush()
  expect(view.state.form.debuggerUserId).toBeUndefined()
  view.state.openForm(); await flush()
  expect(view.state.form.debuggerUserId).toBe(3)
})
