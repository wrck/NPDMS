import { describe, expect, it } from 'vitest'
import type { IssueSummary, TaskSummary, TaskWorkbench } from '@/api/pms/inspection/tasks'
import { workbenchTaskRows } from './taskRows'

const task = (id: number, statusCode: string) =>
  ({ id, taskCode: `INS-${id}`, taskName: `任务${id}`, statusCode }) as TaskSummary
const issue = (id: number, taskId: number | string, statusCode = 'PENDING') =>
  ({ id, taskId, taskCode: `INS-${taskId}`, taskName: `任务${taskId}`, statusCode }) as IssueSummary
const data = (values: Partial<TaskWorkbench>): TaskWorkbench => ({
  taskCounts: [],
  pendingTasks: [],
  createdTasks: [],
  myIssues: [],
  ...values
})

describe('inspection workbench task grouping', () => {
  it('moves a self-created tracking task with twelve issues into one task todo', () => {
    const tracking = task(35, 'TRACKING')
    const result = workbenchTaskRows({
      data: data({
        createdTasks: [tracking, task(36, 'CLOSED')],
        myIssues: Array.from({ length: 12 }, (_, i) => issue(45 + i, '35'))
      }),
      canQuery: true,
      canReadIssues: true
    })
    expect(result.pending).toEqual([tracking])
    expect(result.created.map((row) => row.id)).toEqual([36])
    expect(result.pending[0].issueId).toBeUndefined()
  })

  it('deduplicates existing task todos and keeps a separate assignee issue entry', () => {
    const pending = task(35, 'ANNOTATION_PENDING')
    const result = workbenchTaskRows({
      data: data({
        pendingTasks: [pending],
        myIssues: [issue(45, 35), issue(70, 40), issue(71, 40)]
      }),
      canQuery: true,
      canReadIssues: true
    })
    expect(result.pending).toEqual([
      pending,
      {
        id: 40,
        taskCode: 'INS-40',
        taskName: '任务40',
        issueId: 70
      }
    ])
    expect(result.pending[1].statusCode).toBeUndefined()
  })

  it('keeps issue-only users able to enter their authorized issue without exposing task-only data', () => {
    const result = workbenchTaskRows({
      data: data({
        pendingTasks: [task(1, 'PREPARING')],
        createdTasks: [task(35, 'TRACKING')],
        myIssues: [issue(45, 35), issue(46, 35), issue(50, 36, 'CLOSED')]
      }),
      canQuery: false,
      canReadIssues: true
    })
    expect(result.pending).toEqual([
      {
        id: 35,
        taskCode: 'INS-35',
        taskName: '任务35',
        issueId: 45
      }
    ])
    expect(result.created).toEqual([])
  })

  it('does not consume issues without permission and still shows task-owner tracking work', () => {
    const tracking = task(35, 'TRACKING')
    expect(
      workbenchTaskRows({
        data: data({ createdTasks: [tracking], myIssues: [issue(50, 40)] }),
        canQuery: true,
        canReadIssues: false
      }).pending
    ).toEqual([tracking])
    expect(workbenchTaskRows({ data: null, canQuery: true, canReadIssues: true })).toEqual({
      pending: [],
      created: []
    })
  })
})
