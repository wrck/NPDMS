import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import Dialog from './ProjectDeliverableDialog.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ getDetail: vi.fn(), getTypes: vi.fn(), submit: vi.fn(), evaluate: vi.fn() }))
vi.mock('@/api/pms/acceptance/project-deliverable', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ confirm: vi.fn() }) }))
vi.mock('@/components/PmsFileArtifact', () => ({ PmsFileUploader: { methods: { isBusy: () => false, hasPendingFile: () => false }, render: () => null }, PmsFileReferenceList: { render: () => null } }))
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks() })
it('submits material without crypto.randomUUID and retains the idempotency key on retry', async () => {
  vi.stubGlobal('crypto', {})
  api.getDetail.mockResolvedValue({ id: 12, code: 'D1', name: 'Evidence', status: 'PENDING', writable: true,
    planVersionId: 5, version: 2, history: [],
    configuration: { minimumQuantity: 1, allowedSources: ['UPLOAD'], automaticSources: [] } })
  api.submit.mockRejectedValueOnce(new Error('network failure')).mockResolvedValueOnce({
    submissionId: 900, status: 'CURRENT', evaluation: { satisfied: true, reason: 'DELIVERABLE_RULE_SATISFIED' } })
  const child = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(Dialog, { projectId: 9, ref: child }) }), {},
    { ElDialog: passthrough, ElSkeleton: passthrough, ElRadioGroup: passthrough,
      ElRadioButton: passthrough, ElSelect: passthrough, ElOption: passthrough })
  try {
    await child.value.open(12)
    await nextTick()
    const state = child.value.$.setupState
    expect(api.getDetail).toHaveBeenCalledWith(9, 12)
    expect(state.slotKey).toMatch(/^[0-9a-f-]{36}$/)
    state.uploaded({ artifactId: 21, versionNo: 1, referenceId: 33, referenceKey: 'file-1', sha256: 'ab' })
    await expect(state.submit()).rejects.toThrow('network failure')
    await state.submit()
    expect(api.submit).toHaveBeenCalledTimes(2)
    expect(api.submit.mock.calls[0][3]).toBe(api.submit.mock.calls[1][3])
    expect(api.submit.mock.calls[1][2]).toMatchObject({ planVersionId: 5, expectedVersion: 2, sourceType: 'UPLOAD', files: [{ referenceId: 33 }] })
    expect(state.satisfied).toBe(true)
  } finally { view.app.unmount() }
})
