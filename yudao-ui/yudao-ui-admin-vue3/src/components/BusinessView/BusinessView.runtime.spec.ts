import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BusinessViewHost from './BusinessViewHost.vue'
import { resolveBusinessView, type BusinessViewTarget } from './registry'
import * as FormApi from '@/api/pms/platform/dynamic-form'
import * as RequirementApi from '@/api/pms/engineering/requirement-analysis'
import {
  mount,
  passthrough,
  textOf,
  findByTestId
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const confirm = vi.hoisted(() => vi.fn(async (): Promise<void> => undefined))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ confirm, warning: vi.fn(), success: vi.fn(), info: vi.fn() })
}))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@vueuse/core', () => ({ useWindowSize: () => ({ width: { value: 1280 } }) }))
vi.mock('@/utils/formatTime', () => ({ formatDate: (value: unknown) => String(value) }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({ getInstance: vi.fn(), patchInstance: vi.fn() }))
vi.mock('@/api/pms/engineering/requirement-analysis', () => ({
  getCurrent: vi.fn(),
  getDetail: vi.fn(),
  patchForm: vi.fn(),
  createInitialDraft: vi.fn(),
  completeDraft: vi.fn(),
  createNextDraft: vi.fn()
}))
vi.mock('@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents', () => ({
  registerDynamicFormComponents: vi.fn()
}))
vi.mock(
  '@/views/pms/project/project-master-detail/components/RequirementAnalysisHistoryDrawer.vue',
  () => ({ default: { render: () => null } })
)
vi.mock(
  '@/views/pms/project/project-master-detail/components/RequirementAnalysisCompareDrawer.vue',
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
const options = { 'form-create': FormCreate, ElSkeleton: passthrough }
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
  vi.mocked(RequirementApi.getCurrent).mockResolvedValue({
    draft: { preparationId: 91 },
    allowedActions: ['CREATE_DRAFT']
  } as any)
  vi.mocked(RequirementApi.getDetail).mockResolvedValue({
    preparationId: 91,
    status: 'DRAFT',
    currentDraft: true,
    allowedActions: ['PATCH_FORM', 'COMPLETE'],
    completionBlockers: [],
    dynamicFormInstanceId: 71,
    templateRevisionId: 20,
    formConfJson: {},
    formRulesJson: [{ type: 'input', field: 'note' }],
    values: { note: 'old' },
    controlledFiles: {},
    dynamicFormInstanceVersion: 3
  } as any)
})

describe('PM-03 BusinessView runtime', () => {
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
    expect(RequirementApi.getCurrent).toHaveBeenLastCalledWith(id)
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
    expect(RequirementApi.getDetail).toHaveBeenCalledTimes(1)
    expect(RequirementApi.patchForm).not.toHaveBeenCalled()
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
    expect(RequirementApi.getCurrent).toHaveBeenCalledWith(11)
    expect(RequirementApi.getDetail).toHaveBeenCalledWith(91)
    expect(textOf(mounted.root)).toContain('需求分析')
    expect(textOf(mounted.root)).toContain('form:old; readonly:true')
    expect(textOf(mounted.root)).not.toContain('完成并冻结当前草稿')
    expect(RequirementApi.createInitialDraft).not.toHaveBeenCalled()
    expect(FormApi.getInstance).not.toHaveBeenCalled()
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
    expect(RequirementApi.completeDraft).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('fails closed for unknown components without reading either business Owner', async () => {
    const data = target()
    data.registration.componentKey = 'https://untrusted/view'
    const mounted = mount(BusinessViewHost, data, options)
    await tick()
    expect(textOf(mounted.root)).toContain('尚未部署')
    expect(FormApi.getInstance).not.toHaveBeenCalled()
    expect(RequirementApi.getCurrent).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('preserves stopped registrations as readonly historical views', () => {
    const data = target()
    data.registration.status = 'DISABLED'
    data.allowedActions = ['PATCH_INSTANCE']
    expect(resolveBusinessView(data).props).toMatchObject({ readonly: true, allowedActions: [] })
  })
})
