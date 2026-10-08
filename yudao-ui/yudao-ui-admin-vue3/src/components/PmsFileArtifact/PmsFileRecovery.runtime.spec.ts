import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PmsFileReferenceList from './PmsFileReferenceList.vue'
import PmsFileUploader from './PmsFileUploader.vue'
import PmsFileArtifactField from '@/views/pms/platform/dynamic-form/components/PmsFileArtifactField.vue'
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

const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const uploadStub = defineComponent({
  setup: (_, { attrs, expose }) => {
    expose({ clearFiles: vi.fn() })
    return () => h('section', attrs)
  }
})
const selectFile = (root: TestNode) =>
  (
    find(root, (node) => typeof node.props?.['on-change'] === 'function')!.props![
      'on-change'
    ] as Function
  )({ raw: new File(['data'], 'evidence.txt', { type: 'text/plain' }) })
const submitFile = (root: TestNode) =>
  (
    find(root, (node) => node.type === 'button' && textOf(node).includes('上传'))!.props!
      .onClick as () => Promise<void>
  )()

describe('independent stale callback negatives at c41c12e', () => {
  beforeEach(() => vi.resetAllMocks())

  it('late detach must preserve the new Owner file and suppress the old event', async () => {
    const old = deferred<any>()
    vi.mocked(FileApi.getReference).mockImplementation(async (selected) => ({
      ...reference,
      ...selected
    }))
    vi.mocked(FileApi.getArtifact).mockImplementation(async (_id, selected) => ({
      ...artifact,
      name: selected.objectId === key.objectId ? 'old-owner.txt' : 'new-owner.txt',
      reference: { ...reference, ...selected }
    }))
    message.prompt.mockResolvedValue({ value: 'detach old owner' })
    vi.mocked(FileApi.detachReference).mockReturnValue(old.promise)
    const owner = ref(key.objectId),
      child = ref<any>(),
      detached = vi.fn()
    const host = defineComponent({
      setup: () => () =>
        h(PmsFileReferenceList, {
          ...key,
          objectId: owner.value,
          ref: child,
          editable: true,
          onDetached: detached
        })
    })
    const mounted = mount(host)
    try {
      await flush()
      const pending = child.value.detach()
      await flush()
      expect(FileApi.detachReference).toHaveBeenCalledTimes(1)
      owner.value = '9007199254741001'
      await flush()
      expect(textOf(mounted.root)).toContain('new-owner.txt')
      old.resolve({
        referenceId: reference.referenceId,
        artifactId: reference.artifactId,
        status: 'DETACHED',
        factVersion: 5
      })
      await pending
      await flush()
      expect.soft(detached).not.toHaveBeenCalled()
      expect(textOf(mounted.root)).toContain('new-owner.txt')
      expect(message.success).not.toHaveBeenCalled()
    } finally {
      mounted.app.unmount()
    }
  })

  it('late completed upload must not emit after the stable Owner key changes', async () => {
    const old = deferred<any>()
    vi.mocked(FileApi.initializeUpload).mockResolvedValue({
      artifactId: reference.artifactId,
      sessionId: '9007199254741011',
      expiresAt: reference.createdAt
    })
    vi.mocked(FileApi.completeUpload).mockReturnValue(old.promise)
    const owner = ref(key.objectId),
      completed = vi.fn()
    const host = defineComponent({
      setup: () => () =>
        h(PmsFileUploader, {
          ...key,
          objectId: owner.value,
          categoryCode: 'EVIDENCE',
          onCompleted: completed
        })
    })
    const mounted = mount(host, {}, { ElUpload: uploadStub, ElProgress: passthrough })
    try {
      selectFile(mounted.root)
      await flush()
      const pending = submitFile(mounted.root)
      await flush()
      expect(FileApi.completeUpload).toHaveBeenCalledTimes(1)
      owner.value = '9007199254741001'
      await flush()
      old.resolve({ ...reference, sha256: 'digest' })
      await pending
      await flush()
      expect(completed).not.toHaveBeenCalled()
      expect(message.success).not.toHaveBeenCalled()
    } finally {
      mounted.app.unmount()
    }
  })

  it('real dynamic form instance 41→42 keeps its empty model after the old upload completes', async () => {
    const old = deferred<any>()
    vi.mocked(FileApi.initializeUpload).mockResolvedValue({
      artifactId: reference.artifactId,
      sessionId: '9007199254741011',
      expiresAt: reference.createdAt
    })
    vi.mocked(FileApi.completeUpload).mockReturnValue(old.promise)
    const instance = ref(41),
      updated = vi.fn()
    const host = defineComponent({
      setup: () => () =>
        h(PmsFileArtifactField, {
          instanceId: instance.value,
          fieldKey: 'evidence',
          currentFacts: [],
          allowedActions: ['PATCH_INSTANCE'],
          'onUpdate:modelValue': updated
        })
    })
    const mounted = mount(host, {}, { ElUpload: uploadStub, ElProgress: passthrough })
    try {
      await flush()
      selectFile(mounted.root)
      await flush()
      const pending = submitFile(mounted.root)
      await flush()
      expect(FileApi.completeUpload).toHaveBeenCalledTimes(1)
      const original = vi.mocked(FileApi.initializeUpload).mock.calls[0][0]
      expect(original.objectId).toBe('41')
      instance.value = 42
      await flush()
      old.resolve({ ...reference, referenceKey: original.referenceKey, sha256: 'digest' })
      await pending
      await flush()
      expect(updated).toHaveBeenLastCalledWith([])
    } finally {
      mounted.app.unmount()
    }
  })
})

describe('late callbacks preserve the new pending attempt', () => {
  beforeEach(() => vi.resetAllMocks())
  it.each(['success', 'failure'] as const)(
    'old upload %s and progress cannot finish the new upload',
    async (outcome) => {
      const old = deferred<any>(),
        current = deferred<any>()
      vi.mocked(FileApi.initializeUpload).mockResolvedValue({
        artifactId: reference.artifactId,
        sessionId: '9007199254741011',
        expiresAt: reference.createdAt
      })
      vi.mocked(FileApi.completeUpload)
        .mockReturnValueOnce(old.promise)
        .mockReturnValueOnce(current.promise)
      const owner = ref(key.objectId),
        completed = vi.fn()
      const host = defineComponent({
        setup: () => () =>
          h(PmsFileUploader, {
            ...key,
            objectId: owner.value,
            categoryCode: 'EVIDENCE',
            onCompleted: completed
          })
      })
      const mounted = mount(host, {}, { ElUpload: uploadStub, ElProgress: passthrough })
      try {
        selectFile(mounted.root)
        await flush()
        const first = submitFile(mounted.root)
        await flush()
        owner.value = '9007199254741001'
        await flush()
        selectFile(mounted.root)
        await flush()
        const second = submitFile(mounted.root)
        await flush()
        expect(FileApi.completeUpload).toHaveBeenCalledTimes(2)
        vi.mocked(FileApi.completeUpload).mock.calls[0][4]!(100)
        if (outcome === 'success') old.resolve({ ...reference, sha256: 'old' })
        else old.reject(new Error('OLD_RESPONSE_UNKNOWN'))
        await first
        await flush()
        expect(completed).not.toHaveBeenCalled()
        expect(message.success).not.toHaveBeenCalled()
        const button = find(
          mounted.root,
          (node) => node.type === 'button' && textOf(node).includes('上传')
        )!
        expect(button.props!.loading).toBe(true)
        expect(textOf(mounted.root)).toContain('正在上传文件')
        current.resolve({ ...reference, referenceId: 'current-ref', sha256: 'current' })
        await second
        expect(completed).toHaveBeenCalledTimes(1)
        expect(completed.mock.calls[0][0].referenceId).toBe('current-ref')
      } finally {
        mounted.app.unmount()
      }
    }
  )

  it('late initialization is stopped before completion and leaves the new request intact', async () => {
    const old = deferred<FileApi.FileUploadInitRespVO>()
    vi.mocked(FileApi.initializeUpload).mockReturnValueOnce(old.promise).mockResolvedValueOnce({
      artifactId: 'new-artifact',
      sessionId: 'new-session',
      expiresAt: reference.createdAt
    })
    vi.mocked(FileApi.completeUpload).mockResolvedValue({ ...reference, sha256: 'new' })
    const owner = ref(key.objectId),
      completed = vi.fn()
    const host = defineComponent({
      setup: () => () =>
        h(PmsFileUploader, {
          ...key,
          objectId: owner.value,
          categoryCode: 'EVIDENCE',
          onCompleted: completed
        })
    })
    const mounted = mount(host, {}, { ElUpload: uploadStub, ElProgress: passthrough })
    try {
      selectFile(mounted.root)
      await flush()
      const first = submitFile(mounted.root)
      owner.value = '9007199254741001'
      await flush()
      selectFile(mounted.root)
      await flush()
      await submitFile(mounted.root)
      old.resolve({
        artifactId: reference.artifactId,
        sessionId: 'old-session',
        expiresAt: reference.createdAt
      })
      await first
      expect(FileApi.completeUpload).toHaveBeenCalledTimes(1)
      expect(vi.mocked(FileApi.completeUpload).mock.calls[0].slice(0, 2)).toEqual([
        'new-artifact',
        'new-session'
      ])
      expect(completed).toHaveBeenCalledTimes(1)
    } finally {
      mounted.app.unmount()
    }
  })

  it('switching during the removal prompt does not send its stale request', async () => {
    const prompt = deferred<{ value: string }>()
    message.prompt.mockReturnValue(prompt.promise)
    vi.mocked(FileApi.getReference).mockImplementation(async (selected) => ({
      ...reference,
      ...selected
    }))
    vi.mocked(FileApi.getArtifact).mockResolvedValue(artifact)
    const owner = ref(key.objectId),
      child = ref<any>()
    const host = defineComponent({
      setup: () => () =>
        h(PmsFileReferenceList, { ...key, objectId: owner.value, ref: child, editable: true })
    })
    const mounted = mount(host)
    try {
      await flush()
      const pending = child.value.detach()
      owner.value = '9007199254741001'
      await flush()
      prompt.resolve({ value: 'old prompt' })
      await pending
      expect(FileApi.detachReference).not.toHaveBeenCalled()
    } finally {
      mounted.app.unmount()
    }
  })

  it('switching away and back still rejects the old generation', async () => {
    const old = deferred<any>()
    vi.mocked(FileApi.initializeUpload).mockResolvedValue({
      artifactId: reference.artifactId,
      sessionId: 'old-session',
      expiresAt: reference.createdAt
    })
    vi.mocked(FileApi.completeUpload).mockReturnValue(old.promise)
    const owner = ref(key.objectId),
      completed = vi.fn()
    const host = defineComponent({
      setup: () => () =>
        h(PmsFileUploader, {
          ...key,
          objectId: owner.value,
          categoryCode: 'EVIDENCE',
          onCompleted: completed
        })
    })
    const mounted = mount(host, {}, { ElUpload: uploadStub, ElProgress: passthrough })
    try {
      selectFile(mounted.root)
      await flush()
      const pending = submitFile(mounted.root)
      await flush()
      owner.value = 'other'
      await flush()
      owner.value = key.objectId
      await flush()
      old.resolve({ ...reference, sha256: 'old' })
      await pending
      expect(completed).not.toHaveBeenCalled()
    } finally {
      mounted.app.unmount()
    }
  })
})

describe('new Owner removal retries survive old responses', () => {
  beforeEach(() => vi.resetAllMocks())
  it('keeps the new detach key through an old success and a lost new response', async () => {
    const old = deferred<any>(), current = deferred<any>()
    message.prompt.mockResolvedValue({ value: '同一解绑原因' })
    vi.mocked(FileApi.getReference).mockImplementation(async selected => ({ ...reference, ...selected }))
    vi.mocked(FileApi.getArtifact).mockImplementation(async (_id, selected) => ({
      ...artifact, reference: { ...reference, ...selected,
        referenceId: selected.objectId === key.objectId ? 'old-ref' : 'new-ref' }
    }))
    vi.mocked(FileApi.detachReference).mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(current.promise).mockResolvedValueOnce({ referenceId: 'new-ref', status: 'DETACHED' } as any)
    const owner = ref(key.objectId), child = ref<any>(), detached = vi.fn()
    const host = defineComponent({ setup: () => () => h(PmsFileReferenceList,
      { ...key, objectId: owner.value, ref: child, editable: true, onDetached: detached }) })
    const mounted = mount(host)
    try {
      await flush(); const first = child.value.detach(); await flush()
      owner.value = '9007199254741001'; await flush()
      const second = child.value.detach(); const rejected = expect(second).rejects.toThrow('RESPONSE_UNKNOWN')
      await flush(); expect(FileApi.detachReference).toHaveBeenCalledTimes(2)
      old.resolve({ referenceId: 'old-ref', status: 'DETACHED' }); await first
      expect(detached).not.toHaveBeenCalled()
      current.reject(new Error('RESPONSE_UNKNOWN')); await rejected
      await child.value.detach()
      const calls = vi.mocked(FileApi.detachReference).mock.calls
      expect(calls[0][4]).not.toBe(calls[1][4]); expect(calls[1][4]).toBe(calls[2][4])
      expect(detached).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ referenceId: 'new-ref' }))
    } finally { mounted.app.unmount() }
  })

  it('an unmounted uploader ignores its outstanding completion', async () => {
    const old = deferred<any>()
    vi.mocked(FileApi.initializeUpload).mockResolvedValue({ artifactId: reference.artifactId,
      sessionId: 'old-session', expiresAt: reference.createdAt })
    vi.mocked(FileApi.completeUpload).mockReturnValue(old.promise)
    const completed = vi.fn()
    const mounted = mount(PmsFileUploader, { ...key, categoryCode: 'EVIDENCE', onCompleted: completed },
      { ElUpload: uploadStub, ElProgress: passthrough })
    selectFile(mounted.root); await flush(); const pending = submitFile(mounted.root); await flush()
    mounted.app.unmount(); old.resolve({ ...reference, sha256: 'old' }); await pending
    expect(completed).not.toHaveBeenCalled(); expect(message.success).not.toHaveBeenCalled()
  })
})
