import { reactive } from 'vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ProjectTreeNodeVO, ProjectTreeQueryType, ProjectTreeQueryVO } from '@/api/pms/project/projects'

export const PROJECT_TREE_PAGE_SIZE = 20
export const projectLabel = (node: ProjectTreeNodeVO) => node.visibility === 'PATH_PLACEHOLDER'
  ? '受限项目' : [node.projectCode, node.projectName].filter(Boolean).join(' · ') || '项目概要'
export const projectProgressDisplay = (node: ProjectTreeNodeVO) => {
  if (node.visibility !== 'FULL') return { percentage: null, label: '—' }
  if (node.progressStatus === 'RESTRICTED') return { percentage: null, label: '权限受限' }
  if (node.progressStatus === 'STALE') return { percentage: null, label: '待更新' }
  if (node.progressStatus !== 'READY' || node.projectProgress == null) return { percentage: null, label: '待形成' }
  const value = Number(node.projectProgress)
  if (!Number.isFinite(value) || value < 0 || value > 100) return { percentage: null, label: '记录异常' }
  return { percentage: value, label: `${value}%` }
}
export const searchProjects = async (params: PageParam & { keyword?: string }) => {
  const keyword = params.keyword?.trim()
  const page = { pageNo: params.pageNo, pageSize: PROJECT_TREE_PAGE_SIZE }
  const results = keyword ? await Promise.all([
    ProjectsApi.getProjectPage({ ...page, projectName: keyword } as PageParam),
    ProjectsApi.getProjectPage({ ...page, projectCode: keyword } as PageParam)
  ]) : [await ProjectsApi.getProjectPage(page)]
  return { list: [...new Map(results.flatMap(result => result.list).map(project => [project.id, project])).values()] }
}
type QueryApi = typeof ProjectsApi.queryTree
type Page = { cursor?: string; loaded: boolean; loading: boolean; error: string }
const newPage = (): Page => ({ loaded: false, loading: false, error: '' })
export type TreeRow = ProjectTreeNodeVO & { rowKey: string; children: TreeRow[]; pagerFor?: number }
export const readProjectPath = async (id: number, api: QueryApi = ProjectsApi.queryTree) => {
  const nodes: ProjectTreeNodeVO[] = []
  let cursor: string | undefined, version: number | undefined
  do {
    const result = await api(id, { queryType: 'LOCATE', pageSize: PROJECT_TREE_PAGE_SIZE, cursor })
    if (version !== undefined && version !== result.treeVersion) throw new Error('项目树版本已变化，请重新查询')
    version = result.treeVersion; nodes.push(...result.items); cursor = result.nextCursor
  } while (cursor)
  return { nodes, version: version! }
}

/** 合并实际父子投影，分页状态按父项目隔离；不按名称或编码猜测关系。 */
export const createProjectTreeState = (api: QueryApi = ProjectsApi.queryTree) => {
  const state = reactive({ nodes: new Map<number, ProjectTreeNodeVO>(), pages: new Map<number, Page>(),
    root: newPage(), anchorId: 0, queryType: 'LOCATE' as ProjectTreeQueryType, level: '', version: 0, updating: false })
  let generation = 0
  const pageFor = (id: number) => {
    if (!state.pages.has(id)) state.pages.set(id, newPage())
    return state.pages.get(id)!
  }
  const mergeContext = async (result: ProjectTreeQueryVO, nodes: Map<number, ProjectTreeNodeVO>) => {
    result.items.forEach(node => nodes.set(node.projectId, node))
    for (const node of result.items) {
      if (node.parentId == null || nodes.has(node.parentId) || node.parentId === state.anchorId) continue
      const path = await readProjectPath(node.projectId, api)
      if (path.version !== result.treeVersion) throw new Error('项目树版本已变化，请刷新后继续')
      path.nodes.forEach(parent => nodes.set(parent.projectId, parent))
    }
    for (const node of nodes.values()) {
      const visited = new Set<number>()
      let current: ProjectTreeNodeVO | undefined = node
      while (current) {
        if (visited.has(current.projectId)) throw new Error('项目路径存在循环，未自动调整关系，请核对项目数据')
        visited.add(current.projectId)
        if (current.parentId != null && current.parentId !== state.anchorId && !nodes.has(current.parentId)) {
          throw new Error('项目上级路径缺失，未将节点自动挂到根，请核对项目数据')
        }
        current = current.parentId == null ? undefined : nodes.get(current.parentId)
      }
    }
  }
  const fetchPage = async (parentId?: number, reset = false) => {
    const page = parentId == null ? state.root : pageFor(parentId)
    if (page.loading || (!reset && page.loaded && !page.cursor)) return
    const request = generation
    page.loading = true; page.error = ''
    try {
      const result = await api(parentId ?? state.anchorId, { queryType: parentId == null ? state.queryType : 'CHILDREN',
        businessLevelCode: parentId == null && state.queryType === 'BUSINESS_LEVEL' ? state.level : undefined,
        pageSize: PROJECT_TREE_PAGE_SIZE, cursor: reset ? undefined : page.cursor })
      if (request !== generation) return
      if (!reset && state.version && result.treeVersion !== state.version) throw new Error('项目树版本已变化，请刷新后继续；已加载内容保留')
      const nodes = reset ? new Map<number, ProjectTreeNodeVO>() : new Map(state.nodes)
      await mergeContext(result, nodes)
      if (request !== generation) return
      state.nodes = reset ? nodes : new Map([...state.nodes, ...nodes])
      state.version = result.treeVersion; state.updating = result.updating
      page.cursor = result.nextCursor; page.loaded = true
    } catch (error: any) {
      if (request === generation) page.error = error?.message || '项目树加载失败，请重试；已加载内容保留'
    } finally { page.loading = false }
  }
  const load = async (anchorId: number, queryType: ProjectTreeQueryType, level = '') => {
    if (state.anchorId !== anchorId) { state.nodes.clear(); state.version = 0 }
    generation++; state.anchorId = anchorId; state.queryType = queryType; state.level = level
    state.root = newPage(); state.pages.clear()
    await fetchPage(undefined, true)
  }
  const forest = (): TreeRow[] => {
    const rows = new Map<number, TreeRow>([...state.nodes].map(([id, node]) => [id, { ...node, rowKey: `project:${id}`, children: [] }]))
    const roots: TreeRow[] = []
    for (const row of rows.values()) {
      const parent = row.parentId == null ? undefined : rows.get(row.parentId)
      if (parent) parent.children.push(row)
      else roots.push(row)
    }
    for (const row of rows.values()) {
      const page = state.pages.get(row.projectId)
      if (page?.cursor || page?.error) row.children.push({ ...row, rowKey: `page:${row.projectId}`, children: [], pagerFor: row.projectId })
    }
    return roots
  }
  const canLoadChildren = (id: number) => {
    const visited = new Set<number>()
    let current: number | undefined = id
    while (current != null && !visited.has(current)) {
      if (current === state.anchorId) return true
      visited.add(current)
      current = state.nodes.get(current)?.parentId
    }
    return false
  }
  return { state, pageFor, load, more: () => fetchPage(),
    children: (id: number) => canLoadChildren(id) ? fetchPage(id) : Promise.resolve(), canLoadChildren, forest }
}
