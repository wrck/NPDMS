import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, expect, it, vi } from 'vitest'
import Dialog from './ProjectDeliverableDialog.vue'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ getDetail: vi.fn(), canReadSources: vi.fn(() => true), getTypes: vi.fn(), getDocumentSources: vi.fn().mockResolvedValue([]), submit: vi.fn(), evaluate: vi.fn() }))
vi.mock('@/api/pms/acceptance/project-deliverable', () => api)
vi.mock('@/utils/permission', () => ({ checkPermi: api.canReadSources }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ confirm: vi.fn() }) }))
vi.mock('@/components/PmsFileArtifact', () => ({ PmsFileUploader: { methods: { isBusy: () => false, hasPendingFile: () => false }, render: () => null }, PmsFileReferenceList: { render(this: { $attrs: Record<string, unknown> }) { return h('file-reference', this.$attrs) } } }))
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks() })

const dialogWithFooter = defineComponent({
  setup(_, { slots, attrs }) { return () => h('section', attrs, [slots.default?.(), slots.footer?.()]) }
})
const findButton = (node: TestNode, label: string): TestNode | undefined =>
  node.type === 'button' && textOf(node) === label ? node : node.children.map(child => findButton(child, label)).find(Boolean)

it.each([false, true])('automatic collection hides submission for mixed sources (has history: %s)', async hasHistory => {
  const fileKey = { ownerContext: 'IMP', objectType: 'ARRIVAL', objectId: '18', purposeCode: 'ARRIVAL_SIGN_DOCUMENT', referenceKey: 'receipt' }
  api.getDetail.mockResolvedValue({ id: 12, code: 'D1', name: '自动归集材料', status: 'PENDING', writable: true,
    automaticCollection: true, planVersionId: 5, version: 2,
    configuration: { allowedSources: ['UPLOAD', 'BUSINESS_RESULT'], automaticSources: [] },
    history: hasHistory ? [{ id: 40, sourceType: 'BUSINESS_DOCUMENT', materials: [
      { id: 1, materialKind: 'FILE', fileName: 'receipt.pdf', artifactId: 21, versionNo: 1, fileKey },
      { id: 2, materialKind: 'BUSINESS_RESULT', businessObjectId: 'IMP.INSTALLATION.INSTALLATION_COMPLETED|18|1|' }
    ] }] : [] })
  const child = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(Dialog, { projectId: 9, ref: child }) }), {},
    { ElDialog: dialogWithFooter, ElCollapse: passthrough, ElCollapseItem: passthrough })
  try {
    await child.value.open(12); await nextTick()
    expect(findButton(view.root, '提交材料')).toBeUndefined()
    expect(textOf(view.root)).not.toContain('上传文件')
    expect(textOf(view.root)).not.toContain('关联业务成果')
    expect(api.getTypes).not.toHaveBeenCalled()
    if (hasHistory) {
      expect(textOf(view.root)).toContain('receipt.pdf')
      expect(textOf(view.root)).toContain('硬件安装完成成果')
    }
    const state = child.value.$.setupState
    state.uploaded({ artifactId: 21, versionNo: 1, referenceId: 33, referenceKey: 'file-1', sha256: 'ab' })
    await state.submit()
    expect(api.submit).not.toHaveBeenCalled()
  } finally { view.app.unmount() }
})

it('keeps manual business result selection and its submit button', async () => {
  api.getDetail.mockResolvedValue({ id: 12, code: 'D1', name: '手动关联成果', status: 'PENDING', writable: true,
    automaticCollection: false, planVersionId: 5, version: 2, history: [],
    configuration: { allowedSources: ['BUSINESS_RESULT'], automaticSources: [] } })
  api.getTypes.mockResolvedValue([])
  const child = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(Dialog, { projectId: 9, ref: child }) }), {},
    { ElDialog: dialogWithFooter, ElSelect: passthrough, ElOption: passthrough, ElRadioGroup: passthrough, ElRadioButton: passthrough })
  try {
    await child.value.open(12); await nextTick()
    expect(findButton(view.root, '提交材料')).toBeTruthy()
    expect(textOf(view.root)).toContain('关联业务成果')
    expect(api.getTypes).toHaveBeenCalledWith(9, 12)
  } finally { view.app.unmount() }
})

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
  } finally { view.app.unmount(); api.canReadSources.mockReturnValue(true) }
})

it('renders historical file access using the original anchor and readable result labels', async () => {
  api.canReadSources.mockReturnValue(false)
  const fileKey = { ownerContext: 'IMP', objectType: 'ARRIVAL', objectId: '18', purposeCode: 'ARRIVAL_SIGN_DOCUMENT', referenceKey: 'receipt' }
  api.getDetail.mockResolvedValue({ id: 12, code: 'D_ARRIVAL', name: '到货签收单', status: 'ACCEPTED', writable: false,
    planVersionId: 5, version: 2, configuration: { allowedSources: [] }, history: [
      { id: 40, sourceType: 'BUSINESS_DOCUMENT', submittedAt: '2026-10-09T20:00:00', materials: [
        { id: 1, materialKind: 'FILE', fileName: 'receipt.pdf', artifactId: 21, versionNo: 1, fileKey },
        { id: 2, materialKind: 'BUSINESS_RESULT', businessObjectId: 'IMP.INSTALLATION.INSTALLATION_COMPLETED|object-secret|result-secret|' }
      ] }
    ] })
  const child = ref<any>()
  const view = mount(defineComponent({ setup: () => () => h(Dialog, { projectId: 9, ref: child }) }), {},
    { ElDialog: passthrough, ElCollapse: passthrough, ElCollapseItem: passthrough })
  const findFile = (node: TestNode): TestNode | undefined => node.type === 'file-reference'
    ? node : node.children.map(findFile).find(Boolean)
  try {
    await child.value.open(12)
    await nextTick()
    expect(api.getDocumentSources).not.toHaveBeenCalled()
    expect(findFile(view.root)?.props).toMatchObject({ ...fileKey, 'artifact-id': 21, 'version-no': 1 })
    expect(child.value.$.setupState.expandedHistory).toEqual(['40'])
    expect(textOf(view.root)).toContain('硬件安装完成成果')
    expect(textOf(view.root)).not.toContain('object-secret')
    expect(textOf(view.root)).not.toContain('result-secret')
    expect(child.value.$.setupState.typeLabel({ resultType: 'UNKNOWN_CODE' })).toBe('业务成果')
  } finally { view.app.unmount(); api.canReadSources.mockReturnValue(true) }
})
