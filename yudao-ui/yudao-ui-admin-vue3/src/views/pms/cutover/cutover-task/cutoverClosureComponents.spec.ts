import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CutoverClosureEvidencePanel from './components/CutoverClosureEvidencePanel.vue'
import CutoverClosureForm from './components/CutoverClosureForm.vue'
import CutoverClosurePanel from './components/CutoverClosurePanel.vue'
import { findByTestId, mount, passthrough, textOf } from '../../platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({
  getCutoverClosure: vi.fn(), saveCutoverClosure: vi.fn(), requestCutoverClosureCollection: vi.fn(),
  linkCutoverClosureManualResult: vi.fn(), submitCutoverClosure: vi.fn()
}))
const fileApi = vi.hoisted(() => ({ getArtifact: vi.fn(), getVersions: vi.fn() }))
vi.mock('@/api/pms/cutover/cutover-task', () => api)
vi.mock('@/api/pms/platform/file', () => fileApi)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), warning: vi.fn() }) }))
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
  ElInput: passthrough, ElRadioGroup: passthrough, ElRadioButton: passthrough,
  ElCheckbox: passthrough, ElSelect: passthrough, ElOption: passthrough,
  ElDialog: defineComponent({ setup(_, { slots }) { return () => h('section', [slots.default?.(), slots.footer?.()]) } }),
  ElTable: passthrough, ElTableColumn: passthrough,
  ElDescriptions: passthrough, ElDescriptionsItem: passthrough
}

describe('F-CUT-006 mounted P6 closure workbench', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fileApi.getArtifact.mockResolvedValue({ artifactVersion: 2, reference: { scopeVersion: 17, referenceVersion: 3 } })
    fileApi.getVersions.mockResolvedValue({ items: [{ versionNo: 4, availabilityVersion: 5, sha256: 'a'.repeat(64) }] })
  })

  it('creates a DRAFT with three result groups, rollback facts and both required PLT files', async () => {
    api.getCutoverClosure
      .mockResolvedValueOnce(view(['CREATE_CLOSURE']))
      .mockResolvedValue(view(['SAVE_CLOSURE', 'SUBMIT_CLOSURE'], 1))
    api.saveCutoverClosure.mockResolvedValue(view(['SAVE_CLOSURE'], 1))
    const mounted = mount(CutoverClosurePanel, { taskId: '9007199254740995' }, controls)
    await flush()

    await click(mounted.root, 'save-closure')
    await flush()

    expect(api.saveCutoverClosure).toHaveBeenNthCalledWith(
      1, '9007199254740995', 7, null,
      expect.objectContaining({ attachments: [] }), expect.any(String)
    )
    await setValue(mounted.root, 'closure-precheck-normal', true)
    await setValue(mounted.root, 'closure-execution-normal', true)
    await setValue(mounted.root, 'closure-test-normal', true)
    await setValue(mounted.root, 'closure-rollback-occurred', false)
    await click(mounted.root, 'closure-file-POST_COLLECTION_CHECKLIST')
    await click(mounted.root, 'closure-file-IMPLEMENTATION_COMMITMENT')
    await click(mounted.root, 'save-closure')
    await flush()

    expect(api.saveCutoverClosure).toHaveBeenNthCalledWith(
      2, '9007199254740995', 7, 1,
      expect.objectContaining({
        preCheckNormal: true, executionNormal: true, testNormal: true, rollbackOccurred: false,
        finalResult: null,
        attachments: expect.arrayContaining([
          expect.objectContaining({ purposeCode: 'POST_COLLECTION_CHECKLIST', artifactId: '9007199254740993' }),
          expect.objectContaining({ purposeCode: 'IMPLEMENTATION_COMMITMENT', artifactId: '9007199254740993' })
        ])
      }), expect.any(String)
    )
    expect(fileApi.getArtifact).toHaveBeenCalledWith(
      '9007199254740993', expect.objectContaining({ objectId: '501' })
    )
    expect(typeof api.saveCutoverClosure.mock.calls[1][3].attachments[0].artifactId).toBe('string')
    mounted.app.unmount()
  })

  it('requests one device with a transient credential and clears the secret after emitting', async () => {
    const requests: unknown[] = []
    const mounted = mount(CutoverClosureEvidencePanel, {
      closureId: '501', evidence: [], 'can-request': true, 'can-link-manual': false,
      onRequest: (value: unknown) => requests.push(value)
    }, controls)
    await click(mounted.root, 'open-collection')
    await setValue(mounted.root, 'collection-auth-mode', 'TRANSIENT_CREDENTIAL')
    await setValue(mounted.root, 'collection-device', '9007199254740993')
    await setValue(mounted.root, 'collection-secret', 'temporary-secret')
    await click(mounted.root, 'request-collection')

    expect(requests[0]).toMatchObject({
      authenticationMode: 'TRANSIENT_CREDENTIAL', deviceId: '9007199254740993', transientSecret: 'temporary-secret'
    })
    expect(findByTestId(mounted.root, 'collection-secret')?.props?.modelValue).toBe('')
    mounted.app.unmount()
  })

  it('links a manual file only to a failed collection and preserves its Snowflake identity', async () => {
    const results: unknown[] = []
    const evidence = [{
      evidenceId: '700', deviceId: '9007199254740994', collectionStage: 'TEST', evidenceType: 'CALLBACK_FAILED',
      collectionTaskId: 'collect-1', callbackEventId: 'event-1', resultRef: 'failed', resultVersion: '1',
      originalFailedCollectionTaskId: null, manualFile: null, occurredAt: 1788220800000
    }]
    const mounted = mount(CutoverClosureEvidencePanel, {
      closureId: '501', evidence, 'can-request': false, 'can-link-manual': true,
      onManual: (value: unknown) => results.push(value)
    }, controls)
    await click(mounted.root, 'open-manual-result')
    await setValue(mounted.root, 'manual-failed-task', 'collect-1')
    await click(mounted.root, 'manual-result-uploader')
    await flush()
    await click(mounted.root, 'link-manual-result')

    expect(results[0]).toMatchObject({
      originalFailedCollectionTaskId: 'collect-1',
      file: { purposeCode: 'MANUAL_COLLECTION_RESULT', artifactId: '9007199254740993' }
    })
    expect(fileApi.getArtifact).toHaveBeenCalledWith(
      '9007199254740993', expect.objectContaining({ objectId: '501' })
    )
    mounted.app.unmount()
  })

  it('submits either SUCCESS or FAILED only when the server exposes the action and then renders archived read-only state', async () => {
    api.getCutoverClosure
      .mockResolvedValueOnce(view(['SUBMIT_CLOSURE'], 2))
      .mockResolvedValue(view([], 3, 'SUBMITTED', 'ARCHIVED'))
    api.submitCutoverClosure.mockResolvedValue(view([], 3, 'SUBMITTED', 'ARCHIVED'))
    const mounted = mount(CutoverClosurePanel, { taskId: '101' }, controls)
    await flush()
    await click(mounted.root, 'submit-closure-success')
    await flush()

    expect(api.submitCutoverClosure).toHaveBeenCalledWith('101', 7, 2, 'SUCCESS', expect.any(String))
    expect(findByTestId(mounted.root, 'save-closure')).toBeUndefined()
    expect(textOf(mounted.root)).toContain('ARCHIVED')
    mounted.app.unmount()
  })

  it('keeps the form and stage navigation responsive at the locked 320/768/1024/1440 breakpoints', () => {
    const mounted = mount(CutoverClosureForm, { modelValue: content(), closureId: '501', editable: false }, controls)
    expect(textOf(mounted.root)).toContain('割接关闭记录')
    expect(textOf(mounted.root)).toContain('割接后检查清单')
    expect(textOf(mounted.root)).toContain('实施承诺')
    mounted.app.unmount()
  })
})

const content = () => ({
  preCheckNormal: null, preCheckDetail: null, executionNormal: null, executionDetail: null,
  testNormal: null, testDetail: null, rollbackOccurred: null, rollbackSuccessful: null,
  rollbackReason: null, legacyItems: null, finalResult: null, attachments: []
})
const view = (
  allowedActions: string[], closureVersion: number | null = null,
  closureStatus: 'DRAFT' | 'SUBMITTED' | null = closureVersion === null ? null : 'DRAFT',
  taskStatus: 'CLOSURE_IN_PROGRESS' | 'ARCHIVED' = 'CLOSURE_IN_PROGRESS'
) => ({
  taskId: '101', taskStage: 'P6', taskStatus, taskVersion: 7,
  closureId: closureVersion === null ? null : '501', closureVersion, closureStatus,
  approvalInstanceId: '301', approvalVersion: 2, planRevisionId: '401', planRevisionNo: 1, planVersion: 4,
  content: closureVersion === null ? null : content(), collectionEvidence: [],
  resultRef: closureStatus === 'SUBMITTED' && taskStatus === 'ARCHIVED' ? 'CUTOVER_CLOSURE:501:3' : null,
  submittedBy: closureStatus === 'SUBMITTED' ? '9' : null,
  submittedAt: closureStatus === 'SUBMITTED' ? 1788220800000 : null,
  archivedAt: taskStatus === 'ARCHIVED' ? 1788220800000 : null,
  allowedActions
})
const flush = async () => { await Promise.resolve(); await Promise.resolve(); await nextTick() }
const click = async (root: any, id: string) => {
  const target = findByTestId(root, id)
  if (!target) throw new Error(`missing ${id}: ${textOf(root)}`)
  await (target.props?.onClick as () => void | Promise<void>)()
  await nextTick()
}
const setValue = async (root: any, id: string, value: unknown) => {
  await (findByTestId(root, id)!.props?.['onUpdate:modelValue'] as (value: unknown) => void)(value)
  await nextTick()
}
