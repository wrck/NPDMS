import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Uploader from './PmsFileUploader.vue'
import References from './PmsFileVersionReferenceList.vue'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ initializeUpload: vi.fn(), completeUpload: vi.fn() }))
const facts = vi.hoisted(() => ({ getArtifact: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => api)
vi.mock('@/api/pms/platform/file/version-reference', () => facts)
vi.mock('@/utils/download', () => ({ default: {} }))
vi.mock('@/utils', () => ({ generateUUID: () => crypto.randomUUID() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }) }))
vi.mock('./PmsFileVersionReferenceHistory.vue', () => ({ default: { render: () => null } }))
const key = { ownerContext: 'CUT', objectType: 'CUTOVER_CHECKLIST_ITEM', objectId: '31', purposeCode: 'MANUAL_EVIDENCE', referenceKey: 'manual-proof' }
const find = (node: TestNode, predicate: (value: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map(child => find(child, predicate)).find(Boolean)
const flush = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
const uploadControl = defineComponent({ setup(_, { attrs, expose }) {
  expose({ clearFiles: vi.fn() })
  return () => h('section', attrs)
} })
const renderUpload = (extra = {}) => {
  const view = mount(Uploader, { ...key, categoryCode: 'CUTOVER_CHECKLIST', autoSubmit: true, ...extra }, { ElUpload: uploadControl, ElProgress: passthrough })
  const control = () => find(view.root, node => typeof node.props?.['on-change'] === 'function')!
  const select = (file: File) => (control().props!['on-change'] as Function)({ raw: file, status: 'ready' })
  return { ...view, control, select, state: (view.vm as any).$.setupState }
}
const artifact = (id: number) => ({ artifactId: id, name: `file-${id}`, reference: { ...key, versionNo: 1, referenceVersion: 1, status: 'ACTIVE' }, allowedActions: ['PREVIEW', 'DOWNLOAD'] })
beforeEach(() => {
  vi.resetAllMocks()
  api.initializeUpload.mockResolvedValue({ artifactId: 101, sessionId: 201 })
  api.completeUpload.mockResolvedValue({ artifactId: 101, versionNo: 2, referenceId: 301, referenceKey: key.referenceKey })
})

it('automatically uploads once and retains a string artifact identity for a new version', async () => {
  const view = renderUpload({ artifactId: '9007199254740993', expectedReferenceVersion: 7 })
  try {
    view.select(new File(['data'], 'evidence.txt'))
    await view.state.submit()
    await flush()
    expect(api.initializeUpload).toHaveBeenCalledTimes(1)
    expect(api.initializeUpload.mock.calls[0][0]).toMatchObject({ artifactId: '9007199254740993', modeCode: 'ADD_VERSION', expectedReferenceVersion: 7 })
    expect(api.completeUpload).toHaveBeenCalledTimes(1)
    expect(view.state.selectedFile).toBeUndefined()
  } finally { view.app.unmount() }
})

it('retries an uncertain automatic upload with the original session and completion key', async () => {
  api.completeUpload.mockRejectedValueOnce(new Error('RESPONSE_UNKNOWN'))
  const view = renderUpload()
  try {
    view.select(new File(['data'], 'evidence.txt'))
    await flush()
    expect(textOf(view.root)).toContain('重试上传')
    await view.state.submit()
    expect(api.initializeUpload).toHaveBeenCalledTimes(1)
    expect(api.completeUpload.mock.calls[1].slice(0, 4)).toEqual(api.completeUpload.mock.calls[0].slice(0, 4))
  } finally { view.app.unmount() }
})

it('keeps a rejected empty file removable before selecting another automatic upload', async () => {
  const view = renderUpload()
  try {
    view.select(new File([], 'empty.txt'))
    await flush()
    expect(api.initializeUpload).not.toHaveBeenCalled()
    expect(view.control().props!['show-file-list']).toBe(true)
    ;(view.control().props!['on-remove'] as Function)()
    view.select(new File(['valid'], 'replacement.txt'))
    await flush()
    expect(api.completeUpload).toHaveBeenCalledOnce()
  } finally { view.app.unmount() }
})

it('does not dispatch an automatic upload while disabled', async () => {
  const view = renderUpload({ disabled: true })
  try {
    view.select(new File(['data'], 'evidence.txt'))
    await view.state.submit()
    expect(api.initializeUpload).not.toHaveBeenCalled()
  } finally { view.app.unmount() }
})

it('clears loading when the material is removed during its pending reference request', async () => {
  let resolve!: (value: ReturnType<typeof artifact>) => void
  facts.getArtifact.mockImplementation(() => new Promise(done => { resolve = done }))
  const id = ref<number>()
  const loaded = vi.fn()
  const child = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(References, { ...key, artifactId: id.value, ref: child, onLoaded: loaded }) }))
  try {
    id.value = 1; await flush()
    id.value = undefined; await flush()
    expect(child.value.$.setupState.loading).toBe(false)
    resolve(artifact(1)); await flush()
    expect(loaded).not.toHaveBeenCalled()
    expect(textOf(view.root)).toContain('尚未绑定材料')
  } finally { view.app.unmount() }
})

it('ignores stale reference responses after switching material and retains the card during same-material refresh', async () => {
  const pending = new Map<number, (value: ReturnType<typeof artifact>) => void>()
  facts.getArtifact.mockImplementation((id: number) => new Promise(resolve => pending.set(id, resolve)))
  const id = ref(1)
  const child = ref<any>()
  const loaded = vi.fn()
  const view = mount(defineComponent({ setup: () => () => h(References, { ...key, artifactId: id.value, ref: child, onLoaded: loaded }) }))
  try {
    await flush(); id.value = 2; await flush()
    pending.get(2)!(artifact(2)); await flush()
    pending.get(1)!(artifact(1)); await flush()
    expect(loaded).toHaveBeenCalledTimes(1)
    const refreshing = child.value.refresh(); await flush()
    expect(textOf(view.root)).toContain('file-2')
    expect(find(view.root, node => node.props?.inert === true)).toBeDefined()
    pending.get(2)!(artifact(2)); await refreshing
  } finally { view.app.unmount() }
})
