import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Chapter from './SolutionReviewChapterForm.vue'
const mocks = vi.hoisted(() => ({ images: vi.fn(), topology: vi.fn(), word: vi.fn(), upload: vi.fn() }))
vi.mock('./documentImages', () => ({ prepareDocumentImages: mocks.images }))
vi.mock('@/api/pms/platform/file', () => ({ getPreviewContent: vi.fn() }))
vi.mock('./requirementTopology', async importOriginal => ({
  ...await importOriginal<typeof import('./requirementTopology')>(), requirementTopologyImages: mocks.topology
}))
vi.mock('@/views/pms/engineering/solution/prepareGeneratedSolutionWord', () => ({ prepareGeneratedSolutionWord: mocks.word }))
vi.mock('@/components/DeliveryArtifact/uploadDeliveryFile', () => ({ uploadDeliveryFile: mocks.upload }))
vi.mock('@/components/BusinessEntity/DeliveryPanel.vue', () => ({ default: defineComponent(() => () => h('div')) }))
vi.mock('@/views/pms/engineering/solution/ResourceConfirmationEditor.vue', () => ({ default: defineComponent(() => () => h('div')) }))
vi.mock('@/views/pms/engineering/solution/SolutionEquipmentTable.vue', () => ({ default: defineComponent(() => () => h('div')) }))
vi.mock('./SolutionPlanningImport.vue', () => ({ default: defineComponent(() => () => h('div')) }))
vi.mock('./RequirementTopologyFiles.vue', () => ({ default: defineComponent(() => () => h('div')) }))
vi.mock('./saveCustomerSolutionDocuments', () => ({ resolveCustomerSolutionDocument: vi.fn(), downloadCustomerSolutionDocument: vi.fn(), customerSolutionDocumentUrls: () => [], customerSolutionDocumentName: () => '' }))
vi.mock('./saveSolutionScripts', () => ({ downloadSolutionScript: vi.fn() }))
vi.mock('@/api/pms/commerce/projectEquipment', () => ({ loadProjectEquipmentList: async () => [] }))
vi.mock('@/api/pms/project/unified-members', () => ({ getMemberPage: async () => ({ list: [], total: 0 }), logicalMemberRole: () => '' }))
vi.mock('@/api/pms/customer/contacts', () => ({ getProjectPage: async () => ({ list: [] }) }))
vi.mock('@/api/pms/engineering/stage-plan', () => ({ getStagePlanBatchPage: async () => ({ list: [] }) }))
vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ workspace: async () => ({ currentEffective: null }),
  read: async () => ({ values: {}, revision: { ref: { revisionId: '701' } }, attachments: [] }) }))
vi.mock('@/components/BusinessView/businessActionBar', () => ({ useBusinessActionBar: () => ({ barTarget: ref(null) }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getDictLabel: () => '' }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await nextTick() } }
beforeEach(() => { vi.resetAllMocks(); mocks.images.mockResolvedValue({}); mocks.topology.mockResolvedValue(['data:image/png;base64,A']) })
const render = (overrides: Record<string, unknown> = {}) => {
  const model = ref({ id: 42, projectId: 20, name: '方案A', version: 1, sectionData: '{"requirementInput":{"revisionId":"701"}}', topology: '{"toBeChanged":"no"}', ...overrides })
  let state: any
  const wrapper = defineComponent({ setup: () => () => h(Chapter, { modelValue: model.value, readOnly: true,
    'onUpdate:modelValue': value => { model.value = value as typeof model.value },
    ref: value => { if (value) state = (value as any).$.setupState }
  }) })
  return { ...mount(wrapper, {}, { ElTableColumn: tableColumn }), model, get state() { return state } }
}
it('exports an independently prepared solution without requiring an unselected requirement source', async () => {
  mocks.word.mockRejectedValueOnce(new Error('conversion boundary reached'))
  const view = render({ sectionData: '{}', topology: '{}' })
  try {
    await flush()
    await view.state.downloadSolution()
    expect(mocks.topology).not.toHaveBeenCalled()
    expect(mocks.word).toHaveBeenCalledOnce()
    expect(mocks.word.mock.calls[0][0]).toBe(42)
    expect(mocks.word.mock.calls[0][1]).toContain('2.1 现网拓扑图')
    expect(view.state.solutionDownloadError).toBe('conversion boundary reached')
    expect(mocks.upload).not.toHaveBeenCalled()
  } finally { view.app.unmount() }
})
it('keeps a selected requirement source mandatory when its content has not loaded', async () => {
  const view = render()
  try {
    await flush()
    view.state.requirementSource = null
    await view.state.downloadSolution()
    expect(mocks.topology).not.toHaveBeenCalled()
    expect(mocks.word).not.toHaveBeenCalled()
    expect(view.state.solutionDownloadError).toContain('请先加载所引用的需求分析拓扑附件')
  } finally { view.app.unmount() }
})
it.each(['identity', 'body'])('does not register mixed document when %s changes during topology rendering', async kind => {
  let finish: (value: string[]) => void = () => {}
  mocks.topology.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const view = render()
  try {
    await flush()
    const exporting = view.state.downloadSolution()
    await flush()
    expect(mocks.topology).toHaveBeenCalledOnce()
    view.model.value = { ...view.model.value, ...(kind === 'identity' ? { id: 43, name: '方案B' } : { name: '修改后的方案A' }) }
    await flush()
    finish(['data:image/png;base64,A'])
    await exporting
    expect(mocks.word).not.toHaveBeenCalled()
    expect(mocks.upload).not.toHaveBeenCalled()
    expect(view.state.solutionDownloadError).toContain('方案或引用内容已变化')
  } finally { view.app.unmount() }
})
it('rechecks identity after Word conversion and before registering the generated file', async () => {
  let finish: (value: unknown) => void = () => {}
  mocks.word.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  const view = render()
  try {
    await flush()
    const exporting = view.state.downloadSolution()
    await flush()
    expect(mocks.word.mock.calls[0][0]).toBe(42)
    view.model.value = { ...view.model.value, id: 43, name: '方案B' }
    await flush()
    finish({ owner: { entityId: 42 }, file: new Blob(['A']) })
    await exporting
    expect(mocks.upload).not.toHaveBeenCalled()
    expect(view.state.solutionDownloadError).toContain('方案或引用内容已变化')
  } finally { view.app.unmount() }
})
