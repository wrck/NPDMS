import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import SolutionChapterForm from './SolutionReviewChapterForm.vue'
import { loadProjectEquipmentList } from '@/api/pms/commerce/projectEquipment'
import * as RequirementApi from '@/api/pms/engineering/requirement-analysis/entity'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ workspace: vi.fn(), read: vi.fn() }))
vi.mock('./saveSolutionScripts', () => ({ downloadSolutionScript: vi.fn() }))
vi.mock('@/api/pms/project/unified-members', () => ({ getMemberPage: vi.fn(async () => ({ list: [], total: 0 })), logicalMemberRole: (role: string) => role }))
vi.mock('@/api/pms/customer/contacts', () => ({ getProjectPage: vi.fn(async () => ({ list: [] })) }))
vi.mock('@/api/pms/asset/device/archive', () => ({ getDeviceArchivePage: vi.fn(async () => ({ list: [] })) }))
vi.mock('@/api/pms/engineering/site-survey/entity', () => ({ getSiteSurveyPage: vi.fn(async () => ({ list: [], total: 0 })), getSiteSurvey: vi.fn() }))
vi.mock('@/api/pms/commerce/projectEquipment', () => ({ loadProjectEquipmentList: vi.fn(async () => ({ list: [], scopes: [] })) }))
vi.mock('@/api/pms/commerce', () => ({ getDeliveryScopePage: vi.fn(async () => ({ list: [] })) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: () => '', getIntDictOptions: () => [] }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/DeliveryArtifact/uploadDeliveryFile', () => ({ uploadDeliveryFile: vi.fn() }))
vi.mock('@/views/pms/engineering/solution/saveGeneratedSolutionHtml', () => ({ prepareGeneratedSolutionHtml: vi.fn() }))

vi.mock('@/api/pms/engineering/stage-plan', () => ({ getStagePlanBatchPage: vi.fn(async () => ({ list: [] })) }))
vi.mock('./saveCustomerSolutionDocuments', () => ({ resolveCustomerSolutionDocument: vi.fn(), downloadCustomerSolutionDocument: vi.fn(), customerSolutionDocumentUrls: () => [], customerSolutionDocumentName: () => '' }))

const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
const overview = (values: Record<string, unknown>, draft?: Record<string, unknown>) => ({
  currentEffective: { values, revision: { ref: { revisionId: '7001' }, revisionNo: 1 } }, draft: draft ? { values: draft } : null
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
      expect(mounted.state.reqValues.transmissionCurrentOptions).toEqual(['IPv6'])
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
      expect(mounted.state.requirementError).toBe('PRE-04 尚未形成有效的已完成需求分析版本')
      expect(mounted.state.reqBackground).toBe('')
    } finally { mounted.app.unmount() }
  })


  it('serializes separate deployment and software chapters with order quantity and reopens saved content', async () => {
    vi.mocked(loadProjectEquipmentList).mockResolvedValueOnce({ list: [{ productCode: 'P1', productName: '防火墙', productModel: 'FW', orderQty: 12, projectQty: 20, deviceCount: 3 }], scopes: [] })
    const mounted = render(1)
    try {
      await flush(); mounted.state.generateFromSources(); await flush()
      const topology = JSON.parse(mounted.form.value.topology)
      expect(topology.deploy[0]).toMatchObject({ productCode: 'P1', deviceName: '防火墙', quantity: '12' })
      mounted.state.topology.resourceConfirmation = '<p>手工确认资源</p>'
      mounted.state.tables.deploy[0].location = '机柜/U2'
      mounted.state.tables.software[0].version = 'v3'; await flush()
      const saved = mounted.form.value.topology
      mounted.form.value = { id: 99, projectId: 1, topology: saved }; await flush()
      expect(mounted.state.tables.deploy[0]).toMatchObject({ productCode: 'P1', quantity: '12', location: '机柜/U2' })
      expect(mounted.state.tables.software[0]).toMatchObject({ productCode: 'P1', quantity: '12', version: 'v3' })
      expect(mounted.state.topology.resourceConfirmation).toBe('<p>手工确认资源</p>')
      expect(mounted.state.buildSolutionHtml()).toContain('<p>手工确认资源</p>')
      expect(mounted.state.buildSolutionHtml()).toContain('数量（订单数量）')
      expect(mounted.state.buildSolutionHtml()).toContain('3.5 软件版本')
    } finally { mounted.app.unmount() }
  })

  it('does not import into read-only records', async () => {
    vi.mocked(RequirementApi.workspace).mockResolvedValue(overview({ projectBackground: '来源背景' }))
    const mounted = render(1, true)
    try {
      await flush(); mounted.state.generateFromSources()
      expect(mounted.form.value.background).toBeUndefined()
      expect(mounted.form.value.sectionData).toBeUndefined()
    } finally { mounted.app.unmount() }
  })

  it('reopens the pinned source and requires an explicit choice when effective input changes', async () => {
    vi.mocked(RequirementApi.read).mockResolvedValue({ values: { projectBackground: '旧版输入' } } as any)
    const mounted = render(1)
    try {
      await flush()
      mounted.form.value = { id: 42, projectId: 1, sectionData: JSON.stringify({ requirementInput: { revisionId: '6001', revisionNo: 1 } }) }
      await flush()
      expect(RequirementApi.read).toHaveBeenCalledWith('6001')
      expect(mounted.state.reqBackground).toBe('旧版输入')
      expect(mounted.state.inputChanged).toBe(true)
      mounted.state.differenceReason = '本次需求变动不涉及此实施范围'
      mounted.state.keepRequirementInput()
      expect(JSON.parse(mounted.form.value.sectionData).requirementInput).toMatchObject({ revisionId: '6001', acknowledgedRevisionId: '7001', differenceReason: '本次需求变动不涉及此实施范围' })
      mounted.form.value.background = '人工修改内容'
      mounted.state.refreshRequirementInput()
      expect(JSON.parse(mounted.form.value.sectionData).requirementInput.revisionId).toBe('7001')
      expect(mounted.form.value.background).toBe('人工修改内容')
    } finally { mounted.app.unmount() }
  })
})
