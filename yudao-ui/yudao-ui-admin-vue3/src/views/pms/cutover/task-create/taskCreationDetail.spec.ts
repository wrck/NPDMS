import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TaskCreate from './index.vue'
import ProjectTasks from '../project-tasks/index.vue'
import TaskDevices from '../components/CutoverTaskDevices.vue'
import { findByTestId, mount, textOf, type TestNode } from '../cutoverTestHarness'

const selectionApi = vi.hoisted(() => ({ searchProjectOptions: vi.fn(), getProjectContext: vi.fn(), getProjectDevicePage: vi.fn() }))
const api = vi.hoisted(() => ({ resolveCreationDevices: vi.fn(), getCreationOptions: vi.fn(), createCutoverTask: vi.fn(), getCutoverTaskPage: vi.fn(), getCutoverTaskDetail: vi.fn(), saveCutoverAssessment: vi.fn() }))
const feedback = vi.hoisted(() => ({ confirm: vi.fn(), warning: vi.fn(), success: vi.fn(), error: vi.fn() }))
const operation = vi.hoisted(() => ({ execute: vi.fn(), enabled: false }))
const assets = vi.hoisted(() => ({ getDevice: vi.fn() }))
vi.mock('@/api/pms/cutover', () => api)
vi.mock('@/api/pms/project/selection', () => selectionApi)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => feedback }))
vi.mock('@/components/BusinessView/operationHost', () => ({ useOperationClient: () => ref(operation.enabled ? { execute: operation.execute } : undefined) }))
vi.mock('@/api/pms/asset/device', () => assets)
vi.mock('../task-detail/index.vue', () => ({ default: defineComponent({ props: { taskId: [String, Number] }, setup: props => () => h('div', { 'data-testid': 'created-task-detail' }, props.taskId) }) }))
const dialog = defineComponent({ props: { modelValue: Boolean }, setup: (props, { slots }) => () => props.modelValue ? h('aside', [slots.default?.(), slots.footer?.()]) : null })
const validate = vi.fn(async () => true)
const formControl = defineComponent({ setup(_, { slots, expose }) { expose({ validate }); return () => h('section', slots.default?.()) } })
const controls = { ElDialog: dialog, ElForm: formControl }
const project = (id = '101') => ({ project: { projectId: id, projectCode: `P-${id}`, projectName: `项目${id}`, customerName: '真实客户',
  projectManagerName: '项目经理甲', serviceManagerNames: '服务经理乙', industryName: '金融', departmentName: '华东办事处', salesNames: '销售丙' }, customerServiceLevel: '一级' })
const device = (id = '401') => ({ deviceId: id, serialNumber: `SN-${id}`, productCode: `CODE-${id}`, productModel: `MODEL-${id}` })
const resolution = (projectId = '101', ids = ['401']) => ({ context: { candidates: [{ project: { ...project(projectId).project, projectVersion: '3', projectScopeVersion: '7' },
  devices: ids.map(id => ({ ...device(id), projectAssignmentVersion: '5' })), customerServiceLevel: { status: 'NOT_APPLICABLE', serviceLevelRevisionId: null, serviceLevelCode: null, factVersion: null, effectiveFrom: null, effectiveTo: null },
  implementationReadiness: { snapshotId: null, snapshotVersion: null, decision: 'NOT_APPLICABLE', deviceIds: [] }, createAllowed: true }],
  selectionRequired: false, configurationChoices: [], configurationSelectionRequired: false }, devices: ids.map(id => device(id)) })
const flush = async () => { for (let index = 0; index < 12; index++) await nextTick() }
const collect = (node: TestNode, predicate: (candidate: TestNode) => boolean): TestNode[] => [
  ...(predicate(node) ? [node] : []), ...node.children.flatMap(child => collect(child, predicate))
]
const update = async (root: TestNode, id: string, value: unknown) => { await (findByTestId(root, id)!.props!['onUpdate:modelValue'] as Function)(value); await flush() }
const click = async (root: TestNode, id: string) => { await (findByTestId(root, id)!.props!.onClick as Function)(); await flush() }
const selectedCount = (root: TestNode) => findByTestId(root, 'create-device-table')!.children.filter(node => node.type === 'tr').length
const fillTask = async (_root: TestNode) => { await flush() }

afterEach(() => vi.unstubAllGlobals())
beforeEach(() => {
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  validate.mockReset().mockResolvedValue(true)
  Object.values(api).forEach(value => value.mockReset()); Object.values(selectionApi).forEach(value => value.mockReset()); Object.values(feedback).forEach(value => value.mockReset())
  operation.enabled = false; operation.execute.mockReset(); assets.getDevice.mockReset()
  feedback.confirm.mockResolvedValue(true)
  selectionApi.searchProjectOptions.mockResolvedValue([{ id: '101', code: 'P-101', name: '项目101', customerName: '真实客户' }])
  selectionApi.getProjectContext.mockImplementation(async id => project(id))
  selectionApi.getProjectDevicePage.mockResolvedValue({ list: [device(), device('402')], total: 2 })
  api.resolveCreationDevices.mockImplementation(async serials => resolution('101', serials.map((serial: string) => serial.replace('SN-', ''))))
  api.getCreationOptions.mockResolvedValue([{ revisionId: '41', code: 'STANDARD', name: '标准配置', revisionNo: 1,
    cutoverTypes: [{ value: 'CONFIGURATION_CHANGE', label: '配置变更' }], networkModes: [], deviceTypes: [] }])
  api.createCutoverTask.mockResolvedValue({ taskId: '701' })
  api.getCutoverTaskDetail.mockResolvedValue({ task: { id: '701', version: '2' }, assessment: { rowVersion: '1' }, allowedActions: ['SAVE_ASSESSMENT'] })
  api.saveCutoverAssessment.mockResolvedValue({})
  api.getCutoverTaskPage.mockResolvedValue({ list: [], total: 0 })
})

describe('详情式新建割接任务', () => {
  it('creates with only project and devices, without the removed task-information fields', async () => {
    const view = mount(TaskCreate, {}, controls); await flush()
    for (const id of ['create-configuration', 'create-cutover-type', 'create-network-mode', 'create-scheduled-time', 'create-background']) {
      expect(findByTestId(view.root, id)).toBeUndefined()
    }
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials')
    await click(view.root, 'submit-create-detail')
    expect(api.createCutoverTask).toHaveBeenCalledTimes(1)
    const data = api.createCutoverTask.mock.calls[0][0].data
    for (const field of ['configurationCode', 'background', 'cutoverType', 'networkMode', 'scheduledTime']) expect(data).not.toHaveProperty(field)
    view.app.unmount()
  })
  it('stops before creating when more than one published configuration is available', async () => {
    const single = await api.getCreationOptions()
    api.getCreationOptions.mockResolvedValue([...single, { ...single[0], revisionId: '42', code: 'SECOND' }])
    const view = mount(TaskCreate, {}, controls); await flush()
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials')
    await click(view.root, 'submit-create-detail')
    expect(api.createCutoverTask).not.toHaveBeenCalled()
    expect(textOf(view.root)).toContain('存在多个有效已发布割接配置')
    view.app.unmount()
  })

  it('starts in grade confirmation with an editable questionnaire and prevents entering later stages before saving', async () => {
    const view = mount(TaskCreate, {}, controls); await flush()
    const steps = collect(view.root, node => node.type === 'nav' && node.props?.['aria-label'] === '割接办理流程')[0]
    const buttons = collect(steps, node => node.type === 'button')
    expect(buttons).toHaveLength(5)
    expect(textOf(buttons[0])).toContain('割接等级确认')
    expect(textOf(view.root)).not.toContain('割接准备')
    expect(textOf(view.root)).toContain('割接等级评估问卷')
    expect(buttons[0].props!['aria-current']).toBe('step')
    expect(buttons.every(button => button.props!.disabled !== undefined && button.props!.disabled !== false)).toBe(true)
    expect(buttons.slice(1).every(button => !button.props!['aria-current'])).toBe(true)
    view.app.unmount()
  })

  it('saves the entered questionnaire and retries its exact command without creating another task', async () => {
    api.saveCutoverAssessment.mockRejectedValueOnce(new Error('network interrupted'))
    const created = vi.fn(); const view = mount(TaskCreate, { onCreated: created }, controls); await flush()
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials'); await fillTask(view.root)
    const answers = collect(view.root, node => node.props?.['aria-label'] === '业务重要等级')[0]
    await (answers.props!['onUpdate:modelValue'] as Function)('HIGH'); await flush()
    await click(view.root, 'submit-create-detail')
    expect(created).not.toHaveBeenCalled()
    expect(api.saveCutoverAssessment).toHaveBeenCalledWith(expect.objectContaining({ taskId: '701', taskVersion: '2', assessmentVersion: '1', data: expect.objectContaining({ answers: expect.objectContaining({ businessImportanceLevel: 'HIGH' }) }) }))
    const command = api.saveCutoverAssessment.mock.calls[0][0]
    await click(view.root, 'submit-create-detail')
    expect(api.createCutoverTask).toHaveBeenCalledTimes(1)
    expect(api.saveCutoverAssessment.mock.calls[1][0]).toEqual(command)
    expect(created).toHaveBeenCalledWith('701'); view.app.unmount()
  })

  it('does not save the questionnaire when server actions deny it', async () => {
    api.getCutoverTaskDetail.mockResolvedValue({ task: { id: '701', version: '2' }, allowedActions: [] })
    const created = vi.fn(); const view = mount(TaskCreate, { onCreated: created }, controls); await flush()
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials'); await fillTask(view.root)
    const answers = collect(view.root, node => node.props?.['aria-label'] === '备件是否申请')[0]
    await (answers.props!['onUpdate:modelValue'] as Function)(false); await flush()
    await click(view.root, 'submit-create-detail')
    expect(api.saveCutoverAssessment).not.toHaveBeenCalled(); expect(created).not.toHaveBeenCalled()
    expect(api.createCutoverTask).toHaveBeenCalledTimes(1); view.app.unmount()
  })

  it('does not submit when form validation rejects saving', async () => {
    const view = mount(TaskCreate, {}, controls); await flush()
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials'); await fillTask(view.root)
    validate.mockRejectedValueOnce(new Error('invalid'))
    await click(view.root, 'submit-create-detail')
    expect(validate).toHaveBeenCalled(); expect(api.createCutoverTask).not.toHaveBeenCalled()
    view.app.unmount()
  })

  it('preserves unsaved input when leaving is cancelled and closes after confirmation', async () => {
    const close = vi.fn(); const view = mount(TaskCreate, { onClose: close }, controls); await flush()
    await update(view.root, 'create-serials', '保留未保存的内容')
    feedback.confirm.mockRejectedValueOnce(new Error('cancel'))
    await click(view.root, 'close-cutover-create')
    expect(close).not.toHaveBeenCalled()
    expect(findByTestId(view.root, 'create-serials')!.props!.modelValue).toBe('保留未保存的内容')
    await click(view.root, 'close-cutover-create')
    expect(close).toHaveBeenCalledTimes(1); view.app.unmount()
  })

  it('does not show or submit a client-generated task name', async () => {
    const view = mount(TaskCreate, {}, controls); await flush()
    expect(findByTestId(view.root, 'create-task-name')).toBeUndefined()
    await update(view.root, 'create-project-select', '101')
    await click(view.root, 'choose-project-devices'); await click(view.root, 'add-project-device-401')
    await fillTask(view.root); await click(view.root, 'submit-create-detail')
    expect(api.createCutoverTask.mock.calls[0][0].data).not.toHaveProperty('taskName')
    view.app.unmount()
  })

  it('fills project fields and adds, deduplicates, removes shipped project devices before creating', async () => {
    const created = vi.fn(); const view = mount(TaskCreate, { onCreated: created }, controls); await flush()
    await update(view.root, 'create-project-select', '101')
    expect(textOf(view.root)).toContain('真实客户'); expect(textOf(view.root)).toContain('服务经理乙')
    await click(view.root, 'choose-project-devices'); expect(selectionApi.getProjectDevicePage).toHaveBeenCalledWith('101', { keyword: '', pageNo: 1, pageSize: 20 })
    await click(view.root, 'add-project-device-401'); await click(view.root, 'add-project-device-401'); await click(view.root, 'add-project-device-402')
    expect(selectedCount(view.root)).toBe(2)
    await click(view.root, 'remove-create-device-402'); await fillTask(view.root); await click(view.root, 'submit-create-detail')
    expect(api.createCutoverTask).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ projectId: '101', serialNumbers: ['SN-401'] }) }))
    expect(created).toHaveBeenCalledWith('701'); view.app.unmount()
  })

  it('automatically selects the single project found by serial and fills the device table', async () => {
    const view = mount(TaskCreate, {}, controls); await flush(); await update(view.root, 'create-serials', 'SN-401')
    await click(view.root, 'resolve-create-serials')
    expect(findByTestId(view.root, 'create-project-select')!.props!['model-value']).toBe('101')
    expect(textOf(view.root)).toContain('CODE-401'); expect(textOf(view.root)).toContain('MODEL-401')
    expect(textOf(view.root)).toContain('华东办事处'); view.app.unmount()
  })

  it('requires explicit project selection for ambiguous serials and adds only that project devices', async () => {
    const one = resolution(); const two = resolution('102', ['402'])
    api.resolveCreationDevices.mockResolvedValue({ context: { ...one.context, candidates: [...one.context.candidates, ...two.context.candidates], selectionRequired: true }, devices: [...one.devices, ...two.devices] })
    const view = mount(TaskCreate, {}, controls); await flush(); await update(view.root, 'create-serials', 'SN-401,SN-402'); await click(view.root, 'resolve-create-serials')
    expect(selectionApi.getProjectContext).not.toHaveBeenCalled()
    await update(view.root, 'create-project-candidates', '102'); await click(view.root, 'confirm-create-project')
    expect(findByTestId(view.root, 'create-project-select')!.props!['model-value']).toBe('102')
    expect(textOf(findByTestId(view.root, 'create-device-table')!)).toContain('SN-402')
    expect(textOf(findByTestId(view.root, 'create-device-table')!)).not.toContain('SN-401'); view.app.unmount()
  })

  it('rejects serials from another project without dropping the existing selected devices', async () => {
    const view = mount(TaskCreate, {}, controls); await flush(); await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials')
    api.resolveCreationDevices.mockResolvedValue(resolution('102', ['402']))
    await update(view.root, 'create-serials', 'SN-402'); await click(view.root, 'resolve-create-serials')
    expect(feedback.warning).toHaveBeenCalled(); expect(textOf(findByTestId(view.root, 'create-device-table')!)).toContain('SN-401')
    expect(textOf(findByTestId(view.root, 'create-device-table')!)).not.toContain('SN-402'); view.app.unmount()
  })

  it('keeps the project and devices when the user cancels switching, and clears devices on confirmation', async () => {
    const view = mount(TaskCreate, {}, controls); await flush(); await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials')
    feedback.confirm.mockRejectedValueOnce(new Error('cancel')); await update(view.root, 'create-project-select', '102')
    expect(findByTestId(view.root, 'create-project-select')!.props!['model-value']).toBe('101')
    expect(selectedCount(view.root)).toBe(1)
    await update(view.root, 'create-project-select', '102')
    expect(selectedCount(view.root)).toBe(0); view.app.unmount()
  })

  it('ignores a slow project response after a newer selection', async () => {
    let finish!: (value: unknown) => void
    selectionApi.getProjectContext.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const view = mount(TaskCreate, {}, controls); await flush()
    const first = (findByTestId(view.root, 'create-project-select')!.props!['onUpdate:modelValue'] as Function)('101')
    await update(view.root, 'create-project-select', '102'); finish(project('101')); await first; await flush()
    expect(findByTestId(view.root, 'create-project-select')!.props!['model-value']).toBe('102')
    expect(selectionApi.getProjectContext).toHaveBeenLastCalledWith('102'); view.app.unmount()
  })

  it('locks the pending form and retries the exact request and key after an unknown create result', async () => {
    api.createCutoverTask.mockRejectedValueOnce(new Error('network interrupted'))
    const view = mount(TaskCreate, {}, controls); await flush(); await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials')
    await fillTask(view.root); await click(view.root, 'submit-create-detail')
    const first = api.createCutoverTask.mock.calls[0][0]
    expect(findByTestId(view.root, 'create-project-select')!.props!.disabled).toBe(true)
    expect(findByTestId(view.root, 'close-cutover-create')!.props!.disabled).toBe(true)
    await click(view.root, 'close-cutover-create')
    expect(feedback.warning).toHaveBeenCalledWith('创建结果尚待确认，请重试原请求确认后再离开。')
    await click(view.root, 'submit-create-detail'); expect(api.createCutoverTask.mock.calls[1][0]).toEqual(first)
    expect(api.resolveCreationDevices).toHaveBeenCalledTimes(2); view.app.unmount()
  })

  it('keeps embedded project creation within its operation client and then opens the created detail', async () => {
    operation.enabled = true; operation.execute.mockResolvedValue({ objectId: '701' })
    const view = mount(ProjectTasks, { projectId: '101', allowedActions: ['CREATE'] }, controls); await flush()
    await click(view.root, 'create-project-cutover'); await flush()
    expect(findByTestId(view.root, 'create-project-select')!.props!.disabled).toBe(true)
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials'); await fillTask(view.root); await click(view.root, 'submit-create-detail')
    expect(operation.execute).toHaveBeenCalledWith(expect.objectContaining({ operationCode: 'CUT.CUTOVER_TASK.CREATE', input: expect.objectContaining({ serialNumbers: ['SN-401'] }) }))
    expect(api.createCutoverTask).not.toHaveBeenCalled(); expect(textOf(findByTestId(view.root, 'created-task-detail')!)).toBe('701'); view.app.unmount()
  })

  it('creates through the approval projection and rejects a mismatched result identity', async () => {
    operation.enabled = true
    operation.execute.mockImplementation(async request => {
      const result = { ownerContext: 'CUT', objectType: 'CUTOVER_APPROVAL', objectId: '701', response: { taskId: '701' } }
      expect(() => request.validateResult({ ...result, objectType: 'CUTOVER_TASK' })).toThrow()
      request.validateResult(result)
      return result
    })
    const view = mount(ProjectTasks, { projectId: '101', allowedActions: ['CREATE'], operationObjectType: 'CUTOVER_APPROVAL' }, controls); await flush()
    await click(view.root, 'create-project-cutover')
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials'); await fillTask(view.root); await click(view.root, 'submit-create-detail')
    expect(operation.execute).toHaveBeenCalledWith(expect.objectContaining({ operationCode: 'CUT.CUTOVER_APPROVAL.CREATE', input: { serialNumbers: ['SN-401'] } }))
    expect(api.createCutoverTask).not.toHaveBeenCalled()
    expect(textOf(findByTestId(view.root, 'created-task-detail')!)).toBe('701'); view.app.unmount()
  })

  it('retains the embedded pending request when the host hides CREATE during recovery', async () => {
    operation.enabled = true
    const actions = ref(['CREATE'])
    operation.execute.mockImplementationOnce(async () => { actions.value = []; throw new Error('network interrupted') })
    operation.execute.mockResolvedValueOnce({ objectId: '701' })
    const host = defineComponent({ setup: () => () => h(ProjectTasks, { projectId: '101', allowedActions: actions.value }) })
    const view = mount(host, {}, controls); await flush()
    await click(view.root, 'create-project-cutover')
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials'); await fillTask(view.root)
    await click(view.root, 'submit-create-detail')
    expect(findByTestId(view.root, 'submit-create-detail')!.props!.disabled).toBe(false)
    const first = operation.execute.mock.calls[0][0]
    await click(view.root, 'submit-create-detail')
    expect(operation.execute.mock.calls[1][0].key).toBe(first.key)
    expect(operation.execute.mock.calls[1][0].input).toEqual(first.input)
    expect(textOf(findByTestId(view.root, 'created-task-detail')!)).toBe('701'); view.app.unmount()
  })

  it('blocks new creation when the host withdraws CREATE before submission', async () => {
    operation.enabled = true
    const actions = ref(['CREATE'])
    const host = defineComponent({ setup: () => () => h(ProjectTasks, { projectId: '101', allowedActions: actions.value }) })
    const view = mount(host, {}, controls); await flush()
    await click(view.root, 'create-project-cutover')
    await update(view.root, 'create-serials', 'SN-401'); await click(view.root, 'resolve-create-serials'); await fillTask(view.root)
    actions.value = []; await flush()
    expect(findByTestId(view.root, 'submit-create-detail')!.props!.disabled).toBe(true)
    await click(view.root, 'submit-create-detail')
    expect(operation.execute).not.toHaveBeenCalled(); view.app.unmount()
  })

  it('shows frozen serials in a readonly device table even if one current product lookup fails', async () => {
    assets.getDevice.mockResolvedValueOnce({ summary: { productCode: 'CODE-401', productModel: 'MODEL-401' } }).mockRejectedValueOnce(new Error('unavailable'))
    const view = mount(TaskDevices, { detail: { task: { id: '701' }, devices: [device(), device('402')] } }, controls); await flush()
    expect(textOf(view.root)).toContain('SN-401'); expect(textOf(view.root)).toContain('SN-402'); expect(textOf(view.root)).toContain('CODE-401')
    expect(textOf(view.root)).toContain('部分产品信息暂时无法读取'); expect(findByTestId(view.root, 'remove-create-device-401')).toBeUndefined(); view.app.unmount()
  })
})
