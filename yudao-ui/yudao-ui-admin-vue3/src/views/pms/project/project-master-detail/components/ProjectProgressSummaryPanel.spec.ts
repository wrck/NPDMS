import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProjectProgressSummaryPanel from './ProjectProgressSummaryPanel.vue'
import * as api from '@/api/pms/project/projects'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '../../../platform/dynamic-form/components/runtimeTestHarness'

const messages = vi.hoisted(() => ({ warning: vi.fn(), success: vi.fn() }))
vi.mock('@/api/pms/project/projects', () => ({
  queryTree: vi.fn(), getProgress: vi.fn(), getProgressPolicies: vi.fn(),
  createProgressPolicy: vi.fn(), submitProgressPolicy: vi.fn()
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => messages }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => false }))
vi.mock('./useProjectSplitWorkspace', () => ({ splitErrorMessage: (error: Error) => error.message }))
vi.mock('@/utils', () => { let sequence = 0; return { generateUUID: () => `progress-${++sequence}` } })

const flush = async () => { for (let index = 0; index < 8; index++) { await Promise.resolve(); await nextTick() } }
const nodes = (root: TestNode): TestNode[] => [root, ...root.children.flatMap(nodes)]
const click = async (root: TestNode, label: string) => {
  const button = nodes(root).find(node => node.type === 'button' && textOf(node) === label)
  expect(button, label).toBeDefined()
  await (button!.props!.onClick as () => unknown)()
  await flush()
}
const child = (id: number) => ({ projectId: id, projectCode: `QA-${id}`, projectName: `子项目${id}`, treeVersion: 8 })
const setup = () => mount(ProjectProgressSummaryPanel, { projectId: 100 }, {
  ...Object.fromEntries(['ElTable', 'ElSkeleton', 'ElProgress', 'ElRadioGroup', 'ElRadioButton', 'ElInputNumber']
    .map(name => [name, passthrough])),
  ElTableColumn: tableColumn,
  Dialog: defineComponent({ setup: (_, { attrs, slots }) => () =>
    h('div', attrs.modelValue ? [slots.default?.(), slots.footer?.()] : []) })
})

describe('project progress summary behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.queryTree).mockResolvedValue({ treeVersion: 8, items: [child(101), child(102)] } as never)
    vi.mocked(api.getProgress).mockResolvedValue({ treeVersion: 8, status: 'PENDING', progress: null, items: [] } as never)
    vi.mocked(api.getProgressPolicies).mockResolvedValue([])
  })

  it('does not request an invalid aggregate for a leaf project', async () => {
    vi.mocked(api.queryTree).mockResolvedValue({ treeVersion: 8, items: [] } as never)
    const view = setup(); await flush()
    expect(api.getProgress).not.toHaveBeenCalled()
    expect(textOf(view.root)).toContain('当前项目没有直接子项目')
    expect(nodes(view.root).find(node => node.type === 'button' && textOf(node) === '调整权重')?.props?.disabled).toBe(true)
    view.app.unmount()
  })

  it('loads every page before offering weights and keeps missing progress pending', async () => {
    vi.mocked(api.queryTree).mockResolvedValueOnce({ treeVersion: 8, items: Array.from({ length: 100 }, (_, i) => child(i + 1)), nextCursor: 'page-2' } as never)
      .mockResolvedValueOnce({ treeVersion: 8, items: [child(101)] } as never)
    const view = setup(); await flush()
    expect(api.queryTree).toHaveBeenLastCalledWith(100, { queryType: 'CHILDREN', pageSize: 100, cursor: 'page-2' })
    expect(textOf(view.root)).toContain('进度待计算')
    expect(nodes(view.root).some(node => node.props?.percentage === 0)).toBe(false)
    await click(view.root, '调整权重')
    const weights = nodes(view.root).filter(node => String(node.props?.['aria-label'] || '').endsWith('权重'))
    expect(weights).toHaveLength(101)
    expect(Number(weights.reduce((sum, node) => sum + Number(node.props?.modelValue), 0).toFixed(4))).toBe(100)
    view.app.unmount()
  })

  it('shows loading failures and prevents editing an incomplete tree', async () => {
    vi.mocked(api.queryTree).mockRejectedValue(new Error('项目树读取失败'))
    const view = setup(); await flush()
    expect(textOf(view.root)).toContain('进度汇总读取失败：项目树读取失败')
    expect(textOf(view.root)).not.toContain('当前项目没有直接子项目')
    expect(api.getProgress).not.toHaveBeenCalled()
    view.app.unmount()
  })

  it('rejects mixed tree versions instead of creating a partial policy', async () => {
    vi.mocked(api.queryTree).mockResolvedValueOnce({ treeVersion: 8, items: [child(101)], nextCursor: 'next' } as never)
      .mockResolvedValueOnce({ treeVersion: 9, items: [child(102)] } as never)
    const view = setup(); await flush()
    expect(textOf(view.root)).toContain('项目树已变化')
    expect(api.getProgress).not.toHaveBeenCalled()
    view.app.unmount()
  })

  it('does not treat a tree under reconstruction as an empty project', async () => {
    vi.mocked(api.queryTree).mockResolvedValue({ treeVersion: 8, items: [], updating: true } as never)
    const view = setup(); await flush()
    expect(textOf(view.root)).toContain('项目树正在更新')
    expect(textOf(view.root)).not.toContain('当前项目没有直接子项目')
    expect(api.getProgress).not.toHaveBeenCalled()
    view.app.unmount()
  })

  it('keeps the same idempotency key when saving a policy after a failed response', async () => {
    vi.mocked(api.createProgressPolicy).mockRejectedValueOnce(new Error('响应中断')).mockResolvedValueOnce({} as never)
    const view = setup(); await flush(); await click(view.root, '调整权重')
    await click(view.root, '保存草稿')
    expect(textOf(view.root)).toContain('响应中断')
    await click(view.root, '保存草稿')
    expect(api.createProgressPolicy).toHaveBeenCalledTimes(2)
    const calls = vi.mocked(api.createProgressPolicy).mock.calls
    expect(calls[0][2]).toBe(calls[1][2])
    expect(calls[0][3]).toBe(8)
    view.app.unmount()
  })
})
