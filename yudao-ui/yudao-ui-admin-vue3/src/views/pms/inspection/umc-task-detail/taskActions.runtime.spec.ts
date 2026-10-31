import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Detail from './index.vue'
import PlatformDetail from '../task-detail/index.vue'
import {
  findByTestId,
  mount,
  passthrough,
  tableColumn,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({
  getTask: vi.fn(),
  savePreparation: vi.fn(),
  getIssue: vi.fn(),
  annotateResult: vi.fn(),
  downloadRedactedLog: vi.fn(),
  taskAction: vi.fn()
}))
const saveLogDownload = vi.hoisted(() => vi.fn())
vi.mock('@/utils/download', () => ({ default: { file: saveLogDownload } }))
const confirmLeave = vi.hoisted(() => vi.fn())
const askReason = vi.hoisted(() => vi.fn())
const umcApi = vi.hoisted(() => ({ action: vi.fn() }))
const permissions = vi.hoisted(() => ({ execute: true }))
const routeQuery = vi.hoisted(() => ({} as Record<string, string>))
const people = vi.hoisted(() => ({ search: vi.fn(), resolve: vi.fn() }))
vi.mock('@/api/pms/platform/user-selection', () => ({
  pmsUserSelectionSource: (key: string) => ({ key, search: people.search, resolve: people.resolve })
}))
vi.mock('@/api/pms/inspection/tasks', () => api)
vi.mock('@/api/pms/inspection/rules', () => ({}))
vi.mock('@/api/pms/inspection/umcWorkflow', () => umcApi)
vi.mock('@/api/pms/inspection/reportResults', () => ({}))
vi.mock('@/api/pms/platform/file/version-reference', () => ({}))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: vi.fn(async () => []) }))
vi.mock('@/utils/permission', () => ({
  checkPermi: (names: string[]) =>
    !names.includes('pms:inspection-task:execute') || permissions.execute
}))
vi.mock('@/utils', () => ({ generateUUID: () => 'task-action-test' }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '' }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: '9' } }) }))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    prompt: askReason,
    confirm: confirmLeave
  })
}))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { taskId: '10' }, query: routeQuery }),
  useRouter: () => ({ push: vi.fn() }),
  onBeforeRouteLeave: vi.fn(),
  onBeforeRouteUpdate: vi.fn()
}))
vi.mock('@/components/ProjectInformationCard/useProjectInformation', async () => {
  const { ref } = await import('vue')
  return {
    useProjectInformation: () => ({
      project: ref(),
      customerServiceLevel: ref(),
      loading: ref(false),
      error: ref(''),
      load: vi.fn()
    })
  }
})
vi.mock('@/components/ProjectInformationCard/index.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      setup:
        (_, { slots }) =>
        () =>
          h('section', slots.default?.())
    })
  }
})
vi.mock('../components/InspectionSteps.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/InspectionOnlinePanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/InspectionIssuePanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/InspectionReportPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/InspectionAttachmentPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('../components/InspectionExecutionPanel.vue', async () => {
  const { h, Teleport } = await import('vue')
  return {
    default: {
      inheritAttrs: false,
      props: { actionsTarget: {} },
      setup: (props: { actionsTarget?: HTMLElement }) => () =>
        h(
          Teleport,
          { to: props.actionsTarget || 'body', disabled: !props.actionsTarget },
          h('button', '执行阶段操作')
        )
    }
  }
})
vi.mock('../components/InspectionUmcReportPanel.vue', async () => {
  const { h, Teleport } = await import('vue')
  return {
    default: {
      inheritAttrs: false,
      props: { actionsTarget: {} },
      setup: (props: { actionsTarget?: HTMLElement }) => () =>
        h(
          Teleport,
          { to: props.actionsTarget || 'body', disabled: !props.actionsTarget },
          h('button', '报告阶段操作')
        )
    }
  }
})
vi.mock('../components/InspectionAnnotationPanel.vue', async () => {
  const { h, Teleport, defineComponent } = await import('vue')
  return {
    default: defineComponent({
      inheritAttrs: false,
      props: { actionsTarget: Object },
      setup:
        (props, { slots }) =>
        () =>
          h('section', [
            h(
              Teleport,
              { to: props.actionsTarget || 'body', disabled: !props.actionsTarget },
              h('button', '标注阶段操作')
            ),
            h('section', { 'data-testid': 'annotation-dialog-recovery' }, slots.recovery?.())
          ])
    })
  }
})

const task = () => ({
  id: '10',
  version: '1',
  taskCode: 'INS10',
  taskName: '巡检',
  projectId: '1',
  background: '背景',
  mode: 'OFFLINE',
  statusCode: 'EXECUTING',
  owner: true,
  manager: true,
  currentAttemptId: '20',
  currentReportId: '60',
  devices: [{ deviceId: '30', serialNumber: 'SN-30' }],
  attempts: [{ id: '20', providerCode: 'UMC', mode: 'OFFLINE', statusCode: 'ACTIVE' }],
  executions: [],
  selections: [],
  reports: [{ id: '60', statusCode: 'AVAILABLE' }],
  results: [],
  events: [],
  annotations: [],
  issues: [],
  ruleFeedback: [],
  collectionReceipts: []
})
const apps: Array<{ unmount: () => void }> = []
const flush = async () => {
  for (let i = 0; i < 12; i++) await nextTick()
}
const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
const render = async (component = Detail, productRow?: { productTypeName?: string }) => {
  const view = mount(
    component,
    {},
    {
      ElTableColumn: productRow
        ? defineComponent({
            props: { label: String },
            setup:
              (props, { slots }) =>
              () =>
                props.label === '产品类型'
                  ? h(
                      'span',
                      { 'data-testid': 'inspection-product-type' },
                      slots.default?.({ row: productRow })
                    )
                  : h('span')
          })
        : tableColumn,
      ...Object.fromEntries(
        [
          'ElInput',
          'ElForm',
          'ElFormItem',
          'ElSelect',
          'ElOption',
          'ElRadioGroup',
          'ElRadio',
          'ElTooltip',
          'ElDescriptions',
          'ElDescriptionsItem',
          'ElTable',
          'ElCollapse',
          'ElCollapseItem',
          'ElTimeline',
          'ElTimelineItem',
          'ElDrawer',
          'ElDialog',
          'ElDivider',
          'ElDatePicker',
          'ElCheckboxGroup',
          'ElCheckbox'
        ].map((name) => [name, passthrough])
      )
    }
  )
  apps.push(view.app)
  await flush()
  return {
    ...view,
    state: (view.vm as any).$.setupState,
    footer: () => findByTestId(view.root, 'inspection-task-actions')!
  }
}
beforeEach(() => {
  Object.keys(routeQuery).forEach((key) => delete routeQuery[key])
  vi.clearAllMocks()
  api.getTask.mockReset()
  api.taskAction.mockReset()
  api.annotateResult.mockReset()
  api.downloadRedactedLog.mockReset()
  askReason.mockReset()
  umcApi.action.mockReset()
  permissions.execute = true
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  api.getTask.mockResolvedValue(task())
  people.search.mockResolvedValue({ list: [], total: 0 })
  people.resolve.mockResolvedValue([])
})
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})

it.each([['UMC', Detail], ['platform', PlatformDetail]] as const)(
  '%s existing issue links open tracking without automatically opening the drawer',
  async (_name, component) => {
    routeQuery.issueId = '5'
    api.getTask.mockResolvedValue({ ...task(), statusCode: 'TRACKING' })
    const view = await render(component)
    expect(view.state.activeStep).toBe('issues')
    expect(view.state.selectedIssueId).toBe('')
    expect(api.taskAction).not.toHaveBeenCalled()
  }
)

it.each([['UMC', Detail], ['platform', PlatformDetail]] as const)(
  '%s tracking entry opens the issue list without selecting an individual issue or changing task status',
  async (_name, component) => {
    routeQuery.step = 'issues'
    api.getTask.mockResolvedValue({ ...task(), statusCode: 'ANNOTATION_PENDING' })
    const view = await render(component)
    expect(view.state.activeStep).toBe('issues')
    expect(view.state.selectedIssueId).toBe('')
    expect(view.state.task.statusCode).toBe('ANNOTATION_PENDING')
    expect(api.taskAction).not.toHaveBeenCalled()
  }
)

it('searches UMC assignees by email using sparse authorized options without issuing assignment writes', async () => {
  const view = await render()
  expect(view.state.assignmentSource).toBeDefined()
  expect(view.state.assignmentSource.key).toBe('umc-inspection-assignee:10')
  const picker = nodes(view.root).find(node => node.props?.placeholder === '选择负责人' && node.props?.onVisibleChange)!
  expect(picker).toBeTruthy()
  await (picker.props!.onVisibleChange as Function)(true)
  await flush()
  expect(people.search).toHaveBeenCalledWith({ keyword: '', pageNo: 1, pageSize: 20 })
  people.search.mockResolvedValueOnce({ list: [{ id: '2115387679367467008', nickname: '张三' }], total: 3 })
  ;(picker.props!['remote-method'] as Function)('zhangsan')
  await vi.waitFor(() => expect(people.search).toHaveBeenLastCalledWith({ keyword: 'zhangsan', pageNo: 1, pageSize: 20 }))
  await flush()
  expect(nodes(view.root).some(node => node.props?.label === '张三' && node.props?.value === '2115387679367467008')).toBe(true)
  people.search.mockResolvedValueOnce({ list: [{ id: '2115387679367467008', nickname: '张三' }], total: 3 })
  const params = { keyword: 'zhangsan', pageNo: 2, pageSize: 1 }
  expect(await view.state.assignmentSource.search(params)).toEqual({
    list: [{ id: '2115387679367467008', nickname: '张三' }], total: 3
  })
  expect(people.search).toHaveBeenCalledWith(params)
  expect(api.taskAction).not.toHaveBeenCalled()
  expect(umcApi.action).not.toHaveBeenCalled()
})

it('downloads a device-specific UTF-8 redacted text copy without posting or changing task state', async () => {
  const view = await render()
  const before = JSON.stringify(view.state.task)
  const blob = new Blob(['脱敏日志文本副本'], { type: 'text/plain;charset=UTF-8' })
  api.downloadRedactedLog.mockResolvedValueOnce(blob)
  await view.state.downloadLog({ id: '40', deviceId: '30' })
  expect(api.downloadRedactedLog).toHaveBeenCalledWith('10', '40')
  expect(saveLogDownload).toHaveBeenCalledWith(blob, 'inspection-10-40-SN-30-redacted.txt')
  expect(api.taskAction).not.toHaveBeenCalled()
  expect(umcApi.action).not.toHaveBeenCalled()
  expect(JSON.stringify(view.state.task)).toBe(before)
})

it('does not download a JSON or rejected response as a text log', async () => {
  const view = await render()
  api.downloadRedactedLog.mockResolvedValueOnce(new Blob(['{}'], { type: 'application/json' }))
  await view.state.downloadLog({ id: '40', deviceId: '30' })
  expect(api.downloadRedactedLog).toHaveBeenCalledTimes(1)
  expect(saveLogDownload).not.toHaveBeenCalled()
  api.downloadRedactedLog.mockRejectedValueOnce(new Error('unavailable'))
  await view.state.downloadLog({ id: '40', deviceId: '30' })
  expect(api.downloadRedactedLog).toHaveBeenCalledTimes(2)
  expect(saveLogDownload).not.toHaveBeenCalled()
})

it('keeps the requested device filename when page data changes while its download is pending', async () => {
  const view = await render()
  let finishDownload!: (blob: Blob) => void
  api.downloadRedactedLog.mockReturnValueOnce(
    new Promise<Blob>((resolve) => (finishDownload = resolve))
  )
  const pendingDownload = view.state.downloadLog({ id: '40', deviceId: '30' })
  view.state.task.devices = [{ deviceId: '30', serialNumber: 'CHANGED-PAGE-DEVICE' }]
  const blob = new Blob(['脱敏日志文本副本'], { type: 'text/plain;charset=UTF-8' })
  finishDownload(blob)
  await pendingDownload
  expect(saveLogDownload).toHaveBeenCalledWith(blob, 'inspection-10-40-SN-30-redacted.txt')
})

for (const [name, component] of [
  ['UMC', Detail],
  ['platform', PlatformDetail]
] as const) {
  it.each(['execute', 'report'])(
    `${name} exposes a restart/upload action from %s without editing completed history`,
    async (step) => {
      const previous = { ...task(), statusCode: 'REPORT_PENDING', currentReportId: undefined }
      previous.attempts[0].providerCode = name === 'platform' ? 'PLATFORM' : 'UMC'
      previous.attempts[0].statusCode = 'COMPLETED'
      const previousJson = JSON.stringify(previous)
      api.getTask
        .mockResolvedValueOnce(previous)
        .mockResolvedValueOnce({ ...task(), version: '2', currentAttemptId: '21' })
      const view = await render(component)
      view.state.activeStep = step
      await flush()
      const restart = findByTestId(view.root, 'inspection-restart-upload')!
      expect(restart).toBeDefined()
      expect(restart.props!.disabled).toBe(false)
      askReason.mockResolvedValueOnce({ value: '  更换本次日志  ' })
      await (restart.props!.onClick as Function)()
      if (name === 'UMC')
        expect(umcApi.action).toHaveBeenCalledWith({
          taskId: '10',
          action: 'start-offline',
          body: { explanation: '更换本次日志' },
          version: '1',
          key: 'task-action-test'
        })
      else
        expect(api.taskAction).toHaveBeenCalledWith(
          '10',
          'start-attempt',
          { reason: '更换本次日志' },
          '1',
          'task-action-test'
        )
      expect(view.state.activeStep).toBe('execute')
      expect(view.state.task.currentAttemptId).toBe('21')
      expect(JSON.stringify(previous)).toBe(previousJson)
    }
  )

  it(`${name} requires the existing restart reason and execute permission`, async () => {
    const current = { ...task(), statusCode: 'REPORT_PENDING' }
    current.attempts[0].providerCode = name === 'platform' ? 'PLATFORM' : 'UMC'
    api.getTask.mockResolvedValue(current)
    const view = await render(component)
    askReason.mockRejectedValueOnce(new Error('cancel'))
    await view.state.startAttempt()
    expect(api.taskAction).not.toHaveBeenCalled()
    expect(umcApi.action).not.toHaveBeenCalled()
    permissions.execute = false
    const denied = await render(component)
    expect(findByTestId(denied.root, 'inspection-restart-upload')!.props!.disabled).toBe(true)
    await denied.state.startAttempt()
    expect(askReason).toHaveBeenCalledTimes(1)
  })

  it(`${name} keeps file selection editable after a definite serial mismatch rejection`, async () => {
    const current = task()
    current.attempts[0].providerCode = name === 'platform' ? 'PLATFORM' : 'UMC'
    api.getTask.mockResolvedValue(current)
    const view = await render(component)
    view.state.drafts.execution = true
    const panelKey = view.state.executionPanelKey
    await view.state.perform({
      taskId: '10',
      label: '登记多设备日志',
      section: 'execution',
      run: async () => {
        throw {
          response: {
            status: 400,
            data: {
              msg: '日志设备序列号不能唯一对应本巡检任务设备',
              data: { reasonCode: 'INVALID_REQUEST' }
            }
          }
        }
      }
    })
    expect(view.state.pending).toBeUndefined()
    expect(view.state.fresh).toBe(true)
    expect(view.state.writeDisabled).toBe(false)
    expect(view.state.canExecute).toBe(true)
    expect(view.state.drafts.execution).toBe(true)
    expect(view.state.executionPanelKey).toBe(panelKey)
    expect(api.getTask).toHaveBeenCalledTimes(1)
    expect(view.state.writeError).toContain('日志设备序列号')
  })

  it(`${name} keeps the original upload request and draft locked when the outcome is unknown`, async () => {
    const current = task()
    current.attempts[0].providerCode = name === 'platform' ? 'PLATFORM' : 'UMC'
    api.getTask.mockResolvedValue(current)
    const view = await render(component)
    view.state.drafts.execution = true
    const run = vi.fn().mockRejectedValue(new Error('Network Error'))
    await view.state.perform({ taskId: '10', label: '登记多设备日志', section: 'execution', run })
    const original = view.state.pending
    expect(original).toBeDefined()
    expect(view.state.writeDisabled).toBe(true)
    expect(view.state.drafts.execution).toBe(true)
    await view.state.retryPending()
    expect(view.state.pending).toBe(original)
    expect(run).toHaveBeenCalledTimes(2)
    expect(api.getTask).toHaveBeenCalledTimes(1)
  })

  it.each(['ANNOTATION_PENDING', 'ARCHIVED'])(
    `${name} does not offer restart after formal confirmation or archive: %s`,
    async (statusCode) => {
      api.getTask.mockResolvedValue({ ...task(), statusCode })
      const view = await render(component)
      view.state.activeStep = 'execute'
      await flush()
      expect(findByTestId(view.root, 'inspection-restart-upload')).toBeUndefined()
    }
  )
}

it('keeps unfinished UMC logs behind the existing explicit stop-consumption gate', async () => {
  api.getTask.mockResolvedValue({
    ...task(),
    executions: [
      {
        id: '40',
        attemptId: '20',
        deviceId: '30',
        purposeCode: 'OFFLINE',
        statusCode: 'PENDING',
        artifactId: '300'
      }
    ]
  })
  const view = await render()
  expect(findByTestId(view.root, 'inspection-restart-upload')!.props!.disabled).toBe(true)
  await view.state.startAttempt()
  expect(askReason).not.toHaveBeenCalled()
  expect(umcApi.action).not.toHaveBeenCalled()
  expect(textOf(view.root)).toContain('停止采用')
})

it('keeps UMC history read-only in the original platform page', async () => {
  api.getTask.mockResolvedValue({ ...task(), statusCode: 'REPORT_PENDING' })
  const view = await render(PlatformDetail)
  expect(findByTestId(view.root, 'inspection-restart-upload')!.props!.disabled).toBe(true)
  await view.state.startAttempt()
  expect(askReason).not.toHaveBeenCalled()
  expect(api.taskAction).not.toHaveBeenCalled()
})

for (const [name, component] of [
  ['UMC', Detail],
  ['platform', PlatformDetail]
] as const) {
  const unresolvedView = async () => {
    const current = task()
    current.attempts[0].providerCode = name === 'platform' ? 'PLATFORM' : 'UMC'
    api.getTask.mockResolvedValue(current)
    const view = await render(component)
    const run = vi.fn()
    view.state.pending = { taskId: '10', label: '登记多设备日志', section: 'execution', run }
    view.state.drafts.execution = true
    view.state.writeError = '暂时无法确认操作结果，请重试原请求。'
    await flush()
    return { view, run, original: view.state.pending, current: view.state.task }
  }

  it(`${name} permits explicit refresh confirmation instead of trapping pending drafts`, async () => {
    const { view, run, original } = await unresolvedView()
    const refresh = nodes(view.root).find(
      (node) => node.type === 'button' && textOf(node) === '刷新'
    )!
    expect(refresh.props!.disabled).not.toBe(true)
    confirmLeave.mockRejectedValueOnce(new Error('cancel'))
    await view.state.reload()
    expect(confirmLeave).toHaveBeenCalledTimes(1)
    expect(view.state.pending).toBe(original)
    expect(view.state.drafts.execution).toBe(true)
    expect(api.getTask).toHaveBeenCalledTimes(1)
    expect(run).not.toHaveBeenCalled()
  })

  it(`${name} clears local recovery only after confirmed refresh reads fresh state successfully`, async () => {
    const { view, run } = await unresolvedView()
    api.getTask.mockResolvedValueOnce({ ...task(), version: '2', statusCode: 'REPORT_PENDING' })
    confirmLeave.mockResolvedValueOnce(undefined)
    await view.state.reload()
    expect(confirmLeave).toHaveBeenCalledTimes(1)
    expect(confirmLeave.mock.calls[0][0]).toMatch(/服务器|服务端/)
    expect(api.getTask).toHaveBeenCalledTimes(2)
    expect(view.state.pending).toBeUndefined()
    expect(view.state.drafts.execution).toBe(false)
    expect(view.state.task.version).toBe('2')
    expect(view.state.writeError).toBe('')
    expect(run).not.toHaveBeenCalled()
    expect(api.taskAction).not.toHaveBeenCalled()
    expect(umcApi.action).not.toHaveBeenCalled()
  })

  it(`${name} keeps the original request and files draft if confirmed refresh fails`, async () => {
    const { view, run, original, current } = await unresolvedView()
    const panelKey = view.state.executionPanelKey
    api.getTask.mockRejectedValueOnce(new Error('read unavailable'))
    confirmLeave.mockResolvedValueOnce(undefined)
    await view.state.reload()
    expect(api.getTask).toHaveBeenCalledTimes(2)
    expect(view.state.pending).toBe(original)
    expect(view.state.drafts.execution).toBe(true)
    expect(view.state.task).toBe(current)
    expect(view.state.executionPanelKey).toBe(panelKey)
    expect(run).not.toHaveBeenCalled()
  })

  it(`${name} confirms leaving unresolved local drafts without replaying a server request`, async () => {
    const { view, run, original } = await unresolvedView()
    confirmLeave.mockRejectedValueOnce(new Error('cancel'))
    expect(await view.state.mayLeave()).toBe(false)
    expect(view.state.pending).toBe(original)
    expect(view.state.drafts.execution).toBe(true)
    confirmLeave.mockResolvedValueOnce(undefined)
    expect(await view.state.mayLeave()).toBe(true)
    expect(confirmLeave).toHaveBeenCalledTimes(2)
    expect(confirmLeave.mock.calls[1][0]).toMatch(/服务器|服务端/)
    expect(run).not.toHaveBeenCalled()
    expect(api.getTask).toHaveBeenCalledTimes(1)
  })

  it(`${name} still blocks refresh and leaving while a request is actually in flight`, async () => {
    const { view, run, original } = await unresolvedView()
    view.state.busy = true
    await view.state.reload()
    expect(await view.state.mayLeave()).toBe(false)
    expect(confirmLeave).not.toHaveBeenCalled()
    expect(api.getTask).toHaveBeenCalledTimes(1)
    expect(view.state.pending).toBe(original)
    expect(run).not.toHaveBeenCalled()
  })
}

for (const [page, component] of [
  ['UMC', Detail],
  ['原巡检', PlatformDetail]
] as const) {
  it.each(['', '已知设备类型'])(
    `${page}产品类型缺失时明确提示并保留已有名称：%s`,
    async (productTypeName) => {
      const view = await render(component, { productTypeName })
      expect(textOf(findByTestId(view.root, 'inspection-product-type')!)).toBe(
        productTypeName || '未提供'
      )
    }
  )

  it(`${page}显示本次巡检客户名称快照并对空值明确留空`, async () => {
    api.getTask.mockResolvedValue({ ...task(), customerId: '0', customerName: '手填巡检客户' })
    const view = await render(component)
    const field = () => findByTestId(view.root, 'inspection-task-customer-name')!
    expect(field().props?.['model-value']).toBe('手填巡检客户')
    expect(field().props?.readonly).not.toBe(false)
    view.state.task.customerName = ''
    await flush()
    expect(field().props?.['model-value']).toBe('—')
  })

  it(`${page}办理历史显示动作中文名称并保留原事件说明`, async () => {
    api.getTask.mockResolvedValue({
      ...task(),
      taskName: '任务10离线巡检',
      events: [
        {
          id: '301',
          actionCode: 'ANNOTATE_RESULT',
          description: '保存问题标注版本1',
          actorName: '系统管理员',
          fromStatus: 'ANNOTATION_PENDING',
          toStatus: 'ANNOTATION_PENDING'
        }
      ]
    })
    const view = await render(component)
    const title = nodes(view.root).find((node) => node.type === 'strong')!
    expect(textOf(title)).toBe('保存问题标注')
    expect(textOf(view.root)).toContain('保存问题标注版本1')
    expect(textOf(view.root)).toContain('系统管理员')
    expect(textOf(view.root)).not.toContain('ANNOTATE_RESULT')
  })

  it(`${page}办理历史未知动作保留原说明且不伪造动作名称`, async () => {
    api.getTask.mockResolvedValue({
      ...task(),
      taskName: '  ',
      events: [{ id: '302', actionCode: 'LEGACY_UNKNOWN', description: '原有历史说明' }]
    })
    const view = await render(component)
    const title = nodes(view.root).find((node) => node.type === 'strong')!
    expect(textOf(title)).toBe('历史办理记录')
    expect(textOf(view.root)).toContain('原有历史说明')
    expect(textOf(view.root)).not.toContain('LEGACY_UNKNOWN')
  })
}

it('uses one bottom action bar and teleports only the active step actions', async () => {
  const view = await render()
  expect(
    nodes(view.root).filter((node) => node.props?.['data-testid'] === 'inspection-task-actions')
  ).toHaveLength(1)
  expect(textOf(view.footer())).toContain('返回')
  expect(textOf(view.footer())).toContain('刷新')
  expect(textOf(view.footer())).toContain('执行阶段操作')
  expect(textOf(view.footer())).not.toMatch(/报告阶段操作|标注阶段操作|保存准备资料/)
  view.state.activeStep = 'report'
  await flush()
  expect(textOf(view.footer())).toContain('报告阶段操作')
  expect(textOf(view.footer())).toContain('标注阶段操作')
  expect(textOf(view.footer())).not.toContain('执行阶段操作')
  view.state.activeStep = 'prepare'
  await flush()
  expect(textOf(view.footer())).toContain('保存准备资料')
  expect(textOf(view.footer())).not.toMatch(/执行阶段操作|报告阶段操作|标注阶段操作/)
})

it('keeps preparation and archive disabled during writes and moves management actions only while expanded', async () => {
  const view = await render()
  view.state.activeStep = 'prepare'
  view.state.busy = true
  await flush()
  const save = nodes(view.footer()).find(
    (node) => node.type === 'button' && textOf(node) === '保存准备资料'
  )!
  expect(save.props!.disabled).toBe(true)
  view.state.busy = false
  view.state.activeStep = 'archive'
  view.state.task.statusCode = 'CLOSED'
  await flush()
  expect(textOf(view.footer())).toContain('申请归档')
  expect(textOf(view.footer())).not.toContain('保存负责人分配')
  view.state.historySections = ['management']
  await flush()
  expect(textOf(view.footer())).toContain('保存负责人分配')
  view.state.historySections = []
  await flush()
  expect(textOf(view.footer())).not.toContain('保存负责人分配')
})

it('shows the original request recovery only in the bottom bar and distinguishes in-flight submission', async () => {
  const view = await render()
  view.state.pending = { taskId: '10', label: '原请求', section: 'execution', run: async () => {} }
  view.state.writeError = '请确认结果'
  await flush()
  expect(
    nodes(view.root).filter(
      (node) => node.type === 'button' && textOf(node) === '重试原请求并确认结果'
    )
  ).toHaveLength(1)
  expect(textOf(view.footer())).toContain('重试原请求并确认结果')
  view.state.busy = true
  await flush()
  expect(textOf(view.footer())).toContain('正在提交')
  expect(textOf(view.footer())).not.toContain('重试原请求并确认结果')
})

it('clears tracking drafts only after confirming opening evidence, leaving issue files untouched', async () => {
  const view = await render()
  const evidence = [{ referenceKey: 'issue:20:existing-upload', artifactId: '888', versionNo: 1 }]
  view.state.issue = { id: '20', evidence }
  view.state.drafts.tracking = true
  const discard = vi.fn(() => {
    view.state.drafts.tracking = false
  })
  view.state.trackingPanel = { discardDrafts: discard }
  confirmLeave.mockRejectedValueOnce(new Error('cancel'))
  await view.state.openIssue('21')
  expect(discard).not.toHaveBeenCalled()
  expect(view.state.drafts.tracking).toBe(true)
  expect(view.state.issue.evidence).toEqual(evidence)
  confirmLeave.mockResolvedValueOnce(undefined)
  api.getIssue.mockResolvedValue({ id: '21', evidence: [] })
  await view.state.openIssue('21')
  expect(discard).toHaveBeenCalledTimes(1)
  expect(view.state.drafts.tracking).toBe(false)
  expect(evidence).toEqual([
    { referenceKey: 'issue:20:existing-upload', artifactId: '888', versionNo: 1 }
  ])
})

const annotationRejection = {
  response: {
    status: 409,
    data: { msg: '报告或问题标注未完成', data: { reasonCode: 'STATE_CONFLICT' } }
  }
}

it.each([
  ['UMC', Detail],
  ['platform', PlatformDetail]
] as const)(
  '%s rechecks a deterministically rejected annotation completion and restores editing',
  async (_name, component) => {
    const current = { ...task(), statusCode: 'ANNOTATION_PENDING' }
    current.attempts[0].providerCode = _name === 'platform' ? 'PLATFORM' : 'UMC'
    api.getTask.mockResolvedValue(current)
    api.taskAction.mockRejectedValueOnce(annotationRejection)
    const view = await render(component)
    expect(view.state.fresh).toBe(true)
    expect(view.state.writeDisabled).toBe(false)
    await view.state.completeAnnotations()
    expect(api.taskAction).toHaveBeenCalledWith(
      '10',
      'complete-annotations',
      {},
      '1',
      'task-action-test'
    )
    expect(api.getTask).toHaveBeenCalledTimes(2)
    expect(view.state.pending).toBeUndefined()
    expect(view.state.busy).toBe(false)
    expect(view.state.fresh).toBe(true)
    expect(view.state.writeDisabled).toBe(false)
    expect(view.state.task.statusCode).toBe('ANNOTATION_PENDING')
    expect(view.state.writeError).toContain('报告或问题标注未完成')
  }
)

it('keeps editing blocked if the fresh-state read after a rejected completion fails', async () => {
  api.getTask
    .mockResolvedValueOnce({ ...task(), statusCode: 'ANNOTATION_PENDING' })
    .mockRejectedValueOnce(new Error('403'))
  api.taskAction.mockRejectedValueOnce(annotationRejection)
  const view = await render()
  await view.state.completeAnnotations()
  expect(api.getTask).toHaveBeenCalledTimes(2)
  expect(view.state.pending).toBeUndefined()
  expect(view.state.fresh).toBe(false)
  expect(view.state.writeDisabled).toBe(true)
  await flush()
  expect(textOf(findByTestId(view.root, 'annotation-dialog-recovery')!)).toContain('刷新最新资料')
})

it('retains the original command when annotation completion has an unknown transport outcome', async () => {
  api.getTask.mockResolvedValue({ ...task(), statusCode: 'ANNOTATION_PENDING' })
  api.taskAction.mockRejectedValue(new Error('Network Error'))
  const view = await render()
  await view.state.completeAnnotations()
  const original = view.state.pending
  expect(original).toBeDefined()
  expect(view.state.writeDisabled).toBe(true)
  expect(api.getTask).toHaveBeenCalledTimes(1)
  await view.state.retryPending()
  expect(view.state.pending).toBe(original)
  expect(api.taskAction.mock.calls[1]).toEqual(api.taskAction.mock.calls[0])
})

it('offers original-key annotation recovery inside the dialog after a transport failure', async () => {
  api.annotateResult.mockRejectedValue(new Error('Network Error'))
  const view = await render()
  view.state.drafts.annotation = true
  await view.state.annotate({
    reportId: '60',
    resultId: '31',
    kind: 'IGNORE',
    reason: '未保存的依据'
  })
  await flush()
  const recovery = findByTestId(view.root, 'annotation-dialog-recovery')!
  const retry = nodes(recovery).find(
    (node) => node.type === 'button' && textOf(node) === '重试原请求并确认结果'
  )!
  expect(retry).toBeTruthy()
  await (retry.props!.onClick as Function)()
  expect(api.annotateResult.mock.calls[1]).toEqual(api.annotateResult.mock.calls[0])
  expect(view.state.pending).toBeDefined()
  expect(view.state.drafts.annotation).toBe(true)
})

it('does not discard annotation drafts when a rejected write requires fresh state', async () => {
  const view = await render()
  view.state.drafts.annotation = true
  await view.state.perform({
    taskId: '10',
    label: '保存标注',
    section: 'annotation',
    run: async () => {
      throw annotationRejection
    }
  })
  expect(view.state.pending).toBeUndefined()
  expect(view.state.drafts.annotation).toBe(true)
  expect(api.getTask).toHaveBeenCalledTimes(1)
  expect(view.state.fresh).toBe(false)
  expect(view.state.writeError).toContain('刷新')
})
