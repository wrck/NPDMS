import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import SolutionChapterForm from './SolutionReviewChapterForm.vue'
import * as RequirementApi from '@/api/pms/engineering/requirement-analysis/entity'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ workspace: vi.fn() }))
vi.mock('@/api/pms/project/unified-members', () => ({ getMemberPage: vi.fn(async () => ({ list: [], total: 0 })), logicalMemberRole: (role: string) => role }))
vi.mock('@/api/pms/customer/contacts', () => ({ getProjectPage: vi.fn(async () => ({ list: [] })) }))
vi.mock('@/api/pms/asset/device/archive', () => ({ getDeviceArchivePage: vi.fn(async () => ({ list: [] })) }))
vi.mock('@/api/pms/commerce', () => ({ getDeliveryScopePage: vi.fn(async () => ({ list: [] })) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: () => '', getIntDictOptions: () => [] }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/DeliveryArtifact/uploadDeliveryFile', () => ({ uploadDeliveryFile: vi.fn() }))
vi.mock('@/views/pms/engineering/solution/saveGeneratedSolutionHtml', () => ({ prepareGeneratedSolutionHtml: vi.fn() }))

vi.mock('@/api/pms/engineering/stage-plan', () => ({ getStagePlanBatchPage: vi.fn(async () => ({ list: [] })) }))
vi.mock('./saveCustomerSolutionDocuments', () => ({ resolveCustomerSolutionDocument: vi.fn(), downloadCustomerSolutionDocument: vi.fn(), customerSolutionDocumentUrls: () => [], customerSolutionDocumentName: () => '' }))

const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
const overview = (values: Record<string, unknown>, draft?: Record<string, unknown>) => ({
  currentEffective: { values, revision: { effective: true, state: 'FROZEN', revisionNo: 2, version: 3, ref: { entity: { tenantId: 1, ownerModule: 'SOL', entityType: 'REQUIREMENT_ANALYSIS', entityId: 300 }, revisionId: 400 } } }, draft: draft ? { values: draft } : null
}) as any
const render = (projectId?: number, readOnly = false) => {
  const form = ref<any>({ projectId })
  let state: any
  const wrapper = defineComponent({ setup: () => () => h(SolutionChapterForm, {
    modelValue: form.value, 'onUpdate:modelValue': value => { form.value = value }, readOnly,
    ref: value => { if (value) state = (value as any).$.setupState }
  }) })
  const components = Object.fromEntries(['ElRow', 'ElCol', 'ElInput', 'ElSelect', 'ElOption', 'ElRadioGroup', 'ElRadio', 'ElCheckboxGroup', 'ElCheckbox', 'ElTable', 'ElLink', 'ElUpload', 'Editor', 'UploadFile'].map(name => [name, passthrough]))
  const mounted = mount(wrapper, {}, { ...components, ElTableColumn: tableColumn })
  return { ...mounted, form, get state() { return state } }
}

describe('solution requirement analysis references', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(RequirementApi.workspace).mockResolvedValue(overview({})) })

  it('imports current business fields and structured rows, preferring the effective revision over the draft', async () => {
    vi.mocked(RequirementApi.workspace).mockResolvedValue(overview({
      projectBackground: '<p>已冻结背景</p>', projectObjective: '<p>项目目标</p>', networkTopology: '<p>网络拓扑</p>',
      transmissionCurrentOptions: ['IPv6'], trafficNewConnections: '100/s',
      businessDeviceDetails: [{ deviceName: '设备A', serialNumber: 'SN-A', businessName: '业务A', businessSubnet: '10.0.0.0/24', businessImportance: '高', interfaces: 'GE1', customerBusinessOwner: '负责人', remark: '备注' }]
    }, { projectBackground: '未冻结草稿' }))
    const mounted = render(1)
    try {
      await flush()
      expect(RequirementApi.workspace).toHaveBeenCalledWith(1)
      const referenceButtons = (node: TestNode): TestNode[] => [
        ...(node.type === 'button' && textOf(node) === '从 2.3 需求分析引用' ? [node] : []),
        ...node.children.flatMap(referenceButtons)
      ]
      const buttons = referenceButtons(mounted.root)
      expect(buttons).toHaveLength(3)
      expect(buttons.every(button => button.props?.disabled === false)).toBe(true)
      ;(buttons[0].props!.onClick as () => void)()
      ;(buttons[1].props!.onClick as () => void)()
      expect(mounted.form.value).toMatchObject({ background: '<p>已冻结背景</p>', target: '<p>项目目标</p>' })
      expect(mounted.state.reqTopology).toBe('<p>网络拓扑</p>')
      expect(mounted.state.reqTransmission).toEqual(['IPv6'])
      mounted.state.generateFromSources()
      await flush()
      expect(mounted.form.value).toMatchObject({ background: '<p>已冻结背景</p>', target: '<p>项目目标</p>' })
      expect(JSON.parse(mounted.form.value.remark).business[0]).toEqual({ deviceName: '设备A', sn: 'SN-A', businessName: '业务A', network: '10.0.0.0/24', level: '高', ports: 'GE1', owner: '负责人', remark: '备注' })
      mounted.form.value.background = '人工编辑'
      mounted.state.generateFromSources()
      expect(mounted.form.value.background).toBe('人工编辑')
    } finally { mounted.app.unmount() }
  })

  it('loads when a project is selected after mounting and rejects a late response from the previous project', async () => {
    let resolveOld!: (value: any) => void
    vi.mocked(RequirementApi.workspace).mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce(overview({ projectBackground: '项目二' }))
    const mounted = render()
    try {
      expect(RequirementApi.workspace).not.toHaveBeenCalled()
      mounted.form.value.projectId = 1; await flush()
      mounted.form.value.projectId = 2; await flush()
      resolveOld(overview({ projectBackground: '项目一' })); await flush()
      expect(mounted.state.reqBackground).toBe('项目二')
      mounted.form.value.projectId = undefined; await flush()
      expect(mounted.state.reqBackground).toBe('')
    } finally { mounted.app.unmount() }
  })

  it('shows loading failure, clears prior content and supports retry using the current project', async () => {
    vi.mocked(RequirementApi.workspace).mockRejectedValueOnce(new Error('403'))
    const mounted = render(1)
    try {
      await flush()
      expect(textOf(mounted.root)).toContain('需求分析引用内容加载失败')
      expect(mounted.state.reqBackground).toBe('')
      vi.mocked(RequirementApi.workspace).mockResolvedValueOnce({ currentEffective: null, draft: { values: { projectBackground: '草稿背景' } } } as any)
      await mounted.state.loadRequirement(); await flush()
      expect(mounted.state.requirementError).toBe('')
      expect(mounted.state.reqBackground).toBe('草稿背景')
    } finally { mounted.app.unmount() }
  })

  it('does not import into read-only records', async () => {
    vi.mocked(RequirementApi.workspace).mockResolvedValue(overview({ projectBackground: '来源背景' }))
    const mounted = render(1, true)
    try {
      await flush(); mounted.state.generateFromSources()
      expect(mounted.form.value.background).toBeUndefined()
      expect(RequirementApi.workspace).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })
})
