import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Workbench from './index.vue'

const api = vi.hoisted(() => ({
  page: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), read: vi.fn(),
  definition: vi.fn(), submissionReadiness: vi.fn(), start: vi.fn(), scripts: vi.fn(),
  customerFiles: vi.fn(), error: vi.fn()
}))
vi.mock('@/api/pms/engineering/solution', () => ({
  getSolutionPage: api.page, getSolution: api.get, createSolution: api.create,
  updateSolution: api.update
}))
vi.mock('@/api/pms/engineering/solution-review', () => ({
  policy: vi.fn(async () => ({ configured: true, reviewLevel: 0 })),
  read: api.read, definition: api.definition, submissionReadiness: api.submissionReadiness,
  start: api.start
}))
vi.mock('@/api/pms/project/projects', () => ({
  getProject: vi.fn(async () => ({ projectCode: 'TEST_ONLY', projectName: '提交测试' }))
}))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: api.error }) }))
vi.mock('@/views/pms/project/project-master-detail/components/projectApprovalDetail', () => ({
  projectApprovalDetailKey: Symbol(), useProjectApprovalDetail: () => vi.fn()
}))
vi.mock('./saveSolutionScripts', () => ({ saveSolutionScripts: api.scripts }))
vi.mock('./saveCustomerSolutionDocuments', () => ({ saveCustomerSolutionDocuments: api.customerFiles }))
vi.mock('./CustomerSolutionDocumentPicker.vue', () => ({ default: defineComponent({ setup: () => () => h('div') }) }))
vi.mock('./SolutionDocMetaForm.vue', () => ({ default: defineComponent({ setup: () => () => h('div') }) }))
vi.mock('./SolutionReviewChapterForm.vue', () => ({ default: defineComponent({ setup: () => () => h('div') }) }))

const apps: { unmount: () => void }[] = []
const draft = () => ({ id: 12, projectId: 123, name: 'TEST_ONLY方案', status: 0, version: 5, reviewLevel: 0, background: '已保存' })
let stored: any
beforeEach(() => {
  vi.resetAllMocks()
  stored = draft()
  api.page.mockImplementation(async () => ({ list: [structuredClone(stored)], total: 1 }))
  api.get.mockImplementation(async () => structuredClone(stored))
  api.update.mockImplementation(async row => { stored = { ...row, version: row.version + 1 } })
  api.create.mockImplementation(async row => { stored = { ...row, id: 13, version: 0 }; return 13 })
  api.read.mockResolvedValue(null)
  api.definition.mockResolvedValue({ id: 'definition-1', nodes: [] })
  api.submissionReadiness.mockResolvedValue({ allowed: true, blockers: [] })
  api.start.mockResolvedValue({ status: 'RUNNING', processInstanceId: 'test-process' })
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

const render = async () => {
  const mounted = mount(Workbench, { projectId: 123 }, { ElSelect: passthrough, ElOption: passthrough })
  apps.push(mounted.app)
  const state = (mounted.vm as any).$.setupState
  await vi.waitFor(() => expect(state.form.version).toBe(5))
  return { state, dialog: () => state.tieredReview.$.setupState }
}

it('打开和关闭提交准备窗口不保存草稿或附件，保留未保存正文', async () => {
  const { state, dialog } = await render()
  state.form.background = '未保存的修改'
  state.form.remark = JSON.stringify({ hasCustomerPlan: 'yes' })
  state.pendingCustomerFiles = [new File(['customer'], 'customer.pdf')]
  state.pendingScripts = [{ section: 'implementation', file: new File(['script'], 'test.txt') }]
  await state.submitReview()
  await vi.waitFor(() => expect(dialog().loading).toBe(false))
  expect(dialog().visible).toBe(true)
  dialog().visible = false
  expect(api.update).not.toHaveBeenCalled()
  expect(api.create).not.toHaveBeenCalled()
  expect(api.scripts).not.toHaveBeenCalled()
  expect(api.customerFiles).not.toHaveBeenCalled()
  expect(api.start).not.toHaveBeenCalled()
  expect(stored.version).toBe(5)
  expect(state.form.background).toBe('未保存的修改')
})

it('准备条件不足仍不保存，确认也不会启动审批', async () => {
  api.submissionReadiness.mockResolvedValue({ allowed: false, blockers: ['现场勘察'] })
  const { state, dialog } = await render()
  await state.submitReview()
  await vi.waitFor(() => expect(dialog().loading).toBe(false))
  await dialog().submit()
  expect(api.update).not.toHaveBeenCalled()
  expect(api.start).not.toHaveBeenCalled()
})

it('准备请求失败不保存，保留正文并在窗口显示失败原因', async () => {
  api.submissionReadiness.mockRejectedValue(new Error('准备信息暂不可用'))
  const { state, dialog } = await render()
  state.form.background = '未保存的修改'
  await state.submitReview()
  expect(dialog().error).toBe('准备信息暂不可用')
  await dialog().submit()
  expect(api.update).not.toHaveBeenCalled()
  expect(api.start).not.toHaveBeenCalled()
  expect(state.form.background).toBe('未保存的修改')
})

it('确认时保存未保存正文，以保存后的版本启动审批', async () => {
  const { state, dialog } = await render()
  state.form.background = '本次确认提交的修改'
  await state.submitReview()
  await vi.waitFor(() => expect(dialog().loading).toBe(false))
  expect(api.update).not.toHaveBeenCalled()
  await dialog().submit()
  expect(stored.background).toBe('本次确认提交的修改')
  expect(api.update).toHaveBeenCalledTimes(1)
  expect(api.start).toHaveBeenCalledWith({ projectId: 123, solutionId: 12, expectedVersion: 6, processDefinitionId: 'definition-1' })
})

it('未保存的新方案准备时不查空ID，最终确认才创建记录', async () => {
  const { state, dialog } = await render()
  await state.openForm()
  await state.submitReview()
  await vi.waitFor(() => expect(dialog().loading).toBe(false))
  expect(api.read).not.toHaveBeenCalled()
  expect(api.create).not.toHaveBeenCalled()
  await dialog().submit()
  expect(api.create).toHaveBeenCalledTimes(1)
  expect(api.start).toHaveBeenCalledWith({ projectId: 123, solutionId: 13, expectedVersion: 0, processDefinitionId: 'definition-1' })
})

it('保存失败不启动审批，修正后重试使用保存后的版本', async () => {
  const { state, dialog } = await render()
  await state.submitReview()
  await vi.waitFor(() => expect(dialog().loading).toBe(false))
  api.update.mockRejectedValueOnce(new Error('保存失败'))
  await dialog().submit()
  expect(api.start).not.toHaveBeenCalled()
  expect(dialog().visible).toBe(true)
  await dialog().submit()
  expect(api.start).toHaveBeenCalledWith(expect.objectContaining({ expectedVersion: 6 }))
})

it('启动审批已成功但首响应丢失时，重试同一版本且不再保存运行中的方案', async () => {
  const { state, dialog } = await render()
  api.start.mockImplementationOnce(async () => {
    stored.status = 2
    throw new Error('Network Error')
  }).mockResolvedValueOnce({ status: 'RUNNING', processInstanceId: 'existing-process' })
  await state.submitReview()
  await dialog().submit()
  expect(api.update).toHaveBeenCalledTimes(1)
  expect(stored.status).toBe(2)
  api.update.mockRejectedValue(new Error('审批中的方案不可修改'))
  await dialog().submit()
  expect(api.update).toHaveBeenCalledTimes(1)
  expect(api.start).toHaveBeenCalledTimes(2)
  expect(api.start.mock.calls[0][0]).toEqual(api.start.mock.calls[1][0])
  expect(dialog().record.processInstanceId).toBe('existing-process')
})

it('附件保存失败不启动审批，保留待保存附件及已更新的草稿版本', async () => {
  const { state, dialog } = await render()
  state.pendingScripts = [{ section: 'implementation', file: new File(['script'], 'test.txt') }]
  api.scripts.mockRejectedValueOnce(new Error('脚本登记失败'))
    .mockResolvedValueOnce([{ section: 'implementation', fileVersionId: 'test-file' }])
  await state.submitReview()
  await vi.waitFor(() => expect(dialog().loading).toBe(false))
  expect(api.scripts).not.toHaveBeenCalled()
  await dialog().submit()
  expect(api.start).not.toHaveBeenCalled()
  expect(state.form.version).toBe(6)
  expect(state.pendingScripts).toHaveLength(1)
  await dialog().submit()
  expect(state.pendingScripts).toEqual([])
  expect(api.start).toHaveBeenCalledWith(expect.objectContaining({ expectedVersion: 8 }))
})

it('客户文件绑定失败后重试沿用已保存草稿，完成绑定后的版本才提交', async () => {
  const { state, dialog } = await render()
  state.form.remark = JSON.stringify({ hasCustomerPlan: 'yes' })
  const customerFile = new File(['customer'], 'customer.pdf')
  state.pendingCustomerFiles = [customerFile]
  api.customerFiles.mockRejectedValueOnce(new Error('客户文件绑定失败'))
    .mockResolvedValueOnce({ attached: { version: 7, customerPlanUrl: '/test/customer.pdf' } })
  await state.submitReview()
  expect(api.customerFiles).not.toHaveBeenCalled()
  await dialog().submit()
  expect(api.start).not.toHaveBeenCalled()
  expect(state.form.version).toBe(6)
  expect(state.pendingCustomerFiles).toEqual([customerFile])
  await dialog().submit()
  expect(api.update).toHaveBeenCalledTimes(1)
  expect(state.pendingCustomerFiles).toEqual([])
  expect(api.start).toHaveBeenCalledWith(expect.objectContaining({ expectedVersion: 7 }))
})

it('先前方案的迟到审批查询不会覆盖当前窗口或打开旧流程', async () => {
  const { state, dialog } = await render()
  let finishRead!: (record: unknown) => void
  api.read.mockImplementationOnce(() => new Promise(resolve => { finishRead = resolve }))
  const preparing = state.submitReview()
  await vi.waitFor(() => expect(api.read).toHaveBeenCalledTimes(1))
  await state.tieredReview.open({ ...draft(), id: 14, name: '另一个方案' })
  finishRead({ status: 'RUNNING', processInstanceId: 'old-process' })
  await preparing
  expect(dialog().solution.id).toBe(14)
  expect(dialog().record).toBeNull()
  expect(dialog().visible).toBe(true)
  expect(api.update).not.toHaveBeenCalled()
  expect(api.start).not.toHaveBeenCalled()
})

it('准备后切换方案不会把新方案通过旧窗口保存或提交', async () => {
  const { state, dialog } = await render()
  await state.submitReview()
  await vi.waitFor(() => expect(dialog().loading).toBe(false))
  await state.openForm()
  await dialog().submit()
  expect(api.create).not.toHaveBeenCalled()
  expect(api.update).not.toHaveBeenCalled()
  expect(api.start).not.toHaveBeenCalled()
})

it('确认保存期间重复点击只保存一次，关闭窗口后不启动审批', async () => {
  const { state, dialog } = await render()
  await state.submitReview()
  await vi.waitFor(() => expect(dialog().loading).toBe(false))
  let finish!: () => void
  api.update.mockImplementationOnce(row => new Promise<void>(resolve => {
    stored = { ...row, version: row.version + 1 }
    finish = resolve
  }))
  const submitting = dialog().submit()
  await vi.waitFor(() => expect(api.update).toHaveBeenCalledTimes(1))
  await dialog().submit()
  expect(api.update).toHaveBeenCalledTimes(1)
  dialog().visible = false
  finish()
  await submitting
  expect(api.start).not.toHaveBeenCalled()
})
