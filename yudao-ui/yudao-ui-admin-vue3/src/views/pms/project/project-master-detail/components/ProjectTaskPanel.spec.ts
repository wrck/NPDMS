import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import type { TaskDetail } from '@/api/pms/project/task-workbench'
import { buildTaskUpdatePayload, snapshotTaskEdit } from './project-task-update'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const panel = read('./ProjectTaskPanel.vue')
const tree = read('./ProjectTaskTree.vue')
const drawer = read('./ProjectNodeWorkbenchDrawer.vue')
const flow = read('./ProjectFlowPanel.vue')
const stateActions = read('../../inheritance/detail/TaskStateActions.vue')
const detailsEditor = read('./ProjectTaskDetailsEditor.vue')
const detail = read('../index.vue')
const legacy = read('../../project-task/index.vue')
const api = read('../../../../../api/pms/project/task-workbench/index.ts')
const legacyApi = read('../../../../../api/pms/project/project-task/index.ts')
const legacyController = read(
  '../../../../../../../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttask/ProjectTaskController.java'
)

describe('F-PROJ-007 project task workbench', () => {
  it('loads the task tree lazily and locates search matches through server queries', () => {
    expect(tree).toContain("mode: 'DIRECT_CHILDREN'")
    expect(tree).toContain('stageCode: props.stageCode')
    expect(tree).toContain("mode: 'LOCATE'")
    expect(tree).toContain(':lazy="!keyword"')
    expect(tree).toContain('parentTaskId: node.level === 0')
  })

  it('builds a real partial PATCH payload and represents nullable clears as null', () => {
    const task = {
      taskId: 11,
      treeDepth: 0,
      placeholder: false,
      name: '原任务',
      businessLevelCode: undefined,
      description: undefined
    } as TaskDetail
    const original = snapshotTaskEdit(task)
    const nameOnly = buildTaskUpdatePayload(original, {
      name: '新任务',
      businessLevelCode: '',
      planStartTime: undefined,
      planEndTime: undefined,
      priority: undefined,
      sortOrder: undefined,
      description: ''
    })
    expect(nameOnly).toEqual({ name: '新任务' })

    const populated = snapshotTaskEdit({
      ...task,
      businessLevelCode: 'L1',
      description: '说明'
    })
    const cleared = buildTaskUpdatePayload(populated, {
      name: '原任务',
      businessLevelCode: '',
      planStartTime: undefined,
      planEndTime: undefined,
      priority: undefined,
      sortOrder: undefined,
      description: ''
    })
    expect(cleared).toEqual({ businessLevelCode: null, description: null })
  })

  it('uses only server allowed actions for workspace and task operations', () => {
    expect(panel).toContain("workspace?.allowedActions.includes('MANAGE_PLAN')")
    expect(panel).toContain('<ProjectPlanEditor')
    expect(panel).toContain('TaskWorkbenchApi.getProjectWorkspace(props.projectId)')
    expect(stateActions).toContain('props.workbench.allowedActions?.includes(action)')
    expect(drawer).toContain('<ProjectFlowPanel')
    expect(drawer).not.toMatch(/hasRole|roleCode|v-hasPermi/)
  })

  it('shares responsibility, description and progress controls with the delivery flow', () => {
    expect(flow).toContain('<TaskMaintenancePanel')
    expect(flow).toContain('<ProjectTaskDetailsEditor')
    expect(detailsEditor).toContain(':max="99"')
    expect(api).toContain('{ progress }')
    expect(api).toContain("method: 'PATCH'")
    expect(api).toContain("'If-Match': String(version)")
  })

  it('sends concurrency and idempotency inputs through the new command API', () => {
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
    expect(api).toContain("'If-Match': String(version)")
    expect(detailsEditor).toContain('editingVersion.value')
    expect(stateActions).toContain('intent.keyFor(')
    expect(detail).toContain("{ key: 'tasks', label: '项目任务'")
  })

  it('uses responsive Element Plus layouts and theme variables without inline styles', () => {
    expect(panel).toContain('<ContentWrap')
    expect(tree).toContain('<el-tree')
    expect(drawer).toContain('<el-drawer')
    expect(drawer).toContain('append-to-body')
    expect(drawer).toContain('size="min(1180px, 100vw)"')
    expect(flow).toContain('<el-descriptions')
    expect(panel).toContain('@media (width <= 767px)')
    expect(flow).toContain("useMediaQuery('(max-width: 767px)')")
    expect(panel).toMatch(/var\(--el-(?:text|border|fill)-/)
    expect(`${panel}${tree}${drawer}`).not.toMatch(/\sstyle=/)
  })

  it('retires the V1.7 write entry and preserves only its confirmed tree read', () => {
    expect(legacy).toContain("tab: 'tasks'")
    expect(legacy).not.toContain('ProjectTaskApi')
    expect(legacyApi).toContain('getProjectTaskTree')
    expect(legacyApi).not.toMatch(
      /createProjectTask|updateProjectTask|deleteProjectTask|moveProjectTask/
    )
    expect(legacyController).toContain('@GetMapping("/tree")')
    expect(legacyController).not.toMatch(
      /@(?:Post|Put|Delete)Mapping\("\/(?:create|update|delete|move)"\)/
    )
  })
})
