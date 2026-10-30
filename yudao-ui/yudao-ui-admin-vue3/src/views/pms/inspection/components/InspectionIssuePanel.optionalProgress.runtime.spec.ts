import { expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './InspectionIssuePanel.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/utils/dict', () => ({ getStrDictOptions: () => [] }))
vi.mock('@/utils/formatTime', () => ({ formatDate: () => '' }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn() }) }))
vi.mock('@/components/PmsFileArtifact', () => ({ PmsFileUploader: { methods: { hasPendingFile: () => false }, render: () => null } }))
vi.mock('@/api/pms/platform/file/version-reference', () => ({ getArtifact: vi.fn() }))
function render(optionalProgress = false) {
  const action = vi.fn()
  const page = mount(Panel, {
    issue: { id: '20', taskId: '10', taskCode: 'INS-QA', version: '1', taskStatus: 'TRACKING', statusCode: 'PENDING', responsible: true, remediations: [], evidence: [] },
    disabled: false, optionalProgress, onAction: action
  }, Object.fromEntries(['ElTag', 'ElDescriptions', 'ElDescriptionsItem', 'ElEmpty', 'ElForm', 'ElFormItem', 'ElInput', 'ElButton', 'ElTable', 'ElTableColumn'].map(name => [name, name === 'ElTableColumn' ? { render: () => null } : passthrough])))
  return { ...page, state: (page.vm as any).$.setupState, action }
}
it('saves only verified evidence without mandatory progress text for UMC', async () => {
  const page = render(true)
  expect(page.state.processReady).toBe(false)
  page.state.draft.files.push({ uid: 'a', state: 'READY', fileName: 'proof.txt', file: { artifactId: '100', versionNo: 1, referenceKey: 'issue:20:qa' } })
  await nextTick()
  expect(page.state.processReady).toBe(true)
  page.state.recordProcess()
  expect(page.action).toHaveBeenCalledWith(expect.objectContaining({ action: 'record-process', data: expect.objectContaining({ content: '', evidence: [expect.objectContaining({ artifactId: '100' })] }) }))
  page.app.unmount()
})
it('keeps original platform progress mandatory and blocks unverified evidence', async () => {
  const old = render()
  old.state.draft.files.push({ uid: 'a', state: 'READY', file: {} })
  await nextTick()
  expect(old.state.processReady).toBe(false)
  old.app.unmount()
  const umc = render(true)
  umc.state.draft.files.push({ uid: 'b', state: 'ERROR', file: {} })
  await nextTick()
  expect(umc.state.processReady).toBe(false)
  umc.state.recordProcess()
  expect(umc.action).not.toHaveBeenCalled()
  umc.app.unmount()
})
