import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import type { TaskDetail } from '@/api/pms/project/task-workbench'
import { buildTaskUpdatePayload, snapshotTaskEdit } from './project-task-update'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const exists = (path: string) => existsSync(new URL(path, import.meta.url))
const panel = read('./ProjectTaskPanel.vue')
const tree = read('./ProjectTaskTree.vue')
const drawer = read('./ProjectNodeWorkbenchDrawer.vue')
const flow = read('./ProjectFlowPanel.vue')
const stateActions = read('../../inheritance/detail/TaskStateActions.vue')
const detailsEditor = read('./ProjectTaskDetailsEditor.vue')
const detail = read('../index.vue')
const api = read('../../../../../api/pms/project/task-workbench/index.ts')

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
    expect(stateActions).toContain('props.workbench.allowedActions?.includes(action)')
    expect(drawer).toContain('<ProjectFlowPanel')
    expect(drawer).not.toMatch(/hasRole|roleCode|v-hasPermi/)
  })

  it('shares authorized task state, details and progress controls with the delivery flow', () => {
    expect(flow).toMatch(/<TaskStateActions\b[\s\S]*?<ProjectTaskDetailsEditor\b[\s\S]*?<\/TaskStateActions>/)
    expect(flow).toContain(':before-action="requestLeave" @changed="handleBusinessChanged"')
    expect(detailsEditor).toContain("allowed('UPDATE')")
    expect(detailsEditor).toContain("allowed('UPDATE_PROGRESS')")
    expect(detailsEditor).toContain(':max="99"')
    expect(api).toContain('{ progress }')
    expect(api).toContain("method: 'PATCH'")
    expect(api).toContain("'If-Match': String(version)")
  })

  it('opens the authorized submission dialog for template-frozen task deliverables', () => {
    expect(api).toContain('TaskWorkbenchDeliverable')
    expect(flow).toContain('v-if="workbench?.deliverables?.length"')
    expect(flow).toContain('DICT_TYPE.PMS_PROJECT_DELIVERABLE_STATUS')
    expect(flow).toContain("item.required ? '必选' : '可选'")
    expect(flow).toContain("deliverableRef?.open(item.id)")
    expect(flow).toContain('<ProjectDeliverableDialog ref="deliverableRef" :project-id="projectId"')
    expect(flow).not.toMatch(/deliverables[\s\S]*?(type="file"|el-upload|上传交付件)/i)
  })

  it('sends concurrency and idempotency inputs through the new command API', () => {
    expect(api).toContain("'Idempotency-Key': idempotencyKey")
    expect(api).toContain("'If-Match': String(version)")
    expect(detailsEditor).toContain('editingVersion.value')
    expect(stateActions).toContain('intent.keyFor(')
    expect(detail).toContain("stageSelection.value = { kind: 'task', stageCode, taskId: task.taskId, task }")
    expect(detail).toContain("await switchTab('stage')")
    expect(detail).toContain(':selection="stageSelection"')
    expect(detail).toContain('data-testid="project-pane-stage-workspace"')
  })

  it('uses responsive Element Plus layouts and theme variables without inline styles', () => {
    expect(panel).toContain('<ContentWrap')
    expect(tree).toContain('<el-tree')
    expect(drawer).toContain('<el-drawer')
    expect(drawer).toContain('append-to-body')
    expect(drawer).toContain('size="min(1180px, 100vw)"')
    expect(panel).toContain('@media (width <= 767px)')
    expect(flow).toMatch(/@media \(width <= 767px\)\s*\{\s*\.panel-header\s*\{[^}]*flex-direction: column/)
    expect(flow).toMatch(/var\(--el-(?:text|border|fill)-/)
    expect(panel).toMatch(/var\(--el-(?:text|border|fill)-/)
    expect(`${panel}${tree}${drawer}`).not.toMatch(/\sstyle=/)
  })

  it('removes the retired task page and endpoint while keeping the new workbench', () => {
    for (const path of [
      '../../project-task/index.vue',
      '../../../../../api/pms/project/project-task/index.ts',
      '../../../../../../../../pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projecttask/ProjectTaskController.java'
    ]) {
      expect(exists(path)).toBe(false)
    }
    expect(api).toContain("const baseUrl = '/api/v1/pms'")
    expect(api).toContain('export const getProjectTasks')
  })
})
