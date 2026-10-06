import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PmsFileReferenceList from './PmsFileReferenceList.vue'
import PmsFileUploader from './PmsFileUploader.vue'
import * as FileApi from '@/api/pms/platform/file'
import { useFileSlotState } from './useFileSlotState'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn(), prompt: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@/api/pms/platform/file', () => ({ getReference: vi.fn(), getArtifact: vi.fn(), getVersions: vi.fn(),
  initializeUpload: vi.fn(), completeUpload: vi.fn(), detachReference: vi.fn() }))
const key = { ownerContext: 'SOL', objectType: 'NATIVE_OWNER', objectId: '9007199254740997',
  purposeCode: 'EVIDENCE', referenceKey: 'fixed-slot' }
const reference = { ...key, artifactId: '9007199254740993', referenceId: '9007199254740995', versionNo: 2,
  referenceVersion: 4, scopeVersion: '9007199254740999', status: 'ACTIVE', sensitivityCode: 'INTERNAL',
  createdAt: '2026-10-06T00:00:00', updatedAt: '2026-10-06T00:00:00' }
const artifact = { artifactId: reference.artifactId, name: '已上传材料.txt', categoryCode: 'EVIDENCE',
  ownerContext: 'SOL', lifecycleStatus: 'ACTIVE', artifactVersion: 2, reference, allowedActions: [], createdAt: reference.createdAt }
const flush = async () => { for (let i = 0; i < 4; i++) { await Promise.resolve(); await nextTick() } }
const find = (root: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(root) ? root : root.children.map(child => find(child, predicate)).find(Boolean)

describe('shared file recovery', () => {
  beforeEach(() => vi.resetAllMocks())

  it('recovers the exact stable slot after a full remount with no saved artifact ID', async () => {
    vi.mocked(FileApi.getReference).mockResolvedValue(reference)
    vi.mocked(FileApi.getArtifact).mockResolvedValue(artifact)
    const slot = useFileSlotState()
    const mounted = mount(PmsFileReferenceList, { ...key, onLoaded: slot.loaded })
    await flush()
    expect(FileApi.getReference).toHaveBeenCalledWith(key)
    expect(FileApi.getArtifact).toHaveBeenCalledWith(reference.artifactId, key)
    expect(slot.state.artifactId).toBe(reference.artifactId)
    expect(slot.state.referenceVersion).toBe(4)
    expect(textOf(mounted.root)).toContain('已上传材料.txt')
    mounted.app.unmount()
  })

  it('cannot turn a missing or inaccessible reference into a restored file', async () => {
    vi.mocked(FileApi.getReference).mockResolvedValue(null)
    const loaded = vi.fn()
    const mounted = mount(PmsFileReferenceList, { ...key, onLoaded: loaded })
    await flush()
    expect(FileApi.getArtifact).not.toHaveBeenCalled()
    expect(loaded).not.toHaveBeenCalled()
    mounted.app.unmount()
  })

  it('preserves detached state and refuses to render its removal action', async () => {
    vi.mocked(FileApi.getReference).mockResolvedValue({ ...reference, status: 'DETACHED' })
    vi.mocked(FileApi.getArtifact).mockResolvedValue({ ...artifact, reference: { ...reference, status: 'DETACHED' } })
    const loaded = vi.fn()
    const mounted = mount(PmsFileReferenceList, { ...key, editable: true, onLoaded: loaded })
    await flush()
    expect(loaded).toHaveBeenCalledWith(expect.objectContaining({ reference: expect.objectContaining({ status: 'DETACHED' }) }))
    expect(find(mounted.root, node => node.type === 'button' && textOf(node) === '解绑')).toBeUndefined()
    mounted.app.unmount()
  })

  it('ignores a late lookup from the previous Owner rather than overwriting its new slot', async () => {
    let reply!: (value: FileApi.FileReferenceVO) => void
    vi.mocked(FileApi.getReference).mockImplementationOnce(() => new Promise(resolve => { reply = resolve }))
      .mockResolvedValueOnce(null)
    const owner = ref(key.objectId)
    const loaded = vi.fn()
    const host = defineComponent({ setup: () => () => h(PmsFileReferenceList, { ...key, objectId: owner.value, onLoaded: loaded }) })
    const mounted = mount(host)
    owner.value = '9007199254741001'
    await flush()
    reply(reference)
    await flush()
    expect(FileApi.getArtifact).not.toHaveBeenCalled()
    expect(loaded).not.toHaveBeenCalled()
    mounted.app.unmount()
  })

  it('uploads another version with exact string artifact/session/reference IDs', async () => {
    vi.mocked(FileApi.initializeUpload).mockResolvedValue({ artifactId: reference.artifactId,
      sessionId: '9007199254741011', expiresAt: reference.createdAt })
    vi.mocked(FileApi.completeUpload).mockResolvedValue({ ...reference, sha256: 'digest' })
    const completed = vi.fn()
    const uploadStub = defineComponent({ setup: (_, { attrs, expose }) => {
      expose({ clearFiles: vi.fn() })
      return () => h('section', attrs)
    } })
    const mounted = mount(PmsFileUploader, { ...key, artifactId: reference.artifactId,
      expectedReferenceVersion: reference.referenceVersion, categoryCode: 'EVIDENCE', onCompleted: completed },
    { ElUpload: uploadStub, ElProgress: passthrough })
    const upload = find(mounted.root, node => typeof node.props?.['on-change'] === 'function')!
    ;(upload.props!['on-change'] as Function)({ raw: new File(['data'], 'evidence.txt', { type: 'text/plain' }) })
    await nextTick()
    const button = find(mounted.root, node => node.type === 'button' && textOf(node).includes('上传新版本'))!
    await (button.props!.onClick as Function)()
    expect(vi.mocked(FileApi.initializeUpload).mock.calls[0][0]).toMatchObject({ modeCode: 'ADD_VERSION', artifactId: reference.artifactId, expectedReferenceVersion: 4 })
    expect(vi.mocked(FileApi.completeUpload).mock.calls[0].slice(0, 2)).toEqual([reference.artifactId, '9007199254741011'])
    expect(completed).toHaveBeenCalledWith(expect.objectContaining({ referenceId: reference.referenceId }))
    mounted.app.unmount()
  })
})
