import { defineComponent, h, nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChildTemplatePicker from './ChildTemplatePicker.vue'
import * as api from '@/api/pms/project/project-splits'
import { mount, passthrough, textOf, type TestNode } from '../../../platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/project-splits', () => ({ getChildTemplateOptions: vi.fn() }))
const find = (node: TestNode, test: (node: TestNode) => boolean): TestNode | undefined =>
  test(node) ? node : node.children.map(child => find(child, test)).find(Boolean)
const button = (root: TestNode, label: string) => find(root, node => node.type === 'button' && textOf(node).includes(label))!
const click = (node: TestNode) => (node.props!.onClick as () => void)()
const flush = async () => { await Promise.resolve(); await nextTick() }
const recommended = { templateId: 20, revisionId: '993009245201', revisionNo: 2, name: '工前准备', recommended: true, selectable: true }
const components = { ElDialog: passthrough, ElInput: passthrough, ElPagination: passthrough }

describe('child template selection', () => {
  beforeEach(() => vi.clearAllMocks())
  it('emits the selected revision without substituting the parent version', async () => {
    vi.mocked(api.getChildTemplateOptions).mockResolvedValue({ list: [recommended], total: 1 })
    const change = vi.fn()
    const view = mount(ChildTemplatePicker, { parentProjectId: 100, revisionId: 101, onChange: change }, components)
    click(button(view.root, '选择模板')); await flush()
    expect(api.getChildTemplateOptions).toHaveBeenCalledWith({ parentProjectId: 100, pageNo: 1, pageSize: 20, name: undefined })
    click(button(view.root, '工前准备'))
    expect(change).toHaveBeenCalledWith('993009245201')
    view.app.unmount()
  })
  it('does not emit unavailable options even when their handler is invoked', async () => {
    vi.mocked(api.getChildTemplateOptions).mockResolvedValue({ list: [{ ...recommended, selectable: false }], total: 1 })
    const change = vi.fn()
    const view = mount(ChildTemplatePicker, { parentProjectId: 100, onChange: change }, components)
    click(button(view.root, '选择模板')); await flush()
    expect(button(view.root, '工前准备').props?.disabled).toBe(true)
    click(button(view.root, '工前准备')); expect(change).not.toHaveBeenCalled()
    view.app.unmount()
  })
  it('ignores late list responses after switching parent project', async () => {
    let resolve!: (result: { list: typeof recommended[]; total: number }) => void
    vi.mocked(api.getChildTemplateOptions).mockImplementation(() => new Promise(done => { resolve = done }))
    const state = reactive({ parentProjectId: 100 })
    const view = mount(defineComponent({ setup: () => () => h(ChildTemplatePicker, state) }), {}, components)
    click(button(view.root, '选择模板'))
    state.parentProjectId = 200; await nextTick()
    resolve({ list: [recommended], total: 1 }); await flush()
    expect(textOf(view.root)).not.toContain('工前准备')
    view.app.unmount()
  })
  it('shows a retryable failure and requests the selected page', async () => {
    vi.mocked(api.getChildTemplateOptions).mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({ list: [recommended], total: 30 })
    const view = mount(ChildTemplatePicker, { parentProjectId: 100 }, components)
    click(button(view.root, '选择模板')); await flush()
    expect(textOf(view.root)).toContain('模板列表读取失败')
    click(button(view.root, '查询')); await flush()
    const pagination = find(view.root, node => node.props?.layout === 'prev, pager, next')!
    ;(pagination.props!.onCurrentChange as (page: number) => void)(2); await flush()
    expect(api.getChildTemplateOptions).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 2 }))
    view.app.unmount()
  })
})
