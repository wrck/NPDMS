import { describe, expect, it, vi, beforeEach } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import MultiUpload from '../components/CutoverChecklistMultiUpload.vue'
import { manualEvidenceFiles } from '../shared/manualEvidenceFiles'
import { mount } from '../cutoverTestHarness'

const api = vi.hoisted(() => ({ initializeUpload: vi.fn(), completeUpload: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => api)
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await nextTick() } }
beforeEach(() => {
  vi.clearAllMocks()
  api.initializeUpload.mockImplementation(async request => ({ artifactId: request.fileName === 'first.lic' ? 90 : 91, sessionId: 1 }))
  api.completeUpload.mockImplementation(async id => ({ artifactId: id, versionNo: 1, referenceId: id, referenceKey: `ref-${id}`, sha256: 'a'.repeat(64) }))
})
const render = (bindFile: (selection: any) => Promise<boolean>) => {
  let state: any
  const wrapper = defineComponent({ setup: () => () => h(MultiUpload, {
    itemId: '123', disabled: false, accept: '.lic', bindFile,
    ref: value => { if (value) state = (value as any).$.setupState }
  }) })
  const mounted = mount(wrapper)
  const select = () => {
    state.input = { files: [{ name: 'first.lic', size: 10, type: '' }, { name: 'second.lic', size: 10, type: '' }], value: 'selected' }
    state.selectFiles()
  }
  return { ...mounted, select, get state() { return state } }
}
describe('checklist multiple attachments', () => {
  it('binds selected files sequentially as separate references', async () => {
    const bind = vi.fn(async () => true)
    const mounted = render(bind)
    try {
      mounted.select(); await flush()
      expect(bind.mock.calls).toHaveLength(2)
      expect(api.initializeUpload.mock.calls.map(call => call[0].modeCode)).toEqual(['CREATE_ARTIFACT', 'CREATE_ARTIFACT'])
      expect(new Set(api.initializeUpload.mock.calls.map(call => call[0].referenceKey)).size).toBe(2)
      expect(mounted.state.pending).toHaveLength(0)
      expect(mounted.state.error).toBe('')
    } finally { mounted.app.unmount() }
  })
  it('stops at a business binding failure and retries without reuploading the completed file', async () => {
    const bind = vi.fn().mockResolvedValueOnce(false).mockResolvedValue(true)
    const mounted = render(bind)
    try {
      mounted.select(); await flush()
      expect(api.completeUpload).toHaveBeenCalledOnce()
      expect(mounted.state.pending).toHaveLength(2)
      expect(mounted.state.error).toContain('尚未选入清单')
      await mounted.state.upload()
      expect(api.completeUpload).toHaveBeenCalledTimes(2)
      expect(bind).toHaveBeenCalledTimes(3)
      expect(bind.mock.calls[0][0]).toEqual(bind.mock.calls[1][0])
      expect(mounted.state.pending).toHaveLength(0)
    } finally { mounted.app.unmount() }
  })
  it('reads every frozen file and preserves old single-file snapshots', () => {
    const one = { artifactId: '90', versionNo: 1, referenceKey: 'one' }
    const two = { artifactId: '91', versionNo: 1, referenceKey: 'two' }
    expect(manualEvidenceFiles({ resultSourceCode: 'MANUAL', answerSnapshot: JSON.stringify({ files: [one, two], file: one }) })).toEqual([one, two])
    expect(manualEvidenceFiles({ resultSourceCode: 'MANUAL', answerSnapshot: JSON.stringify({ file: one }) })).toEqual([one])
    expect(manualEvidenceFiles({ resultSourceCode: 'MANUAL', answerSnapshot: JSON.stringify(one) })).toEqual([one])
    expect(manualEvidenceFiles({ resultSourceCode: 'DIRECT', answerSnapshot: JSON.stringify({ files: [one] }) })).toEqual([])
  })
})
