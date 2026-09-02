import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CutoverPlanView } from '@/api/pms/cutover/cutover-task'
import CutoverPlanPanel from './components/CutoverPlanPanel.vue'
import CutoverPlanEditor from './components/CutoverPlanEditor.vue'
import CutoverWorkbenchSteps from './components/CutoverWorkbenchSteps.vue'
import {
  findByTestId,
  mount,
  passthrough,
  textOf,
  type TestNode
} from '../../platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({
  getCutoverPlan: vi.fn(),
  createCutoverPlanDraft: vi.fn(),
  saveCutoverPlanDraft: vi.fn(),
  downloadCutoverPlanDraft: vi.fn(),
  submitCutoverPlan: vi.fn(),
  patchApprovedCutoverPlanContact: vi.fn(),
  reviseCutoverPlan: vi.fn()
}))
const fileApi = vi.hoisted(() => ({ getArtifact: vi.fn(), getVersions: vi.fn(), createAccessTicket: vi.fn() }))
vi.mock('@/api/pms/cutover/cutover-task', () => api)
vi.mock('@/api/pms/platform/file', () => fileApi)
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() })
}))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: defineComponent({
    inheritAttrs: false,
    emits: ['completed'],
    setup(_, { attrs, emit }) {
      return () => h('button', {
        ...attrs,
        onClick: () => emit('completed', {
          artifactId: '9007199254740993', versionNo: 4,
          referenceKey: String(attrs.referenceKey ?? attrs['reference-key'])
        })
      }, 'upload')
    }
  })
}))

const controls = {
  ElInput: passthrough,
  ElDatePicker: passthrough,
  ElRadioGroup: passthrough,
  ElRadioButton: passthrough,
  ElCheckbox: passthrough,
  ElDescriptions: passthrough,
  ElDescriptionsItem: passthrough
}

describe('F-CUT-004 mounted plan workbench', () => {
  const accessTarget = { opener: {} as unknown, location: { replace: vi.fn() }, close: vi.fn() }
  beforeEach(() => {
    vi.clearAllMocks()
    accessTarget.location.replace.mockReset()
    accessTarget.close.mockReset()
    vi.stubGlobal('window', { open: vi.fn(() => accessTarget) })
  })

  it('edits and saves the complete A/B/C standard content exposed by server actions', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT', 'SUBMIT_PLAN']))
    api.saveCutoverPlanDraft.mockResolvedValue(standardPlan(['SAVE_DRAFT', 'SUBMIT_PLAN']))
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    const description = findByTestId(mounted.root, 'plan-project-description')!
    await (description.props?.['onUpdate:modelValue'] as (value: string) => void)('核心网割接实施方案')
    const operation = findByTestId(mounted.root, 'plan-step-OPERATION-1')!
    await (operation.props?.['onUpdate:modelValue'] as (value: string) => void)('执行割接命令')
    await click(mounted.root, 'save-plan')
    await flush()

    expect(api.saveCutoverPlanDraft).toHaveBeenCalledWith(
      '101', 7, 1,
      expect.objectContaining({
        editMode: 'ONLINE_TEMPLATE_STANDARD',
        overview: expect.objectContaining({ projectDescription: '核心网割接实施方案' }),
        steps: expect.arrayContaining([expect.objectContaining({ sectionCode: 'OPERATION', content: '执行割接命令' })]),
        supportArrangements: expect.arrayContaining([expect.objectContaining({ roleCode: 'CUSTOMER' })])
      }),
      expect.any(String)
    )
    expect(textOf(mounted.root)).toContain('A/B/C 标准方案')
    mounted.app.unmount()
  })

  it('renders D simple content and preserves only operation and rollback edits', async () => {
    const content = {
      editMode: 'ONLINE_TEMPLATE_SIMPLE_D' as const,
      steps: [
        { sectionCode: 'OPERATION' as const, stepNo: 1, content: '阶段操作' },
        { sectionCode: 'ROLLBACK' as const, stepNo: 1, content: '原路回退' }
      ]
    }
    const updates: unknown[] = []
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content,
      sourceSnapshot: null,
      taskId: '101',
      editable: true,
      'onUpdate:modelValue': (value: unknown) => updates.push(value)
    }, controls)

    const rollback = findByTestId(mounted.root, 'plan-step-ROLLBACK-1')!
    await (rollback.props?.['onUpdate:modelValue'] as (value: string) => void)('恢复原配置')
    expect(updates.at(-1)).toEqual({
      editMode: 'ONLINE_TEMPLATE_SIMPLE_D',
      steps: [
        { sectionCode: 'OPERATION', stepNo: 1, content: '阶段操作' },
        { sectionCode: 'ROLLBACK', stepNo: 1, content: '恢复原配置' }
      ]
    })
    expect(textOf(mounted.root)).toContain('D 级简易方案')
    mounted.app.unmount()
  })

  it('freezes PLT file facts and creates the uploaded plan without converting Snowflake ids', async () => {
    api.getCutoverPlan
      .mockResolvedValueOnce(emptyPlan())
      .mockResolvedValue(standardPlan([]))
    api.createCutoverPlanDraft.mockResolvedValue(standardPlan([]))
    fileApi.getArtifact.mockResolvedValue({ artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'a'.repeat(64) }] })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    const mode = findByTestId(mounted.root, 'plan-create-mode')!
    await (mode.props?.['onUpdate:modelValue'] as (value: string) => void)('FULL_FILE_UPLOAD')
    await nextTick()
    await (findByTestId(mounted.root, 'create-plan-uploader')!.props?.onClick as () => Promise<void>)()
    await flush()
    const ownership = findByTestId(mounted.root, 'create-plan-ownership')!
    await (ownership.props?.['onUpdate:modelValue'] as (value: boolean) => void)(true)
    await nextTick()
    await click(mounted.root, 'create-plan')
    await flush()

    expect(api.createCutoverPlanDraft).toHaveBeenCalledWith('101', 7, {
      editMode: 'FULL_FILE_UPLOAD',
      fileArtifactFact: {
        artifactId: '9007199254740993',
        versionNo: 4,
        referenceKey: 'cutover-plan-101',
        scopeVersion: 17,
        sha256: 'a'.repeat(64),
        fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 }
      },
      ownershipConfirmed: true
    }, expect.any(String))
    expect(typeof api.createCutoverPlanDraft.mock.calls[0][2].fileArtifactFact.artifactId).toBe('string')
    mounted.app.unmount()
  })

  it('executes download, submit and rejected revision only from server allowedActions', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['DOWNLOAD_DRAFT', 'SUBMIT_PLAN', 'REVISE_PLAN'], 'REJECTED'))
    api.downloadCutoverPlanDraft.mockResolvedValue({
      planRevisionId: '501', planVersion: 1,
      fileArtifactFact: uploadedFact(), downloadedAt: 1788220800000
    })
    api.submitCutoverPlan.mockResolvedValue({ taskId: '101' })
    api.reviseCutoverPlan.mockResolvedValue(standardPlan([]))
    fileApi.createAccessTicket.mockResolvedValue({ shortLivedUrl: 'https://download.local/ticket' })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    await click(mounted.root, 'download-plan'); await flush()
    await click(mounted.root, 'submit-plan'); await flush()
    await click(mounted.root, 'revise-plan'); await flush()

    expect(api.downloadCutoverPlanDraft).toHaveBeenCalledWith('101', 1, expect.any(String))
    expect(fileApi.createAccessTicket).toHaveBeenCalledWith(
      '9007199254740993', 4, 'DOWNLOAD',
      expect.objectContaining({ ownerContext: 'CUT', purposeCode: 'FULL_PLAN', referenceKey: 'cutover-plan-file' })
    )
    expect(accessTarget.location.replace).toHaveBeenCalledWith('https://download.local/ticket')
    expect(api.submitCutoverPlan).toHaveBeenCalledWith('101', 7, 1, expect.any(String))
    expect(api.reviseCutoverPlan).toHaveBeenCalledWith('101', 7,
      { sourcePlanRevisionId: '501', reason: 'APPROVAL_REJECTED' }, expect.any(String))
    mounted.app.unmount()
  })

  it('patches an approved P6 support contact with the frozen arrangement identity', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['UPDATE_APPROVED_CONTACTS'], 'APPROVED', 'P6'))
    api.patchApprovedCutoverPlanContact.mockResolvedValue(standardPlan([]))
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 9, manualGrade: 'A' }, controls)
    await flush()

    const name = findByTestId(mounted.root, 'support-name-CUSTOMER')!
    expect(name.props?.disabled).toBe(false)
    await (name.props?.['onUpdate:modelValue'] as (value: string) => void)('新客户经理')
    await nextTick()
    await click(mounted.root, 'patch-support-CUSTOMER')
    await flush()

    expect(api.patchApprovedCutoverPlanContact).toHaveBeenCalledWith('101', '601', 1, {
      personName: '新客户经理', phone: '13800000000', arrivalTime: 1788220800000
    }, expect.any(String))
    mounted.app.unmount()
  })

  it('preserves multiple ordered steps in the same section when one step is edited', async () => {
    const content = standardPlan([]).content!
    if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    content.steps = [
      { sectionCode: 'OPERATION', stepNo: 1, content: '第一步' },
      { sectionCode: 'OPERATION', stepNo: 2, content: '第二步' }
    ]
    const updates: any[] = []
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: true,
      'onUpdate:modelValue': (value: unknown) => updates.push(value)
    }, controls)

    const second = findByTestId(mounted.root, 'plan-step-OPERATION-2')!
    await (second.props?.['onUpdate:modelValue'] as (value: string) => void)('调整后的第二步')

    expect(updates.at(-1).steps).toEqual([
      { sectionCode: 'OPERATION', stepNo: 1, content: '第一步' },
      { sectionCode: 'OPERATION', stepNo: 2, content: '调整后的第二步' }
    ])
    mounted.app.unmount()
  })

  it('fills all three standard plan file slots with PLT facts', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT']))
    api.saveCutoverPlanDraft.mockResolvedValue(standardPlan([]))
    fileApi.getArtifact.mockResolvedValue({ artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'b'.repeat(64) }] })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    for (const field of ['preTopologyFile', 'postTopologyFile', 'networkConfigurationFile']) {
      await click(mounted.root, `plan-file-${field}`)
      await flush()
    }
    await click(mounted.root, 'save-plan')
    await flush()

    expect(api.saveCutoverPlanDraft).toHaveBeenCalledWith('101', 7, 1,
      expect.objectContaining({ overview: expect.objectContaining({
        preTopologyFile: expect.objectContaining({ referenceKey: 'cutover-plan-101-pre-topology' }),
        postTopologyFile: expect.objectContaining({ referenceKey: 'cutover-plan-101-post-topology' }),
        networkConfigurationFile: expect.objectContaining({ referenceKey: 'cutover-plan-101-network-configuration' })
      }) }), expect.any(String))
    mounted.app.unmount()
  })

  it('renders migrated legacy plan steps without exposing a write action', async () => {
    api.getCutoverPlan.mockResolvedValue({
      ...standardPlan([]),
      originCode: 'LEGACY_FORWARD',
      status: 'LEGACY_READ_ONLY',
      content: {
        editMode: 'LEGACY_READ_ONLY',
        steps: [{ sectionCode: 'OPERATION', stepNo: 1, content: '历史割接操作记录' }]
      }
    })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 9, manualGrade: 'A' }, controls)
    await flush()

    expect(textOf(mounted.root)).toContain('历史割接操作记录')
    expect(findByTestId(mounted.root, 'save-plan')).toBeUndefined()
    mounted.app.unmount()
  })

  it('retries only the authoritative refresh after a successful save response', async () => {
    api.getCutoverPlan.mockReset()
    api.saveCutoverPlanDraft.mockReset()
    api.getCutoverPlan
      .mockResolvedValueOnce(standardPlan(['SAVE_DRAFT']))
      .mockRejectedValueOnce(new Error('refresh unavailable'))
      .mockResolvedValue(standardPlan(['SAVE_DRAFT']))
    api.saveCutoverPlanDraft.mockResolvedValue(standardPlan(['SAVE_DRAFT']))
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    await click(mounted.root, 'save-plan'); await flush()
    expect(api.saveCutoverPlanDraft).toHaveBeenCalledTimes(1)
    await click(mounted.root, 'save-plan'); await flush()
    expect(api.saveCutoverPlanDraft).toHaveBeenCalledTimes(1)
    await click(mounted.root, 'save-plan'); await flush()
    expect(api.saveCutoverPlanDraft).toHaveBeenCalledTimes(2)
    mounted.app.unmount()
  })

  it.each([320, 768, 1024, 1440])('mounts every workbench stage at %ipx', (width) => {
    vi.stubGlobal('innerWidth', width)
    const mounted = mount(CutoverWorkbenchSteps, {
      steps: [
        { stage: 'P2', label: '分级评估', state: 'COMPLETED' },
        { stage: 'P3', label: '现场调研', state: 'COMPLETED' },
        { stage: 'P4', label: '方案编制', state: 'CURRENT' },
        { stage: 'P5', label: '方案审批', state: 'FUTURE' },
        { stage: 'P6', label: '割接执行', state: 'FUTURE' }
      ]
    })

    expect(collect(mounted.root, (node) => node.type === 'li')).toHaveLength(5)
    expect(textOf(mounted.root)).toContain('P4方案编制当前阶段')
    mounted.app.unmount()
  })
})

const standardPlan = (
  allowedActions: CutoverPlanView['allowedActions'],
  approvalStatus?: 'REJECTED' | 'APPROVED',
  taskStage: 'P4' | 'P6' = 'P4'
): CutoverPlanView => ({
  taskId: '101', taskStage, taskVersion: 7, planRevisionId: '501', revisionNo: 1, planVersion: 1,
  originCode: 'NEW_PLATFORM', status: 'DRAFT', legacyPlanId: null, legacyStatusRaw: null,
  sourcePlanRevisionId: null, revisionReason: 'INITIAL', sourceSnapshot: sourceSnapshot(),
  content: {
    editMode: 'ONLINE_TEMPLATE_STANDARD',
    overview: {
      projectDescription: '原方案',
      scheduleTable: [{ sequenceNo: 1, plannedAt: 1788220800000, content: '窗口开始' }],
      preTopologyFile: null, postTopologyFile: null,
      deviceSummary: sourceSnapshot().devices,
      networkConfigurationFile: null
    },
    steps: [{ sectionCode: 'OPERATION', stepNo: 1, content: '原操作' }],
    riskMitigations: [],
    supportArrangements: [{
      arrangementId: '601', roleCode: 'CUSTOMER', personName: '客户经理',
      dutyDescription: '现场确认', phone: '13800000000', arrivalTime: 1788220800000
    }]
  },
  approvalFact: approvalStatus ? {
    approvalInstanceId: '701', approvalVersion: 2, status: approvalStatus,
    decisionAt: 1788220800000, rejectionReason: approvalStatus === 'REJECTED' ? '请修订' : null
  } : null,
  allowedActions
})

const emptyPlan = (): CutoverPlanView => ({
  taskId: '101', taskStage: 'P4', taskVersion: 7, planRevisionId: null, revisionNo: null,
  planVersion: null, originCode: null, status: null, legacyPlanId: null, legacyStatusRaw: null,
  sourcePlanRevisionId: null, revisionReason: null, sourceSnapshot: null, content: null,
  approvalFact: null, allowedActions: ['CREATE_DRAFT']
})

const sourceSnapshot = () => ({
  snapshotVersion: 1, taskId: '101', taskVersion: 7, assessmentId: '201', assessmentVersion: 2,
  grade: 'A' as const, checklistId: '301', checklistVersion: 3, projectId: '401', projectVersion: 4,
  projectScopeVersion: '5',
  devices: [{ deviceId: '9007199254740995', serialNumber: 'SN-001', projectAssignmentVersion: '8', deviceTypeCode: 'ROUTER', deviceTypeSourceVersion: 'v1' }],
  configurationRevisionId: '501', configurationCode: 'CORE_STANDARD', configurationRevisionNo: 1,
  templateSections: [], failedRiskFacts: []
})
const uploadedFact = () => ({
  artifactId: '9007199254740993', versionNo: 4, referenceKey: 'cutover-plan-file',
  fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 },
  scopeVersion: 17, sha256: 'a'.repeat(64)
})

const click = async (root: any, id: string) => {
  const node = findByTestId(root, id)
  expect(node, id).toBeDefined()
  await (node!.props?.onClick as () => Promise<void>)()
}
const collect = (node: TestNode, predicate: (candidate: TestNode) => boolean): TestNode[] => [
  ...(predicate(node) ? [node] : []),
  ...node.children.flatMap((child) => collect(child, predicate))
]
const flush = async () => {
  await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick()
}
