import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CutoverPlanView } from '@/api/pms/cutover'
import { getCurrentDeviceSummaries } from '@/api/pms/asset/device'
import { checkPermi } from '@/utils/permission'
import CutoverPlanPanel from '../components/CutoverPlanPanel.vue'
import CutoverPlanEditor from '../components/CutoverPlanEditor.vue'
import CutoverWorkbenchSteps from '../components/CutoverWorkbenchSteps.vue'
import {
  findByTestId,
  mount,
  passthrough,
  textOf,
  type TestNode
} from '../cutoverTestHarness'

const api = vi.hoisted(() => ({
  getCutoverExternalState: vi.fn(),
  getCutoverChecklist: vi.fn(),
  getSupportPersonChoices: vi.fn(async () => []),
  getProjectContactChoices: vi.fn(async () => []),
  getCutoverPlan: vi.fn(),
  createCutoverPlanDraft: vi.fn(),
  saveCutoverPlanDraft: vi.fn(),
  downloadCutoverPlanDraft: vi.fn(),
  downloadSubmittedCutoverPlan: vi.fn(),
  submitCutoverPlan: vi.fn(),
  patchApprovedCutoverPlanContact: vi.fn(),
  reviseCutoverPlan: vi.fn()
}))
const messages = vi.hoisted(() => ({ error: vi.fn() }))
const submittedFileDownload = vi.hoisted(() => vi.fn())
vi.mock('@/utils/download', () => ({ default: { file: submittedFileDownload } }))
const supportValidationFocus = vi.hoisted(() => vi.fn())
vi.mock('../shared/cutoverPlanValidation', async importOriginal => ({
  ...await importOriginal<typeof import('../shared/cutoverPlanValidation')>(),
  focusPlanSupportValidation: supportValidationFocus
}))
const fileApi = vi.hoisted(() => ({ getArtifact: vi.fn(), getVersions: vi.fn(), createAccessTicket: vi.fn(), getCurrentReference: vi.fn() }))
vi.mock('@/api/pms/cutover', () => api)
vi.mock('@/api/pms/platform/file/version-reference', () => fileApi)
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ error: messages.error, success: vi.fn(), warning: vi.fn(), prompt: vi.fn(async () => ({ value: '值班人员变更' })) })
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
    vi.mocked(checkPermi).mockReturnValue(true)
    vi.mocked(getCurrentDeviceSummaries).mockReset().mockResolvedValue([{ deviceId: '9007199254740995', sn: 'SN-001' }] as any)
    api.getCutoverExternalState.mockReset().mockResolvedValue({ checklistId: '301', devices: [], items: [] })
    api.getCutoverChecklist.mockReset().mockResolvedValue({ taskId: '101', checklistId: '301', checklistVersion: 3, status: 'SUBMITTED', items: [] })
    fileApi.getCurrentReference.mockReset().mockResolvedValue(null)
    accessTarget.location.replace.mockReset()
    accessTarget.close.mockReset()
    vi.stubGlobal('window', { open: vi.fn(() => accessTarget) })
  })

  it('retains a failed plan draft, retries the original command and permits discard without deleting saved attachments', async () => {
    const server = standardPlan(['SAVE_DRAFT'])
    const original = structuredClone(server)
    api.getCutoverPlan.mockResolvedValue(server)
    api.saveCutoverPlanDraft.mockReset().mockRejectedValue(new Error('response lost'))
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      const state = (page.vm as any).$.setupState
      state.draftContent.overview.projectDescription = '未保存的说明'
      const leaving = (page.vm as any).saveBeforeLeave()
      for (let i = 0; i < 8; i++) await flush()
      expect(state.leaveDialogVisible).toBe(true)
      const command = api.saveCutoverPlanDraft.mock.calls[0][0]
      state.resolveLeaveDecision('retry')
      for (let i = 0; i < 8; i++) await flush()
      expect(state.leaveDialogVisible).toBe(true)
      expect(api.saveCutoverPlanDraft.mock.calls[1][0]).toEqual(command)
      expect(state.draftContent.overview.projectDescription).toBe('未保存的说明')
      state.resolveLeaveDecision('discard')
      expect(await leaving).toBe(true)
      expect(state.draftContent.overview.projectDescription).toBe('原方案')
      expect(await (page.vm as any).saveBeforeLeave()).toBe(true)
      expect(api.saveCutoverPlanDraft).toHaveBeenCalledTimes(2)
      expect(server).toEqual(original)
      expect(api.submitCutoverPlan).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('offers discard after plan save permission changes while keeping the save authorization enforced', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan([]))
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      const state = (page.vm as any).$.setupState
      state.draftContent.overview.projectDescription = '未保存的说明'
      const leaving = (page.vm as any).saveBeforeLeave(); await flush()
      expect(state.leaveDialogVisible).toBe(true)
      state.resolveLeaveDecision('discard')
      expect(await leaving).toBe(true)
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('fills task device model and running version and saves them with the ordinary draft', async () => {
    let server = standardPlan(['SAVE_DRAFT'])
    const original = structuredClone(server)
    vi.mocked(getCurrentDeviceSummaries).mockResolvedValue([{ deviceId: '9007199254740995', sn: 'SN-001', productModel: 'FW-1000', conpVersion: 'R-11' }] as any)
    api.getCutoverPlan.mockImplementation(async () => structuredClone(server))
    api.saveCutoverPlanDraft.mockImplementation(async command => { server = { ...server, content: command.data, planVersion: 2 }; return server })
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      expect(findByTestId(page.root, 'plan-device-9007199254740995-productModel')?.props?.['model-value']).toBe('FW-1000')
      expect(findByTestId(page.root, 'plan-device-9007199254740995-currentVersion')?.props?.['model-value']).toBe('R-11')
      expect(getCurrentDeviceSummaries).toHaveBeenCalledWith(['9007199254740995'], '9007199254741999')
      expect(server).toEqual(original)
      await click(page.root, 'save-plan'); await flush()
      expect(api.saveCutoverPlanDraft.mock.calls[0][0].data.overview.deviceDetails).toEqual([
        { deviceId: '9007199254740995', productModel: 'FW-1000', currentVersion: 'R-11', targetVersion: '', remark: '' }
      ])
      expect(findByTestId(page.root, 'plan-device-9007199254740995-currentVersion')?.props?.['model-value']).toBe('R-11')
    } finally { page.app.unmount() }
  })

  it('only fills missing fields and preserves manually recorded values, target version and remarks', async () => {
    const server = standardPlan(['SAVE_DRAFT'])
    if (server.content?.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    server.content.overview.deviceDetails = [{ deviceId: '9007199254740995', productModel: '原型号', currentVersion: '', targetVersion: '目标版本', remark: '原备注' }]
    vi.mocked(getCurrentDeviceSummaries).mockResolvedValue([{ deviceId: '9007199254740995', sn: 'SN-001', productModel: '新型号', conpVersion: 'R-11' }] as any)
    api.getCutoverPlan.mockResolvedValue(server)
    api.saveCutoverPlanDraft.mockResolvedValue(server)
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush(); await click(page.root, 'save-plan')
      expect(api.saveCutoverPlanDraft.mock.calls[0][0].data.overview.deviceDetails[0]).toEqual({
        deviceId: '9007199254740995', productModel: '原型号', currentVersion: 'R-11', targetVersion: '目标版本', remark: '原备注'
      })
      expect(server.content.overview.deviceDetails[0].currentVersion).toBe('')
    } finally { page.app.unmount() }
  })

  it('fills only blank target versions from the plan referenced checklist and saves the mapped device rows', async () => {
    const server = standardPlan(['SAVE_DRAFT'])
    if (server.content?.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    const first = server.content.overview.deviceSummary[0]
    server.content.overview.deviceSummary.push({ ...first, deviceId: '9007199254740996', serialNumber: 'SN-002' })
    server.content.overview.deviceDetails = [
      { deviceId: first.deviceId, productModel: 'FW-1000', currentVersion: 'R-11', targetVersion: '  ', remark: '保留备注' },
      { deviceId: '9007199254740996', productModel: 'FW-2000', currentVersion: 'R-12', targetVersion: '手填升级版本', remark: '' }
    ]
    api.getCutoverExternalState.mockResolvedValue({ checklistId: 301, devices: [
      { deviceId: '9007199254740996', sn: 'SN-002', targetSoftwareVersion: 'R-99' },
      { deviceId: first.deviceId, sn: 'SN-001', targetSoftwareVersion: 'R-22' }
    ] })
    api.getCutoverPlan.mockResolvedValue(server)
    api.saveCutoverPlanDraft.mockResolvedValue(server)
    // CUT target versions do not require the separate asset-query permission.
    vi.mocked(checkPermi).mockReturnValue(false)
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      expect(api.getCutoverExternalState).toHaveBeenCalledWith('101', '301')
      expect(findByTestId(page.root, 'plan-device-9007199254740995-targetVersion')?.props?.['model-value']).toBe('R-22')
      expect(findByTestId(page.root, 'plan-device-9007199254740996-targetVersion')?.props?.['model-value']).toBe('手填升级版本')
      await click(page.root, 'save-plan')
      expect(api.saveCutoverPlanDraft.mock.calls[0][0].data.overview.deviceDetails).toEqual([
        { deviceId: first.deviceId, productModel: 'FW-1000', currentVersion: 'R-11', targetVersion: 'R-22', remark: '保留备注' },
        { deviceId: '9007199254740996', productModel: 'FW-2000', currentVersion: 'R-12', targetVersion: '手填升级版本', remark: '' }
      ])
      expect(server.content.overview.deviceDetails[0].targetVersion).toBe('  ')
      expect(getCurrentDeviceSummaries).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it.each(['unavailable', 'wrong checklist', 'wrong device', 'no target'])('does not invent a target version when the source is %s', async condition => {
    if (condition === 'unavailable') api.getCutoverExternalState.mockRejectedValue(new Error('unavailable'))
    else api.getCutoverExternalState.mockResolvedValue({ checklistId: condition === 'wrong checklist' ? '302' : '301', devices: [
      { deviceId: '9007199254740995', sn: condition === 'wrong device' ? 'SN-OTHER' : 'SN-001', targetSoftwareVersion: condition === 'no target' ? null : 'R-99' }
    ] })
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT']))
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      expect(api.getCutoverExternalState).toHaveBeenCalledOnce()
      expect(findByTestId(page.root, 'plan-device-9007199254740995-targetVersion')?.props).toMatchObject({ 'model-value': '', disabled: false })
      if (condition !== 'no target') expect(textOf(page.root)).toContain('调研目标版本暂时无法读取')
    } finally { page.app.unmount() }
  })

  it.each(['failed', 'wrong device'])('keeps manual editing available when the device lookup is %s', async condition => {
    if (condition === 'failed') vi.mocked(getCurrentDeviceSummaries).mockRejectedValue(new Error('unavailable'))
    else vi.mocked(getCurrentDeviceSummaries).mockResolvedValue([{ deviceId: '9007199254740996', sn: 'SN-OTHER', productModel: '不应填入' }] as any)
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT']))
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      expect(findByTestId(page.root, 'plan-device-9007199254740995-productModel')?.props).toMatchObject({ 'model-value': '', disabled: false })
      expect(textOf(page.root)).toContain('部分设备信息暂时无法读取')
    } finally { page.app.unmount() }
  })

  it('keeps each device response on its own row when only part of the lookup succeeds', async () => {
    const server = standardPlan(['SAVE_DRAFT'])
    if (server.content?.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    server.content.overview.deviceSummary.push({ ...server.content.overview.deviceSummary[0], deviceId: '9007199254740996', serialNumber: 'SN-002' })
    vi.mocked(getCurrentDeviceSummaries).mockResolvedValue([{ deviceId: '9007199254740996', sn: 'SN-002', productModel: '第二台型号', conpVersion: 'R-22' }] as any)
    api.getCutoverPlan.mockResolvedValue(server)
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      expect(findByTestId(page.root, 'plan-device-9007199254740995-productModel')?.props?.['model-value']).toBe('')
      expect(findByTestId(page.root, 'plan-device-9007199254740996-productModel')?.props?.['model-value']).toBe('第二台型号')
      expect(findByTestId(page.root, 'plan-device-9007199254740996-currentVersion')?.props?.['model-value']).toBe('R-22')
      expect(getCurrentDeviceSummaries).toHaveBeenCalledOnce()
      expect(getCurrentDeviceSummaries).toHaveBeenCalledWith(['9007199254740995', '9007199254740996'], '9007199254741999')
      expect(textOf(page.root)).toContain('部分设备信息暂时无法读取')
    } finally { page.app.unmount() }
  })

  it.each(['readonly', 'submitted', 'no device permission'])('does not fill current asset facts into %s', async condition => {
    const server = standardPlan(['SAVE_DRAFT'])
    if (condition === 'submitted') { server.status = 'SUBMITTED'; server.allowedActions = [] }
    if (condition === 'no device permission') vi.mocked(checkPermi).mockReturnValue(false)
    api.getCutoverPlan.mockResolvedValue(server)
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A', readonly: condition === 'readonly' }, controls)
    try {
      await flush()
      expect(getCurrentDeviceSummaries).not.toHaveBeenCalled()
      if (condition !== 'no device permission') expect(api.getCutoverExternalState).not.toHaveBeenCalled()
      expect(findByTestId(page.root, 'plan-device-9007199254740995-productModel')?.props?.['model-value']).toBe('')
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('does not replace a newly opened task with an older pending summary batch', async () => {
    const first = standardPlan(['SAVE_DRAFT'])
    const second = { ...standardPlan(['SAVE_DRAFT']), taskId: '102', planRevisionId: '502', sourceSnapshot: null }
    api.getCutoverPlan.mockImplementation(async id => id === '101' ? first : second)
    let resolveOld!: (value: any) => void
    vi.mocked(getCurrentDeviceSummaries).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce([{ deviceId: '9007199254740995', sn: 'SN-001', productModel: '当前型号' }] as any)
    const taskId = ref('101')
    const page = mount(defineComponent({ setup: () => () => h(CutoverPlanPanel,
      { taskId: taskId.value, projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }) }), {}, controls)
    try {
      await vi.waitFor(() => expect(getCurrentDeviceSummaries).toHaveBeenCalledOnce())
      taskId.value = '102'
      await vi.waitFor(() => expect(findByTestId(page.root, 'plan-device-9007199254740995-productModel')?.props?.['model-value']).toBe('当前型号'))
      resolveOld([{ deviceId: '9007199254740995', sn: 'SN-001', productModel: '过期型号' }]); await flush()
      expect(findByTestId(page.root, 'plan-device-9007199254740995-productModel')?.props?.['model-value']).toBe('当前型号')
    } finally { page.app.unmount() }
  })

  it('edits and saves the complete A/B/C standard content exposed by server actions', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT', 'SUBMIT_PLAN']))
    api.saveCutoverPlanDraft.mockResolvedValue(standardPlan(['SAVE_DRAFT', 'SUBMIT_PLAN']))
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    const description = findByTestId(mounted.root, 'plan-project-description')!
    await (description.props?.['onUpdate:modelValue'] as (value: string) => void)('核心网割接实施方案')
    const operation = findByTestId(mounted.root, 'plan-step-OPERATION-1-content')!
    await (operation.props?.['onUpdate:modelValue'] as (value: string) => void)('执行割接命令')
    await click(mounted.root, 'save-plan')
    await flush()

    expect(api.saveCutoverPlanDraft).toHaveBeenCalledWith({ taskId: '101', taskVersion: 7, planVersion: 1, data: expect.objectContaining({
        editMode: 'ONLINE_TEMPLATE_STANDARD',
        overview: expect.objectContaining({ projectDescription: '核心网割接实施方案' }),
        steps: expect.arrayContaining([expect.objectContaining({ sectionCode: 'OPERATION', content: '执行割接命令' })]),
        supportArrangements: expect.arrayContaining([expect.objectContaining({ roleCode: 'CUSTOMER' })])
      }), idempotencyKey: expect.any(String) }
    )
    expect(textOf(mounted.root)).toContain('基本信息部分')
    mounted.app.unmount()
  })

  it('prefills an empty overview from the exact submitted survey while preserving the original server content', async () => {
    const server = standardPlan(['SAVE_DRAFT'])
    if (server.content?.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    server.content.overview.projectDescription = ''
    api.getCutoverChecklist.mockResolvedValue({ taskId: '101', checklistId: '301', checklistVersion: 3, status: 'SUBMITTED', items: [
      { stableItemKey: 'SURVEY_CUTOVER_BACKGROUND', applicable: true, currentResult: { answerSnapshot: JSON.stringify({ value: { backgroundDescription: '升级设备' } }) } },
      { stableItemKey: 'SURVEY_BUSINESS_SUMMARY', applicable: true, currentResult: { answerSnapshot: JSON.stringify({ value: '办公业务' }) } }
    ] })
    api.getCutoverPlan.mockResolvedValue(server)
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      expect(api.getCutoverChecklist).toHaveBeenCalledWith('101', '301')
      expect(findByTestId(page.root, 'plan-project-description')?.props?.['model-value']).toBe('割接背景：升级设备\n涉及业务：办公业务')
      expect(server.content.overview.projectDescription).toBe('')
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('shows predecessor topology in its original read context until saved and never treats it as post topology or network configuration', async () => {
    const content = standardPlan([]).content!
    const surveyItems = [{ itemId: '201', stableItemKey: 'SURVEY_CURRENT_TOPOLOGY', applicable: true,
      currentResult: { resultSourceCode: 'MANUAL', answerSnapshot: JSON.stringify({ file: uploadedFact() }) } }]
    const page = mount(CutoverPlanEditor, { modelValue: content, taskId: '101', editable: true,
      sourceSnapshot: sourceSnapshot(), surveyItems }, controls)
    try {
      await flush()
      expect(textOf(page.root)).toContain('来自调研，保存时带入方案')
      expect(fileApi.getArtifact).toHaveBeenCalledWith('9007199254740993', {
        ownerContext: 'CUT', objectType: 'CUTOVER_CHECKLIST_ITEM', objectId: '201',
        purposeCode: 'MANUAL_EVIDENCE', referenceKey: uploadedFact().referenceKey
      })
      if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
      expect(content.overview.preTopologyFile).toBeNull()
      expect(content.overview.postTopologyFile).toBeNull()
      expect(content.overview.networkConfigurationFile).toBeNull()
    } finally { page.app.unmount() }
  })

  it('keeps the submitted online plan download visible in read-only mode and only exports that version', async () => {
    const server = { ...standardPlan([]), status: 'SUBMITTED', taskStage: 'P5' }
    api.getCutoverPlan.mockResolvedValue(server)
    const blob = new Blob(['submitted-document'])
    api.downloadSubmittedCutoverPlan.mockResolvedValue(blob)
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A', readonly: true }, controls)
    try {
      await flush()
      expect(textOf(page.root)).toContain('下载方案')
      expect(findByTestId(page.root, 'save-plan')).toBeUndefined()
      await click(page.root, 'download-plan'); await flush()
      expect(api.downloadSubmittedCutoverPlan).toHaveBeenCalledWith({ taskId: '101', planVersion: 1 })
      expect(submittedFileDownload).toHaveBeenCalledWith(blob, '割接方案-v1.docx')
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
      expect(api.downloadCutoverPlanDraft).not.toHaveBeenCalled()
      expect(api.submitCutoverPlan).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('asks the server to validate the rendering format even when an older generated file is already displayed', async () => {
    const server = standardPlan(['DOWNLOAD_DRAFT'])
    const generated = { planRevisionId: '501', planVersion: 1, fileArtifactFact: uploadedFact(), downloadedAt: 1793498400000 }
    server.generatedDraft = generated
    api.getCutoverPlan.mockResolvedValue(server)
    api.downloadCutoverPlanDraft.mockResolvedValue(generated)
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'a'.repeat(64) }] })
    fileApi.createAccessTicket.mockResolvedValue({ shortLivedUrl: '/current-rendering' })
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush(); await click(page.root, 'download-plan'); await flush()
      expect(api.downloadCutoverPlanDraft).toHaveBeenCalledWith({ taskId: '101', planVersion: 1, idempotencyKey: expect.any(String) })
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
      expect(accessTarget.location.replace).toHaveBeenCalledWith('/current-rendering')
    } finally { page.app.unmount() }
  })

  it('downloads the exact submitted upload version through PLT without generating an online document', async () => {
    const server = standardPlan([])
    server.status = 'SUBMITTED'
    server.content = { editMode: 'FULL_FILE_UPLOAD', fileArtifactFact: uploadedFact(), ownershipConfirmed: true }
    api.getCutoverPlan.mockResolvedValue(server)
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'a'.repeat(64) }] })
    fileApi.createAccessTicket.mockResolvedValue({ shortLivedUrl: '/authorized-submitted-file' })
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A', readonly: true }, controls)
    try {
      await flush(); await click(page.root, 'download-plan'); await flush()
      expect(fileApi.createAccessTicket).toHaveBeenCalledWith(uploadedFact().artifactId, 4, 'DOWNLOAD', {
        ownerContext: 'CUT', objectType: 'CUTOVER_PLAN', objectId: '101', purposeCode: 'FULL_PLAN', referenceKey: uploadedFact().referenceKey
      })
      expect(accessTarget.location.replace).toHaveBeenCalledWith('/authorized-submitted-file')
      expect(api.downloadSubmittedCutoverPlan).not.toHaveBeenCalled()
      expect(api.downloadCutoverPlanDraft).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('retains the submitted download button after an export failure and allows retry without saving', async () => {
    api.getCutoverPlan.mockResolvedValue({ ...standardPlan([]), status: 'SUBMITTED' })
    api.downloadSubmittedCutoverPlan.mockRejectedValueOnce(new Error('unavailable')).mockResolvedValueOnce(new Blob(['retry']))
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush(); await click(page.root, 'download-plan'); await flush()
      expect(findByTestId(page.root, 'download-plan')!.props!.loading).toBe(false)
      await click(page.root, 'download-plan'); await flush()
      expect(api.downloadSubmittedCutoverPlan).toHaveBeenCalledTimes(2)
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('does not download another revision when the task has changed after the page opened', async () => {
    const original = { ...standardPlan([]), status: 'SUBMITTED' }
    api.getCutoverPlan.mockResolvedValueOnce(original).mockResolvedValue({ ...original, planRevisionId: '502' })
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush(); await click(page.root, 'download-plan'); await flush()
      expect(api.downloadSubmittedCutoverPlan).not.toHaveBeenCalled()
      expect(submittedFileDownload).not.toHaveBeenCalled()
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })

  it('switches an online draft to upload, requires confirmation, and keeps inputs when switching back', async () => {
    let server = standardPlan(['SAVE_DRAFT'])
    server.content!.scheduledTime = 1793498400000
    api.getCutoverPlan.mockImplementation(async () => structuredClone(server))
    api.saveCutoverPlanDraft.mockImplementation(async command => {
      server = { ...server, content: structuredClone(command.data), planVersion: server.planVersion! + 1 }
    })
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'a'.repeat(64) }] })
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush(); await flush()
      await (findByTestId(page.root, 'plan-project-description')!.props!['onUpdate:modelValue'] as Function)('保留当前填写')
      await (findByTestId(page.root, 'plan-edit-mode')!.props!['onUpdate:modelValue'] as Function)('FULL_FILE_UPLOAD')
      await flush()
      await click(page.root, 'save-plan')
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
      await click(page.root, 'plan-file-uploader'); await flush(); await flush()
      await click(page.root, 'save-plan')
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
      await (findByTestId(page.root, 'plan-ownership-confirmed')!.props!['onUpdate:modelValue'] as Function)(true)
      await flush()
      await click(page.root, 'save-plan'); await flush(); await flush()
      expect(server.content).toMatchObject({ editMode: 'FULL_FILE_UPLOAD', scheduledTime: 1793498400000,
        ownershipConfirmed: true, fileArtifactFact: { artifactId: '9007199254740993', versionNo: 4 } })
      await (findByTestId(page.root, 'plan-edit-mode')!.props!['onUpdate:modelValue'] as Function)('ONLINE_TEMPLATE_STANDARD')
      await flush(); await flush()
      expect(findByTestId(page.root, 'plan-project-description')!.props!['model-value']).toBe('保留当前填写')
      expect(textOf(page.root)).toContain('原上传方案（参考）')
    } finally { page.app.unmount() }
  })

  it.each(['A', 'D'] as const)('restores the uploaded reference after saving and reopening a %s online draft', async grade => {
    vi.mocked(getCurrentDeviceSummaries).mockResolvedValue([{ deviceId: '9007199254740995', sn: 'SN-001', productModel: 'FW-1000', conpVersion: 'R-11' }] as any)
    let server = standardPlan(['SAVE_DRAFT'])
    server.sourceSnapshot!.grade = grade
    server.content = { editMode: 'FULL_FILE_UPLOAD', fileArtifactFact: { ...uploadedFact(), referenceKey: 'cutover-plan-101' }, ownershipConfirmed: true, scheduledTime: 1793498400000 }
    const original = structuredClone(server)
    fileApi.getCurrentReference.mockImplementation(async key => key.referenceKey === 'cutover-plan-101'
      ? { ...key, artifactId: uploadedFact().artifactId, versionNo: 4, referenceVersion: 3, status: 'ACTIVE' } : null)
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'a'.repeat(64) }] })
    api.getCutoverPlan.mockImplementation(async () => structuredClone(server))
    api.saveCutoverPlanDraft.mockImplementation(async command => {
      server = { ...server, content: structuredClone(command.data), planVersion: server.planVersion! + 1 }
    })
    const props = { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: grade }
    const page = mount(CutoverPlanPanel, props, controls)
    await flush(); await flush(); await flush()
    expect(getCurrentDeviceSummaries).not.toHaveBeenCalled()
    await (findByTestId(page.root, 'plan-edit-mode')!.props!['onUpdate:modelValue'] as Function)(grade === 'D' ? 'ONLINE_TEMPLATE_SIMPLE_D' : 'ONLINE_TEMPLATE_STANDARD')
    await flush(); await flush()
    await click(page.root, 'save-plan'); await flush(); await flush()
    expect(server.content!.editMode).toBe(grade === 'D' ? 'ONLINE_TEMPLATE_SIMPLE_D' : 'ONLINE_TEMPLATE_STANDARD')
    expect(server.content!.scheduledTime).toBe(1793498400000)
    if (grade === 'A') {
      expect(server.content).toMatchObject({ overview: { deviceDetails: [
        { deviceId: '9007199254740995', productModel: 'FW-1000', currentVersion: 'R-11', targetVersion: '', remark: '' }
      ] } })
    } else expect(getCurrentDeviceSummaries).not.toHaveBeenCalled()
    expect(original.content!.editMode).toBe('FULL_FILE_UPLOAD')
    page.app.unmount()
    const reopened = mount(CutoverPlanPanel, props, controls)
    try {
      await flush(); await flush(); await flush()
      expect(textOf(reopened.root)).toContain('原上传方案（参考）')
      expect(findByTestId(reopened.root, 'plan-edit-mode')!.props!.disabled).toBe(false)
    } finally { reopened.app.unmount() }
  })

  it('disables mode changes until failed file reference loading is retried, and does not expose changes for submitted plans', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT']))
    fileApi.getCurrentReference.mockRejectedValueOnce(new Error('unavailable'))
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush(); await flush()
      expect(findByTestId(page.root, 'plan-edit-mode')!.props!.disabled).toBe(true)
      await click(page.root, 'retry-plan-upload-reference'); await flush()
      expect(findByTestId(page.root, 'plan-edit-mode')!.props!.disabled).toBe(false)
    } finally { page.app.unmount() }
    const submitted = { ...standardPlan(['DOWNLOAD_DRAFT']), status: 'SUBMITTED' }
    api.getCutoverPlan.mockResolvedValue(submitted)
    const history = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A', readonly: true }, controls)
    try { await flush(); expect(findByTestId(history.root, 'plan-edit-mode')).toBeUndefined() }
    finally { history.app.unmount() }
  })

  it('does not mark an unchanged restored upload as dirty because PLT and CUT serialize file fields in different order', async () => {
    const server = standardPlan(['SAVE_DRAFT'])
    server.content = { editMode: 'FULL_FILE_UPLOAD', scheduledTime: 1793498400000,
      fileArtifactFact: { artifactId: '9007199254740993', versionNo: 4, referenceKey: 'cutover-plan-101',
        fileFactVersion: { artifactVersion: 2, referenceVersion: 3, availabilityVersion: 5 }, scopeVersion: '17', sha256: 'a'.repeat(64) },
      ownershipConfirmed: true }
    api.getCutoverPlan.mockResolvedValue(server)
    fileApi.getCurrentReference.mockResolvedValue({ artifactId: '9007199254740993', versionNo: 4, referenceVersion: 3, status: 'ACTIVE' })
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'a'.repeat(64) }] })
    const page = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush(); await flush(); await flush()
      expect(textOf(page.root)).not.toContain('内容已修改')
    } finally { page.app.unmount() }
  })

  it('uses date-time pickers for operation and support dates while preserving duration text and legacy input', async () => {
    const model = standardPlan([]).content!
    if (model.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    model.steps = [
      { sectionCode: 'OPERATION', stepNo: 1, content: '执行', details: { stepName: '', operator: '', testResult: '', businessImpactTime: '5分钟', operationTime: '原时间待确定' } },
      { sectionCode: 'ROLLBACK', stepNo: 1, content: '恢复' },
      { sectionCode: 'POST_CUTOVER_SUPPORT', stepNo: 1, content: '2026-10-22 00:00:00' }
    ]
    const updates = vi.fn()
    const page = mount(CutoverPlanEditor, { modelValue: model, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: true, 'onUpdate:modelValue': updates }, controls)
    try {
      await flush()
      for (const id of ['plan-step-OPERATION-1-operationTime', 'plan-step-ROLLBACK-1-operationTime', 'plan-step-POST_CUTOVER_SUPPORT-1-content']) {
        expect(findByTestId(page.root, id)!.props).toMatchObject({ type: 'datetime', 'value-format': 'YYYY-MM-DD HH:mm:ss' })
      }
      expect(findByTestId(page.root, 'plan-step-OPERATION-1-businessImpactTime')!.props!.type).toBe('textarea')
      expect(textOf(page.root)).toContain('原时间待确定')
      await (findByTestId(page.root, 'plan-step-OPERATION-1-operationTime')!.props!['onUpdate:modelValue'] as Function)('2026-10-22 01:00:00')
      expect(updates.mock.calls.at(-1)![0].steps[0].details).toMatchObject({ operationTime: '2026-10-22 01:00:00', businessImpactTime: '5分钟' })
      expect(model.steps[0].details!.operationTime).toBe('原时间待确定')
    } finally { page.app.unmount() }
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
    expect(textOf(mounted.root)).toContain('D级简易割接')
    mounted.app.unmount()
  })

  it('freezes PLT file facts and creates the uploaded plan without converting Snowflake ids', async () => {
    api.getCutoverPlan
      .mockResolvedValueOnce(emptyPlan())
      .mockResolvedValue(standardPlan([]))
    api.createCutoverPlanDraft.mockResolvedValue(standardPlan([]))
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'a'.repeat(64) }] })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    const mode = findByTestId(mounted.root, 'plan-create-mode')!
    await (mode.props?.['onUpdate:modelValue'] as (value: string) => void)('FULL_FILE_UPLOAD')
    await nextTick()
    await (findByTestId(mounted.root, 'create-plan-uploader')!.props?.onClick as () => Promise<void>)()
    await flush()
    expect(findByTestId(mounted.root, 'create-plan-uploader')!.props).toMatchObject({
      'artifact-id': '9007199254740993', 'expected-reference-version': 3
    })
    const ownership = findByTestId(mounted.root, 'create-plan-ownership')!
    await (ownership.props?.['onUpdate:modelValue'] as (value: boolean) => void)(true)
    await nextTick()
    await click(mounted.root, 'create-plan')
    await flush()

    expect(api.createCutoverPlanDraft).toHaveBeenCalledWith({ taskId: '101', taskVersion: 7, data: {
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
    }, idempotencyKey: expect.any(String) })
    expect(typeof api.createCutoverPlanDraft.mock.calls[0][0].data.fileArtifactFact.artifactId).toBe('string')
    mounted.app.unmount()
  })

  it('shows missing plan content for a 422 generation failure without downloading or submitting', async () => {
    const plan = standardPlan(['DOWNLOAD_DRAFT'])
    if (plan.content?.editMode === 'ONLINE_TEMPLATE_STANDARD') {
      const customer = plan.content.supportArrangements[0]
      plan.content.supportArrangements = ['CUSTOMER', 'DP_FIRST_LINE', 'DP_SECOND_LINE', 'DP_RND']
        .map(roleCode => ({ ...customer, roleCode } as typeof customer))
    }
    api.getCutoverPlan.mockResolvedValue(plan)
    api.downloadCutoverPlanDraft.mockRejectedValueOnce({ response: { status: 422, data: {
      msg: '方案内容尚未完整', data: { category: 'BUSINESS_INCOMPLETE', reasonCode: 'PLAN_SECTION_INCOMPLETE', recoveryAction: 'FIX_REQUEST' }
    } } })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()
    await click(mounted.root, 'download-plan'); await flush()
    expect(messages.error).toHaveBeenCalledWith('方案内容尚未完整，请补充方案各章节及步骤后再生成或提交。')
    expect(accessTarget.close).toHaveBeenCalledOnce()
    expect(fileApi.createAccessTicket).not.toHaveBeenCalled()
    expect(api.submitCutoverPlan).not.toHaveBeenCalled()
    mounted.app.unmount()
  })

  it('names missing support roles and focuses their section after a generation rejection', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['DOWNLOAD_DRAFT']))
    api.downloadCutoverPlanDraft.mockRejectedValueOnce({ response: { status: 422, data: {
      msg: '方案内容尚未完整', data: { category: 'BUSINESS_INCOMPLETE', reasonCode: 'PLAN_SECTION_INCOMPLETE', recoveryAction: 'FIX_REQUEST' }
    } } })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'C' }, controls)
    await flush()
    await click(mounted.root, 'download-plan'); await flush()
    expect(messages.error).toHaveBeenCalledWith('保障安排缺少角色：迪普一线工程师。请新增人员并补齐保障信息。')
    expect(textOf(mounted.root)).toContain('保障安排缺少角色')
    expect(supportValidationFocus).toHaveBeenCalledOnce()
    expect(api.downloadCutoverPlanDraft).toHaveBeenCalledOnce()
    expect(api.submitCutoverPlan).not.toHaveBeenCalled()
    expect(accessTarget.close).toHaveBeenCalledOnce()
    expect(fileApi.createAccessTicket).not.toHaveBeenCalled()
    mounted.app.unmount()
  })

  it.each(['请填写计划割接时间', '方案步骤不完整', '标准方案概览不完整'])(
    'keeps the specific server rejection %s without redirecting to unrelated support fields', async (serverMessage) => {
      api.getCutoverPlan.mockResolvedValue(standardPlan(['DOWNLOAD_DRAFT']))
      api.downloadCutoverPlanDraft.mockRejectedValueOnce({ response: { status: 422, data: {
        msg: serverMessage,
        data: { category: 'BUSINESS_INCOMPLETE', reasonCode: 'PLAN_SECTION_INCOMPLETE', recoveryAction: 'FIX_REQUEST' }
      } } })
      const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'C' }, controls)
      await flush()
      await click(mounted.root, 'download-plan'); await flush()
      expect(messages.error).toHaveBeenCalledWith(serverMessage)
      expect(supportValidationFocus).not.toHaveBeenCalled()
      expect(textOf(mounted.root)).not.toContain('保障安排缺少角色')
      expect(api.submitCutoverPlan).not.toHaveBeenCalled()
      expect(fileApi.createAccessTicket).not.toHaveBeenCalled()
      mounted.app.unmount()
    }
  )

  it('highlights customer support after the server rejects a missing customer arrangement', async () => {
    const plan = standardPlan(['SUBMIT_PLAN'])
    if (plan.content?.editMode === 'ONLINE_TEMPLATE_STANDARD') plan.content.supportArrangements = []
    api.getCutoverPlan.mockResolvedValue(plan)
    api.submitCutoverPlan.mockRejectedValueOnce({ response: { status: 422, data: {
      msg: '客户保障安排尚未填写',
      data: { category: 'BUSINESS_INCOMPLETE', reasonCode: 'SUPPORT_ARRANGEMENT_INCOMPLETE', recoveryAction: 'FIX_REQUEST' }
    } } })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'C' }, controls)
    try {
      await flush()
      await click(mounted.root, 'submit-plan'); await flush()
      expect(messages.error).toHaveBeenCalledWith('保障安排缺少角色：客户、迪普一线工程师。请新增人员并补齐保障信息。')
      expect(textOf(mounted.root)).toContain('保障安排缺少角色：客户')
      expect(supportValidationFocus).toHaveBeenCalledOnce()
      expect(api.submitCutoverPlan).toHaveBeenCalledOnce()
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
      expect(api.downloadCutoverPlanDraft).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
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
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    await click(mounted.root, 'download-plan'); await flush()
    await click(mounted.root, 'submit-plan'); await flush()
    await click(mounted.root, 'revise-plan'); await flush()

    expect(api.downloadCutoverPlanDraft).toHaveBeenCalledWith({ taskId: '101', planVersion: 1, idempotencyKey: expect.any(String) })
    expect(fileApi.createAccessTicket).toHaveBeenCalledWith(
      '9007199254740993', 4, 'DOWNLOAD',
      expect.objectContaining({ ownerContext: 'CUT', purposeCode: 'FULL_PLAN', referenceKey: 'cutover-plan-file' })
    )
    expect(accessTarget.location.replace).toHaveBeenCalledWith('https://download.local/ticket')
    expect(api.submitCutoverPlan).toHaveBeenCalledWith({ taskId: '101', taskVersion: 7, planVersion: 1, idempotencyKey: expect.any(String) })
    expect(api.reviseCutoverPlan).toHaveBeenCalledWith({ taskId: '101', taskVersion: 7, data: { sourcePlanRevisionId: '501', reason: 'APPROVAL_REJECTED' }, idempotencyKey: expect.any(String) })
    mounted.app.unmount()
  })

  it('replaces the uploaded revision using its existing reference while keeping the old fact immutable', async () => {
    const frozenFact = { ...uploadedFact(), versionNo: 3 }
    const content = { editMode: 'FULL_FILE_UPLOAD' as const, fileArtifactFact: frozenFact, ownershipConfirmed: true }
    const updates: any[] = []
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 3, reference: { scopeVersion: 17, referenceVersion: 4 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'b'.repeat(64) }] })
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content, sourceSnapshot: null, taskId: '101', editable: true,
      'onUpdate:modelValue': (value: unknown) => updates.push(value)
    }, controls)

    expect(findByTestId(mounted.root, 'plan-file-uploader')!.props).toMatchObject({
      'artifact-id': frozenFact.artifactId,
      'reference-key': frozenFact.referenceKey,
      'expected-reference-version': 3
    })
    await click(mounted.root, 'plan-file-uploader'); await flush()
    expect(updates.at(-1).fileArtifactFact).toMatchObject({
      artifactId: frozenFact.artifactId, referenceKey: frozenFact.referenceKey,
      versionNo: 4, fileFactVersion: { referenceVersion: 4 }
    })
    expect(frozenFact.versionNo).toBe(3)
    expect(frozenFact.fileFactVersion.referenceVersion).toBe(3)
    mounted.app.unmount()
  })

  it.each(['preTopologyFile', 'postTopologyFile', 'networkConfigurationFile'] as const)(
    'retains the existing file identity and reference version when replacing %s', (field) => {
      const content = standardPlan([]).content!
      if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
      content.overview[field] = uploadedFact()
      const mounted = mount(CutoverPlanEditor, {
        modelValue: content, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: true
      }, controls)
      expect(findByTestId(mounted.root, `plan-file-${field}`)!.props).toMatchObject({
        'artifact-id': '9007199254740993', 'reference-key': 'cutover-plan-file', 'expected-reference-version': 3
      })
      mounted.app.unmount()
    }
  )

  it('stages uploaded standard files into an empty draft without persisting or changing the original input', async () => {
    const content = standardPlan([]).content!
    const updates = vi.fn()
    fileApi.getCurrentReference.mockImplementation(async (key) => ({ ...key,
      artifactId: '9007199254740993', referenceVersion: 0, versionNo: 1, status: 'ACTIVE'
    }))
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 1, reference: { scopeVersion: 17, referenceVersion: 0 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 1, availabilityVersion: 1, sha256: 'b'.repeat(64) }] })
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: true,
      'onUpdate:modelValue': updates
    }, controls)
    try {
      await flush(); await flush()
      for (const field of ['preTopologyFile', 'postTopologyFile', 'networkConfigurationFile']) {
        expect(findByTestId(mounted.root, `plan-file-${field}`)!.props).toMatchObject({
          'artifact-id': '9007199254740993', 'expected-reference-version': 0, disabled: false
        })
      }
      expect(fileApi.getCurrentReference).toHaveBeenCalledTimes(3)
      expect(fileApi.getCurrentReference).toHaveBeenCalledWith(expect.objectContaining({
        ownerContext: 'CUT', objectType: 'CUTOVER_PLAN', objectId: '101', purposeCode: 'FULL_PLAN',
        referenceKey: 'cutover-plan-101-pre-topology'
      }))
      expect(updates).toHaveBeenCalledOnce()
      const restored = updates.mock.calls[0][0]
      expect(restored.overview.preTopologyFile.versionNo).toBe(1)
      expect(restored.overview.postTopologyFile.versionNo).toBe(1)
      expect(restored.overview.networkConfigurationFile.versionNo).toBe(1)
      expect(content.editMode === 'ONLINE_TEMPLATE_STANDARD' && content.overview.preTopologyFile).toBeNull()
      expect(textOf(mounted.root)).toContain('保存方案后生效')
    } finally { mounted.app.unmount() }
  })

  it.each(['preTopologyFile', 'postTopologyFile', 'networkConfigurationFile'] as const)(
    'saves the uploaded newer version after reopening a draft that still references V1 in %s', async field => {
      let server = standardPlan(['SAVE_DRAFT'])
      if (server.content?.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
      const key = `cutover-plan-101-${field}`
      const original = { ...uploadedFact(), referenceKey: key, versionNo: 1,
        fileFactVersion: { artifactVersion: 1, referenceVersion: 0, availabilityVersion: 1 } }
      server.content.overview[field] = original
      api.getCutoverPlan.mockImplementation(async () => structuredClone(server))
      api.saveCutoverPlanDraft.mockImplementation(async command => {
        server = { ...server, content: structuredClone(command.data), planVersion: 2 }
        return server
      })
      fileApi.getCurrentReference.mockImplementation(async business => business.referenceKey === key
        ? { ...business, artifactId: original.artifactId, versionNo: 2, referenceVersion: 1, status: 'ACTIVE' } : null)
      fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 1 } })
      fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 2, availabilityVersion: 1, sha256: 'b'.repeat(64) }] })
      const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
      try {
        await flush(); await flush()
        await click(mounted.root, 'save-plan'); await flush()
        expect(api.saveCutoverPlanDraft.mock.calls.at(-1)?.[0].data.overview[field]).toMatchObject({
          artifactId: original.artifactId, versionNo: 2, fileFactVersion: { referenceVersion: 1 }
        })
        expect(server.content?.editMode === 'ONLINE_TEMPLATE_STANDARD' && server.content.overview[field]?.versionNo).toBe(2)
        expect(original.versionNo).toBe(1)
        expect(original.fileFactVersion.referenceVersion).toBe(0)
      } finally { mounted.app.unmount() }
    }
  )

  it('blocks standard uploads after a failed reference read and lets the user retry without discarding plan edits', async () => {
    fileApi.getCurrentReference.mockRejectedValue(new Error('reference unavailable'))
    const content = standardPlan([]).content!
    const updates = vi.fn()
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: true,
      'onUpdate:modelValue': updates
    }, controls)
    try {
      await flush()
      expect(findByTestId(mounted.root, 'plan-file-preTopologyFile')!.props?.disabled).toBe(true)
      expect(textOf(mounted.root)).toContain('文件状态读取失败')
      fileApi.getCurrentReference.mockResolvedValue(null)
      await click(mounted.root, 'retry-plan-file-references'); await flush()
      expect(findByTestId(mounted.root, 'plan-file-preTopologyFile')!.props?.disabled).toBe(false)
      expect(updates).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('waits for current reference lookup before saving a reopened draft', async () => {
    let complete!: (value: unknown) => void
    fileApi.getCurrentReference.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT']))
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      expect(findByTestId(mounted.root, 'save-plan')!.props?.disabled).toBe(true)
      await click(mounted.root, 'save-plan')
      expect(api.saveCutoverPlanDraft).not.toHaveBeenCalled()
      complete(null); await flush(); await flush()
      expect(findByTestId(mounted.root, 'save-plan')!.props?.disabled).toBe(false)
    } finally { mounted.app.unmount() }
  })

  it('does not downgrade a pending newer file fact from a delayed older reference', async () => {
    const content = standardPlan([]).content!
    if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    content.overview.preTopologyFile = uploadedFact()
    const updates = vi.fn()
    fileApi.getCurrentReference.mockImplementation(async key => key.referenceKey === 'cutover-plan-file'
      ? { ...key, artifactId: '9007199254740993', versionNo: 2, referenceVersion: 1, status: 'ACTIVE' } : null)
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: true,
      'onUpdate:modelValue': updates
    }, controls)
    try {
      await flush(); await flush()
      expect(updates).not.toHaveBeenCalled()
      expect(content.overview.preTopologyFile.versionNo).toBe(4)
      expect(findByTestId(mounted.root, 'plan-file-preTopologyFile')!.props?.['expected-reference-version']).toBe(3)
    } finally { mounted.app.unmount() }
  })

  it('requires rereading instead of combining a recovered file with a concurrently changed reference version', async () => {
    const content = standardPlan([]).content!
    if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    content.overview.preTopologyFile = { ...uploadedFact(), referenceKey: 'cutover-plan-file', versionNo: 1,
      fileFactVersion: { artifactVersion: 1, referenceVersion: 0, availabilityVersion: 1 } }
    fileApi.getCurrentReference.mockImplementation(async key => key.referenceKey === 'cutover-plan-file'
      ? { ...key, artifactId: '9007199254740993', versionNo: 2, referenceVersion: 1, status: 'ACTIVE' } : null)
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 3,
      reference: { scopeVersion: 17, referenceVersion: 2 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 2, availabilityVersion: 1, sha256: 'b'.repeat(64) }] })
    const updates = vi.fn()
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: true,
      'onUpdate:modelValue': updates
    }, controls)
    try {
      await flush(); await flush()
      expect(updates).not.toHaveBeenCalled()
      expect(content.overview.preTopologyFile.versionNo).toBe(1)
      expect(textOf(mounted.root)).toContain('文件状态读取失败')
      expect(findByTestId(mounted.root, 'plan-file-preTopologyFile')!.props?.disabled).toBe(true)
    } finally { mounted.app.unmount() }
  })

  it('does not look up current standard slot references when displaying frozen history', async () => {
    const content = standardPlan([]).content!
    if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    content.overview.preTopologyFile = { ...uploadedFact(), versionNo: 1 }
    const updates = vi.fn()
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: false,
      'onUpdate:modelValue': updates
    }, controls)
    try {
      await flush()
      expect(fileApi.getCurrentReference).not.toHaveBeenCalled()
      expect(updates).not.toHaveBeenCalled()
      expect(content.overview.preTopologyFile.versionNo).toBe(1)
    } finally { mounted.app.unmount() }
  })

  it('uses the current upload concurrency version while retaining the saved file fact until a new upload completes', async () => {
    const content = standardPlan([]).content!
    if (content.editMode !== 'ONLINE_TEMPLATE_STANDARD') throw new Error('fixture')
    const frozen = uploadedFact()
    content.overview.preTopologyFile = frozen
    const updates = vi.fn()
    fileApi.getCurrentReference.mockResolvedValue({ artifactId: frozen.artifactId, referenceVersion: 4 })
    const mounted = mount(CutoverPlanEditor, {
      modelValue: content, sourceSnapshot: sourceSnapshot(), taskId: '101', editable: true,
      'onUpdate:modelValue': updates
    }, controls)
    try {
      await flush()
      expect(findByTestId(mounted.root, 'plan-file-preTopologyFile')!.props?.['expected-reference-version']).toBe(4)
      expect(frozen.fileFactVersion.referenceVersion).toBe(3)
      expect(updates).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('ignores an old task reference response after switching the plan being edited', async () => {
    const pending = new Map<string, Array<(value: unknown) => void>>()
    fileApi.getCurrentReference.mockImplementation(key => new Promise(resolve => {
      const requests = pending.get(key.objectId) || []
      requests.push(resolve); pending.set(key.objectId, requests)
    }))
    const taskId = ref('101')
    const mounted = mount(defineComponent({ setup: () => () => h(CutoverPlanEditor, {
      modelValue: standardPlan([]).content!, sourceSnapshot: sourceSnapshot(), taskId: taskId.value, editable: true
    }) }), {}, controls)
    try {
      await flush(); taskId.value = '102'; await flush()
      pending.get('102')!.forEach(resolve => resolve({ artifactId: '9007199254740995', referenceVersion: 2 }))
      await flush()
      pending.get('101')!.forEach(resolve => resolve({ artifactId: '9007199254740993', referenceVersion: 1 }))
      await flush()
      expect(findByTestId(mounted.root, 'plan-file-preTopologyFile')!.props).toMatchObject({
        'artifact-id': '9007199254740995', 'expected-reference-version': 2
      })
    } finally { mounted.app.unmount() }
  })

  it('patches an approved P6 support contact with the frozen arrangement identity', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['UPDATE_APPROVED_CONTACTS'], 'APPROVED', 'P6'))
    api.patchApprovedCutoverPlanContact.mockResolvedValue(standardPlan([]))
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 9, manualGrade: 'A' }, controls)
    await flush()

    const arrival = findByTestId(mounted.root, 'support-arrival-0')!
    expect(arrival.props?.disabled).toBe(false)
    await (arrival.props?.['onUpdate:modelValue'] as (value: number) => void)(1788220801000)
    await nextTick()
    await click(mounted.root, 'patch-support-CUSTOMER')
    await flush()

    expect(api.patchApprovedCutoverPlanContact).toHaveBeenCalledWith({ taskId: '101', arrangementId: '601', planVersion: 1, data: {
      personName: expect.any(String), phone: '13800000000', arrivalTime: 1788220801000, reason: '值班人员变更', customerContactReference: { contactId: '801', contactVersion: 2 }, personReference: null
    }, idempotencyKey: expect.any(String) })
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

    const second = findByTestId(mounted.root, 'plan-step-OPERATION-2-content')!
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
    fileApi.getArtifact.mockResolvedValue({ allowedActions: ['READ'], artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'b'.repeat(64) }] })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    for (const field of ['preTopologyFile', 'postTopologyFile', 'networkConfigurationFile']) {
      await click(mounted.root, `plan-file-${field}`)
      await flush()
    }
    await click(mounted.root, 'save-plan')
    await flush()

    expect(api.saveCutoverPlanDraft).toHaveBeenCalledWith({ taskId: '101', taskVersion: 7, planVersion: 1, data: expect.objectContaining({ overview: expect.objectContaining({
        preTopologyFile: expect.objectContaining({ referenceKey: 'cutover-plan-101-pre-topology' }),
        postTopologyFile: expect.objectContaining({ referenceKey: 'cutover-plan-101-post-topology' }),
        networkConfigurationFile: expect.objectContaining({ referenceKey: 'cutover-plan-101-network-configuration' })
      }) }), idempotencyKey: expect.any(String) })
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
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    await flush()

    await click(mounted.root, 'save-plan'); await flush()
    expect(api.saveCutoverPlanDraft).toHaveBeenCalledTimes(1)
    await click(mounted.root, 'save-plan'); await flush()
    expect(api.saveCutoverPlanDraft).toHaveBeenCalledTimes(1)
    await click(mounted.root, 'save-plan'); await flush()
    expect(api.saveCutoverPlanDraft).toHaveBeenCalledTimes(2)
    mounted.app.unmount()
  })

  it('retains all locally edited plan fields when source recovery refreshes the server draft after rejection', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT', 'SUBMIT_PLAN']))
    api.saveCutoverPlanDraft.mockRejectedValueOnce({ response: { status: 409, data: {
      msg: '方案来源事实已变化或尚未完成，请刷新前序环节', data: { recoveryAction: 'REFRESH_OWNER_FACTS' }
    } } })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      await (findByTestId(mounted.root, 'plan-project-description')!.props?.['onUpdate:modelValue'] as Function)('不能丢失的项目概述')
      await (findByTestId(mounted.root, 'plan-step-OPERATION-1-content')!.props?.['onUpdate:modelValue'] as Function)('不能丢失的操作步骤')
      await click(mounted.root, 'save-plan'); await flush()
      expect(findByTestId(mounted.root, 'plan-project-description')!.props?.['model-value']).toBe('不能丢失的项目概述')
      expect(findByTestId(mounted.root, 'plan-step-OPERATION-1-content')!.props?.['model-value']).toBe('不能丢失的操作步骤')
      expect(api.saveCutoverPlanDraft).toHaveBeenCalledOnce()
      expect(api.submitCutoverPlan).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('saves pending edits before submission and retains the persisted draft when completeness validation rejects submission', async () => {
    let server = standardPlan(['SAVE_DRAFT', 'SUBMIT_PLAN'])
    api.getCutoverPlan.mockImplementation(async () => JSON.parse(JSON.stringify(server)))
    api.saveCutoverPlanDraft.mockImplementation(async command => {
      server = { ...server, content: command.data, planVersion: 2 }
      return server
    })
    api.submitCutoverPlan.mockRejectedValueOnce({ response: { status: 422, data: {
      msg: '方案内容尚未完整', data: { reasonCode: 'PLAN_SECTION_INCOMPLETE', recoveryAction: 'START_NEW_INTENT' }
    } } })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', projectId: '9007199254741999', taskVersion: 7, manualGrade: 'A' }, controls)
    try {
      await flush()
      await (findByTestId(mounted.root, 'plan-project-description')!.props?.['onUpdate:modelValue'] as Function)('已落为草稿的填写')
      await click(mounted.root, 'submit-plan'); await flush()
      expect(api.saveCutoverPlanDraft).toHaveBeenCalledOnce()
      expect(api.submitCutoverPlan).toHaveBeenCalledOnce()
      expect(api.saveCutoverPlanDraft.mock.invocationCallOrder.at(-1)!).toBeLessThan(api.submitCutoverPlan.mock.invocationCallOrder.at(-1)!)
      expect(server.content?.editMode === 'ONLINE_TEMPLATE_STANDARD' && server.content.overview.projectDescription).toBe('已落为草稿的填写')
      expect(findByTestId(mounted.root, 'plan-project-description')!.props?.['model-value']).toBe('已落为草稿的填写')
    } finally { mounted.app.unmount() }
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
    expect(textOf(mounted.root)).not.toContain('割接准备')
    expect(textOf(mounted.root)).toContain('割接方案')
    mounted.app.unmount()
  })
})

const standardPlan = (
  allowedActions: CutoverPlanView['allowedActions'],
  approvalStatus?: 'REJECTED' | 'APPROVED',
  taskStage: 'P4' | 'P6' = 'P4'
): CutoverPlanView => ({
  taskId: '101', taskStage, taskVersion: 7, planRevisionId: '501', revisionNo: 1, planVersion: 1,
  originCode: 'NEW_PLATFORM', status: 'DRAFT',
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
      dutyDescription: '现场确认', phone: '13800000000', arrivalTime: 1788220800000, customerContactReference: { contactId: '801', contactVersion: 2 }
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
  planVersion: null, originCode: null, status: null,
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
  for (let index = 0; index < 12; index++) { await Promise.resolve(); await nextTick() }
}
