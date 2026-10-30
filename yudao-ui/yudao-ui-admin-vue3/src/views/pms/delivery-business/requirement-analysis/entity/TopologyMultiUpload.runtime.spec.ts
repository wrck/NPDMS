import { expect, it, vi, beforeEach } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import MultiUpload from './TopologyMultiUpload.vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ initializeUpload: vi.fn(), completeUpload: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => api)
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await nextTick() } }
beforeEach(() => {
  vi.clearAllMocks()
  api.initializeUpload.mockImplementation(async request => ({ artifactId: request.fileName === 'first.vsdx' ? 90 : 91, sessionId: 1 }))
  api.completeUpload.mockImplementation(async id => ({ artifactId: id, versionNo: 1, referenceKey: `ref-${id}` }))
})
const render = () => {
  let state: any
  const completed = vi.fn()
  const revision = ref('701')
  const wrapper = defineComponent({ setup: () => () => h(MultiUpload, {
    revisionId: revision.value, disabled: false, accept: '.vsd,.vsdx', onCompleted: completed,
    ref: value => { if (value) state = (value as any).$.setupState }
  }) })
  const mounted = mount(wrapper)
  return { ...mounted, completed, revision, get state() { return state }, select: () => {
    state.input = { files: [{ name: 'first.vsdx', size: 10, type: '' }, { name: 'second.vsd', size: 10, type: '' }], value: 'selected' }
    state.selectFiles()
  } }
}
it('uploads multiple Visio files under separate references and normalizes browser MIME', async () => {
  const view = render()
  try {
    view.select(); await flush()
    expect(view.completed).toHaveBeenCalledTimes(2)
    expect(api.initializeUpload.mock.calls.map(call => call[0].declaredMediaType)).toEqual(['application/vnd.ms-visio.drawing', 'application/vnd.visio'])
    expect(new Set(api.initializeUpload.mock.calls.map(call => call[0].referenceKey)).size).toBe(2)
    expect(api.initializeUpload.mock.calls.every(call => call[0].objectId === '701')).toBe(true)
  } finally { view.app.unmount() }
})
it('retries failed completion with the original upload identity', async () => {
  api.completeUpload.mockRejectedValueOnce(new Error('network failed'))
  const view = render()
  try {
    view.select(); await flush()
    expect(view.completed).not.toHaveBeenCalled()
    expect(api.initializeUpload).toHaveBeenCalledTimes(1)
    await view.state.upload()
    expect(api.initializeUpload).toHaveBeenCalledTimes(2)
    expect(api.completeUpload.mock.calls[0]).toEqual(api.completeUpload.mock.calls[1])
    expect(view.completed).toHaveBeenCalledTimes(2)
  } finally { view.app.unmount() }
})
it('does not continue uploads or emit into a different revision', async () => {
  let finish: (value: unknown) => void = () => {}
  api.initializeUpload.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const view = render()
  try {
    view.select(); await flush()
    view.revision.value = '702'; await nextTick()
    finish({ artifactId: 90, sessionId: 1 }); await flush()
    expect(api.completeUpload).not.toHaveBeenCalled()
    expect(view.completed).not.toHaveBeenCalled()
  } finally { view.app.unmount() }
})
