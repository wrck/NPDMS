import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type {
  CutoverChecklistView,
  CutoverTaskDetail
} from '@/api/pms/cutover/cutover-task'
import CutoverChecklistField from './components/CutoverChecklistField.vue'
import CutoverChecklistPanel from './components/CutoverChecklistPanel.vue'
import {
  findByTestId,
  mount,
  passthrough
} from '../../platform/dynamic-form/components/runtimeTestHarness'

const fileApi = vi.hoisted(() => ({ getArtifact: vi.fn(), getVersions: vi.fn() }))
const downloadFile = vi.hoisted(() => ({ excel: vi.fn() }))
const checklistApi = vi.hoisted(() => ({
  getCutoverChecklist: vi.fn(),
  saveCutoverChecklist: vi.fn(),
  generateCutoverChecklist: vi.fn(),
  addCustomChecklistItem: vi.fn(),
  removeCustomChecklistItem: vi.fn(),
  requestChecklistCollection: vi.fn(),
  saveManualChecklistResult: vi.fn(),
  exportCutoverChecklist: vi.fn(),
  submitCutoverChecklist: vi.fn()
}))
vi.mock('@/api/pms/platform/file', () => fileApi)
vi.mock('@/api/pms/cutover/cutover-task', () => checklistApi)
vi.mock('@/utils/download', () => ({ default: downloadFile }))
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
          artifactId: '9007199254740993', versionNo: 4, referenceKey: 'manual-proof'
        })
      }, 'upload')
    }
  })
}))

const controls = {
  ElInput: passthrough,
  ElSelect: passthrough,
  ElOption: passthrough,
  ElTag: passthrough,
  ElButton: passthrough,
  ElCheckbox: passthrough
}
const item = {
  itemId: '9007199254740995',
  stableItemKey: 'risk-check',
  itemTypeCode: 'RISK',
  itemName: '风险核查',
  itemDescription: '确认现场风险',
  interfaceFormatCode: 'TEXT',
  interfaceSchemaSnapshot: null,
  workModeCode: 'DIRECT' as const,
  required: true,
  sourceCode: 'SYSTEM_MATCHED' as const,
  applicable: true,
  sortOrder: 10,
  currentResult: null
}

describe('F-CUT-003 mounted checklist field', () => {
  it('emits direct answer and freezes PLT public fact without converting Snowflake ids', async () => {
    fileApi.getArtifact.mockResolvedValue({
      artifactVersion: 2,
      reference: { scopeVersion: 17, referenceVersion: 3 }
    })
    fileApi.getVersions.mockResolvedValue({
      items: [{ versionNo: 4, availabilityVersion: 5 }]
    })
    const direct: unknown[][] = []
    const manual: unknown[][] = []
    const mounted = mount(CutoverChecklistField, {
      item,
      directValue: '',
      readonly: false,
      allowSave: true,
      onDirect: (...args: unknown[]) => direct.push(args),
      onManual: (...args: unknown[]) => manual.push(args)
    }, controls)

    const input = findByTestId(mounted.root, 'checklist-input')!
    await (input.props?.['onUpdate:modelValue'] as (value: string) => void)('已核查')
    const uploader = findByTestId(mounted.root, 'manual-uploader')!
    await (uploader.props?.onClick as () => Promise<void>)()
    await nextTick()

    expect(direct).toEqual([['risk-check', '已核查']])
    expect(manual[0][0]).toBe('risk-check')
    expect(manual[0][1]).toMatchObject({
      artifactId: '9007199254740993',
      scopeVersion: 17,
      fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 }
    })
    expect(typeof (manual[0][1] as { artifactId: unknown }).artifactId).toBe('string')
    mounted.app.unmount()
  })

  it('requests controlled collection with stable WireLong device and template identities', async () => {
    const collection: unknown[][] = []
    const mounted = mount(CutoverChecklistField, {
      item: { ...item, workModeCode: 'COLLECTION' },
      directValue: '',
      readonly: false,
      allowCollection: true,
      devices: [{ deviceId: '9007199254740991', serialNumber: 'SN-001', projectAssignmentVersion: '7' }],
      onCollection: (...args: unknown[]) => collection.push(args)
    }, controls)
    const template = findByTestId(mounted.root, 'collection-template')!
    await (template.props?.['onUpdate:modelValue'] as (value: string) => void)('9007199254740992')
    const requestButton = findByTestId(mounted.root, 'request-collection')!
    await (requestButton.props?.onClick as () => Promise<void>)()

    expect(collection).toEqual([['risk-check', '9007199254740991', '9007199254740992']])
    mounted.app.unmount()
  })

  it('saves a DIRECT value as JSON and hydrates the refreshed control value', async () => {
    const initialChecklist = checklistView(null)
    checklistApi.getCutoverChecklist
      .mockResolvedValueOnce(initialChecklist)
      .mockResolvedValueOnce(checklistView('{"value":"已核查"}'))
    checklistApi.saveCutoverChecklist.mockResolvedValue(undefined)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()

    const input = findByTestId(mounted.root, 'checklist-input')!
    await (input.props?.['onUpdate:modelValue'] as (value: string) => void)('已核查')
    const saveButton = findByTestId(mounted.root, 'checklist-save')!
    await (saveButton.props?.onClick as () => Promise<void>)()
    await flush()

    expect(checklistApi.saveCutoverChecklist).toHaveBeenCalledWith('101', {
      expectedTaskVersion: 7,
      expectedProjectScopeVersion: '12',
      checklistId: '201',
      expectedChecklistVersion: 3,
      answers: [{ stableItemKey: 'risk-check', answerSnapshot: '{"value":"已核查"}' }]
    })
    await vi.waitFor(() => {
      const refreshedInput = findByTestId(mounted.root, 'checklist-input')
      expect(refreshedInput).toBeDefined()
      expect(refreshedInput?.props?.['model-value']).toBe('已核查')
    })
    mounted.app.unmount()
  })

  it('adds a task-level custom item through the mounted P3 workbench', async () => {
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockResolvedValue(checklistView(null))
    checklistApi.addCustomChecklistItem.mockResolvedValue(undefined)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()

    const name = findByTestId(mounted.root, 'custom-item-name')!
    await (name.props?.['onUpdate:modelValue'] as (value: string) => void)('现场补充核查')
    const add = findByTestId(mounted.root, 'add-custom-item')!
    await (add.props?.onClick as () => Promise<void>)()
    await flush()

    expect(checklistApi.addCustomChecklistItem).toHaveBeenCalledWith('101', expect.objectContaining({
      checklistId: '201',
      expectedChecklistVersion: 3,
      itemName: '现场补充核查',
      interfaceSchema: '{"type":"string"}'
    }))
    mounted.app.unmount()
  })

  it('selects MANUAL evidence and consumes the server navigation decision after P4 refresh', async () => {
    fileApi.getArtifact.mockResolvedValue({
      artifactVersion: 2,
      reference: { scopeVersion: 17, referenceVersion: 3 }
    })
    fileApi.getVersions.mockResolvedValue({
      items: [{ versionNo: 4, availabilityVersion: 5 }]
    })
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist
      .mockResolvedValueOnce(checklistView(null, 3))
      .mockResolvedValueOnce(checklistView(null, 4))
    checklistApi.saveManualChecklistResult.mockResolvedValue(undefined)
    checklistApi.submitCutoverChecklist.mockResolvedValue({
      replayed: false,
      navigationDecision: {
        ruleKey: 'POST_SUBMIT',
        configurationRevisionId: '301',
        target: 'CURRENT_STAGE_WORKBENCH'
      }
    })
    const navigated: unknown[][] = []
    const refreshWorkspace = vi.fn().mockResolvedValue(undefined)
    const mounted = mount(CutoverChecklistPanel, {
      detail: taskDetail,
      refreshWorkspace,
      onNavigate: (...args: unknown[]) => navigated.push(args)
    }, controls)
    await flush()

    const uploader = findByTestId(mounted.root, 'manual-uploader')!
    await (uploader.props?.onClick as () => Promise<void>)()
    await flush()
    expect(checklistApi.saveManualChecklistResult).toHaveBeenCalledWith('101', 'risk-check', {
      expectedTaskVersion: 7,
      expectedProjectScopeVersion: '12',
      checklistId: '201',
      expectedChecklistVersion: 3,
      file: {
        artifactId: '9007199254740993',
        versionNo: 4,
        referenceKey: 'manual-proof',
        fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 },
        scopeVersion: 17
      },
      factDescription: ''
    })

    const submit = findByTestId(mounted.root, 'checklist-submit')!
    await (submit.props?.onClick as () => Promise<void>)()
    expect(checklistApi.submitCutoverChecklist).toHaveBeenCalledWith('101', {
      expectedTaskVersion: 7,
      expectedAssessmentVersion: 2,
      expectedProjectScopeVersion: '12',
      checklistId: '201',
      expectedChecklistVersion: 4
    }, expect.any(String))
    expect(refreshWorkspace).toHaveBeenCalledOnce()
    expect(navigated).toEqual([['CURRENT_STAGE_WORKBENCH']])
    mounted.app.unmount()
  })

  it('downloads the current authorized checklist with the locked filename', async () => {
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockResolvedValue(checklistView(null))
    checklistApi.exportCutoverChecklist.mockResolvedValue(new Blob(['xlsx']))
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()

    const exportButton = findByTestId(mounted.root, 'checklist-export')!
    await (exportButton.props?.onClick as () => Promise<void>)()

    expect(checklistApi.exportCutoverChecklist).toHaveBeenCalledWith('101', 1)
    expect(downloadFile.excel).toHaveBeenCalledWith(
      expect.any(Blob),
      'cutover-checklist-101-v1.xlsx'
    )
    mounted.app.unmount()
  })

  it('retries only workspace refresh after an uncertain submit response', async () => {
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockResolvedValue(checklistView(null))
    checklistApi.submitCutoverChecklist.mockReset()
    checklistApi.submitCutoverChecklist.mockRejectedValue(new Error('navigation read failed'))
    const refreshWorkspace = vi.fn().mockResolvedValue(undefined)
    const navigated: unknown[][] = []
    const mounted = mount(CutoverChecklistPanel, {
      detail: taskDetail,
      refreshWorkspace,
      onNavigate: (...args: unknown[]) => navigated.push(args)
    }, controls)
    await flush()

    const submit = findByTestId(mounted.root, 'checklist-submit')!
    await (submit.props?.onClick as () => Promise<void>)()
    await (submit.props?.onClick as () => Promise<void>)()

    expect(checklistApi.submitCutoverChecklist).toHaveBeenCalledOnce()
    expect(refreshWorkspace).toHaveBeenCalledOnce()
    expect(navigated).toEqual([['TASK_OVERVIEW']])
    mounted.app.unmount()
  })

  it('consumes the same navigation decision from an idempotent submit replay', async () => {
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockResolvedValue(checklistView(null))
    checklistApi.submitCutoverChecklist.mockReset()
    checklistApi.submitCutoverChecklist.mockResolvedValue({
      replayed: true,
      navigationDecision: {
        ruleKey: 'POST_SUBMIT',
        configurationRevisionId: '301',
        target: 'TASK_OVERVIEW'
      }
    })
    const navigated: unknown[][] = []
    const mounted = mount(CutoverChecklistPanel, {
      detail: taskDetail,
      refreshWorkspace: vi.fn().mockResolvedValue(undefined),
      onNavigate: (...args: unknown[]) => navigated.push(args)
    }, controls)
    await flush()

    await (findByTestId(mounted.root, 'checklist-submit')?.props?.onClick as () => Promise<void>)()

    expect(checklistApi.submitCutoverChecklist).toHaveBeenCalledOnce()
    expect(navigated).toEqual([['TASK_OVERVIEW']])
    mounted.app.unmount()
  })
})

const taskDetail = {
  task: { id: '101', currentStage: 'P3', manualGrade: 'A' },
  project: { projectScopeVersion: '12' },
  devices: [{ deviceId: '9007199254740991', serialNumber: 'SN-001', projectAssignmentVersion: '7' }],
  assessment: { assessmentVersion: 2 },
  allowedActions: ['SAVE_CHECKLIST', 'REQUEST_COLLECTION', 'SUBMIT_CHECKLIST']
} as CutoverTaskDetail

const checklistView = (
  answerSnapshot: string | null,
  checklistFactVersion = 3
): CutoverChecklistView => ({
  taskId: '101',
  taskStage: 'P3',
  taskVersion: 7,
  projectScopeVersion: '12',
  checklistId: '201',
  checklistVersion: 1,
  checklistFactVersion,
  status: 'DRAFT',
  inputSnapshotHash: 'input',
  configRevisionSnapshot: '{}',
  matchTrace: '{}',
  configGapSnapshot: '[]',
  items: [{
    ...item,
    currentResult: answerSnapshot === null ? null : {
      resultVersion: 1,
      resultSourceCode: 'DIRECT',
      answerSnapshot,
      factDescription: null,
      manualEvidenceFileReference: null,
      collectionTaskId: null,
      collectionResultReferenceId: null,
      collectionResultVersion: null,
      loadFailureCode: null
    }
  }]
})

const flush = async () => {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
