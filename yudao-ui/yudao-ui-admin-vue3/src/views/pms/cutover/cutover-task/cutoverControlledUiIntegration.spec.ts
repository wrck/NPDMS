import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import CutoverAssessmentPanel from './components/CutoverAssessmentPanel.vue'
import CutoverApprovalPanel from './components/CutoverApprovalPanel.vue'
import CutoverChecklistPanel from './components/CutoverChecklistPanel.vue'
import CutoverClosurePanel from './components/CutoverClosurePanel.vue'
import CutoverCreateWizard from './components/CutoverCreateWizard.vue'
import CutoverPlanPanel from './components/CutoverPlanPanel.vue'
import {
  button,
  findByTestId,
  mount,
  passthrough,
  tableColumn,
  textOf,
  type TestNode
} from '../../platform/dynamic-form/components/runtimeTestHarness'

const trace: string[] = []
const runtime = {
  stage: 'P1',
  approvalNode: 1,
  closureStatus: 'DRAFT'
}

const api = vi.hoisted(() => ({
  resolveCreateContext: vi.fn(),
  createCutoverTask: vi.fn(),
  saveCutoverAssessment: vi.fn(),
  submitCutoverAssessment: vi.fn(),
  getCutoverChecklist: vi.fn(),
  generateCutoverChecklist: vi.fn(),
  saveCutoverChecklist: vi.fn(),
  saveManualChecklistResult: vi.fn(),
  addCustomChecklistItem: vi.fn(),
  removeCustomChecklistItem: vi.fn(),
  requestChecklistCollection: vi.fn(),
  submitCutoverChecklist: vi.fn(),
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

vi.mock('@/api/pms/cutover/cutover-task', () => api)
vi.mock('@/api/pms/platform/file', () => fileApi)
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() })
}))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: defineComponent({
    setup: () => () => h('button', { 'data-testid': 'controlled-file-uploader' }, 'upload')
  })
}))

const dialog = defineComponent({
  setup(_, { slots }) {
    return () => h('section', [slots.default?.(), slots.footer?.()])
  }
})

const controls = {
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
  ElTableColumn: tableColumn
}

describe('CUT P1-P6 controlled UI integration', () => {
  it('runs the existing mounted workbench components through one A-grade archived task', async () => {
    configureControlledApi()
    const mounted = mount(ControlledCutoverFlow, {}, controls)

    await update('create-serials', ' sn-001 ', mounted.root)
    await clickLabel(mounted.root, '解析项目')
    await flush()
    await update('create-project-candidates', '9007199254741001', mounted.root)
    await clickLabel(mounted.root, '下一步')
    await update('create-task-name', '核心网割接', mounted.root)
    await update('create-background', '设备替换', mounted.root)
    await update('create-cutover-type', 'CORE_REPLACEMENT', mounted.root)
    await update('create-scheduled-time', '2026-09-03T10:00:00', mounted.root)
    await clickLabel(mounted.root, '创建并进入 P2')
    await flush()

    await clickLabel(mounted.root, '保存草稿')
    await clickLabel(mounted.root, '提交人工分级')
    await flush()

    await click(mounted.root, 'checklist-submit')
    await flush()
    await click(mounted.root, 'submit-plan')
    await flush()

    for (let nodeNo = 1; nodeNo <= 4; nodeNo += 1) {
      await update('approval-feedback', `第${nodeNo}节点通过`, mounted.root)
      await click(mounted.root, 'approve-approval')
      await flush()
    }

    await click(mounted.root, 'save-closure')
    await flush()
    await click(mounted.root, 'submit-closure-success')
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
    expect(textOf(mounted.root)).toContain('ARCHIVED')
    expect(textOf(mounted.root)).toContain('cutover-closure:801:1')
    mounted.app.unmount()
  })
})

const ControlledCutoverFlow = defineComponent({
  setup() {
    const stage = ref(runtime.stage)
    const assessment = reactive({
      answers: {
        businessImportanceLevel: 'HIGH',
        operationComplexityLevel: 'MEDIUM',
        hiddenRiskLevel: 'LOW',
        sparePartApplied: true
      },
      manualGrade: 'A' as const
    })
    const syncStage = () => {
      stage.value = runtime.stage
    }
    return () => {
      if (stage.value === 'P1') {
        return h(CutoverCreateWizard, {
          modelValue: true,
          'onUpdate:modelValue': () => undefined,
          onCreated: syncStage
        })
      }
      if (stage.value === 'P2') {
        return h(CutoverAssessmentPanel, {
          model: assessment,
          editable: true,
          submittable: true,
          saving: false,
          submitting: false,
          onSave: async () => api.saveCutoverAssessment('101', 1, 0, assessment),
          onSubmit: async () => {
            await api.submitCutoverAssessment('101', 1, 1, 'controlled-p2')
            syncStage()
          }
        })
      }
      if (stage.value === 'P3') {
        return h(CutoverChecklistPanel, { detail: taskDetail('P3'), onSubmitted: syncStage })
      }
      if (stage.value === 'P4') {
        return h(CutoverPlanPanel, {
          taskId: '101',
          taskVersion: 3,
          manualGrade: 'A',
          onChanged: syncStage
        })
      }
      if (stage.value === 'P5') {
        return h(CutoverApprovalPanel, { taskId: '101', onChanged: syncStage })
      }
      return h(CutoverClosurePanel, { taskId: '101', onChanged: syncStage })
    }
  }
})

function configureControlledApi() {
  trace.length = 0
  runtime.stage = 'P1'
  runtime.approvalNode = 1
  runtime.closureStatus = 'DRAFT'
  Object.values(api).forEach((mock) => mock.mockReset())

  api.resolveCreateContext.mockImplementation(async () => {
    trace.push('read:P1:owners')
    return createContext()
  })
  api.createCutoverTask.mockImplementation(async () => {
    trace.push('write:P1:create')
    runtime.stage = 'P2'
    return { taskId: '101', version: 1 }
  })
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
    async (_taskId, _taskVersion, _closureVersion, result) => {
      trace.push(`write:P6:submit:${result}`)
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

const taskDetail = (currentStage: 'P3') =>
  ({
    task: { id: '101', currentStage, taskVersion: 2, version: 2, manualGrade: 'A' },
    project: { projectScopeVersion: '12' },
    devices: [
      { deviceId: '9007199254742001', serialNumber: 'SN-001', projectAssignmentVersion: '6' }
    ],
    assessment: { assessmentId: '201', assessmentVersion: 1 },
    allowedActions: ['SAVE_CHECKLIST', 'SUBMIT_CHECKLIST']
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
    legacyPlanId: null,
    legacyStatusRaw: null,
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
  finalResult: null,
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
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
