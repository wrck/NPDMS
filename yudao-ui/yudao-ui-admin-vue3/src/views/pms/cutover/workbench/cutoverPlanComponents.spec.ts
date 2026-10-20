import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CutoverPlanView } from '@/api/pms/cutover'
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
  getSupportPersonChoices: vi.fn(async () => []),
  getProjectContactChoices: vi.fn(async () => []),
  getCutoverPlan: vi.fn(),
  createCutoverPlanDraft: vi.fn(),
  saveCutoverPlanDraft: vi.fn(),
  downloadCutoverPlanDraft: vi.fn(),
  submitCutoverPlan: vi.fn(),
  patchApprovedCutoverPlanContact: vi.fn(),
  reviseCutoverPlan: vi.fn()
}))
const messages = vi.hoisted(() => ({ error: vi.fn() }))
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
    fileApi.getCurrentReference.mockReset().mockResolvedValue(null)
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
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
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
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
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
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'C' }, controls)
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
      const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
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
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
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
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
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

  it('retains all locally edited plan fields when source recovery refreshes the server draft after rejection', async () => {
    api.getCutoverPlan.mockResolvedValue(standardPlan(['SAVE_DRAFT', 'SUBMIT_PLAN']))
    api.saveCutoverPlanDraft.mockRejectedValueOnce({ response: { status: 409, data: {
      msg: '方案来源事实已变化或尚未完成，请刷新前序环节', data: { recoveryAction: 'REFRESH_OWNER_FACTS' }
    } } })
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
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
    const mounted = mount(CutoverPlanPanel, { taskId: '101', taskVersion: 7, manualGrade: 'A' }, controls)
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
  await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick()
}
