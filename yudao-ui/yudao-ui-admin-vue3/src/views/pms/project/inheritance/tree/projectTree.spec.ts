import { describe, expect, it, vi } from 'vitest'
import { createProjectTreeState, projectLabel, projectProgressDisplay, readProjectPath } from './projectTree'
import type { ProjectTreeNodeVO } from '@/api/pms/project/projects'
vi.mock('@/api/pms/project/projects', () => ({}))
const node = (projectId: number, parentId?: number): ProjectTreeNodeVO => ({ projectId, parentId, projectCode: `P${projectId}`, projectName: `项目${projectId}`, visibility: 'FULL' })
const page = (items: ProjectTreeNodeVO[], nextCursor?: string, treeVersion = 1) => ({ items, nextCursor, treeVersion, updating: false })

describe('project tree hierarchy and independent paging', () => {
  it('uses recorded project progress, preserves zero and never substitutes legacy progress or hidden values', () => {
    const project = { ...node(1), milestoneProgress: 90 }
    expect(projectProgressDisplay(project).label).toBe('待形成')
    expect(projectProgressDisplay({ ...project, progressStatus: 'READY', projectProgress: 0 }).percentage).toBe(0)
    expect(projectProgressDisplay({ ...project, progressStatus: 'READY', projectProgress: 37.5 }).label).toBe('37.5%')
    expect(projectProgressDisplay({ ...project, progressStatus: 'STALE', projectProgress: 90 }).label).toBe('待更新')
    expect(projectProgressDisplay({ ...project, progressStatus: 'RESTRICTED', projectProgress: 90 }).percentage).toBeNull()
    expect(projectProgressDisplay({ ...project, visibility: 'PATH_PLACEHOLDER', progressStatus: 'READY', projectProgress: 90 }).label).toBe('—')
    expect(projectProgressDisplay({ ...project, progressStatus: 'READY', projectProgress: 101 }).label).toBe('记录异常')
  })
  it('retains true deep hierarchy and merges duplicate results once', async () => {
    const api = vi.fn().mockResolvedValueOnce(page([node(1), node(2, 1)], 'next'))
      .mockResolvedValueOnce(page([node(2, 1), node(3, 2)]))
    const tree = createProjectTreeState(api)
    await tree.load(1, 'DESCENDANTS'); await tree.more()
    expect(tree.forest()).toHaveLength(1)
    expect(tree.forest()[0].children[0].children[0].projectId).toBe(3)
    expect(tree.state.nodes.size).toBe(3)
  })
  it('keeps child cursors independent and retries the original cursor after failure', async () => {
    const api = vi.fn().mockResolvedValueOnce(page([node(1), node(2, 1), node(3, 1)]))
      .mockResolvedValueOnce(page([node(4, 2)], 'p2'))
      .mockResolvedValueOnce(page([node(5, 3)], 'p3'))
      .mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(page([node(6, 2)]))
    const tree = createProjectTreeState(api)
    await tree.load(1, 'LOCATE'); await tree.children(2); await tree.children(3); await tree.children(2)
    expect(tree.state.nodes.has(4)).toBe(true)
    expect(tree.pageFor(2).cursor).toBe('p2'); expect(tree.pageFor(3).cursor).toBe('p3')
    expect(tree.pageFor(2).error).toBe('offline')
    await tree.children(2)
    expect(api).toHaveBeenLastCalledWith(2, expect.objectContaining({ cursor: 'p2' }))
    expect(tree.state.nodes.has(6)).toBe(true); expect(tree.state.nodes.has(5)).toBe(true)
  })
  it('does not mix a different tree version or overwrite loaded data', async () => {
    const api = vi.fn().mockResolvedValueOnce(page([node(1)], 'next'))
      .mockResolvedValueOnce(page([node(2, 1)], undefined, 2))
    const tree = createProjectTreeState(api)
    await tree.load(1, 'LOCATE'); await tree.more()
    expect(tree.state.nodes.size).toBe(1); expect(tree.state.root.cursor).toBe('next')
    expect(tree.state.root.error).toContain('版本已变化')
  })
  it('preserves both sibling responses during concurrent expansion', async () => {
    const api = vi.fn().mockResolvedValueOnce(page([node(1), node(2, 1), node(3, 1)]))
      .mockResolvedValueOnce(page([node(4, 2)])).mockResolvedValueOnce(page([node(5, 3)]))
    const tree = createProjectTreeState(api)
    await tree.load(1, 'LOCATE'); await Promise.all([tree.children(2), tree.children(3)])
    expect([...tree.state.nodes.keys()]).toEqual([1, 2, 3, 4, 5])
  })
  it('ignores a response from a previously selected project', async () => {
    let finish!: (value: ReturnType<typeof page>) => void
    const api = vi.fn().mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
      .mockResolvedValueOnce(page([node(9)]))
    const tree = createProjectTreeState(api)
    const first = tree.load(1, 'LOCATE'); await tree.load(9, 'LOCATE'); finish(page([node(1)])); await first
    expect([...tree.state.nodes.keys()]).toEqual([9])
  })
  it('loads missing path context for business-level matches and keeps placeholders anonymous', async () => {
    const hidden = { ...node(2, 1), visibility: 'PATH_PLACEHOLDER' as const, projectName: undefined, projectCode: undefined }
    const api = vi.fn().mockResolvedValueOnce(page([node(3, 2)]))
      .mockResolvedValueOnce(page([node(1), hidden], 'path')).mockResolvedValueOnce(page([node(3, 2)]))
    const tree = createProjectTreeState(api)
    await tree.load(1, 'BUSINESS_LEVEL', 'SITE')
    expect(tree.forest()[0].children[0].children[0].projectId).toBe(3)
    expect(projectLabel(tree.forest()[0].children[0])).toBe('受限项目')
  })
  it('rejects incomplete or cyclic paths instead of flattening or repairing them', async () => {
    const api = vi.fn().mockResolvedValueOnce(page([node(2, 3)]))
      .mockResolvedValueOnce(page([node(2, 3)]))
    const tree = createProjectTreeState(api); await tree.load(1, 'DESCENDANTS')
    expect(tree.state.root.error).toContain('路径缺失')
    api.mockResolvedValueOnce(page([node(2, 3), node(3, 2)]))
    await tree.load(1, 'DESCENDANTS'); expect(tree.state.root.error).toContain('循环')
  })
  it('checks path-page versions rather than joining different project-tree snapshots', async () => {
    const api = vi.fn().mockResolvedValueOnce(page([node(1)], 'p')).mockResolvedValueOnce(page([node(2, 1)], undefined, 2))
    await expect(readProjectPath(2, api)).rejects.toThrow('版本已变化')
  })
  it('does not retain another project tree when the current project changes and loading fails', async () => {
    const api = vi.fn().mockResolvedValueOnce(page([node(1), node(2, 1)])).mockRejectedValueOnce(new Error('offline'))
    const tree = createProjectTreeState(api)
    await tree.load(1, 'LOCATE'); await tree.load(9, 'LOCATE')
    expect(tree.forest()).toEqual([])
    expect(tree.canLoadChildren(1)).toBe(false)
    expect(tree.state.root.error).toBe('offline')
  })
})
