import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  CutoverChecklistView,
  CutoverTaskDetail
} from '@/api/pms/cutover'
import CutoverChecklistField from '../components/CutoverChecklistField.vue'
import CutoverChecklistPanel from '../components/CutoverChecklistPanel.vue'
import CutoverSchemaInput from '../components/CutoverSchemaInput.vue'
import CutoverFrozenAnswer from '../components/CutoverFrozenAnswer.vue'
import { getDevicePage } from '@/api/pms/asset/device'
import { checkPermi } from '@/utils/permission'
import {
  findByTestId,
  mount,
  passthrough
} from '../cutoverTestHarness'
import { textOf } from '../cutoverTestHarness'

const fileApi = vi.hoisted(() => ({ getArtifact: vi.fn(), getVersions: vi.fn(), getCurrentReference: vi.fn() }))
const acceptedTemplate = vi.hoisted(() => ({ value: '9007199254740992' }))
const downloadFile = vi.hoisted(() => ({ excel: vi.fn() }))
const validationFocus = vi.hoisted(() => vi.fn())
vi.mock('../shared/cutoverChecklistValidation', async importOriginal => ({
  ...await importOriginal<typeof import('../shared/cutoverChecklistValidation')>(),
  focusChecklistValidationItem: validationFocus
}))
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
    props: { readonly: Boolean },
    emits: ['accepted', 'closed'],
    setup(props, { emit, expose }) {
      expose({ open: vi.fn() })
      return () => h('section', { 'data-testid': 'platform-collection-dialog', 'data-readonly': props.readonly }, [h('button', { 'data-testid': 'accept-platform-execution', onClick: () => emit('accepted', {
        id: '9007199254740994', task: { deviceId: '9007199254740991', templateId: acceptedTemplate.value }
      }) }, '确认平台采集执行'), h('button', { 'data-testid': 'close-platform-execution', onClick: () => emit('closed') }, '关闭采集')])
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
  beforeEach(() => { vi.clearAllMocks(); fileApi.getCurrentReference.mockReset().mockResolvedValue(null); acceptedTemplate.value = '9007199254740992'; vi.mocked(getDevicePage).mockReset().mockResolvedValue({ list: [], total: 0 }); vi.mocked(checkPermi).mockReturnValue(true) })
  it('uses the same server field descriptions in survey and approval without rewriting the frozen answer', () => {
    const schema = { fields: [{ code: 'executed', type: 'BOOLEAN', label: '服务端检查结论', required: true },
      { code: 'remark', type: 'TEXT', label: '服务端事实说明' }] }
    const snapshot = JSON.stringify({ value: { executed: false, remark: '保留原始说明' } })
    for (const readonly of [false, true]) {
      const survey = mount(CutoverChecklistField, { item: { ...item, itemTypeCode: 'DUAL_MACHINE_CHECK',
        interfaceSchemaSnapshot: JSON.stringify(schema) }, directValue: JSON.stringify({ executed: false, remark: '保留原始说明' }),
        readonly, allowSave: !readonly }, controls)
      expect(textOf(survey.root)).toContain('服务端检查结论')
      expect(textOf(survey.root)).not.toContain('是否已执行')
      survey.app.unmount()
    }
    const approval = mount(CutoverFrozenAnswer, { snapshot, schema })
    expect(textOf(approval.root)).toContain('服务端检查结论')
    expect(textOf(approval.root)).toContain('否')
    expect(textOf(approval.root)).toContain('保留原始说明')
    expect(snapshot).toBe(JSON.stringify({ value: { executed: false, remark: '保留原始说明' } }))
    approval.app.unmount()
  })
  it('reads the independent manual slot after collection replaces the saved manual result', async () => {
    fileApi.getCurrentReference.mockResolvedValue({ artifactId: '9007199254740993', versionNo: 2,
      referenceVersion: 0, referenceKey: 'cutover-checklist-risk-check' })
    const mounted = mount(CutoverChecklistField, { item: { ...item, workModeCode: 'COLLECTION',
      currentResult: { resultSourceCode: 'COLLECTION', answerSnapshot: '{"technicalStatus":"FAILED"}', loadFailureCode: 'CONNECT_TIMEOUT' } },
      directValue: '', readonly: false, allowSave: true }, controls)
    try {
      expect(findByTestId(mounted.root, 'manual-uploader')!.props!.disabled).toBe(true)
      await flush()
      expect(fileApi.getCurrentReference).toHaveBeenCalledWith({ ownerContext: 'CUT', objectType: 'CUTOVER_CHECKLIST_ITEM',
        objectId: item.itemId, purposeCode: 'MANUAL_EVIDENCE', referenceKey: 'cutover-checklist-risk-check' })
      expect(findByTestId(mounted.root, 'manual-uploader')!.props).toMatchObject({
        'artifact-id': '9007199254740993', 'expected-reference-version': 0, disabled: false })
      expect(textOf(mounted.root)).toContain('采集失败：CONNECT_TIMEOUT')
    } finally { mounted.app.unmount() }
  })
  it('blocks upload on slot read failure and retries without replacing the field', async () => {
    fileApi.getCurrentReference.mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({ artifactId: '9007199254740993', referenceVersion: 3 })
    const mounted = mount(CutoverChecklistField, { item: { ...item, workModeCode: 'MANUAL' }, directValue: '', readonly: false, allowSave: true }, controls)
    try {
      await flush()
      expect(findByTestId(mounted.root, 'manual-uploader')!.props!.disabled).toBe(true)
      await (findByTestId(mounted.root, 'retry-manual-reference')!.props!.onClick as Function)()
      await flush()
      expect(findByTestId(mounted.root, 'manual-uploader')!.props).toMatchObject({
        'artifact-id': '9007199254740993', 'expected-reference-version': 3, disabled: false })
    } finally { mounted.app.unmount() }
  })
  it('starts slot loading when editing becomes available and advances the cursor after upload', async () => {
    const allowed = ref(false)
    fileApi.getCurrentReference.mockResolvedValue({ artifactId: '9007199254740993', versionNo: 3, referenceVersion: 0 })
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { referenceVersion: 1, scopeVersion: 17 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5 }] })
    const mounted = mount(defineComponent({ setup: () => () => h(CutoverChecklistField,
      { item: { ...item, workModeCode: 'MANUAL' }, directValue: '', readonly: false, allowSave: allowed.value }) }), {}, controls)
    try {
      await flush()
      expect(fileApi.getCurrentReference).not.toHaveBeenCalled()
      allowed.value = true
      await flush()
      expect(findByTestId(mounted.root, 'manual-uploader')!.props!['expected-reference-version']).toBe(0)
      await (findByTestId(mounted.root, 'manual-uploader')!.props!.onClick as Function)()
      await flush()
      expect(findByTestId(mounted.root, 'manual-uploader')!.props!['expected-reference-version']).toBe(1)
      allowed.value = false
      await flush()
      allowed.value = true
      await flush()
      expect(fileApi.getCurrentReference).toHaveBeenCalledOnce()
    } finally { mounted.app.unmount() }
  })
  it('ignores an old slot read after switching checklist revisions and leaves readonly history untouched', async () => {
    let resolveOld!: (value: any) => void
    fileApi.getCurrentReference.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce({ artifactId: '9007199254740998', referenceVersion: 1 })
    const selected = ref({ ...item, workModeCode: 'MANUAL' as const })
    const mounted = mount(defineComponent({ setup: () => () => h(CutoverChecklistField,
      { item: selected.value, directValue: '', readonly: false, allowSave: true }) }), {}, controls)
    try {
      selected.value = { ...selected.value, itemId: '9007199254740996' }
      await flush()
      resolveOld({ artifactId: '9007199254740993', referenceVersion: 9 })
      await flush()
      expect(findByTestId(mounted.root, 'manual-uploader')!.props!['artifact-id']).toBe('9007199254740998')
      fileApi.getCurrentReference.mockClear()
      const history = mount(CutoverChecklistField, { item: selected.value, readonly: true, allowSave: true }, controls)
      await flush()
      expect(fileApi.getCurrentReference).not.toHaveBeenCalled()
      history.app.unmount()
    } finally { mounted.app.unmount() }
  })
  it('fills available device-list facts without blocking the survey or overwriting input entered while the source loads', async () => {
    let resolveList!: (value: any) => void
    vi.mocked(getDevicePage).mockImplementation(() => new Promise(resolve => { resolveList = resolve }))
    const current = checklistView(null)
    current.items[0] = { ...current.items[0], stableItemKey: 'SURVEY_DEVICE_LOCATION_PLAN', interfaceFormatCode: 'TABLE',
      interfaceSchemaSnapshot: JSON.stringify({ columns: [{ code: 'serialNumber', label: '设备序列号' },
        { code: 'deviceName', label: '设备名称' }, { code: 'location', label: '安装位置' }] }) }
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(current)
    checklistApi.getCutoverExternalState.mockResolvedValue(null)
    const page = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()
    try {
      expect(findByTestId(page.root, 'checklist-save')).toBeDefined()
      expect(getDevicePage).toHaveBeenCalledWith({ sn: 'SN-001', pageNo: 1, pageSize: 100 })
      findInput(page.root, '请填写核查事实').props['onUpdate:modelValue']('现场填写名称')
      await flush()
      resolveList({ list: [{ deviceId: taskDetail.devices[0].deviceId, sn: 'SN-001', name: '来源名称', locationSnapshot: '设备清单位置' }], total: 1 })
      await flush()
      checklistApi.saveCutoverChecklist.mockResolvedValue(undefined)
      await (findByTestId(page.root, 'checklist-save')!.props!.onClick as Function)()
      expect(JSON.parse(checklistApi.saveCutoverChecklist.mock.calls[0][0].data.answers[0].answerSnapshot).value)
        .toEqual([{ serialNumber: 'SN-001', deviceName: '现场填写名称', location: '设备清单位置' }])
    } finally { page.app.unmount() }
  })

  it('does not fetch mutable device-list facts for a readonly checklist or without device query permission', async () => {
    const current = checklistView(null)
    current.items[0] = { ...current.items[0], stableItemKey: 'SURVEY_DEVICE_LOCATION_PLAN', interfaceFormatCode: 'TABLE',
      interfaceSchemaSnapshot: '{"columns":[{"code":"serialNumber"},{"code":"deviceName"}]}' }
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(current)
    checklistApi.getCutoverExternalState.mockResolvedValue(null)
    const historical = mount(CutoverChecklistPanel, { detail: taskDetail, readonly: true }, controls)
    await flush(); historical.app.unmount()
    expect(getDevicePage).not.toHaveBeenCalled()
    vi.mocked(checkPermi).mockReturnValue(false)
    const denied = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush(); denied.app.unmount()
    expect(getDevicePage).not.toHaveBeenCalled()
  })

  it('keeps existing facts when the device-list lookup fails or its current project no longer matches', async () => {
    const current = checklistView('{"value":[{"serialNumber":"SN-001","deviceName":"原名称","location":""}]}')
    current.items[0] = { ...current.items[0], stableItemKey: 'SURVEY_DEVICE_LOCATION_PLAN', interfaceFormatCode: 'TABLE',
      interfaceSchemaSnapshot: '{"columns":[{"code":"serialNumber"},{"code":"deviceName"},{"code":"location"}]}' }
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(current)
    checklistApi.getCutoverExternalState.mockResolvedValue(null)
    const detail = { ...taskDetail, task: { ...taskDetail.task, projectId: '101' } }
    for (const failed of [true, false]) {
      if (failed) vi.mocked(getDevicePage).mockRejectedValueOnce(new Error('unavailable'))
      else vi.mocked(getDevicePage).mockResolvedValueOnce({ list: [{ deviceId: taskDetail.devices[0].deviceId, sn: 'SN-001',
        projectId: 'OTHER-PROJECT', name: '外部名称', locationSnapshot: '外部位置' } as any], total: 1 })
      const page = mount(CutoverChecklistPanel, { detail }, controls)
      await flush()
      try {
        await vi.waitFor(() => expect(textOf(page.root)).toContain('部分设备资料暂时无法读取'))
        const values: unknown[] = []
        const collect = (node: any) => { if (node.props?.placeholder === '请填写核查事实') values.push(node.props['model-value']); for (const child of node.children || []) collect(child) }
        collect(page.root)
        expect(values).toContain('原名称'); expect(values).toContain('')
        expect(values).not.toContain('外部名称'); expect(values).not.toContain('外部位置')
        expect(checklistApi.saveCutoverChecklist).not.toHaveBeenCalled()
      } finally { page.app.unmount() }
    }
  })
  it('uses task serial choices for nested schema fields without accepting free-text devices', async () => {
    const updates: unknown[] = []
    const mounted = mount(CutoverSchemaInput, { schema: { fields: [{ code: 'serialNumber', label: '设备序列号' }] },
      modelValue: {}, devices: taskDetail.devices, 'onUpdate:modelValue': (value: unknown) => updates.push(value) }, controls)
    try {
      const selectors: any[] = []
      const options: any[] = []
      const collect = (node: any) => {
        if (node.props?.placeholder === '选择割接设备') selectors.push(node.props)
        if (node.props?.label && node.props?.value) options.push(node.props)
        for (const child of node.children || []) collect(child)
      }
      collect(mounted.root)
      expect(selectors).toHaveLength(1)
      expect(selectors[0]['allow-create']).toBeUndefined()
      expect(options).toEqual([expect.objectContaining({ label: 'SN-001', value: 'SN-001' })])
      selectors[0]['onUpdate:modelValue']('SN-001')
      expect(updates).toEqual([{ serialNumber: 'SN-001' }])
    } finally { mounted.app.unmount() }
  })

  it('uses task identity for the software-version list and ignores foreign or mismatched device contexts', async () => {
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(checklistView(null))
    checklistApi.getCutoverExternalState.mockResolvedValueOnce({ checklistId: '201', checklistFactVersion: 3, items: [], devices: [
      { deviceId: taskDetail.devices[0].deviceId, sn: 'MISMATCH', productModel: 'WRONG-MODEL', currentSoftwareVersion: 'WRONG-VERSION' },
      { deviceId: 'OTHER-DEVICE', sn: 'FOREIGN-SN', productModel: 'FOREIGN-MODEL' }
    ] } as any)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()
    try {
      expect(textOf(mounted.root)).toContain('SN-001')
      expect(textOf(mounted.root)).not.toMatch(/MISMATCH|FOREIGN-SN|WRONG-MODEL|WRONG-VERSION|FOREIGN-MODEL/)
    } finally { mounted.app.unmount() }
  })
  it('rebuilds deployment rows from task devices, saves the change and preserves matching installation facts on reopen', async () => {
    let current = checklistView(JSON.stringify({ value: [{ serialNumber: 'OTHER-TASK', location: '旧设备机柜' },
      { serialNumber: 'SN-001', deviceName: '保留名称', location: '保留机柜' }] }))
    current.items[0] = { ...current.items[0], stableItemKey: 'SURVEY_DEVICE_LOCATION_PLAN', itemTypeCode: 'BUSINESS_SURVEY',
      interfaceFormatCode: 'TABLE', interfaceSchemaSnapshot: JSON.stringify({ columns: [
        { code: 'serialNumber', label: '设备序列号' }, { code: 'deviceName', label: '设备名称' }, { code: 'location', label: '设备安装位置' }
      ] }) }
    const detail = { ...taskDetail, devices: [...taskDetail.devices,
      { deviceId: '9007199254740993', serialNumber: 'SN-002', projectAssignmentVersion: '8' }] }
    checklistApi.getCutoverChecklist.mockReset().mockImplementation(async () => current)
    checklistApi.getCutoverExternalState.mockResolvedValue(null)
    checklistApi.saveCutoverChecklist.mockImplementation(async command => {
      current = { ...current, checklistFactVersion: current.checklistFactVersion + 1, items: current.items.map(row => ({
        ...row, currentResult: { ...row.currentResult!, answerSnapshot: command.data.answers[0].answerSnapshot }
      })) }
    })
    const mounted = mount(CutoverChecklistPanel, { detail }, controls)
    await flush()
    expect(checklistApi.saveCutoverChecklist).not.toHaveBeenCalled()
    await (findByTestId(mounted.root, 'checklist-save')!.props!.onClick as Function)()
    expect(JSON.parse(checklistApi.saveCutoverChecklist.mock.calls[0][0].data.answers[0].answerSnapshot).value)
      .toEqual([{ serialNumber: 'SN-001', deviceName: '保留名称', location: '保留机柜' }, { serialNumber: 'SN-002' }])
    mounted.app.unmount()
    checklistApi.saveCutoverChecklist.mockClear()
    const reopened = mount(CutoverChecklistPanel, { detail }, controls)
    await flush()
    await (findByTestId(reopened.root, 'checklist-save')!.props!.onClick as Function)()
    expect(checklistApi.saveCutoverChecklist).not.toHaveBeenCalled()
    reopened.app.unmount()
  })

  it('leaves submitted historical device rows unchanged and never saves their old serials', async () => {
    const current = checklistView(JSON.stringify({ value: [{ serialNumber: 'HISTORY-SN', location: '历史位置' }] }))
    current.status = 'SUBMITTED'
    current.items[0] = { ...current.items[0], stableItemKey: 'SURVEY_DEVICE_LOCATION_PLAN', interfaceFormatCode: 'TABLE',
      interfaceSchemaSnapshot: JSON.stringify({ columns: [{ code: 'serialNumber', label: '设备序列号' }] }) }
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(current)
    checklistApi.getCutoverExternalState.mockResolvedValue(null)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail, readonly: true }, controls)
    await flush()
    const serials: any[] = []
    const collect = (node: any) => { if (node.props?.['aria-label'] === '设备序列号') serials.push(node.props); for (const child of node.children || []) collect(child) }
    collect(mounted.root)
    expect(serials).toContainEqual(expect.objectContaining({ 'model-value': 'HISTORY-SN', readonly: true }))
    expect(findByTestId(mounted.root, 'checklist-save')).toBeUndefined()
    expect(checklistApi.saveCutoverChecklist).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
  it('rejects an absent file reference without emitting manual evidence', async () => {
    fileApi.getArtifact.mockResolvedValueOnce(null)
    fileApi.getVersions.mockClear()
    const manual = vi.fn()
    const mounted = mount(CutoverChecklistField, {
      item, directValue: '', readonly: false, allowSave: true, onManual: manual
    }, controls)
    try {
      await expect((mounted.vm as any).$.setupState.completeUpload({
        artifactId: '9007199254740993', versionNo: 4, referenceId: '9007199254740997', referenceKey: 'manual-proof'
      })).rejects.toThrow('文件引用已失效')
      expect(fileApi.getVersions).not.toHaveBeenCalled()
      expect(manual).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

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
    const material = mount(CutoverChecklistField, { item: { ...item, workModeCode: 'MANUAL' }, directValue: '', readonly: false, allowSave: true, onManual: (...args: unknown[]) => manual.push(args) }, controls)
    await flush()
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
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactId: '9007199254740993', artifactVersion: 2,
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
      resolve({ allowedActions: ['READ'], artifactId: '9007199254740993', artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
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
    expect(findByTestId(mounted.root, 'accept-platform-execution')).toBeUndefined()
    await (findByTestId(mounted.root, 'open-item-collection')!.props!.onClick as () => Promise<void>)()
    await (findByTestId(mounted.root, 'accept-platform-execution')!.props?.onClick as () => void)()
    expect(collection).toEqual([['risk-check', '9007199254740991', '9007199254740992', '9007199254740994']])
    mounted.app.unmount()
  })

  it.each([
    ['not collected', null],
    ['pending', { resultSourceCode: 'COLLECTION', collectionResultReferenceId: null }],
    ['failed', { resultSourceCode: 'COLLECTION', collectionResultReferenceId: '301', loadFailureCode: 'FAILED' }],
    ['manual evidence', { resultSourceCode: 'MANUAL' }]
  ])('shows an unanswered, disabled collection conclusion when %s', (_, currentResult) => {
    const onDirect = vi.fn()
    const mounted = mount(CutoverChecklistField, {
      item: { ...item, workModeCode: 'COLLECTION', interfaceSchemaSnapshot: JSON.stringify({
        fields: [{ code: 'normal', label: '检查是否正常', type: 'BOOLEAN', required: true }]
      }), currentResult }, directValue: '', readonly: false, allowSave: true, onDirect
    }, controls)
    try {
      expect(textOf(mounted.root)).toContain('检查是否正常')
      expect(findConclusion(mounted.root)?.props).toMatchObject({ disabled: true })
      expect(findConclusion(mounted.root)?.props?.['model-value']).toBeUndefined()
      expect(onDirect).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it.each([false, true])('retains the collected conclusion with readonly=%s', readonly => {
    const mounted = mount(CutoverChecklistField, {
      item: { ...item, workModeCode: 'COLLECTION', interfaceSchemaSnapshot: JSON.stringify({
        fields: [{ code: 'normal', label: '检查是否正常', type: 'BOOLEAN', required: true }]
      }), currentResult: { resultSourceCode: 'COLLECTION', collectionResultReferenceId: '301' } },
      directValue: '{"normal":false}', readonly, allowSave: true
    }, controls)
    try {
      expect(findConclusion(mounted.root)?.props).toMatchObject({ disabled: readonly, 'model-value': false })
    } finally { mounted.app.unmount() }
  })

  it.each([false, true])('allows a saved manual evidence conclusion and preserves readonly=%s', readonly => {
    const onDirect = vi.fn()
    const mounted = mount(CutoverChecklistField, { item: { ...item, workModeCode: 'COLLECTION',
      interfaceSchemaSnapshot: JSON.stringify({ fields: [{ code: 'normal', label: '检查是否正常', type: 'BOOLEAN', required: true }] }),
      currentResult: { resultSourceCode: 'MANUAL', answerSnapshot: JSON.stringify({ file: {
        artifactId: '9007199254740993', versionNo: 2, referenceKey: 'manual-proof' } }) } },
      directValue: '{"normal":false}', readonly, allowSave: true, onDirect }, controls)
    try {
      expect(findConclusion(mounted.root)?.props).toMatchObject({ disabled: readonly, 'model-value': false })
      if (!readonly) findConclusion(mounted.root)!.props!['onUpdate:modelValue'](true)
      expect(onDirect).toHaveBeenCalledTimes(readonly ? 0 : 1)
    } finally { mounted.app.unmount() }
  })

  it('hydrates manual analysis and saves only changed conclusions without resending its file as a direct result', async () => {
    const current = checklistView(null)
    current.items[0] = { ...current.items[0], workModeCode: 'COLLECTION', interfaceSchemaSnapshot: JSON.stringify({ fields: [
      { code: 'normal', label: '检查是否正常', type: 'BOOLEAN' }, { code: 'remark', label: '备注', type: 'TEXT' }] }), currentResult: {
      resultSourceCode: 'MANUAL', answerSnapshot: JSON.stringify({ file: { artifactId: '9007199254740993', versionNo: 2, referenceKey: 'manual-proof' },
        analysis: { value: { normal: false, remark: '原核查备注' } } }) } } as any
    current.items.push({ ...item, itemId: '302', stableItemKey: 'second-answer', workModeCode: 'DIRECT', currentResult: {
      resultSourceCode: 'DIRECT', answerSnapshot: '{"value":"已保存原值"}' } } as any)
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(current)
    checklistApi.saveCutoverChecklist.mockReset().mockResolvedValue(undefined)
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], reference: {} })
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    try {
      await flush()
      expect(findConclusion(mounted.root)!.props!['model-value']).toBe(false)
      expect(findInput(mounted.root, '请填写核查事实')!.props!['model-value']).toBe('原核查备注')
      findConclusion(mounted.root)!.props!['onUpdate:modelValue'](true)
      await flush()
      await (findByTestId(mounted.root, 'checklist-save')!.props!.onClick as Function)()
      const answers = checklistApi.saveCutoverChecklist.mock.calls[0][0].data.answers
      expect(answers).toHaveLength(1)
      expect(answers[0].answerSnapshot).toContain('normal')
      expect(answers[0].answerSnapshot).not.toContain('artifactId')
    } finally { mounted.app.unmount() }
  })

  it('can bind a completed upload again without uploading another file version', async () => {
    fileApi.getCurrentReference.mockResolvedValue({ artifactId: '9007199254740993', versionNo: 4,
      referenceKey: 'manual-proof', referenceVersion: 3 })
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2,
      reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5 }] })
    const onManual = vi.fn()
    const mounted = mount(CutoverChecklistField, { item: { ...item, workModeCode: 'COLLECTION', currentResult: {
      resultSourceCode: 'MANUAL', answerSnapshot: JSON.stringify({ file: {
        artifactId: '9007199254740993', versionNo: 3, referenceKey: 'manual-proof' } }) } },
      directValue: '{"normal":true}', readonly: false, allowSave: true, onManual }, controls)
    try {
      await flush()
      const retry = findByTestId(mounted.root, 'retry-manual-binding')
      expect(retry).toBeDefined()
      await (retry!.props!.onClick as Function)()
      await flush()
      expect(onManual).toHaveBeenCalledWith('risk-check', expect.objectContaining({
        artifactId: '9007199254740993', versionNo: 4,
        fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 } }), '')
    } finally { mounted.app.unmount() }
  })

  it('binds the new file before saving a pending conclusion against that file', async () => {
    let selectedVersion = 3
    let checklistVersion = 3
    let analysis: unknown
    const order: string[] = []
    checklistApi.getCutoverChecklist.mockReset().mockImplementation(async () => {
      const view = checklistView(null, checklistVersion)
      view.items[0] = { ...view.items[0], workModeCode: 'COLLECTION', interfaceSchemaSnapshot: JSON.stringify({ fields: [
        { code: 'normal', label: '检查是否正常', type: 'BOOLEAN' }] }), currentResult: {
        resultSourceCode: 'MANUAL', answerSnapshot: JSON.stringify({ file: {
          artifactId: '9007199254740993', versionNo: selectedVersion, referenceKey: 'manual-proof' },
          ...(analysis ? { analysis: { value: analysis } } : {}) }) } } as any
      return view
    })
    checklistApi.saveManualChecklistResult.mockReset().mockImplementation(async () => {
      order.push('bind'); selectedVersion = 4; checklistVersion++
    })
    checklistApi.saveCutoverChecklist.mockReset().mockImplementation(async ({ data }) => {
      if (selectedVersion !== 4) throw new Error('文件引用不存在：仍在校验旧版本')
      order.push('answer'); analysis = JSON.parse(data.answers[0].answerSnapshot).value; checklistVersion++
    })
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2,
      reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5 }] })
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    try {
      await flush()
      findConclusion(mounted.root)!.props!['onUpdate:modelValue'](true)
      await flush()
      await (findByTestId(mounted.root, 'manual-uploader')!.props!.onClick as Function)()
      await vi.waitFor(() => expect(order).toEqual(['bind', 'answer']))
      expect(analysis).toEqual({ normal: true })
      expect(findConclusion(mounted.root)!.props!['model-value']).toBe(true)
      expect(checklistApi.saveCutoverChecklist.mock.calls[0][0].data.expectedChecklistVersion).toBe(4)
    } finally {
      mounted.app.unmount()
      checklistApi.saveCutoverChecklist.mockReset()
      checklistApi.saveManualChecklistResult.mockReset()
    }
  })

  it('does not link an accepted platform execution from a readonly checklist', async () => {
    const onCollection = vi.fn()
    const mounted = mount(CutoverChecklistField, {
      item: { ...item, workModeCode: 'COLLECTION' }, directValue: '', readonly: true,
      allowCollection: false, onCollection
    }, controls)
    await (findByTestId(mounted.root, 'open-item-collection')!.props!.onClick as () => Promise<void>)()
    expect(findByTestId(mounted.root, 'platform-collection-dialog')?.props?.['data-readonly']).toBe(true)
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
    await (findByTestId(mounted.root, 'open-item-collection')!.props!.onClick as () => Promise<void>)()
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

  it('keeps entered checklist facts when a failed save triggers a source refresh', async () => {
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockImplementation(async () => checklistView(null))
    const failure = { response: { status: 409, data: { data: { recoveryAction: 'REFRESH_OWNER_FACTS' } } } }
    checklistApi.saveCutoverChecklist.mockRejectedValueOnce(failure)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()
    findInput(mounted.root, '请填写核查事实').props['onUpdate:modelValue']('必须保留的核查事实')
    await expect((findByTestId(mounted.root, 'checklist-save')!.props!.onClick as Function)()).rejects.toEqual(failure)
    await flush()
    expect(findInput(mounted.root, '请填写核查事实').props['model-value']).toBe('必须保留的核查事实')
    expect(checklistApi.getCutoverChecklist).toHaveBeenCalledTimes(2)
    mounted.app.unmount()
  })

  it('saves pending checklist facts before refreshing after collection closes', async () => {
    const current = () => ({ ...checklistView(null), items: [item, { ...item, itemId: '202', stableItemKey: 'collection-check', workModeCode: 'COLLECTION' }] })
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockImplementation(async () => current())
    checklistApi.saveCutoverChecklist.mockImplementation(async () => {
      checklistApi.getCutoverChecklist.mockImplementation(async () => ({ ...current(), items: [
        { ...item, currentResult: { resultSourceCode: 'DIRECT', answerSnapshot: '{"value":"关闭采集前的填写"}' } }, current().items[1]
      ] }))
    })
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()
    findInput(mounted.root, '请填写核查事实').props['onUpdate:modelValue']('关闭采集前的填写')
    await (findByTestId(mounted.root, 'open-item-collection')!.props!.onClick as () => Promise<void>)()
    await (findByTestId(mounted.root, 'close-platform-execution')!.props!.onClick as Function)()
    await vi.waitFor(() => expect(checklistApi.saveCutoverChecklist).toHaveBeenCalledOnce())
    await vi.waitFor(() => expect(checklistApi.getCutoverChecklist).toHaveBeenCalledTimes(3))
    expect(findInput(mounted.root, '请填写核查事实').props['model-value']).toBe('关闭采集前的填写')
    mounted.app.unmount()
    checklistApi.saveCutoverChecklist.mockReset()
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

  it('uploads collection manual verification evidence directly without creating a failed collection', async () => {
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5 }] })
    const manual = vi.fn()
    const mounted = mount(CutoverChecklistField, {
      item: { ...item, workModeCode: 'COLLECTION', currentResult: null }, directValue: '',
      readonly: false, allowSave: true, allowCollection: true, onManual: manual
    }, controls)
    expect(textOf(mounted.root)).toContain('上传人工校验证据')
    const uploader = findByTestId(mounted.root, 'manual-uploader')!
    for (const extension of ['.xls', '.xlsx', '.csv', '.txt', '.log', '.cfg'])
      expect(uploader.props?.accept).toContain(extension)
    await (uploader.props?.onClick as () => Promise<void>)()
    await flush()
    expect(manual).toHaveBeenCalledWith('risk-check', expect.objectContaining({ artifactId: '9007199254740993' }), '')
    expect(checklistApi.requestChecklistCollection).not.toHaveBeenCalled()
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
    expect(checklistApi.saveManualChecklistResult).toHaveBeenCalledWith(expect.objectContaining({
      stableItemKey: 'manual-check', data: expect.objectContaining({ expectedChecklistVersion: 3 })
    }))
    expect(checklistApi.saveCutoverChecklist.mock.calls[0][0].data.expectedChecklistVersion).toBe(4)
    expect(findInput(mounted.root, '请填写核查事实')?.props?.readonly).toBe(true)

    completeDirectSave()
    await vi.waitFor(() => expect(checklistApi.getCutoverChecklist).toHaveBeenCalledTimes(3))
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

  it('reveals and focuses a required item hidden by search after server validation rejects submission', async () => {
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockResolvedValue(checklistView(null))
    checklistApi.submitCutoverChecklist.mockReset()
    checklistApi.submitCutoverChecklist.mockRejectedValue({
      response: { status: 200, data: { msg: '风险核查 / 联系电话：尚未填写' } }
    })
    const refreshWorkspace = vi.fn()
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail, refreshWorkspace }, controls)
    await flush()
    const search = findInput(mounted.root, '查找采集项')!
    search.props['onUpdate:modelValue']('不存在的采集项')
    await nextTick()
    await (findByTestId(mounted.root, 'checklist-submit')!.props?.onClick as () => Promise<void>)()
    await flush()

    expect(findInput(mounted.root, '查找采集项').props.modelValue).toBe('')
    expect(textOf(mounted.root)).toContain('风险核查 / 联系电话：尚未填写')
    expect(validationFocus).toHaveBeenCalledWith(expect.anything(), 'risk-check')
    expect(checklistApi.submitCutoverChecklist).toHaveBeenCalledOnce()
    expect(refreshWorkspace).not.toHaveBeenCalled()
    findInput(mounted.root, '请填写核查事实').props['onUpdate:modelValue']('已补充')
    await nextTick()
    expect(textOf(mounted.root)).not.toContain('风险核查 / 联系电话：尚未填写')
    mounted.app.unmount()
  })

  it('does not turn authorization rejection into missing-item navigation', async () => {
    checklistApi.getCutoverChecklist.mockReset()
    checklistApi.getCutoverChecklist.mockResolvedValue(checklistView(null))
    checklistApi.submitCutoverChecklist.mockReset()
    const failure = { response: { status: 403, data: { msg: '风险核查：无权限' } } }
    checklistApi.submitCutoverChecklist.mockRejectedValue(failure)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    await flush()
    await expect((findByTestId(mounted.root, 'checklist-submit')!.props?.onClick as () => Promise<void>)())
      .rejects.toEqual(failure)
    expect(validationFocus).not.toHaveBeenCalled()
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
  it('loads external state alongside a known checklist instead of waiting for its full result', async () => {
    let resolveChecklist!: (value: CutoverChecklistView) => void
    checklistApi.getCutoverChecklist.mockReset().mockImplementation(() => new Promise(resolve => { resolveChecklist = resolve }))
    checklistApi.getCutoverExternalState.mockResolvedValue(null)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    try {
      await flush()
      expect(checklistApi.getCutoverExternalState).toHaveBeenCalledExactlyOnceWith('101', undefined)
      resolveChecklist(checklistView(null)); await flush()
      expect(findByTestId(mounted.root, 'checklist-save')).toBeDefined()
    } finally { mounted.app.unmount() }
  })

  it('offers stay or discard after a checklist save error and restores only the local draft', async () => {
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(checklistView(JSON.stringify('原核查事实')))
    checklistApi.getCutoverExternalState.mockReset().mockResolvedValue(null)
    checklistApi.saveCutoverChecklist.mockReset().mockRejectedValue(new Error('save unavailable'))
    const page = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    try {
      await flush()
      const state = (page.vm as any).$.setupState
      const saved = JSON.stringify(state.answers)
      state.answers['risk-check'] = '未保存核查事实'
      const leaving = (page.vm as any).saveBeforeLeave()
      for (let i = 0; i < 8; i++) await flush()
      expect(state.leaveDialogVisible).toBe(true)
      state.resolveLeaveDecision('stay')
      expect(await leaving).toBe(false)
      expect(state.answers['risk-check']).toBe('未保存核查事实')
      const discard = (page.vm as any).saveBeforeLeave()
      for (let i = 0; i < 8; i++) await flush()
      const writes = checklistApi.saveCutoverChecklist.mock.calls.length
      expect(writes).toBeGreaterThan(0)
      state.resolveLeaveDecision('discard')
      expect(await discard).toBe(true)
      expect(JSON.stringify(state.answers)).toBe(saved)
      expect(await (page.vm as any).saveBeforeLeave()).toBe(true)
      expect(checklistApi.saveCutoverChecklist).toHaveBeenCalledTimes(writes)
      expect(checklistApi.submitCutoverChecklist).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('allows explicit discard of unapplied matching choices without rematching or saving', async () => {
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(checklistView(null))
    checklistApi.getCutoverExternalState.mockReset().mockResolvedValue(null)
    const page = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    try {
      await flush()
      const state = (page.vm as any).$.setupState
      state.networkMode = 'UNAPPLIED'
      const leaving = (page.vm as any).saveBeforeLeave(); await flush()
      expect(state.leaveDialogVisible).toBe(true)
      state.resolveLeaveDecision('discard')
      expect(await leaving).toBe(true)
      expect(state.networkMode).toBe('')
      expect(checklistApi.generateCutoverChecklist).not.toHaveBeenCalled()
      expect(checklistApi.saveCutoverChecklist).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('does not request external state when a checklist has not been generated', async () => {
    checklistApi.getCutoverChecklist.mockReset().mockRejectedValue({ response: { status: 404 } })
    const mounted = mount(CutoverChecklistPanel, { detail: { ...taskDetail, workbenchSteps: [], allowedActions: ['GENERATE_CHECKLIST'] } }, controls)
    try { await flush(); expect(checklistApi.getCutoverExternalState).not.toHaveBeenCalled() }
    finally { mounted.app.unmount() }
  })

  it('blocks editing after a failed parallel read and restores it only after a successful retry', async () => {
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(checklistView(null))
    checklistApi.getCutoverExternalState.mockRejectedValueOnce(new Error('provider unavailable')).mockResolvedValue(null)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    try {
      await flush()
      expect(textOf(mounted.root)).toContain('调研清单加载失败或版本已变化')
      expect(findByTestId(mounted.root, 'checklist-save')).toBeUndefined()
      await (findByTestId(mounted.root, 'checklist-load-retry')!.props!.onClick as () => Promise<void>)()
      await flush()
      expect(findByTestId(mounted.root, 'checklist-save')).toBeDefined()
      expect(textOf(mounted.root)).not.toContain('调研清单加载失败或版本已变化')
    } finally { mounted.app.unmount() }
  })

  it('rejects external state belonging to a different checklist revision', async () => {
    checklistApi.getCutoverChecklist.mockReset().mockResolvedValue(checklistView(null))
    checklistApi.getCutoverExternalState.mockResolvedValueOnce({ checklistId: '999' } as any)
    const mounted = mount(CutoverChecklistPanel, { detail: taskDetail }, controls)
    try {
      await flush()
      expect(textOf(mounted.root)).toContain('调研清单加载失败或版本已变化')
      expect(findByTestId(mounted.root, 'checklist-save')).toBeUndefined()
      expect(checklistApi.saveCutoverChecklist).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
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

const findConclusion = (node: any): any => {
  if (node.props?.['onUpdate:modelValue'] && node.children?.some((child: any) => textOf(child) === '是')) return node
  for (const child of node.children || []) { const found = findConclusion(child); if (found) return found }
}
