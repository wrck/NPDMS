import { defineComponent, h, nextTick, reactive } from 'vue'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import ProjectSplitWizard from './ProjectSplitWizard.vue'
import * as api from '@/api/pms/project/project-splits'
import { mount, passthrough, textOf, type TestNode } from '../../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/project-splits', () => ({ getDraft: vi.fn(), createDraft: vi.fn(), updateDraft: vi.fn(), previewDraft: vi.fn(), validateDraft: vi.fn(), applyDraft: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn(), success: vi.fn() }) }))
vi.mock('./ChildTemplatePicker.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({ inheritAttrs: false, setup: (_, { attrs }) => () => h('child-picker', attrs) }) }
})
const find = (node: TestNode, test: (node: TestNode) => boolean): TestNode | undefined =>
  test(node) ? node : node.children.map(child => find(child, test)).find(Boolean)
const button = (root: TestNode, label: string) => find(root, node => node.type === 'button' && textOf(node) === label)!
const click = async (node: TestNode) => { await (node.props!.onClick as () => Promise<void>)(); await nextTick() }
const flush = async () => { await Promise.resolve(); await nextTick() }
const draft = {
  id: 20, parentProjectId: 100, status: 'DRAFT', draftVersion: 2, parentVersion: 3, scopeVersion: 1, treeVersion: 1,
  items: [{ id: 30, clientItemKey: 'A', projectName: '现场工勘', templateRevisionId: '993009245201', templateSelectionReason: '独立交付',
    scopes: [{ id: 40, orderLineId: 10, allocatedQty: 1, sourceScopeVersion: 1 }] }]
}
const preview = { requestId: 20, draftVersion: 2, valid: true, parentVersion: 3, scopeVersion: 1, treeVersion: 1, errors: [], items: [] }
const components = { ElSteps: passthrough, ElStep: passthrough, ElInput: passthrough, ElInputNumber: passthrough, ElResult: passthrough }

describe('split child template persistence', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('localStorage', { getItem: () => '20', setItem: vi.fn(), removeItem: vi.fn() })
    vi.mocked(api.getDraft).mockResolvedValue(structuredClone(draft))
    vi.mocked(api.updateDraft).mockResolvedValue({ ...draft, draftVersion: 3 })
    vi.mocked(api.previewDraft).mockResolvedValue(preview)
  })
  afterEach(() => vi.unstubAllGlobals())
  it('reopens and saves each child version and reason without a parent template field', async () => {
    const view = mount(ProjectSplitWizard, { projectId: 100 }, components); await flush()
    await click(button(view.root, '保存草稿'))
    const input = vi.mocked(api.updateDraft).mock.calls[0][1]
    expect(input).not.toHaveProperty('templateRevisionId')
    expect(input.items[0]).toMatchObject({ templateRevisionId: '993009245201', templateSelectionReason: '独立交付' })
    view.app.unmount()
  })
  it('invalidates a previous preview after editing and cannot apply unsaved selections', async () => {
    const view = mount(ProjectSplitWizard, { projectId: 100 }, components); await flush()
    await click(button(view.root, '生成预览'))
    expect(button(view.root, '确认原子应用').props?.disabled).toBe(false)
    const input = find(view.root, node => node.props?.placeholder === '说明子项目采用该模板的原因')!
    ;(input.props!['onUpdate:modelValue'] as (value: string) => void)('重新划分交付范围')
    await nextTick()
    expect(button(view.root, '确认原子应用').props?.disabled).toBe(true)
    await click(button(view.root, '确认原子应用'))
    await click(button(view.root, '生成预览'))
    expect(api.applyDraft).not.toHaveBeenCalled(); expect(api.previewDraft).toHaveBeenCalledTimes(1)
    await click(button(view.root, '保存草稿'))
    expect(button(view.root, '生成预览').props?.disabled).toBe(false)
    view.app.unmount()
  })
  it('changing one child selection clears its old reason and persists the new exact revision', async () => {
    const view = mount(ProjectSplitWizard, { projectId: 100 }, components); await flush()
    const picker = find(view.root, node => node.type === 'child-picker')!
    ;(picker.props!.onChange as (revision: string) => void)('993009245202')
    await nextTick()
    await click(button(view.root, '保存草稿'))
    expect(vi.mocked(api.updateDraft).mock.calls[0][1].items[0]).toMatchObject({ templateRevisionId: '993009245202', templateSelectionReason: '' })
    view.app.unmount()
  })
  it('a delayed draft from the previous parent cannot replace the current child selections', async () => {
    let resolve!: (value: typeof draft) => void
    vi.mocked(api.getDraft).mockImplementationOnce(() => new Promise(done => { resolve = done }))
      .mockResolvedValueOnce({ ...draft, id: 21, parentProjectId: 200, items: [{ ...draft.items[0], projectName: '需求分析', templateRevisionId: '993009245202' }] })
    const state = reactive({ projectId: 100 })
    const view = mount(defineComponent({ setup: () => () => h(ProjectSplitWizard, state) }), {}, components)
    state.projectId = 200; await nextTick(); await flush()
    resolve(draft); await flush()
    await click(button(view.root, '保存草稿'))
    const [id, input] = vi.mocked(api.updateDraft).mock.calls[0]
    expect(id).toBe(21); expect(input.parentProjectId).toBe(200)
    expect(input.items[0]).toMatchObject({ projectName: '需求分析', templateRevisionId: '993009245202' })
    view.app.unmount()
  })
})
