// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Training from './index.vue'
import { mount, textOf, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ users: vi.fn(), options: vi.fn(), page: vi.fn(), create: vi.fn(), update: vi.fn(), templates: vi.fn(), canQueryTemplates: false, admin: false, canManageTemplates: true, getTraining: vi.fn(), preview: vi.fn() }))
vi.mock('@/api/pms/platform/user-selection', () => ({ searchPmsUserOptions: api.options, pmsUserSelectionSource: (key: string) => ({ key, resolve: vi.fn() }) }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 3, nickname: 'Self' } }) }))
vi.mock('@/utils/permission', () => ({ checkRole: () => api.admin, checkPermi: (permissions: string[]) => !permissions.includes('system:user:query') && (!permissions.includes('pms:dynamic-form-instance:query') || api.canQueryTemplates) && (!permissions.includes('pms:dynamic-form-template:query') || api.canManageTemplates) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ getRoutes: () => [] }) }))
vi.mock('@/api/system/user', () => ({ getUserPage: vi.fn(() => { throw new Error('Administrative user API must not be used') }), getSimpleUserList: api.users }))
vi.mock('@/api/pms/engineering/training', () => ({ getTrainingPage: api.page, createTraining: api.create, updateTraining: api.update, getTraining: api.getTraining }))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn(async () => ({ projectName: 'Project' })), getProjectPage: vi.fn() }))
vi.mock('@/api/pms/customer/contacts', () => ({ getProjectPage: vi.fn(async () => ({ list: [], total: 0 })) }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({ getTemplateSelection: api.templates }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [] }))
vi.mock('@/utils/formatTime', () => ({ formatDate: vi.fn() }))
vi.mock('@/utils/auth', () => ({ getTenantId: () => 1 }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn() }) }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./TrainingPrintTemplates.vue', () => ({ default: { render: () => null } }))
vi.mock('./TrainingPrintPreview.vue', () => ({ default: { setup(_: unknown, { expose }: any) { expose({ open: api.preview }); return () => null } } }))
vi.mock('@/components/Qrcode', () => ({ Qrcode: { render: () => null } }))
const formStub = defineComponent({ setup(_, { slots, expose }) { expose({ validate: async () => true }); return () => h('form', slots.default?.()) } })
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
const render = () => {
  const view = mount(Training, { projectId: 1 }, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElDatePicker: passthrough, ElSelect: passthrough, ElOption: passthrough, ElCheckbox: passthrough, ElCheckboxGroup: passthrough, ElDrawer: passthrough, PmsEntitySelect: passthrough, Editor: passthrough })
  apps.push(view.app)
  return { ...view, state: (view.vm as any).$.setupState }
}
beforeEach(() => {
  vi.resetAllMocks(); api.canQueryTemplates = false; api.admin = false; api.canManageTemplates = true
  api.page.mockResolvedValue({ list: [], total: 0 }); api.templates.mockResolvedValue({ list: [], total: 0 })
  api.options.mockRejectedValue(new Error('403'))
  vi.stubGlobal('useRoute', () => ({ query: {} })); vi.stubGlobal('useRouter', () => ({ getRoutes: () => [] }))
})
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.unstubAllGlobals() })
it('allows known Self training with built-in print layout and default confirmation when template querying is unavailable', async () => {
  const view = render(); view.state.openForm(); await flush()
  expect(await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 20 })).toEqual({ list: [{ id: 3, nickname: 'Self' }], total: 1 })
  expect(view.state.form.trainerUserId).toBe(3)
  expect(view.state.form.confirmationTemplateId).toBe(992209200001)
  expect(view.state.form.printTemplateId).toBeUndefined()
  expect(textOf(view.root)).not.toContain('未选择打印模板时使用内置培训记录版式，可继续办理。')
  expect(api.templates).not.toHaveBeenCalled()
  Object.assign(view.state.form, { name: 'Training', trainingTypeList: ['EXISTING'], trainingTime: '2026-10-19' })
  await view.state.save()
  expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ trainerUserId: 3, confirmationTemplateId: 992209200001, printTemplateId: undefined }))
})
it('retains existing personnel and both template selections after a failed query and clears the personnel hint on retry', async () => {
  const view = render(); view.state.openForm({ id: 8, status: 0, projectId: 1, trainerUserId: 9, trainerName: 'Existing', printTemplateId: 77, confirmationTemplateId: 88, trainingTypes: 'EXISTING' }); await flush()
  expect(await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 20 })).toEqual({ list: [{ id: 3, nickname: 'Self' }], total: 1 })
  expect(view.state.trainerUsersError).toContain('保留原培训工程师')
  expect(view.state.form).toMatchObject({ trainerUserId: 9, printTemplateId: 77, confirmationTemplateId: 88 })
  api.options.mockResolvedValueOnce({ list: [{ id: 3, nickname: 'Self' }], total: 1 })
  await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 20 })
  expect(view.state.trainerUsersError).toBe('')
  await view.state.save()
  expect(api.update).toHaveBeenCalledWith(expect.objectContaining({ trainerUserId: 9, printTemplateId: 77, confirmationTemplateId: 88 }))
})
it('keeps a departed trainer snapshot for display without adding that user to new candidates', async () => {
  const view = render()
  view.state.openForm({ id: 8, status: 0, projectId: 1, trainerUserId: '2112914651700375582', trainerName: '原培训姓名' })
  await flush()
  api.options.mockResolvedValueOnce({ list: [{ id: 8, nickname: '在职人员' }], total: 1 })
  const result = await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 20 })
  expect(result.list).toEqual([{ id: 8, nickname: '在职人员' }])
  expect(await view.state.trainerSource.resolve(['2112914651700375582'])).toEqual([
    { id: '2112914651700375582', nickname: '原培训姓名' }
  ])
  expect(view.state.selectedTrainer.nickname).toBe('原培训姓名')
  expect(api.update).not.toHaveBeenCalled()
})

it('retains the historical trainer on directory failure without exposing it as a candidate', async () => {
  const view = render()
  view.state.openForm({ id: 8, status: 0, projectId: 1, trainerUserId: 9, trainerName: '离职工程师' })
  await flush()
  const result = await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 20 })
  expect(result.list.some((user: { id: number }) => user.id === 9)).toBe(false)
  expect(await view.state.trainerSource.resolve(['9'])).toEqual([{ id: 9, nickname: '离职工程师' }])
  await view.state.save()
  expect(api.update).toHaveBeenCalledWith(expect.objectContaining({ trainerUserId: 9 }))
  expect(api.update.mock.calls.at(-1)![0]).not.toHaveProperty('trainerName')
})

it('prefers the recorded name and discards the previous historical label when another record opens', async () => {
  const view = render()
  view.state.openForm({ id: 8, status: 0, projectId: 1, trainerUserId: 3, trainerName: '当时姓名' })
  await flush()
  expect(await view.state.trainerSource.resolve(['3'])).toEqual([{ id: 3, nickname: '当时姓名' }])
  view.state.openForm({ id: 9, status: 0, projectId: 1, trainerUserId: 9, trainerName: '另一名历史人员' })
  await flush()
  expect(await view.state.trainerSource.resolve([9])).toEqual([{ id: 9, nickname: '另一名历史人员' }])
  expect(await view.state.trainerSource.resolve([3])).toEqual([])
  view.state.openForm(); await flush()
  expect(await view.state.trainerSource.resolve([3])).toEqual([{ id: 3, nickname: 'Self' }])
  expect(await view.state.trainerSource.resolve([9])).toEqual([])
})

it('shows the current trainer name even when the permitted query page does not contain that user', async () => {
  const view = render(); view.state.openForm(); await flush()
  api.options.mockResolvedValueOnce({ list: [{ id: 8, nickname: 'Other' }], total: 1 })
  const params = { pageNo: 1, pageSize: 50, nickname: '' }
  const result = await view.state.queryTrainerUsers(params)
  expect(result.list).toEqual([{ id: 8, nickname: 'Other' }])
  expect(await view.state.trainerSource.resolve([3])).toEqual([{ id: 3, nickname: 'Self' }])
  expect(api.options).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 50, keyword: '' })
  expect(view.state.form.trainerUserId).toBe(3)
  expect(view.state.trainerUsersError).toBe('')
})
it('retains the saved trainer name outside the query page and resets it when opening a new record', async () => {
  const view = render()
  view.state.openForm({ id: 8, status: 0, projectId: 1, trainerUserId: 9, trainerName: 'Existing' }); await flush()
  api.options.mockResolvedValueOnce({ list: [], total: 0 })
  expect((await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 50 })).list).toEqual([])
  expect(await view.state.trainerSource.resolve([9])).toEqual([{ id: 9, nickname: 'Existing' }])
  view.state.openForm(); await flush()
  api.options.mockResolvedValueOnce({ list: [], total: 0 })
  expect((await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 50 })).list).toEqual([])
  expect(await view.state.trainerSource.resolve([3])).toEqual([{ id: 3, nickname: 'Self' }])
})
it('keeps the name of a newly selected trainer without changing the submitted user ID', async () => {
  const view = render(); view.state.openForm(); await flush()
  view.state.form.trainerUserId = 8
  view.state.onTrainerChange({ id: 8, nickname: 'Chosen' })
  api.options.mockResolvedValueOnce({ list: [], total: 0 })
  expect((await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 50 })).list).toEqual([])
  expect(await view.state.trainerSource.resolve([8])).toEqual([{ id: 8, nickname: 'Chosen' }])
  await view.state.save()
  expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ trainerUserId: 8 }))
  view.state.form.trainerUserId = undefined
  view.state.onTrainerChange(undefined)
  api.options.mockResolvedValueOnce({ list: [], total: 0 })
  expect((await view.state.queryTrainerUsers({ pageNo: 1, pageSize: 50 })).list).toEqual([])
})
const hasTemplateField = (node: { props?: Record<string, unknown>; children: any[] }): boolean =>
  ['客户确认模板', '打印模板'].includes(String(node.props?.label)) || node.children.some(hasTemplateField)
it('hides template fields and configuration from ordinary users even with template query permission', async () => {
  api.canQueryTemplates = true
  const view = render(); view.state.openForm(); await flush()
  expect(hasTemplateField(view.root)).toBe(false)
  expect(textOf(view.root)).not.toContain('客户确认模板配置')
  expect(textOf(view.root)).not.toContain('打印模板配置')
  expect(view.state.form.confirmationTemplateId).toBe(992209200001)
})
it('keeps both configuration entries outside the form for authorized administrators', async () => {
  api.admin = true
  const view = render(); view.state.openForm(); await flush()
  expect(hasTemplateField(view.root)).toBe(false)
  expect(textOf(view.root)).toContain('客户确认模板配置')
  expect(textOf(view.root)).toContain('打印模板配置')
})
it('does not expose template configuration to administrators without the existing template query permission', async () => {
  api.admin = true; api.canManageTemplates = false
  const view = render(); await flush()
  expect(textOf(view.root)).not.toContain('客户确认模板配置')
  expect(textOf(view.root)).not.toContain('打印模板配置')
})

it('does not send customer signature or confirmation facts when editing a draft', async () => {
  const view=render();view.state.openForm({id:8,status:0,projectId:1,trainingTypes:'TECHNICAL_PRINCIPLE',
    signatureImageDataUrl:'data:image/png;base64,old',signConfirmerName:'历史客户',signConfirmTime:'2026-10-10',confirmationValues:'historical'})
  await flush();await view.state.save()
  const payload=api.update.mock.calls.at(-1)![0]
  expect(payload).not.toHaveProperty('signatureImageDataUrl')
  expect(payload).not.toHaveProperty('signConfirmerName')
  expect(payload).not.toHaveProperty('signConfirmTime')
  expect(payload).not.toHaveProperty('confirmationValues')
})

it('opens current template selection for a legacy record without a print snapshot', async () => {
  api.getTraining.mockResolvedValue({ id: 8, projectId: 1, status: 2 })
  const view = render(); await flush()
  await view.state.downloadRecord({ id: 8 }); await flush()
  expect(api.preview).toHaveBeenCalledWith({ id: 8, projectId: 1, status: 2 }, 'Project', undefined)
})

it('preserves the enabled directory search page without system user management permission', async () => {
  const view = render(); view.state.openForm(); await flush()
  api.options.mockResolvedValueOnce({ list: [{ id: 9, nickname: 'Engineer B' }], total: 2 })
  const result = await view.state.queryTrainerUsers({ pageNo: 2, pageSize: 1, nickname: ' engineer ' })
  expect(result).toEqual({ list: [{ id: 9, nickname: 'Engineer B' }], total: 2 })
  expect(view.state.trainerUsersError).toBe('')
  expect(api.options).toHaveBeenLastCalledWith({ pageNo: 2, pageSize: 1, keyword: ' engineer ' })
})

it('passes email search and server pagination through while preserving the selected trainer', async () => {
  api.users.mockResolvedValue([{ id: 8, nickname: '张三' }])
  api.options.mockResolvedValue({ list: [{ id: 8, nickname: '张三' }], total: 1 })
  const view = render(); view.state.openForm(); await flush()
  expect(await view.state.queryTrainerUsers({ pageNo: 2, pageSize: 1, nickname: 'zhangsan' })).toEqual({
    list: [{ id: 8, nickname: '张三' }], total: 1
  })
  expect(api.options).toHaveBeenCalledWith({ pageNo: 2, pageSize: 1, keyword: 'zhangsan' })
  expect(api.users).not.toHaveBeenCalled()
})
