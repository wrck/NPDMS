import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BusinessViewHost from './BusinessViewHost.vue'
import ProjectRequirementAnalysisPanel from '@/views/pms/delivery-business/requirement-analysis/entity/EntityPanel.vue'
import { businessViewTargetKey, resolveBusinessView, type BusinessViewTarget } from './registry'
import SiteSurveyPage from '@/views/pms/delivery-business/site-survey/index.vue'
import * as FormApi from '@/api/pms/platform/dynamic-form'
import * as RequirementApi from '@/api/pms/engineering/requirement-analysis/entity'
import {
  mount,
  passthrough,
  tableColumn,
  textOf,
  findByTestId
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

// The custom renderer has no DOM; keyboard/ARIA integration is covered by its DOM suite.
vi.mock('@/views/pms/project/project-master-detail/components/formCreateKeyboardRows', () => ({ vFormCreateKeyboardRows: {} }))
vi.mock('@/views/pms/delivery-business/requirement-analysis/entity/RevisionFiles.vue', () => ({ default: { render: () => null } }))
vi.mock('@/views/pms/delivery-business/site-survey/index.vue', () => ({ default: { name: 'PmsEngSiteSurvey', render: () => null } }))
vi.mock('@/views/pms/acceptance/acceptance-report/index.vue', () => ({ default: { name: 'AcceptanceReport', render: () => null } }))
const confirm = vi.hoisted(() => vi.fn(async (): Promise<void> => undefined))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ confirm, warning: vi.fn(), success: vi.fn(), info: vi.fn() })
}))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@vueuse/core', () => ({ useWindowSize: () => ({ width: { value: 1280 } }) }))
vi.mock('@/utils/formatTime', () => ({ formatDate: (value: unknown) => String(value) }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({ getInstance: vi.fn(), patchInstance: vi.fn() }))
vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({
  workspace: vi.fn(),
  read: vi.fn(),
  save: vi.fn(),
  create: vi.fn(),
  complete: vi.fn(),
  copy: vi.fn()
}))
vi.mock('@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents', () => ({
  registerDynamicFormComponents: vi.fn()
}))
vi.mock(
  '@/views/pms/delivery-business/requirement-analysis/entity/RevisionDrawer.vue',
  () => ({ default: { render: () => null } })
)
vi.mock(
  '@/views/pms/delivery-business/requirement-analysis/entity/CompareDrawer.vue',
  () => ({ default: { render: () => null } })
)
const tick = async () => {
  for (let i = 0; i < 8; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
const instance = (version = 4, value = 'old') =>
  ({
    instanceId: 7,
    instanceCode: 'DFI-7',
    instanceName: '现场记录',
    templateId: 2,
    templateCode: 'T',
    templateName: '巡检',
    templateRevisionId: 20,
    templateRevisionNo: 3,
    formConfJson: {},
    formRulesJson: [{ type: 'input', field: 'note' }],
    values: { note: value },
    controlledFiles: {},
    instanceVersion: version,
    allowedActions: ['PATCH_INSTANCE']
  }) as any
const target = (
  source: 'PAGE' | 'DYNAMIC_FORM' = 'DYNAMIC_FORM'
): BusinessViewTarget & Record<string, unknown> => ({
  registration: {
    id: 8,
    revisionNo: 1,
    version: 1,
    viewKey: 'FIELD_RECORD',
    status: 'PUBLISHED',
    componentKey: source === 'PAGE' ? 'PROJ_REQUIREMENT_ANALYSIS' : 'PLATFORM_DYNAMIC_FORM',
    componentVersion: '1',
    entityType: source === 'PAGE' ? 'REQUIREMENT_ANALYSIS' : 'DYNAMIC_FORM_INSTANCE',
    ownerContext: source === 'PAGE' ? 'SOL' : 'PLATFORM',
    viewSource: source,
    dynamicFormRevisionId: source === 'PAGE' ? undefined : 20,
    contextSchema: {},
    supportedActions:
      source === 'PAGE'
        ? ['CREATE_INITIAL_DRAFT', 'PATCH_FORM', 'COMPLETE', 'CREATE_DRAFT']
        : ['QUERY_INSTANCE', 'PATCH_INSTANCE'],
    allowedActions: ['UPDATE', 'PUBLISH'],
    queryProviderKey: 'QUERY',
    commandProviderKey: 'COMMAND',
    permissionProviderKey: 'PERMISSION'
  },
  resolvedContext:
    source === 'PAGE' ? { project: { id: 11, version: 1 } as any } : { instanceId: 7 },
  allowedActions: []
})
const FormCreate = defineComponent({
  props: { modelValue: Object, disabled: Boolean },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('div', [
        h('span', `form:${props.modelValue?.note}; readonly:${props.disabled}`),
        h(
          'button',
          {
            'data-testid': 'change-form',
            onClick: () => emit('update:modelValue', { note: 'local' })
          },
          'edit'
        )
      ])
  }
})
const options = {
  'form-create': FormCreate,
  ElSkeleton: passthrough,
  ElInput: passthrough,
  ElTable: passthrough,
  ElTableColumn: tableColumn,
  ElDescriptions: passthrough,
  ElDescriptionsItem: passthrough
}
const requirementView = (id: string | number = 91, state = 'DRAFT', allowedActions = ['PATCH_FORM', 'COMPLETE']) => ({
  projectId: 11, revision: { ref: { entity: { tenantId: 1, ownerModule: 'SOL', entityType: 'REQUIREMENT_ANALYSIS', entityId: 90 }, revisionId: id },
    revisionNo: 1, state, effective: state === 'FROZEN', version: 4 },
  extensionValueVersion: 0, allowedActions, fieldCatalog: [{ code: 'note', type: 'TEXT', required: false }],
  form: { binding: { formRevisionId: 20, version: 1, fieldBindings: { note: 'note' } }, revisionNo: 1,
    formConfJson: '{}', formRulesJson: '[{"type":"input","field":"note"}]' },
  values: { note: 'old' }, attachments: []
}) as any
beforeEach(() => {
  vi.clearAllMocks()
  confirm.mockResolvedValue(undefined)
  const storage = new Map<string, string>()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key)
  })
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  vi.mocked(FormApi.getInstance).mockResolvedValue(instance())
  const detail = requirementView()
  vi.mocked(RequirementApi.read).mockResolvedValue(detail)
  vi.mocked(RequirementApi.workspace).mockResolvedValue({
    projectId: 11, draft: detail, allowedActions: ['CREATE_DRAFT']
  } as any)
})

describe('PM-03 BusinessView runtime', () => {
  const stageExecution = {
    projectId: 11, projectVersion: 1, stageId: '2099999999999999997', stageVersion: 1,
    executionContractId: '2099999999999999996', contractVersion: 1,
    planVersionId: '2099999999999999995', executionId: '2099999999999999994',
    executionVersion: 1, roundNo: 2, writable: true
  }
  const taskExecution = {
    projectId: 11, projectVersion: 1, taskId: '2099999999999999991', taskVersion: 1,
    executionContractId: '2099999999999999996', contractVersion: 1,
    planVersionId: '2099999999999999995', executionId: '2099999999999999994',
    executionVersion: 1, roundNo: 2, stageExecutionId: '2099999999999999993', stageExecutionVersion: 1, writable: true
  }

  it('passes the chosen task from the shared host through the original SOL creation page', async () => {
    const data = target('PAGE')
    data.resolvedContext.taskId = taskExecution.taskId
    data.resolvedContext.taskExecution = taskExecution
    data.allowedActions = ['CREATE_INITIAL_DRAFT']
    vi.mocked(RequirementApi.workspace).mockResolvedValue({ projectId: 11, allowedActions: ['CREATE_INITIAL_DRAFT'] } as any)
    const mounted = mount(BusinessViewHost, data, options)
    await tick()
    expect(RequirementApi.workspace).toHaveBeenCalledWith(11, undefined, taskExecution.taskId)
    const visit = (node: any): any => node.type === 'button' && textOf(node).includes('创建需求分析草稿')
      ? node : node.children.map(visit).find(Boolean)
    await visit(mounted.root).props.onClick()
    expect(RequirementApi.create).toHaveBeenCalledWith(11, expect.any(String), { task: taskExecution })
    mounted.app.unmount()
  })

  it('rejects missing, foreign and mixed task execution identities instead of using a default node', () => {
    const data = target('PAGE')
    data.resolvedContext.taskId = taskExecution.taskId
    expect(resolveBusinessView(data).error).toBeTruthy()
    data.resolvedContext.taskExecution = { ...taskExecution, projectId: 12 }
    expect(resolveBusinessView(data).error).toBeTruthy()
    data.resolvedContext.taskExecution = { ...taskExecution, taskId: '2099999999999999990' }
    expect(resolveBusinessView(data).error).toBeTruthy()
    data.resolvedContext.taskExecution = taskExecution
    data.resolvedContext.stageExecution = stageExecution
    expect(resolveBusinessView(data).error).toBeTruthy()
  })

  it.each(['task', 'stage'] as const)('retains unsaved SOL input when the same %s starts a new round', async (kind) => {
    const state = reactive({ project: { id: 11 } as any, allowedActions: ['PATCH_FORM'],
      taskExecution: kind === 'task' ? { ...taskExecution } : undefined,
      stageExecution: kind === 'stage' ? { ...stageExecution } : undefined })
    const panel = ref<any>()
    const mounted = mount(defineComponent({ setup: () => () => h(ProjectRequirementAnalysisPanel, { ...state, ref: panel }) }), {}, options)
    await tick()
    const edit = findByTestId(mounted.root, 'change-form')?.props?.onClick
    expect(edit).toBeTypeOf('function')
    ;(edit as () => void)()
    await tick()
    const context = kind === 'task' ? state.taskExecution! : state.stageExecution!
    context.executionId = '2099999999999999988'
    context.roundNo = 3
    await tick()
    expect(textOf(mounted.root)).toContain('form:local')
    expect(panel.value.isDirty()).toBe(true)
    expect(RequirementApi.workspace).toHaveBeenCalledTimes(1)
    vi.mocked(RequirementApi.save).mockRejectedValueOnce(new Error('execution changed'))
    const save = findByTestId(mounted.root, 'save-requirement-form')?.props?.onClick
    expect(save).toBeTypeOf('function')
    await (save as () => Promise<void>)()
    await tick()
    expect(RequirementApi.save).toHaveBeenCalledWith(requirementView().revision, {
      values: { note: 'local' }, expectedExtensionVersion: 0, extensionDefinitionRevisionId: undefined,
      execution: kind === 'task' ? { task: taskExecution } : { stage: stageExecution }
    }, expect.any(String))
    expect(textOf(mounted.root)).toContain('form:local')
    expect(panel.value.isDirty()).toBe(true)
    mounted.app.unmount()
  })

  it('keeps the original task execution while revision confirmation is open', async () => {
    const completed = requirementView(91, 'FROZEN', ['CREATE_DRAFT'])
    vi.mocked(RequirementApi.workspace).mockResolvedValue({ projectId: 11, currentEffective: completed, allowedActions: ['CREATE_DRAFT'] } as any)
    const state = reactive({ project: { id: 11 } as any, allowedActions: ['CREATE_DRAFT'], taskExecution: { ...taskExecution } })
    const mounted = mount(defineComponent({ setup: () => () => h(ProjectRequirementAnalysisPanel, state) }), {}, options)
    await tick()
    let approve!: () => void
    confirm.mockImplementationOnce(() => new Promise<void>((resolve) => { approve = resolve }))
    const visit = (node: any): any => node.type === 'button' && textOf(node).includes('从查看版本创建草稿')
      ? node : node.children.map(visit).find(Boolean)
    const pending = visit(mounted.root).props.onClick()
    state.taskExecution.executionId = '2099999999999999988'
    await tick()
    approve()
    await pending
    expect(RequirementApi.copy).toHaveBeenCalledWith(completed.revision, expect.any(String), { task: taskExecution })
    mounted.app.unmount()
  })

  it('creates through the original SOL page with the exact stage execution instead of a task identity', async () => {
    const data = target('PAGE')
    data.resolvedContext.stageExecution = stageExecution
    data.allowedActions = ['CREATE_INITIAL_DRAFT']
    vi.mocked(RequirementApi.workspace).mockResolvedValue({ projectId: 11, allowedActions: ['CREATE_INITIAL_DRAFT'] } as any)
    vi.mocked(RequirementApi.create).mockResolvedValue(requirementView().revision)
    const mounted = mount(BusinessViewHost, data, options)
    await tick()
    expect(RequirementApi.workspace).toHaveBeenCalledWith(11, stageExecution.stageId, undefined)
    const visit = (node: any): any => node.type === 'button' && textOf(node).includes('创建需求分析草稿')
      ? node : node.children.map(visit).find(Boolean)
    const create = visit(mounted.root)
    expect(create).toBeTruthy()
    await create.props.onClick()
    await tick()
    expect(RequirementApi.create).toHaveBeenCalledWith(11, expect.any(String), { stage: stageExecution })
    mounted.app.unmount()
  })
  it('rejects a foreign stage and switches the frozen target on a new execution, not a version refresh', () => {
    const data = target('PAGE')
    data.resolvedContext.stageExecution = stageExecution
    expect(resolveBusinessView(data).props).toHaveProperty('stageExecution', stageExecution)
    const key = businessViewTargetKey(data)
    data.resolvedContext.stageExecution = { ...stageExecution, executionVersion: 2 }
    expect(businessViewTargetKey(data)).toBe(key)
    data.resolvedContext.stageExecution = { ...stageExecution, executionId: '2099999999999999993' }
    expect(businessViewTargetKey(data)).not.toBe(key)
    data.resolvedContext.stageExecution = { ...stageExecution, projectId: 12 }
    expect(resolveBusinessView(data).error).toBeTruthy()
  })
  it('refreshes pre-start SOL creation actions when the retained task host grants them', async () => {
    vi.mocked(RequirementApi.workspace).mockResolvedValueOnce({ projectId: 11, allowedActions: [] } as any)
    const state = reactive({ ...target('PAGE'), allowedActions: ['QUERY'] })
    const mounted = mount(defineComponent({ setup: () => () => h(BusinessViewHost, state) }), {}, options)
    await tick()
    expect(textOf(mounted.root)).not.toContain('创建需求分析草稿')
    vi.mocked(RequirementApi.workspace).mockResolvedValue({ projectId: 11, allowedActions: ['CREATE_INITIAL_DRAFT'] } as any)
    state.allowedActions = ['QUERY', 'CREATE_INITIAL_DRAFT']
    await tick()
    expect(textOf(mounted.root)).toContain('创建需求分析草稿')
    expect(RequirementApi.workspace).toHaveBeenCalledTimes(2)
    expect(RequirementApi.read).not.toHaveBeenCalled()
    state.allowedActions = [...state.allowedActions]
    await tick()
    expect(RequirementApi.workspace).toHaveBeenCalledTimes(2)
    mounted.app.unmount()
  })
  it('ignores a delayed SOL creation grant after the host revokes it', async () => {
    vi.mocked(RequirementApi.workspace).mockResolvedValueOnce({ projectId: 11, allowedActions: [] } as any)
    const state = reactive({ ...target('PAGE'), allowedActions: ['QUERY'] })
    const mounted = mount(defineComponent({ setup: () => () => h(BusinessViewHost, state) }), {}, options)
    await tick()
    let finish!: (value: any) => void
    vi.mocked(RequirementApi.workspace).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    state.allowedActions = ['QUERY', 'CREATE_INITIAL_DRAFT']
    await tick()
    state.allowedActions = ['QUERY']
    await tick()
    finish({ projectId: 11, allowedActions: ['CREATE_INITIAL_DRAFT'] })
    await tick()
    expect(textOf(mounted.root)).not.toContain('创建需求分析草稿')
    expect(RequirementApi.read).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('refreshes rework revision actions without replacing the selected frozen business result', async () => {
    const completed = requirementView(91, 'FROZEN', ['CREATE_DRAFT'])
    vi.mocked(RequirementApi.workspace).mockResolvedValueOnce({ projectId: 11, currentEffective: completed, allowedActions: [] } as any)
    vi.mocked(RequirementApi.read).mockResolvedValue(completed as any)
    const state = reactive({ ...target('PAGE'), allowedActions: ['QUERY'] })
    const mounted = mount(defineComponent({ setup: () => () => h(BusinessViewHost, state) }), {}, options)
    await tick()
    expect(textOf(mounted.root)).not.toContain('从查看版本创建草稿')
    vi.mocked(RequirementApi.workspace).mockResolvedValue({ projectId: 11, currentEffective: completed, allowedActions: ['CREATE_DRAFT'] } as any)
    state.allowedActions = ['QUERY', 'CREATE_DRAFT']
    await tick()
    expect(textOf(mounted.root)).toContain('从查看版本创建草稿')
    expect(RequirementApi.read).not.toHaveBeenCalled()
    expect(RequirementApi.copy).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it.each([93, '9007199254740993'])('opens exact linked SOL preparation %s instead of the default current draft', async (id) => {
    const data = target('PAGE')
    data.resolvedContext.businessObjectId = id
    vi.mocked(RequirementApi.read).mockResolvedValueOnce(requirementView(id, 'FROZEN', []))
    const mounted = mount(BusinessViewHost, data, options)
    await tick()
    expect(RequirementApi.read).toHaveBeenCalledWith(id)
    expect(RequirementApi.read).not.toHaveBeenCalledWith(91)
    mounted.app.unmount()
  })
  it('maps only the exact SOL site-survey page and preserves typed Owner context without numeric conversion', () => {
    const data = target('PAGE')
    Object.assign(data.registration, { componentKey: 'SOL_SITE_SURVEY', entityType: 'SITE_SURVEY' })
    data.resolvedContext = { project: { id: '2099999999999999999' } as any, businessObjectId: '2099999999999999998', taskId: '2099999999999999997' }
    data.allowedActions = ['QUERY', 'UPDATE']
    const resolved = resolveBusinessView(data)
    expect(resolved.component).toBe(SiteSurveyPage)
    expect(resolved.props).toEqual({ projectId: '2099999999999999999', objectId: '2099999999999999998', taskId: '2099999999999999997', readonly: false, allowedActions: ['QUERY', 'UPDATE'] })
    for (const patch of [{ componentVersion: '2' }, { ownerContext: 'PROJ' }, { entityType: 'OTHER' }, { viewSource: 'DYNAMIC_FORM' }, { dynamicFormRevisionId: 20 }]) {
      expect(resolveBusinessView({ ...data, registration: { ...data.registration, ...patch } as any }).error).toBeTruthy()
    }
    const key = businessViewTargetKey(data)
    for (const context of [{ businessObjectId: 11 }, { taskId: 12 }]) {
      expect(businessViewTargetKey({ ...data, resolvedContext: { ...data.resolvedContext, ...context } })).not.toBe(key)
    }
    expect(resolveBusinessView({ ...data, resolvedContext: { ...data.resolvedContext, taskId: '2e18' } }).error).toBeTruthy()
    data.registration.status = 'DISABLED'
    expect(resolveBusinessView(data).props).toMatchObject({ readonly: true, allowedActions: [] })
  })
  it('passes exact survey execution snapshots and refuses mixed or foreign-node contexts', () => {
    const data = target('PAGE')
    Object.assign(data.registration, { componentKey: 'SOL_SITE_SURVEY', entityType: 'SITE_SURVEY' })
    const task = { projectId: 11, taskId: '2099999999999999997', executionId: '2099999999999999998' } as any
    data.resolvedContext = { project: { id: 11 }, taskId: task.taskId, taskExecution: task }
    expect(resolveBusinessView(data).props).toMatchObject({ taskExecution: task })
    expect(resolveBusinessView({ ...data, resolvedContext: { ...data.resolvedContext, taskExecution: { ...task, projectId: 12 } } }).error).toBeTruthy()
    expect(resolveBusinessView({ ...data, resolvedContext: { ...data.resolvedContext, stageExecution } }).error).toBeTruthy()
    data.resolvedContext = { project: { id: 11 }, stageExecution }
    expect(resolveBusinessView(data).props).toMatchObject({ stageExecution })
    expect(resolveBusinessView({ ...data, resolvedContext: { project: { id: 12 }, stageExecution } }).error).toBeTruthy()
  })
  it('maps exact acceptance report identity without granting missing publish permission', () => {
    const data = target('PAGE')
    Object.assign(data.registration, {componentKey:'ACC_ACCEPTANCE_REPORT', ownerContext:'ACC', entityType:'ACCEPTANCE'})
    data.resolvedContext={project:{id:'9007199254740993'},businessObjectId:'9007199254740994'}
    data.allowedActions=['QUERY','UPDATE']
    expect(resolveBusinessView(data).props).toEqual({projectId:'9007199254740993',objectId:'9007199254740994',readonly:false,allowedActions:['QUERY','UPDATE']})
    expect(resolveBusinessView({...data,registration:{...data.registration,ownerContext:'SOL'}}).error).toBeTruthy()
  })
  it('loads and saves a Snowflake form ID without losing a decimal digit', async () => {
    const id = '2099999999999999999'
    const revisionId = '2099999999999999998'
    const data = target()
    data.registration.id = '2099999999999999997'
    data.registration.dynamicFormRevisionId = revisionId
    data.resolvedContext.instanceId = id
    data.allowedActions = ['PATCH_INSTANCE']
    vi.mocked(FormApi.getInstance).mockResolvedValue({
      ...instance(),
      instanceId: id,
      templateRevisionId: revisionId
    })
    const mounted = mount(BusinessViewHost, data, options)
    await tick()
    expect(FormApi.getInstance).toHaveBeenLastCalledWith(id)
    expect(textOf(mounted.root)).toContain('form:old')
    await (findByTestId(mounted.root, 'change-form')!.props!.onClick as Function)()
    await tick()
    const visit = (node: any): any =>
      node.type === 'button' && textOf(node).includes('保存填写值')
        ? node
        : node.children.map(visit).find(Boolean)
    vi.mocked(FormApi.patchInstance).mockResolvedValue({} as any)
    await visit(mounted.root).props.onClick()
    expect(FormApi.patchInstance).toHaveBeenCalledWith(id, 4, { values: { note: 'local' } })
    mounted.app.unmount()
    const page = target('PAGE')
    page.resolvedContext.project = { id, version: 1 } as any
    const sol = mount(BusinessViewHost, page, options)
    await tick()
    expect(RequirementApi.workspace).toHaveBeenLastCalledWith(id, undefined, undefined)
    sol.app.unmount()
  })
  it.each(['0', '-1', '1.5', '2e18', ' 7', '07', '9223372036854775808', 2099999999999999999])(
    'rejects malformed or already lossy ID %s',
    (id) => {
      const data = target()
      data.resolvedContext.instanceId = id
      expect(resolveBusinessView(data).error).toBeTruthy()
    }
  )
  it('keeps dirty SOL body through host permission replacement and revocation without reloading its detail', async () => {
    const state = reactive({ ...target('PAGE'), allowedActions: ['PATCH_FORM'] })
    const host = ref<any>()
    const mounted = mount(
      defineComponent({ setup: () => () => h(BusinessViewHost, { ...state, ref: host }) }),
      {},
      options
    )
    await tick()
    await (findByTestId(mounted.root, 'change-form')!.props!.onClick as Function)()
    await tick()
    state.allowedActions = ['PATCH_FORM', 'COMPLETE']
    await tick()
    expect(textOf(mounted.root)).toContain('form:local; readonly:false')
    state.allowedActions = []
    await tick()
    expect(textOf(mounted.root)).toContain('form:local; readonly:true')
    expect(findByTestId(mounted.root, 'save-requirement-form')).toBeUndefined()
    expect(host.value.isDirty()).toBe(true)
    expect(await host.value.requestLeave()).toBe(false)
    expect(RequirementApi.read).not.toHaveBeenCalled()
    expect(RequirementApi.save).not.toHaveBeenCalled()
    state.allowedActions = ['PATCH_FORM']
    await tick()
    expect(textOf(mounted.root)).toContain('form:local; readonly:false')
    mounted.app.unmount()
  })
  it('never discards A when a pending A-to-B confirmation becomes stale after target returns to A', async () => {
    const state = reactive({ ...target(), allowedActions: ['PATCH_INSTANCE'] })
    const host = ref<any>()
    const mounted = mount(
      defineComponent({ setup: () => () => h(BusinessViewHost, { ...state, ref: host }) }),
      {},
      options
    )
    await tick()
    await (findByTestId(mounted.root, 'change-form')!.props!.onClick as Function)()
    await tick()
    let approve!: () => void
    confirm.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          approve = resolve
        })
    )
    state.resolvedContext = { instanceId: 9 }
    await tick()
    state.resolvedContext = { instanceId: 7 }
    await tick()
    approve()
    await tick()
    expect(textOf(mounted.root)).toContain('form:local')
    expect(host.value.isDirty()).toBe(true)
    expect(FormApi.getInstance).toHaveBeenCalledTimes(1)
    expect(await host.value.requestLeave()).toBe(true)
    expect(textOf(mounted.root)).toContain('form:local')
    expect(host.value.isDirty()).toBe(true)
    mounted.app.unmount()
  })
  it('never resolves metadata object ids, unknown components, wrong Owner or component versions', () => {
    const context = target()
    context.resolvedContext = {}
    Object.assign(context.registration, { instanceId: 7, projectId: 11 })
    expect(resolveBusinessView(context).error).toContain('上下文')
    for (const patch of [
      { componentKey: '/arbitrary/page' },
      { componentVersion: '2' },
      { ownerContext: 'SOL' },
      { status: 'DRAFT' }
    ]) {
      expect(
        resolveBusinessView({
          ...target(),
          registration: { ...target().registration, ...patch } as any
        }).error
      ).toBeTruthy()
    }
  })
  it('actually loads the shared PLT instance content readonly, never treating registration actions as permission', async () => {
    const mounted = mount(BusinessViewHost, target(), options)
    await tick()
    expect(FormApi.getInstance).toHaveBeenCalledWith(7)
    expect(textOf(mounted.root)).toContain('冻结修订 3')
    expect(textOf(mounted.root)).toContain('readonly:true')
    expect(textOf(mounted.root)).not.toContain('保存填写值')
    expect(FormApi.patchInstance).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('actually loads the existing SOL panel and intersects host actions with its Owner response', async () => {
    const mounted = mount(BusinessViewHost, target('PAGE'), options)
    await tick()
    expect(RequirementApi.workspace).toHaveBeenCalledWith(11, undefined, undefined)
    expect(RequirementApi.read).not.toHaveBeenCalled()
    expect(findByTestId(mounted.root, 'requirement-version-table')).toBeTruthy()
    expect(textOf(mounted.root)).toContain('form:old; readonly:true')
    expect(textOf(mounted.root)).not.toContain('完成并冻结当前草稿')
    expect(RequirementApi.create).not.toHaveBeenCalled()
    expect(FormApi.getInstance).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it.each([
    { projectId: 12 },
    { projectId: 11, draft: { ...requirementView(), projectId: 12 } }
  ])('rejects a foreign SOL workspace or version without fallback requests: %j', async (workspace) => {
    vi.mocked(RequirementApi.workspace).mockResolvedValue({ ...workspace, allowedActions: ['CREATE_INITIAL_DRAFT'] } as any)
    const mounted = mount(BusinessViewHost, { ...target('PAGE'), allowedActions: ['CREATE_INITIAL_DRAFT', 'PATCH_FORM'] }, options)
    await tick()
    expect(textOf(mounted.root)).toContain('工作区加载失败')
    expect(textOf(mounted.root)).not.toContain('form:old')
    expect(textOf(mounted.root)).not.toContain('创建需求分析草稿')
    expect(RequirementApi.read).not.toHaveBeenCalled()
    expect(RequirementApi.create).not.toHaveBeenCalled()
    expect(RequirementApi.save).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('rejects an instance whose frozen revision differs, without fallback rendering or writes', async () => {
    vi.mocked(FormApi.getInstance).mockResolvedValue({ ...instance(), templateRevisionId: 99 })
    const mounted = mount(BusinessViewHost, target(), options)
    await tick()
    expect(textOf(mounted.root)).toContain('修订不匹配')
    expect(textOf(mounted.root)).not.toContain('form:')
    expect(FormApi.patchInstance).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('keeps the mounted instance when dirty switch is cancelled, then switches only after approval', async () => {
    const state = reactive({ ...target(), allowedActions: ['PATCH_INSTANCE'] })
    const host = ref<any>()
    const blocked = vi.fn()
    const mounted = mount(
      defineComponent({
        setup: () => () => h(BusinessViewHost, { ...state, ref: host, onSwitchBlocked: blocked })
      }),
      {},
      options
    )
    await tick()
    await (findByTestId(mounted.root, 'change-form')!.props!.onClick as Function)()
    await tick()
    expect(host.value.isDirty()).toBe(true)
    confirm.mockRejectedValueOnce(new Error('cancel'))
    state.resolvedContext = { instanceId: 9 }
    await tick()
    expect(blocked).toHaveBeenCalledTimes(1)
    expect(FormApi.getInstance).toHaveBeenCalledTimes(1)
    expect(textOf(mounted.root)).toContain('form:local')
    state.resolvedContext = { instanceId: 10 }
    vi.mocked(FormApi.getInstance).mockResolvedValue({ ...instance(), instanceId: 10 })
    await tick()
    expect(FormApi.getInstance).toHaveBeenLastCalledWith(10)
    expect(host.value.isDirty()).toBe(false)
    mounted.app.unmount()
  })
  it('refreshes CAS version after rejected save while retaining ordinary values and existing retry storage policy', async () => {
    const mounted = mount(
      BusinessViewHost,
      { ...target(), allowedActions: ['PATCH_INSTANCE'] },
      options
    )
    await tick()
    await (findByTestId(mounted.root, 'change-form')!.props!.onClick as Function)()
    await tick()
    const clickSave = async () => {
      const visit = (node: any): any =>
        node.type === 'button' && textOf(node).includes('保存填写值')
          ? node
          : node.children.map(visit).find(Boolean)
      await visit(mounted.root).props.onClick()
      await tick()
    }
    vi.mocked(FormApi.patchInstance).mockRejectedValueOnce({ code: 'VERSION_CONFLICT' })
    vi.mocked(FormApi.getInstance).mockResolvedValue(instance(5))
    await clickSave()
    expect(FormApi.patchInstance).toHaveBeenLastCalledWith(7, 4, { values: { note: 'local' } })
    expect(textOf(mounted.root)).toContain('实例版本 5')
    expect(textOf(mounted.root)).toContain('form:local')
    expect(sessionStorage.getItem('pms:fplt002:instance-patch:7')).toContain('local')
    vi.mocked(FormApi.patchInstance).mockResolvedValue({} as any)
    vi.mocked(FormApi.getInstance).mockResolvedValue(instance(6, 'local'))
    await clickSave()
    expect(FormApi.patchInstance).toHaveBeenLastCalledWith(7, 5, { values: { note: 'local' } })
    expect(sessionStorage.getItem('pms:fplt002:instance-patch:7')).toBeNull()
    mounted.app.unmount()
  })
  it('refuses to unmount dirty SOL forms without discarding their Owner input', async () => {
    const mounted = mount(
      BusinessViewHost,
      { ...target('PAGE'), allowedActions: ['PATCH_FORM'] },
      options
    )
    await tick()
    expect(textOf(mounted.root)).toContain('form:old; readonly:false')
    await (findByTestId(mounted.root, 'change-form')!.props!.onClick as Function)()
    await tick()
    expect(await (mounted.vm as any).requestLeave()).toBe(false)
    expect(confirm).not.toHaveBeenCalled()
    expect(RequirementApi.complete).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('fails closed for unknown components without reading either business Owner', async () => {
    const data = target()
    data.registration.componentKey = 'https://untrusted/view'
    const mounted = mount(BusinessViewHost, data, options)
    await tick()
    expect(textOf(mounted.root)).toContain('尚未部署')
    expect(FormApi.getInstance).not.toHaveBeenCalled()
    expect(RequirementApi.workspace).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('preserves stopped registrations as readonly historical views', () => {
    const data = target()
    data.registration.status = 'DISABLED'
    data.allowedActions = ['PATCH_INSTANCE']
    expect(resolveBusinessView(data).props).toMatchObject({ readonly: true, allowedActions: [] })
  })
})
