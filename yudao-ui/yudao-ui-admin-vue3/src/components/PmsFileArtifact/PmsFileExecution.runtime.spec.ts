import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PmsFileUploader from './PmsFileUploader.vue'
import PmsFileReferenceList from './PmsFileReferenceList.vue'
import * as FileApi from '@/api/pms/platform/file'
import type { TaskExecutionContext } from '@/api/pms/project/task-business'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn(), prompt: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/api/pms/platform/file', () => ({
  initializeUpload: vi.fn(), completeUpload: vi.fn(), detachReference: vi.fn(),
  getArtifact: vi.fn(), getVersions: vi.fn()
}))
const key = { ownerContext: 'PLATFORM', objectType: 'DYNAMIC_FORM_INSTANCE', objectId: '31',
  purposeCode: 'FORM_FIELD_ATTACHMENT/evidence', referenceKey: 'slot-a' }
const stageExecution = {
  projectId: '11', projectVersion: 2, stageId: '12', stageVersion: 3,
  executionContractId: '41', contractVersion: 4, planVersionId: '51',
  executionId: '13', executionVersion: 5, roundNo: 2, writable: true
} satisfies StageExecutionContext
const taskExecution = {
  projectId: '11', projectVersion: 2, taskId: '21', taskVersion: 3,
  executionContractId: '42', contractVersion: 4, planVersionId: '51',
  executionId: '23', executionVersion: 5, roundNo: 2, writable: true,
  stageExecutionId: '13', stageExecutionVersion: 5
} satisfies TaskExecutionContext
const find = (root: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(root) ? root : root.children.map(child => find(child, predicate)).find(Boolean)
const flush = async () => { await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick() }

describe('file writes keep the selected Owner execution', () => {
  beforeEach(() => vi.clearAllMocks())

  it.each(['task', 'stage'] as const)('retains the selected %s round through upload initialization, completion and retry', async (kind) => {
    const selected = kind === 'task' ? { task: taskExecution } : { stage: stageExecution }
    const execution = ref(structuredClone(selected))
    const completed = vi.fn()
    let initialize!: (value: FileApi.FileUploadInitRespVO) => void
    vi.mocked(FileApi.initializeUpload).mockImplementation(() => new Promise(resolve => { initialize = resolve }))
    vi.mocked(FileApi.completeUpload).mockRejectedValueOnce(new Error('RESPONSE_UNKNOWN')).mockResolvedValueOnce({
      artifactId: 101, versionNo: 1, referenceId: 301, referenceKey: 'slot-a', sha256: 'a'
    })
    const host = defineComponent({ setup: () => () => h(PmsFileUploader, {
      ...key, categoryCode: 'DYNAMIC_FORM_ATTACHMENT', ownerExecutionContext: execution.value, onCompleted: completed
    }) })
    const uploadStub = defineComponent({ setup: (_, { attrs, expose }) => {
      expose({ clearFiles: vi.fn() })
      return () => h('section', attrs)
    } })
    const mounted = mount(host, {}, { ElUpload: uploadStub, ElProgress: passthrough })
    const upload = find(mounted.root, node => typeof node.props?.['on-change'] === 'function')!
    ;(upload.props!['on-change'] as (file: { raw: File }) => void)({ raw: new File(['data'], 'evidence.txt', { type: 'text/plain' }) })
    await nextTick()
    const button = find(mounted.root, node => node.type === 'button' && textOf(node).includes('上传并绑定'))!
    const submit = button.props!.onClick as () => Promise<void>
    const first = submit()
    const rejected = expect(first).rejects.toThrow('RESPONSE_UNKNOWN')
    const current = execution.value.task ?? execution.value.stage!
    current.executionId = '99'
    current.executionVersion = 1
    current.roundNo = 3
    await nextTick()
    initialize({ artifactId: 101, sessionId: 201, expiresAt: '2026-09-15T01:00:00' })
    await rejected
    expect(completed).not.toHaveBeenCalled()
    await submit()
    expect(FileApi.initializeUpload).toHaveBeenCalledTimes(1)
    expect(vi.mocked(FileApi.initializeUpload).mock.calls[0][0].ownerExecutionContext).toEqual(selected)
    const calls = vi.mocked(FileApi.completeUpload).mock.calls
    expect(calls[0][5]).toEqual(selected)
    expect(calls[1][5]).toEqual(calls[0][5])
    expect(calls[1][3]).toBe(calls[0][3])
    expect(completed).toHaveBeenCalledTimes(1)
    mounted.app.unmount()
  })

  it('captures stage execution before the removal confirmation and retains the file on rejection', async () => {
    const execution = ref({ stage: { ...stageExecution } })
    vi.mocked(FileApi.getArtifact).mockResolvedValue({
      artifactId: 101, name: '材料.txt', allowedActions: [], categoryCode: 'DYNAMIC_FORM_ATTACHMENT',
      ownerContext: 'PLATFORM', lifecycleStatus: 'ACTIVE', artifactVersion: 1,
      createdAt: '2026-09-15T01:00:00',
      reference: { ...key, artifactId: 101, referenceId: 301, referenceVersion: 1, versionNo: 1,
        status: 'ACTIVE', sensitivityCode: 'INTERNAL', scopeVersion: 1,
        createdAt: '2026-09-15T01:00:00', updatedAt: '2026-09-15T01:00:00' }
    })
    let confirm!: (value: { value: string }) => void
    message.prompt.mockImplementation(() => new Promise(resolve => { confirm = resolve }))
    vi.mocked(FileApi.detachReference).mockRejectedValue(new Error('STALE_EXECUTION'))
    const detached = vi.fn()
    const host = defineComponent({ setup: () => () => h(PmsFileReferenceList, {
      ...key, artifactId: 101, editable: true, ownerExecutionContext: execution.value, onDetached: detached
    }) })
    const mounted = mount(host, {}, { ElSkeleton: passthrough })
    await flush()
    const button = find(mounted.root, node => node.type === 'button' && textOf(node).includes('解绑'))!
    const pending = (button.props!.onClick as () => Promise<void>)()
    const rejected = expect(pending).rejects.toThrow('STALE_EXECUTION')
    execution.value.stage.executionId = '99'
    execution.value.stage.executionVersion = 1
    execution.value.stage.roundNo = 3
    await nextTick()
    confirm({ value: '材料重复' })
    await rejected
    expect(vi.mocked(FileApi.detachReference).mock.calls[0][5]).toEqual({
      stage: stageExecution
    })
    expect(textOf(mounted.root)).toContain('材料.txt')
    expect(detached).not.toHaveBeenCalled()
    mounted.app.unmount()
  })
})
