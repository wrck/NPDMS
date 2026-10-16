import { defineComponent, h, nextTick, reactive } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CutoverTaskWorkbench from './index.vue'
import CutoverTaskDetail from '../task-detail/index.vue'
import {
  button,
  findByTestId,
  mount,
  passthrough,
  textOf,
  type TestNode
} from '../cutoverTestHarness'

const trace: string[] = []
const runtime = {
  stage: 'P1',
  approvalNode: 1,
  closureStatus: 'DRAFT'
}

const api = vi.hoisted(() => ({
  resolveCreateContext: vi.fn(),
  resolveCreationDevices: vi.fn(),
  searchCreationProjects: vi.fn(),
  getCreationProject: vi.fn(),
  getCreationOptions: vi.fn(),
  createCutoverTask: vi.fn(),
  getCutoverDashboardKpis: vi.fn(),
  getCutoverTaskPage: vi.fn(),
  getCutoverTaskDetail: vi.fn(),
  getCutoverRevisionOptions: vi.fn(),
  reviseCutoverTask: vi.fn(),
  getCutoverApprovalTodos: vi.fn(),
  getCutoverApprovalReassignmentCandidates: vi.fn(),
  saveCutoverAssessment: vi.fn(),
  submitCutoverAssessment: vi.fn(),
  getCutoverChecklistMatchingOptions: vi.fn(),
  getCutoverChecklistRevisions: vi.fn(),
  getCutoverExternalState: vi.fn(async () => null),
  getCutoverChecklist: vi.fn(),
  generateCutoverChecklist: vi.fn(),
  saveCutoverChecklist: vi.fn(),
  saveManualChecklistResult: vi.fn(),
  addCustomChecklistItem: vi.fn(),
  removeCustomChecklistItem: vi.fn(),
  requestChecklistCollection: vi.fn(),
  submitCutoverChecklist: vi.fn(),
  getProjectContactChoices: vi.fn(async () => []),
  getCutoverPlan: vi.fn(),
  createCutoverPlanDraft: vi.fn(),
  saveCutoverPlanDraft: vi.fn(),
  downloadCutoverPlanDraft: vi.fn(),
  submitCutoverPlan: vi.fn(),
  patchApprovedCutoverPlanContact: vi.fn(),
  reviseCutoverPlan: vi.fn(),
  getCutoverApproval: vi.fn(),
  approveCutoverApproval: vi.fn(),
  rejectCutoverApproval: vi.fn(),
  reassignCutoverApproval: vi.fn(),
  getCutoverClosure: vi.fn(),
  getResultProgress: vi.fn(),
  saveCutoverClosure: vi.fn(),
  requestCutoverClosureCollection: vi.fn(),
  linkCutoverClosureManualResult: vi.fn(),
  submitCutoverClosure: vi.fn()
}))
const fileApi = vi.hoisted(() => ({
  getArtifact: vi.fn(),
  getVersions: vi.fn(),
  createAccessTicket: vi.fn()
}))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))
const route = reactive({ path: '/pms/cutover/workbench', query: {} as Record<string, string> })
vi.mock('vue-router', () => ({ useRouter: () => router, useRoute: () => route,
  onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
const selectionApi = vi.hoisted(() => ({ searchProjectOptions: vi.fn(), getProjectContext: vi.fn(), getProjectDevicePage: vi.fn() }))
vi.mock('@/api/pms/project/selection', () => selectionApi)
vi.mock('@/api/pms/asset/device', () => ({ getDevice: vi.fn(async (deviceId) => ({ summary: { id: deviceId, productCode: 'ROUTER-01', productModel: 'Router' } })) }))
vi.mock('@/components/DeviceCollection/BusinessCollectionDialog.vue', () => ({ default: defineComponent({ setup: () => () => null }) }))
vi.mock('@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue', () => ({ default: defineComponent({ setup: () => () => null }) }))

vi.mock('@/api/pms/cutover', () => api)
vi.mock('@/api/pms/platform/file/version-reference', () => fileApi)
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() })
}))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: defineComponent({
    setup: (_, { expose }) => {
      expose({ isBusy: () => false, hasPendingFile: () => false })
      return () => h('button', { 'data-testid': 'controlled-file-uploader' }, 'upload')
    }
  })
}))

const dialog = defineComponent({
  setup(_, { slots }) {
    return () => h('section', [slots.default?.(), slots.footer?.()])
  }
})

const workbenchTableColumn = defineComponent({
  inheritAttrs: false,
  setup(_, { slots }) {
    return () => h('span', slots.default?.({ row: taskSummary() }))
  }
})


const controls = {
  ElForm: defineComponent({ setup(_, { slots, expose }) {
    expose({ validate: async () => true })
    return () => h('section', slots.default?.())
  } }),
  Dialog: dialog,
  ElSteps: passthrough,
  ElStep: passthrough,
  ElInput: passthrough,
  ElInputNumber: passthrough,
  ElSelect: passthrough,
  ElOption: passthrough,
  ElRadioGroup: passthrough,
  ElRadio: passthrough,
  ElRadioButton: button,
  ElCheckbox: passthrough,
  ElDatePicker: passthrough,
  ElDescriptions: passthrough,
  ElDescriptionsItem: passthrough,
  ElResult: passthrough,
  ElTable: passthrough,
  ElTableColumn: workbenchTableColumn,
  ElDialog: dialog
}

afterEach(() => vi.unstubAllGlobals())

describe('CUT P1-P6 controlled UI integration', () => {
  it('requires explicit configuration selection and a reason before creating a revision', async () => {
    configureControlledApi()
    runtime.stage = 'P4'
    api.getCutoverTaskDetail.mockImplementation(async () => ({ ...taskDetail(), allowedActions: ['REVISE_CHECKLIST'] }))
    api.getCutoverRevisionOptions.mockResolvedValue([{ revisionId: '42', code: 'CORE_STANDARD', name: '核心网标准配置', revisionNo: 3 }])
    api.reviseCutoverTask.mockImplementation(async () => { runtime.stage = 'P3'; return {} })
    const mounted = mount(CutoverTaskDetail, { taskId: '101' }, controls)
    await flush()
    await clickLabel(mounted.root, '调研及风险考察')
    await flush()
    await click(mounted.root, 'revise-current-step')
    await flush()
    expect(api.getCutoverRevisionOptions).toHaveBeenCalledWith('101')
    expect(findByTestId(mounted.root, 'revision-configuration')?.props?.modelValue).toBe('')
    expect(findByTestId(mounted.root, 'confirm-step-revision')?.props?.disabled).toBe(true)
    expect(api.reviseCutoverTask).not.toHaveBeenCalled()
    await update('revision-configuration', '42', mounted.root)
    await update('revision-reason', '采用校正后的检查规则', mounted.root)
    await click(mounted.root, 'confirm-step-revision')
    await flush()
    expect(api.reviseCutoverTask).toHaveBeenCalledWith(expect.objectContaining({ taskId: '101', targetStage: 'P3',
      data: { expectedProjectScopeVersion: '12', reason: '采用校正后的检查规则', configurationRevisionId: '42' },
      idempotencyKey: expect.any(String) }))
    expect(runtime.stage).toBe('P3')
    mounted.app.unmount()
  })

  it('runs the existing mounted workbench components through one A-grade archived task', async () => {
    configureControlledApi()
    const mounted = mount(CutoverTaskWorkbench, {}, controls)

    await flush()
    await click(mounted.root, 'create-cutover-task')
    await flush()
    expect(router.push).toHaveBeenLastCalledWith({ path: '/pms/cutover/workbench', query: { create: '1' } })
    route.query = { create: '1' }
    const creation = mounted
    await flush()
    await update('create-serials', ' sn-001 ', creation.root)
    await click(creation.root, 'resolve-create-serials')
    await flush()
    for (const id of ['create-task-name', 'create-background', 'create-cutover-type', 'create-scheduled-time']) {
      expect(findByTestId(creation.root, id)).toBeUndefined()
    }
    await click(creation.root, 'submit-create-detail')
    await flush()
    expect(api.createCutoverTask).toHaveBeenCalledTimes(1)
    expect(api.createCutoverTask.mock.calls[0][0]).toMatchObject({
      data: { projectId: '9007199254741001', serialNumbers: ['SN-001'] }, idempotencyKey: expect.any(String)
    })
    for (const field of ['taskName', 'background', 'cutoverType', 'scheduledTime']) {
      expect(api.createCutoverTask.mock.calls[0][0].data).not.toHaveProperty(field)
    }
    expect(router.push).toHaveBeenLastCalledWith('/pms/cutover/tasks/101')
    creation.app.unmount()
    const detail = mount(CutoverTaskDetail, { taskId: '101' }, controls)
    await flush()

    await clickLabel(detail.root, '保存')
    await flush()
    await clickLabel(detail.root, '下一步')
    await flush()

    await click(detail.root, 'checklist-submit')
    await flush()
    await click(detail.root, 'submit-plan')
    await flush()

    for (let nodeNo = 1; nodeNo <= 4; nodeNo += 1) {
      await update('approval-feedback', `第${nodeNo}节点通过`, detail.root)
      await click(detail.root, 'approve-approval')
      await flush()
    }

    await click(detail.root, 'save-closure')
    await flush()
    await update('closure-final-result', 'SUCCESS', detail.root)
    await click(detail.root, 'submit-closure')
    await flush()

    expect(trace.filter((entry) => entry.startsWith('write:'))).toEqual([
      'write:P1:create',
      'write:P2:save',
      'write:P2:submit',
      'write:P3:submit',
      'write:P4:submit',
      'write:P5:approve:1',
      'write:P5:approve:2',
      'write:P5:approve:3',
      'write:P5:approve:4',
      'write:P6:save',
      'write:P6:submit:SUCCESS'
    ])
    expect(runtime.stage).toBe('P6')
    expect(runtime.closureStatus).toBe('SUBMITTED')
    expect(textOf(detail.root)).toContain('已归档')
    expect(textOf(detail.root)).toContain('cutover-closure:801:1')
    expect(api.getCutoverTaskPage).toHaveBeenCalledWith(expect.objectContaining({ listKind: 'TODO' }))
    expect(api.getCutoverTaskPage).toHaveBeenCalledWith(expect.objectContaining({ listKind: 'SELF_CREATED' }))
    expect(api.getCutoverDashboardKpis).toHaveBeenCalled()
    expect(api.getCutoverTaskDetail.mock.calls.length).toBeGreaterThanOrEqual(7)
    expect(api.approveCutoverApproval).toHaveBeenCalledWith(expect.objectContaining({ approvalId: '701', data: expect.any(Object), idempotencyKey: expect.any(String) }))
    expect(api.submitCutoverChecklist).toHaveBeenCalledWith(expect.objectContaining({ taskId: '101', data: expect.objectContaining({ expectedTaskVersion: 2 }), idempotencyKey: expect.any(String) }))
    detail.app.unmount()
  })
})

function configureControlledApi() {
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  route.query = {}
  trace.length = 0
  runtime.stage = 'P1'
  runtime.approvalNode = 1
  runtime.closureStatus = 'DRAFT'
  Object.values(api).forEach((mock) => mock.mockReset())
  router.push.mockClear()
  router.replace.mockClear()
  selectionApi.searchProjectOptions.mockResolvedValue([])
  selectionApi.getProjectContext.mockResolvedValue({ project: createContext().candidates[0].project, customerServiceLevel: 'GOLD' })
  selectionApi.getProjectDevicePage.mockResolvedValue({ list: [], total: 0 })
  api.getCutoverChecklistRevisions.mockResolvedValue([])
  api.getCutoverChecklistMatchingOptions.mockResolvedValue({ matchingInput: { cutoverType: 'CORE_REPLACEMENT', networkMode: null, deviceTypeCodes: ['ROUTER'] }, deviceTypes: [{ value: 'ROUTER', label: '路由器' }] })
  api.getCutoverExternalState.mockResolvedValue(null)
  api.getProjectContactChoices.mockResolvedValue([])
  api.getCreationOptions.mockResolvedValue([{ revisionId: '41', code: 'CORE_STANDARD', name: '核心网标准配置', revisionNo: 2, cutoverTypes: [{ value: 'CORE_REPLACEMENT', label: '核心替换' }], networkModes: [], deviceTypes: [{ value: 'ROUTER', label: '路由器' }] }])
  api.getResultProgress.mockResolvedValue({ assetReceipts: [], projectCoverage: { coveredDeviceIds: ['9007199254742001'], requiredDevices: [{ deviceId: '9007199254742001' }], complete: true } })

  api.resolveCreateContext.mockImplementation(async () => {
    trace.push('read:P1:owners')
    return createContext()
  })
  api.searchCreationProjects.mockResolvedValue([])
  api.getCreationProject.mockResolvedValue({ project: createContext().candidates[0].project, customerServiceLevel: 'GOLD' })
  api.resolveCreationDevices.mockImplementation(async () => ({ context: createContext(), devices: [
    { deviceId: '9007199254742001', serialNumber: 'SN-001', productCode: 'CODE-1', productModel: 'MODEL-1' }
  ] }))
  api.createCutoverTask.mockImplementation(async () => {
    trace.push('write:P1:create')
    runtime.stage = 'P2'
    return { taskId: '101', version: 1 }
  })
  api.getCutoverDashboardKpis.mockImplementation(async () => ({
    todoCount: runtime.stage === 'P1' || runtime.stage === 'P6' ? 0 : 1,
    archivedCount: runtime.closureStatus === 'SUBMITTED' ? 1 : 0,
    approvingCount: runtime.stage === 'P5' ? 1 : 0,
    rejectedPendingModificationCount: 0,
    generatedAt: 1788314400000
  }))
  api.getCutoverTaskPage.mockImplementation(async () => {
    trace.push(`read:workbench:list:${runtime.stage}`)
    return {
      list: runtime.stage === 'P1' ? [] : [taskSummary()],
      total: runtime.stage === 'P1' ? 0 : 1,
      pageNo: 1,
      pageSize: 20
    }
  })
  api.getCutoverTaskDetail.mockImplementation(async () => {
    trace.push(`read:workbench:detail:${runtime.stage}`)
    return taskDetail()
  })
  api.getCutoverApprovalTodos.mockResolvedValue({ list: [], total: 0, pageNo: 1, pageSize: 20 })
  api.getCutoverApprovalReassignmentCandidates.mockResolvedValue([])
  api.saveCutoverAssessment.mockImplementation(async () => {
    trace.push('write:P2:save')
    return { taskId: '101', taskVersion: 1, assessmentVersion: 1, assessmentRowVersion: 1 }
  })
  api.submitCutoverAssessment.mockImplementation(async () => {
    trace.push('write:P2:submit')
    runtime.stage = 'P3'
    return { taskId: '101', version: 2 }
  })
  api.getCutoverChecklist.mockImplementation(async () => {
    trace.push('read:P3:checklist')
    return checklistView()
  })
  api.submitCutoverChecklist.mockImplementation(async () => {
    trace.push('write:P3:submit')
    runtime.stage = 'P4'
    return { taskId: '101', taskVersion: 3 }
  })
  api.getCutoverPlan.mockImplementation(async () => {
    trace.push('read:P4:plan')
    return planView()
  })
  api.submitCutoverPlan.mockImplementation(async () => {
    trace.push('write:P4:submit')
    runtime.stage = 'P5'
    return { taskId: '101', taskVersion: 4, approvalInstanceId: '701' }
  })
  api.getCutoverApproval.mockImplementation(async () => {
    trace.push(`read:P5:approval:${runtime.approvalNode}`)
    return approvalView(runtime.approvalNode)
  })
  api.approveCutoverApproval.mockImplementation(async () => {
    trace.push(`write:P5:approve:${runtime.approvalNode}`)
    runtime.approvalNode += 1
    if (runtime.approvalNode > 4) runtime.stage = 'P6'
    return approvalView(runtime.approvalNode)
  })
  api.getCutoverClosure.mockImplementation(async () => {
    trace.push(`read:P6:closure:${runtime.closureStatus}`)
    return closureView()
  })
  api.saveCutoverClosure.mockImplementation(async () => {
    trace.push('write:P6:save')
    return closureView()
  })
  api.submitCutoverClosure.mockImplementation(
    async (command) => {
      trace.push(`write:P6:submit:${command.finalResult}`)
      runtime.closureStatus = 'SUBMITTED'
      return closureView()
    }
  )
}

const createContext = () => ({
  candidates: [
    {
      project: {
        projectId: '9007199254741001',
        projectVersion: 3,
        projectCode: 'P-001',
        projectName: '核心网扩容',
        customerId: '8001',
        customerCode: 'CUS-01',
        customerName: '示例客户',
        officeDepartmentId: '7001',
        officeCode: 'OFF-01',
        officeName: '华东办事处',
        projectScopeVersion: '12'
      },
      devices: [
        { deviceId: '9007199254742001', serialNumber: 'SN-001', projectAssignmentVersion: '6' }
      ],
      customerServiceLevel: {
        status: 'AVAILABLE',
        customerId: '8001',
        customerCode: 'CUS-01',
        customerName: '示例客户',
        serviceLevelRevisionId: '21',
        serviceLevelCode: 'GOLD',
        factVersion: '8',
        effectiveFrom: 1788105600000,
        effectiveTo: null
      },
      implementationReadiness: {
        snapshotId: '31',
        snapshotVersion: '4',
        decision: 'READY',
        projectId: '9007199254741001',
        deviceIds: ['9007199254742001'],
        sourceWatermark: { projectVersion: 3 },
        unmetCodes: []
      },
      createAllowed: true
    }
  ],
  selectionRequired: false,
  configurationChoices: [
    {
      configurationCode: 'CORE_STANDARD',
      configurationName: '核心网标准配置',
      revisionId: '41',
      revisionNo: 2,
      effectiveFrom: 1788105600000,
      effectiveTo: null
    }
  ],
  configurationSelectionRequired: false
})

const stageVersion = () => ({ P1: 0, P2: 1, P3: 2, P4: 3, P5: 4, P6: 5 })[runtime.stage] ?? 0

const taskStatus = () => {
  if (runtime.stage === 'P6') {
    return runtime.closureStatus === 'SUBMITTED' ? 'ARCHIVED' : 'IN_PROGRESS'
  }
  return runtime.stage === 'P1' ? 'DRAFT' : 'IN_PROGRESS'
}

const taskSummary = () => ({
  id: '101',
  taskNo: 'CUT-001',
  taskName: '核心网割接',
  projectId: '9007199254741001',
  projectName: '核心网扩容',
  intakeSourceType: 'SELF_CREATED',
  officeName: '华东办事处',
  generatedAt: 1788400800000,
  currentStage: runtime.stage === 'P1' ? 'P2' : runtime.stage,
  taskStatus: taskStatus(),
  manualGrade: runtime.stage === 'P1' ? null : 'A',
  version: stageVersion()
})

const taskDetail = () =>
  ({
    task: {
      ...taskSummary(),
      scheduledTime: 1788400800000
    },
    source: { intakeSourceType: 'SELF_CREATED' },
    project: { projectName: '核心网扩容', projectScopeVersion: '12' },
    devices: [
      { deviceId: '9007199254742001', serialNumber: 'SN-001', projectAssignmentVersion: '6' }
    ],
    assessment: {
      assessmentId: '201',
      assessmentVersion: 1,
      rowVersion: 1,
      manualGrade: 'A',
      answers: {
        businessImportanceLevel: 'HIGH',
        operationComplexityLevel: 'MEDIUM',
        hiddenRiskLevel: 'LOW',
        sparePartApplied: true
      }
    },
    customerServiceLevel: { serviceLevelCode: 'GOLD' },
    implementationReadiness: { decision: 'READY' },
    workbenchSteps: ['P2', 'P3', 'P4', 'P5', 'P6'].map((stage, index) => {
      const currentIndex = ['P2', 'P3', 'P4', 'P5', 'P6'].indexOf(runtime.stage)
      return {
        stage,
        label: `${stage} 阶段`,
        state: index === currentIndex ? 'CURRENT' : index < currentIndex ? 'COMPLETED' : 'FUTURE'
      }
    }),
    allowedActions:
      runtime.stage === 'P2'
        ? ['SAVE_ASSESSMENT', 'SUBMIT_ASSESSMENT']
        : runtime.stage === 'P3'
          ? ['SAVE_CHECKLIST', 'SUBMIT_CHECKLIST']
          : []
  }) as any

const checklistView = () => ({
  taskId: '101',
  taskStage: 'P3',
  taskVersion: 2,
  projectScopeVersion: '12',
  checklistId: '301',
  checklistVersion: 1,
  checklistFactVersion: 1,
  status: 'DRAFT',
  inputSnapshotHash: 'controlled-input',
  inputSnapshot: { deviceTypeCodes: ['ROUTER'] },
  configRevisionSnapshot: '{}',
  matchTrace: '{}',
  configGapSnapshot: '[]',
  items: [
    {
      itemId: '311',
      stableItemKey: 'PRECHECK-READY',
      itemTypeCode: 'BUSINESS_SURVEY',
      itemName: '割接前置确认',
      itemDescription: null,
      interfaceFormatCode: 'TEXT',
      interfaceSchemaSnapshot: null,
      workModeCode: 'DIRECT',
      required: true,
      sourceCode: 'SYSTEM_MATCHED',
      applicable: true,
      sortOrder: 1,
      currentResult: {
        resultVersion: 1,
        resultSourceCode: 'DIRECT',
        answerSnapshot: '{"value":"YES"}',
        factDescription: null,
        manualEvidenceFileReference: null,
        collectionTaskId: null,
        collectionResultReferenceId: null,
        collectionResultVersion: null,
        loadFailureCode: null
      }
    }
  ]
})

const planSource = () => ({
  snapshotVersion: 1,
  taskId: '101',
  taskVersion: 3,
  assessmentId: '201',
  assessmentVersion: 1,
  grade: 'A',
  checklistId: '301',
  checklistVersion: 1,
  projectId: '9007199254741001',
  projectVersion: 3,
  projectScopeVersion: '12',
  devices: [
    {
      deviceId: '9007199254742001',
      serialNumber: 'SN-001',
      projectAssignmentVersion: '6',
      deviceTypeCode: 'ROUTER',
      deviceTypeSourceVersion: 'type-v1'
    }
  ],
  configurationRevisionId: '41',
  configurationCode: 'CORE_STANDARD',
  configurationRevisionNo: 2,
  templateSections: [],
  failedRiskFacts: []
})

const planContent = () => ({
  editMode: 'ONLINE_TEMPLATE_STANDARD',
  overview: {
    projectDescription: '核心网割接实施方案',
    scheduleTable: [{ sequenceNo: 1, plannedAt: 1788391200000, content: '实施割接' }],
    preTopologyFile: null,
    postTopologyFile: null,
    deviceSummary: planSource().devices,
    networkConfigurationFile: null
  },
  steps: [
    'PRE_OPERATION',
    'OPERATION',
    'CLOSING_COLLECTION',
    'POST_BUSINESS_TEST',
    'ROLLBACK',
    'POST_CUTOVER_SUPPORT'
  ].map((sectionCode) => ({ sectionCode, stepNo: 1, content: `${sectionCode}执行内容` })),
  riskMitigations: [],
  supportArrangements: ['CUSTOMER', 'DP_FIRST_LINE', 'DP_SECOND_LINE', 'DP_RND'].map(
    (roleCode, index) => ({
      arrangementId: String(601 + index),
      roleCode,
      personName: `${roleCode}负责人`,
      dutyDescription: `${roleCode}保障`,
      phone: '13800000000',
      arrivalTime: 1788391200000
    })
  )
})

const planView = () =>
  ({
    taskId: '101',
    taskStage: 'P4',
    taskVersion: 3,
    planRevisionId: '501',
    revisionNo: 1,
    planVersion: 1,
    originCode: 'NEW_PLATFORM',
    status: 'DRAFT',
    sourcePlanRevisionId: null,
    revisionReason: 'INITIAL',
    sourceSnapshot: planSource(),
    content: planContent(),
    approvalFact: null,
    allowedActions: ['SAVE_DRAFT', 'SUBMIT_PLAN']
  }) as any

const nodeCodes = ['INITIATOR', 'SERVICE_MANAGER', 'SECOND_LINE', 'RND'] as const
const approvalView = (currentNode: number) => {
  if (currentNode > 4) {
    return {
      viewMode: 'FINAL_RESULT_ONLY',
      approvalInstanceId: '701',
      taskId: '101',
      planRevisionId: '501',
      grade: 'A',
      status: 'APPROVED',
      decisionAt: 1788394800000,
      rejectionReason: null,
      allowedActions: []
    }
  }
  return {
    viewMode: 'FULL',
    approvalInstanceId: '701',
    approvalVersion: currentNode - 1,
    taskId: '101',
    taskVersion: 4,
    planRevisionId: '501',
    planRevisionNo: 1,
    grade: 'A',
    status: 'PENDING',
    holdReason: null,
    currentNodeNo: currentNode,
    nodes: nodeCodes.map((nodeCode, index) => ({
      nodeId: String(711 + index),
      nodeNo: index + 1,
      nodeCode,
      status:
        index + 1 < currentNode ? 'APPROVED' : index + 1 === currentNode ? 'PENDING' : 'WAITING',
      originalApproverUserId: String(8 + index),
      currentApproverUserId: String(8 + index),
      decisionAt: index + 1 < currentNode ? 1788391200000 : null,
      feedback: index + 1 < currentNode ? '已通过' : null,
      reviewItems: [],
      assessmentReview: null
    })),
    sourceSnapshot: {
      snapshotVersion: 1,
      taskId: '101',
      taskVersion: 4,
      checklistId: '301',
      checklistVersion: 1,
      project: createContext().candidates[0].project,
      collectionAnalysis: {
        cutoverType: 'CORE_REPLACEMENT',
        networkMode: null,
        scheduledTime: 1788391200000
      },
      riskItems: [],
      businessSurveyItems: [],
      assessment: {
        assessmentId: '201',
        assessmentVersion: 1,
        questionnaireTemplateCode: 'CUT_P2_MANUAL_ASSESSMENT',
        questionnaireTemplateVersion: '1',
        businessImportanceLevel: 'HIGH',
        operationComplexityLevel: 'MEDIUM',
        hiddenRiskLevel: 'LOW',
        sparePartApplied: true,
        customerServiceLevelCode: 'GOLD',
        manualGrade: 'A',
        submittedBy: '8',
        submittedAt: 1788391200000
      },
      plan: {
        planRevisionId: '501',
        planRevisionNo: 1,
        planVersion: 1,
        originCode: 'NEW_PLATFORM',
        sourceSnapshot: planSource(),
        content: planContent()
      }
    },
    decisionAt: null,
    rejectionReason: null,
    allowedActions: ['APPROVE', 'REJECT']
  }
}

const closureContent = () => ({
  preCheckNormal: true,
  preCheckDetail: null,
  executionNormal: true,
  executionDetail: null,
  testNormal: true,
  testDetail: null,
  rollbackOccurred: false,
  rollbackSuccessful: null,
  rollbackReason: null,
  legacyItems: null,
  finalResult: runtime.closureStatus === 'SUBMITTED' ? 'SUCCESS' : null,
  attachments: [
    closureFile('POST_COLLECTION_CHECKLIST', 'post-checklist', '901'),
    closureFile('IMPLEMENTATION_COMMITMENT', 'commitment', '902')
  ]
})

const closureFile = (purposeCode: string, suffix: string, artifactId: string) => ({
  purposeCode,
  artifactId,
  versionNo: 1,
  referenceKey: `cutover-closure-801-${suffix}`,
  scopeVersion: 4,
  sha256: 'a'.repeat(64),
  fileFactVersion: { artifactVersion: 1, referenceVersion: 2, availabilityVersion: 3 }
})

const closureView = () => ({
  taskId: '101',
  taskVersion: 5,
  taskStatus: runtime.closureStatus === 'SUBMITTED' ? 'ARCHIVED' : 'CLOSURE_IN_PROGRESS',
  closureId: '801',
  closureVersion: 1,
  closureStatus: runtime.closureStatus,
  content: closureContent(),
  collectionEvidence: [
    {
      evidenceId: '851',
      evidenceVersion: 1,
      deviceId: '9007199254742001',
      collectionStage: 'POST_CHECK',
      evidenceType: 'CALLBACK_SUCCESS',
      collectionTaskId: '841',
      externalTaskId: 'EXT-1',
      externalTaskVersion: 'v1',
      status: 'SUCCEEDED',
      resultRef: 'result-1',
      fileFact: null
    }
  ],
  resultRef: runtime.closureStatus === 'SUBMITTED' ? 'cutover-closure:801:1' : null,
  archivedAt: runtime.closureStatus === 'SUBMITTED' ? 1788394800000 : null,
  allowedActions: runtime.closureStatus === 'SUBMITTED' ? [] : ['SAVE_CLOSURE', 'SUBMIT_CLOSURE']
})

const collect = (node: TestNode, predicate: (candidate: TestNode) => boolean): TestNode[] => [
  ...(predicate(node) ? [node] : []),
  ...node.children.flatMap((child) => collect(child, predicate))
]

const click = async (root: TestNode, testId: string) => {
  const target = findByTestId(root, testId)
  expect(target, `${testId} should be mounted`).toBeDefined()
  await (target!.props?.onClick as () => void | Promise<void>)()
}

const clickLabel = async (root: TestNode, label: string) => {
  const target = collect(root, (node) => node.type === 'button' && textOf(node).includes(label))[0]
  expect(target, `${label} should be mounted`).toBeDefined()
  await (target.props?.onClick as () => void | Promise<void>)()
}

const update = async (testId: string, value: unknown, root: TestNode) => {
  const target = findByTestId(root, testId)
  expect(target, `${testId} should be mounted`).toBeDefined()
  await (target!.props?.['onUpdate:modelValue'] as (next: unknown) => void)(value)
  await nextTick()
}

const flush = async () => {
  for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
}
