import { computed, reactive, ref } from 'vue'
import { getProjectTasks, type TaskNode, type CursorResult } from '@/api/pms/project/task-workbench'

// Shared by the new navigation and stage table; this limits each page, not the project.
export const FLOW_TASK_PAGE_SIZE = 100

export function mergeTaskRows(current: TaskNode[], incoming: TaskNode[]) {
  const rows = new Map(current.map(row => [row.taskId, row]))
  for (const row of incoming) {
    if (!row.placeholder || !rows.has(row.taskId)) rows.set(row.taskId, row)
  }
  return [...rows.values()]
}

export function taskForest(rows: TaskNode[], stageCode: string): TaskNode[] {
  const scoped = rows.filter(row => row.stageCode === stageCode || row.placeholder)
  const nodes = new Map(scoped.map(row => [row.taskId, { ...row, children: [] as TaskNode[] }]))
  const roots: TaskNode[] = []
  for (const node of nodes.values()) {
    const parent = node.parentTaskId == null ? undefined : nodes.get(node.parentTaskId)
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

export function useFlowTaskPaging(projectId: () => number, stageCode: () => string | undefined,
  onPage?: (page: CursorResult<TaskNode>) => void) {
  const rows = ref<TaskNode[]>([])
  const cursor = ref<string>()
  const loading = ref(false)
  const error = ref('')
  const children = reactive(new Map<number, { loading: boolean; loaded: boolean; error: string; cursor?: string }>())
  let generation = 0
  let retryAppend = false
  const load = async (append = false) => {
    if (append && (loading.value || !cursor.value)) return
    const token = append ? generation : ++generation
    if (!append) children.forEach(state => { state.loading = false })
    retryAppend = append
    loading.value = true
    error.value = ''
    try {
      const result = await getProjectTasks(projectId(), {
        mode: 'DIRECT_CHILDREN', stageCode: stageCode(),
        pageSize: FLOW_TASK_PAGE_SIZE, cursor: append ? cursor.value : undefined
      })
      if (token !== generation) return
      onPage?.(result)
      if (!append) children.clear()
      rows.value = mergeTaskRows(append ? rows.value : [], result.rows)
      cursor.value = result.nextCursor
    } catch {
      if (token === generation) error.value = '任务加载失败，已加载内容保留，请重试。'
    } finally {
      if (token === generation) loading.value = false
    }
  }
  const childState = (id: number) => children.get(id)
  const loadChildren = async (parentTaskId: number) => {
    const existing = children.get(parentTaskId)
    if (existing?.loading || (existing?.loaded && !existing.cursor && !existing.error)) return
    const token = generation
    const state = existing || reactive({ loading: false, loaded: false, error: '', cursor: undefined as string | undefined })
    children.set(parentTaskId, state)
    state.loading = true
    state.error = ''
    try {
      const result = await getProjectTasks(projectId(), {
        mode: 'DIRECT_CHILDREN', stageCode: stageCode(), parentTaskId,
        cursor: state.cursor, pageSize: FLOW_TASK_PAGE_SIZE
      })
      if (token !== generation) return
      onPage?.(result)
      rows.value = mergeTaskRows(rows.value, result.rows)
      state.loaded = true
      state.cursor = result.nextCursor
    } catch {
      if (token === generation) state.error = '子任务加载失败，请重试。'
    } finally {
      if (token === generation) state.loading = false
    }
  }
  const reset = () => {
    ++generation
    rows.value = []
    cursor.value = undefined
    error.value = ''
    loading.value = false
    children.clear()
  }
  return {
    rows, loading, error, hasMore: computed(() => Boolean(cursor.value)),
    reload: () => load(), more: () => load(true), retry: () => load(retryAppend), reset,
    childState, loadChildren
  }
}
