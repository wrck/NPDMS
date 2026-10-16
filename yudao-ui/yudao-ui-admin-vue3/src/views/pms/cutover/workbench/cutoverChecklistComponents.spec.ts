import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  CutoverChecklistView,
  CutoverTaskDetail
} from '@/api/pms/cutover'
import CutoverChecklistField from '../components/CutoverChecklistField.vue'
import CutoverChecklistPanel from '../components/CutoverChecklistPanel.vue'
import {
  findByTestId,
  mount,
  passthrough
} from '../cutoverTestHarness'
import { textOf } from '../cutoverTestHarness'

const fileApi = vi.hoisted(() => ({ getArtifact: vi.fn(), getVersions: vi.fn() }))
const acceptedTemplate = vi.hoisted(() => ({ value: '9007199254740992' }))
const downloadFile = vi.hoisted(() => ({ excel: vi.fn() }))
const checklistApi = vi.hoisted(() => ({
  getCutoverChecklistMatchingOptions: vi.fn(async () => ({ matchingInput: { cutoverType: 'CONFIGURATION_CHANGE', networkMode: null, deviceTypeCodes: ['ROUTER'] }, deviceTypes: [{ value: 'ROUTER', label: '路由器' }] })),
  getCutoverChecklistRevisions: vi.fn(async () => []),
  getCutoverExternalState: vi.fn(async () => null),
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
vi.mock('@/api/pms/platform/file/version-reference', () => fileApi)
vi.mock('@/api/pms/cutover', () => checklistApi)
vi.mock('@/utils/download', () => ({ default: downloadFile }))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() })
}))
vi.mock('@/components/DeviceCollection/BusinessCollectionDialog.vue', () => ({
  default: defineComponent({
    emits: ['accepted', 'closed'],
    setup(_, { emit, expose }) {
      expose({ open: vi.fn() })
      return () => h('button', { 'data-testid': 'accept-platform-execution', onClick: () => emit('accepted', {
        id: '9007199254740994', task: { deviceId: '9007199254740991', templateId: acceptedTemplate.value }
      }) }, '确认平台采集执行')
    }
  })
}))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileUploader: defineComponent({
    inheritAttrs: false,
    emits: ['completed'],
    setup(_, { attrs, emit, expose }) {
      expose({ isBusy: () => false, hasPendingFile: () => false })
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
  ElDialog: defineComponent({ setup(_, { slots }) { return () => h('section', [slots.default?.(), slots.footer?.()]) } }),
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
  beforeEach(() => { vi.clearAllMocks(); acceptedTemplate.value = '9007199254740992' })
  it('emits direct answer and freezes PLT public fact without converting Snowflake ids', async () => {
    fileApi.getArtifact.mockResolvedValue({
      allowedActions: ['READ'], artifactVersion: 2,
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

    const input = findInput(mounted.root, '请填写核查事实')!
    await (input.props?.['onUpdate:modelValue'] as (value: string) => void)('已核查')
    expect(findByTestId(mounted.root, 'manual-uploader')).toBeUndefined()
    const material = mount(CutoverChecklistField, { item: { ...item, workModeCode: 'MANUAL' }, readonly: false, allowSave: true, onManual: (...args: unknown[]) => manual.push(args) }, controls)
    const uploader = findByTestId(material.root, 'manual-uploader')!
    expect(uploader.props?.disabled).not.toBe(true)
    await (uploader.props?.onClick as () => Promise<void>)()
    await nextTick()

    expect(direct).toEqual([['risk-check', '"已核查"']])
    expect(manual[0][0]).toBe('risk-check')
    expect(manual[0][2]).toBe('')
    expect(manual[0][1]).toMatchObject({
      artifactId: '9007199254740993',
      scopeVersion: 17,
      fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 }
    })
    expect(typeof (manual[0][1] as { artifactId: unknown }).artifactId).toBe('string')
    material.app.unmount()
    mounted.app.unmount()
  })

  it('does not reuse the previous revision material when a checklist item is replaced', async () => {
    fileApi.getArtifact.mockResolvedValue({ artifactId: '9007199254740993', artifactVersion: 2,
      reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5 }] })
    const selected = ref({ ...item, workModeCode: 'MANUAL' as const })
    const mounted = mount(defineComponent({ setup: () => () => h(CutoverChecklistField,
      { item: selected.value, directValue: '', readonly: false, allowSave: true }) }), {}, controls)
    try {
      await (findByTestId(mounted.root, 'manual-uploader')!.props!.onClick as Function)()
      for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
      expect(findByTestId(mounted.root, 'manual-uploader')!.props!['artifact-id']).toBe('9007199254740993')
      selected.value = { ...selected.value, itemId: '9007199254740996' }
      await nextTick()
      expect(findByTestId(mounted.root, 'manual-uploader')!.props!['artifact-id']).toBeUndefined()
    } finally { mounted.app.unmount() }
  })

  it('ignores a completed upload fact read after the checklist revision has changed', async () => {
    let resolve!: (value: unknown) => void
    fileApi.getArtifact.mockImplementation(() => new Promise(done => { resolve = done }))
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5 }] })
    const manual = vi.fn()
    const selected = ref({ ...item, workModeCode: 'MANUAL' as const })
    const mounted = mount(defineComponent({ setup: () => () => h(CutoverChecklistField,
      { item: selected.value, directValue: '', readonly: false, allowSave: true, onManual: manual }) }), {}, controls)
    try {
      await (findByTestId(mounted.root, 'manual-uploader')!.props!.onClick as Function)()
      selected.value = { ...selected.value, itemId: '9007199254740996' }
      await nextTick()
      resolve({ artifactId: '9007199254740993', artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
      for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() }
      expect(manual).not.toHaveBeenCalled()
      expect(findByTestId(mounted.root, 'manual-uploader')!.props!['artifact-id']).toBeUndefined()
    } finally { mounted.app.unmount() }
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
    await (findByTestId(mounted.root, 'accept-platform-execution')!.props?.onClick as () => void)()
    expect(collection).toEqual([['risk-check', '9007199254740991', '9007199254740992', '9007199254740994']])
    mounted.app.unmount()
  })

  it('does not link an accepted platform execution from a readonly checklist', async () => {
    const onCollection = vi.fn()
    const mounted = mount(CutoverChecklistField, {
      item: { ...item, workModeCode: 'COLLECTION' }, directValue: '', readonly: true,
      allowCollection: false, onCollection
    }, controls)
    await (findByTestId(mounted.root, 'accept-platform-execution')!.props?.onClick as () => void)()
    expect(onCollection).not.toHaveBeenCalled()
    mounted.app.unmount()
  })

  it('links a manual execution using its formal execution id and no invented template id', async () => {
    acceptedTemplate.value = `manual-${item.itemId}`
    const onCollection = vi.fn()
    const mounted = mount(CutoverChecklistField, {
      item: { ...item, workModeCode: 'COLLECTION' }, directValue: '', readonly: false,
      allowCollection: true, onCollection
    }, controls)
    await (findByTestId(mounted.root, 'accept-platform-execution')!.props?.onClick as () => void)()
    expect(onCollection).toHaveBeenCalledWith('risk-check', '9007199254740991', null, '9007199254740994')
    mounted.app.unmount()
  })

  it('displays device-sourced model and current version without editable duplicate fields', async () => {
    checklistApi.getCutoverChecklist.mockResolvedValue(checklistView(null))
    checklistApi.getCutoverExternalState.mockResolvedValueOnce({ checklistId: '201', checklistFactVersion: 3,
      devices: [{ deviceId: '9007199254740991', sn: 'SN-001', productModel: 'MODEL-DEVICE',
        currentSoftwareVersion: 'R-DEVICE', targetSoftwareVersion: null }], items: [] } as any)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()
    expect(textOf(mounted.root)).toContain('MODEL-DEVICE')
    expect(textOf(mounted.root)).toContain('R-DEVICE')
    const inputs: any[] = []
    const collect = (node: any) => { if (node.props?.['aria-label']) inputs.push(node.props['aria-label']); for (const child of node.children || []) collect(child) }
    collect(mounted.root)
    expect(inputs).not.toContain('当前软件版本')
    expect(inputs).not.toContain('设备型号')
    mounted.app.unmount()
  })

  it('saves a DIRECT value as JSON and hydrates the refreshed control value', async () => {
    const initialChecklist = checklistView(null)
    checklistApi.getCutoverChecklist
      .mockResolvedValueOnce(initialChecklist)
      .mockResolvedValueOnce(checklistView('"已核查"'))
    checklistApi.saveCutoverChecklist.mockResolvedValue(undefined)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()

    const input = findInput(mounted.root, '请填写核查事实')!
    await (input.props?.['onUpdate:modelValue'] as (value: string) => void)('已核查')
    const saveButton = findByTestId(mounted.root, 'checklist-save')!
    await (saveButton.props?.onClick as () => Promise<void>)()
    await flush()

    expect(checklistApi.saveCutoverChecklist).toHaveBeenCalledWith({ taskId: '101', idempotencyKey: expect.any(String), data: {
      expectedTaskVersion: 7,
      expectedProjectScopeVersion: '12',
      checklistId: '201',
      expectedChecklistVersion: 3,
      answers: [{ stableItemKey: 'risk-check', answerSnapshot: '{"value":"已核查"}' }]
    } })
    await vi.waitFor(() => {
      const refreshedInput = findInput(mounted.root, '请填写核查事实')
      expect(refreshedInput).toBeDefined()
      expect(refreshedInput?.props?.['model-value']).toBe('已核查')
    })
    mounted.app.unmount()
  })

  it('retains custom survey and risk entry points when the configuration matches no items', async () => {
    const empty = checklistView(null)
    empty.items = []
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockResolvedValue(empty)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    try {
      await flush()
      expect(textOf(mounted.root)).toContain('增加自定义调研项')
      expect(textOf(mounted.root)).toContain('增加自定义考察项')
    } finally { mounted.app.unmount() }
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

    expect(checklistApi.addCustomChecklistItem).toHaveBeenCalledWith({ taskId: '101', idempotencyKey: expect.any(String), data: expect.objectContaining({
      checklistId: '201',
      expectedChecklistVersion: 3,
      itemName: '现场补充核查',
      interfaceSchema: '{"type":"string"}'
    }) })
    mounted.app.unmount()
  })

  it('preserves unsaved DIRECT answers when another item receives MANUAL evidence', async () => {
    let savedAnswer: string | null = null
    let checklistVersion = 3
    let completeDirectSave!: () => void
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockImplementation(async () => {
      const view = checklistView(savedAnswer, checklistVersion)
      view.items.unshift({ ...item, itemId: '302', stableItemKey: 'manual-check', workModeCode: 'MANUAL' })
      return view
    })
    checklistApi.saveCutoverChecklist.mockImplementation(async ({ data }) => {
      await new Promise<void>(resolve => { completeDirectSave = resolve })
      savedAnswer = data.answers[0].answerSnapshot
      checklistVersion++
    })
    checklistApi.saveManualChecklistResult.mockImplementation(async () => { checklistVersion++ })
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5 }] })
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()

    await (findInput(mounted.root, '请填写核查事实')!.props?.['onUpdate:modelValue'] as (value: string) => void)('未手动保存的现场事实')
    await (findInput(mounted.root, '材料说明（选填）')!.props?.['onUpdate:modelValue'] as (value: string) => void)('另一项材料')
    await nextTick()
    await (findByTestId(mounted.root, 'manual-uploader')!.props?.onClick as () => void)()
    await vi.waitFor(() => expect(checklistApi.saveCutoverChecklist).toHaveBeenCalledOnce())
    expect(checklistApi.saveManualChecklistResult).not.toHaveBeenCalled()
    expect(findInput(mounted.root, '请填写核查事实')?.props?.readonly).toBe(true)

    completeDirectSave()
    await vi.waitFor(() => expect(checklistApi.saveManualChecklistResult).toHaveBeenCalledWith(expect.objectContaining({
      stableItemKey: 'manual-check', data: expect.objectContaining({ expectedChecklistVersion: 4 })
    })))
    await vi.waitFor(() => expect(findInput(mounted.root, '请填写核查事实')?.props?.['model-value']).toBe('未手动保存的现场事实'))
    expect(savedAnswer).toBe('{"value":"未手动保存的现场事实"}')
    expect(checklistApi.getCutoverChecklistMatchingOptions).toHaveBeenCalledOnce()
    expect(checklistApi.getCutoverChecklistRevisions).toHaveBeenCalledOnce()
    expect(checklistApi.getCutoverExternalState).toHaveBeenCalledOnce()
    mounted.app.unmount()
    checklistApi.saveCutoverChecklist.mockReset()
    checklistApi.saveManualChecklistResult.mockReset()
  })

  it('selects MANUAL evidence and consumes the server navigation decision after P4 refresh', async () => {
    fileApi.getArtifact.mockResolvedValue({
      allowedActions: ['READ'], artifactVersion: 2,
      reference: { scopeVersion: 17, referenceVersion: 3 }
    })
    fileApi.getVersions.mockResolvedValue({
      items: [{ versionNo: 4, availabilityVersion: 5 }]
    })
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist
      .mockResolvedValueOnce({ ...checklistView(null, 3), items: [{ ...item, workModeCode: 'MANUAL' }] })
      .mockResolvedValueOnce({ ...checklistView(null, 4), items: [{ ...item, workModeCode: 'MANUAL' }] })
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

    await (findInput(mounted.root, '材料说明（选填）')!.props?.['onUpdate:modelValue'] as (value: string) => void)('现场核验完成')
    await nextTick()
    const uploader = findByTestId(mounted.root, 'manual-uploader')!
    await (uploader.props?.onClick as () => Promise<void>)()
    await flush()
    await vi.waitFor(() => expect(checklistApi.saveManualChecklistResult).toHaveBeenCalledOnce())
    expect(checklistApi.saveManualChecklistResult).toHaveBeenCalledWith({ taskId: '101', stableItemKey: 'risk-check', idempotencyKey: expect.any(String), data: {
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
      factDescription: '现场核验完成'
    } })

    const submit = findByTestId(mounted.root, 'checklist-submit')!
    await (submit.props?.onClick as () => Promise<void>)()
    expect(checklistApi.submitCutoverChecklist).toHaveBeenCalledWith({ taskId: '101', idempotencyKey: expect.any(String), data: {
      expectedTaskVersion: 7,
      expectedAssessmentVersion: 5,
      expectedProjectScopeVersion: '12',
      checklistId: '201',
      expectedChecklistVersion: 4
    } })
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
  assessment: { assessmentVersion: 2, rowVersion: 5 },
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

const findInput = (node: any, placeholder: string): any => {
  if (node.props?.placeholder === placeholder) return node
  for (const child of node.children || []) { const found = findInput(child, placeholder); if (found) return found }
}
