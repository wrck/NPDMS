import { computed, defineComponent, h, inject, nextTick, provide } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as Api from '@/api/pms/platform/business-view'
import BusinessViewManagement from './index.vue'
import {
  mount,
  passthrough,
  textOf,
  type TestNode
} from '../dynamic-form/components/runtimeTestHarness'

vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@/api/pms/platform/business-view', async (importOriginal) => ({
  ...(await importOriginal<typeof Api>()),
  getBusinessViewPage: vi.fn(),
  getBusinessView: vi.fn(),
  getBusinessViewComponents: vi.fn(),
  createBusinessView: vi.fn(),
  updateBusinessView: vi.fn(),
  validateBusinessView: vi.fn(),
  copyBusinessView: vi.fn(),
  publishBusinessView: vi.fn(),
  disableBusinessView: vi.fn()
}))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/api/pms/platform/dynamic-form', () => ({
  getTemplateSelection: vi.fn(async () => ({ list: [], total: 0 }))
}))
const table = defineComponent({
  props: { data: Array },
  setup(props, { slots }) {
    provide(
      'rows',
      computed(() => props.data || [])
    )
    return () => h('div', slots.default?.())
  }
})
const column = defineComponent({
  props: { prop: { type: String, default: '' } },
  setup(props, { slots }) {
    const rows = inject<any>('rows')
    return () =>
      h(
        'div',
        rows.value.map((row: any) => slots.default?.({ row }) || String(row[props.prop] || ''))
      )
  }
})
const drawer = defineComponent({
  props: { modelValue: Boolean },
  setup:
    (props, { slots }) =>
    () =>
      props.modelValue ? h('div', [slots.default?.(), slots.footer?.()]) : null
})
const component = {
  entityType: 'REQUIREMENT_ANALYSIS',
  ownerContext: 'SOL',
  viewSource: 'PAGE',
  componentKey: 'PROJ_REQUIREMENT_ANALYSIS',
  componentVersion: '1',
  contextSchema: { projectId: 'positive' },
  supportedActions: ['CREATE_INITIAL_DRAFT', 'PATCH_FORM', 'COMPLETE', 'CREATE_DRAFT'],
  queryProviderKey: 'SOL_QUERY',
  commandProviderKey: 'SOL_COMMAND',
  permissionProviderKey: 'SOL_PERMISSION'
} as Api.BusinessViewComponentVO
const registration = (version = 1, actions = ['UPDATE', 'COPY', 'VALIDATE', 'PUBLISH']) =>
  ({
    ...component,
    id: 8,
    viewKey: 'REQUIREMENTS',
    revisionNo: 1,
    version,
    status: 'DRAFT',
    allowedActions: actions
  }) as Api.BusinessViewRegistrationVO
const tick = async () => {
  for (let i = 0; i < 8; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
const button = (node: TestNode, label: string): TestNode | undefined =>
  node.type === 'button' && textOf(node) === label
    ? node
    : node.children.map((child) => button(child, label)).find(Boolean)
const click = async (root: TestNode, label: string) => {
  const found = button(root, label)
  expect(found, label).toBeTruthy()
  await (found!.props!.onClick as Function)()
  await tick()
}
const options = {
  ElTable: table,
  ElTableColumn: column,
  ElDrawer: drawer,
  ElInput: passthrough,
  ElSelect: passthrough,
  ElOption: passthrough,
  ElDescriptions: passthrough,
  ElDescriptionsItem: passthrough
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  vi.mocked(Api.getBusinessViewPage).mockResolvedValue({ list: [registration()], total: 1 })
  vi.mocked(Api.getBusinessView).mockResolvedValue(registration())
  vi.mocked(Api.getBusinessViewComponents).mockResolvedValue([component])
})

describe('PM-03 registration management interactions', () => {
  it('creates only from the controlled component selection and queries without mounting an object', async () => {
    const mounted = mount(BusinessViewManagement, {}, options)
    await tick()
    await click(mounted.root, '新建业务视图')
    const placeholder = (node: TestNode, value: string): TestNode | undefined =>
      node.props?.placeholder === value
        ? node
        : node.children.map((child) => placeholder(child, value)).find(Boolean)
    const keyInput = placeholder(mounted.root, '稳定编码，不可改义')!
    await (keyInput.props!['onUpdate:modelValue'] as Function)('NEW_VIEW')
    const select = placeholder(mounted.root, '选择精确组件版本')!
    await (select.props!['onUpdate:modelValue'] as Function)(
      JSON.stringify([component.componentKey, component.componentVersion])
    )
    await tick()
    await (select.props!.onChange as Function)()
    await tick()
    vi.mocked(Api.createBusinessView).mockResolvedValue({ ...registration(), viewKey: 'NEW_VIEW' })
    await click(mounted.root, '保存草稿')
    expect(Api.createBusinessView).toHaveBeenCalledWith(
      {
        entityType: 'REQUIREMENT_ANALYSIS',
        viewKey: 'NEW_VIEW',
        componentKey: 'PROJ_REQUIREMENT_ANALYSIS',
        componentVersion: '1',
        dynamicFormRevisionId: undefined
      },
      expect.any(String)
    )
    expect(Api.publishBusinessView).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('refreshes a conflicting authoritative version and uses the new CAS/key only on explicit retry', async () => {
    const mounted = mount(BusinessViewManagement, {}, options)
    await tick()
    await click(mounted.root, '查看 / 管理')
    vi.mocked(Api.updateBusinessView).mockRejectedValueOnce({ code: 1010004002 })
    vi.mocked(Api.getBusinessView).mockResolvedValue(registration(2))
    await click(mounted.root, '保存草稿')
    expect(Api.updateBusinessView).toHaveBeenCalledTimes(1)
    expect(Api.updateBusinessView).toHaveBeenLastCalledWith(
      8,
      1,
      expect.objectContaining({ componentKey: 'PROJ_REQUIREMENT_ANALYSIS' }),
      expect.any(String)
    )
    expect(textOf(mounted.root)).toContain('版本冲突')
    vi.mocked(Api.updateBusinessView).mockResolvedValue(registration(3))
    await click(mounted.root, '保存草稿')
    expect(Api.updateBusinessView).toHaveBeenLastCalledWith(
      8,
      2,
      expect.anything(),
      expect.any(String)
    )
    expect(vi.mocked(Api.updateBusinessView).mock.calls[0][3]).not.toBe(
      vi.mocked(Api.updateBusinessView).mock.calls[1][3]
    )
    mounted.app.unmount()
  })
  it('uses server actions rather than supportedActions and never offers published-body editing', async () => {
    vi.mocked(Api.getBusinessView).mockResolvedValue({
      ...registration(4, ['QUERY']),
      status: 'PUBLISHED',
      supportedActions: ['UPDATE', 'PUBLISH', 'DISABLE']
    })
    const mounted = mount(BusinessViewManagement, {}, options)
    await tick()
    await click(mounted.root, '查看 / 管理')
    expect(button(mounted.root, '保存草稿')).toBeUndefined()
    expect(button(mounted.root, '发布')).toBeUndefined()
    expect(button(mounted.root, '停用')).toBeUndefined()
    expect(Api.createBusinessView).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('validates without publishing, then copies/publishes/disables only explicit permitted commands', async () => {
    const mounted = mount(BusinessViewManagement, {}, options)
    await tick()
    await click(mounted.root, '查看 / 管理')
    vi.mocked(Api.validateBusinessView).mockResolvedValue({
      valid: false,
      issues: [{ field: 'componentKey', code: 'UNAVAILABLE', message: '组件暂不可用' }]
    })
    await click(mounted.root, '校验')
    expect(textOf(mounted.root)).toContain('组件暂不可用')
    expect(Api.publishBusinessView).not.toHaveBeenCalled()
    vi.mocked(Api.copyBusinessView).mockResolvedValue({ ...registration(), id: 9, revisionNo: 2 })
    await click(mounted.root, '复制下一修订')
    expect(Api.copyBusinessView).toHaveBeenCalledWith(8, 1, expect.any(String))
    vi.mocked(Api.publishBusinessView).mockResolvedValue({
      ...registration(2, ['QUERY', 'COPY', 'DISABLE']),
      id: 9,
      revisionNo: 2,
      status: 'PUBLISHED'
    })
    await click(mounted.root, '发布')
    expect(Api.publishBusinessView).toHaveBeenCalledWith(9, 1, expect.any(String))
    vi.mocked(Api.disableBusinessView).mockResolvedValue({
      ...registration(3, ['QUERY', 'COPY']),
      id: 9,
      revisionNo: 2,
      status: 'DISABLED'
    })
    await click(mounted.root, '停用')
    expect(Api.disableBusinessView).toHaveBeenCalledWith(9, 2, expect.any(String))
    expect(button(mounted.root, '保存草稿')).toBeUndefined()
    mounted.app.unmount()
  })
  it('queries all pages before filtering exact identity history, including disabled revisions', async () => {
    const mounted = mount(BusinessViewManagement, {}, options)
    await tick()
    vi.mocked(Api.getBusinessViewPage)
      .mockResolvedValueOnce({ list: [{ ...registration(), viewKey: 'OTHER' }], total: 2 })
      .mockResolvedValueOnce({
        list: [{ ...registration(), id: 99, status: 'DISABLED' }],
        total: 2
      })
    await click(mounted.root, '历史')
    expect(Api.getBusinessViewPage).toHaveBeenLastCalledWith({
      pageNo: 2,
      pageSize: 100,
      entityType: 'REQUIREMENT_ANALYSIS'
    })
    expect(textOf(mounted.root)).toContain('已停用')
    expect(textOf(mounted.root)).not.toContain('OTHER')
    mounted.app.unmount()
  })
})
