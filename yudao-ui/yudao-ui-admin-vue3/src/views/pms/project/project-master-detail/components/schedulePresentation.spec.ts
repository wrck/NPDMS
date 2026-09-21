import { describe, it, expect } from 'vitest'
import {
  shortPlan,
  deadlineHint,
  taskPlanIssue,
  buildScheduleRows,
  scheduleRange,
  timelineBar,
  compareSchedules
} from './schedulePresentation'

describe('施工计划展示与安排检查', () => {
  it('区分短工期和临近截止，不把未来短工期说成即将到期', () => {
    expect(shortPlan({ baselineStart: '2027-06-01', baselineEnd: '2027-06-30', items: [] })).toBe(
      true
    )
    expect(deadlineHint('2027-06-30', '2026-09-20')).toBe('')
    expect(shortPlan({ baselineStart: '2026-01-01', baselineEnd: '2026-10-01', items: [] })).toBe(
      false
    )
    expect(deadlineHint('2026-10-01', '2026-09-20')).toContain('还有 11 天')
  })
  it('使用日历月边界，区分今日截止与过期', () => {
    expect(deadlineHint('2026-12-20', '2026-09-20')).toBe('')
    expect(deadlineHint('2026-09-20', '2026-09-20')).toContain('今天')
    expect(deadlineHint('2026-09-19', '2026-09-20')).toContain('已过 1 天')
  })
  it('允许尚未安排的草稿任务，识别半填、越界和晚于验收', () => {
    const task = { taskId: 1, stageCode: 'S4', name: '安装', version: 1 }
    const stage = { planStart: '2026-10-01', planEnd: '2026-10-31' }
    expect(taskPlanIssue(task, stage)).toBe('待安排日期')
    expect(taskPlanIssue({ ...task, planStart: '2026-10-01' }, stage)).toContain('同时填写')
    expect(
      taskPlanIssue({ ...task, planStart: '2026-09-30', planEnd: '2026-10-02' }, stage)
    ).toContain('超出')
    expect(
      taskPlanIssue(
        {
          ...task,
          planStart: '2026-10-01',
          planEnd: '2026-10-20',
          acceptanceTime: '2026-10-10T12:00:00'
        },
        stage
      )
    ).toContain('验收')
    expect(
      taskPlanIssue(
        {
          ...task,
          planStart: '2026-10-01',
          planEnd: '2026-10-10',
          acceptanceTime: '2026-10-10T12:00:00'
        },
        stage
      )
    ).toBe('')
  })
  it('保留深层任务及筛选祖先，编辑命中原任务而非展示副本', () => {
    const stage = { phaseId: 10, phaseCode: 'CUSTOM', phaseName: '实施' }
    const tasks = [
      { taskId: 1, stageCode: 'CUSTOM', name: '配置', version: 1 },
      { taskId: 2, parentTaskId: 1, stageCode: 'CUSTOM', name: '交换机', version: 1 },
      { taskId: 3, parentTaskId: 2, stageCode: 'CUSTOM', name: '核心验证', version: 1 }
    ]
    const rows = buildScheduleRows([stage], tasks, '核心')
    const leaf = rows[0].children![0].children![0].children![0]
    expect(leaf.task).toBe(tasks[2])
    leaf.value.planStart = '2026-10-01'
    expect(tasks[2]).toHaveProperty('planStart', '2026-10-01')
    expect(buildScheduleRows([stage], tasks, '不存在')).toEqual([])
  })
  it('父任务不存在或循环时仍展示所有任务，不无限递归', () => {
    const tasks = [
      { taskId: 1, parentTaskId: 2, stageCode: 'S', name: '一', version: 1 },
      { taskId: 2, parentTaskId: 1, stageCode: 'S', name: '二', version: 1 },
      { taskId: 3, parentTaskId: 99, stageCode: 'S', name: '三', version: 1 }
    ]
    const rows = buildScheduleRows([{ phaseId: 1, phaseCode: 'S' }], tasks)
    expect(rows[0].children).toHaveLength(2)
    expect(rows[0].children![1].children![0].task?.taskId).toBe(2)
  })
  it('时间轴按含首尾自然日呈现单日任务，范围覆盖越界任务便于检查', () => {
    const range = scheduleRange(
      [{ planStart: '2026-10-02', planEnd: '2026-10-10' }],
      [
        {
          taskId: 1,
          stageCode: 'S',
          name: '检查',
          version: 1,
          planStart: '2026-10-01',
          planEnd: '2026-10-01'
        }
      ]
    )
    expect(range).toEqual({ start: '2026-10-01', end: '2026-10-10' })
    expect(timelineBar('2026-10-01', '2026-10-01', range)).toEqual({ left: '0%', width: '10%' })
    expect(timelineBar('2026-10-05', '2026-10-02', range)).toBeUndefined()
  })
  it('版本对比区分修改、新增和移出，仅比较同一实体的日期', () => {
    const original = {
      items: [{ phaseId: 1, phaseName: '实施', planStart: '2026-10-01', planEnd: '2026-10-10' }],
      tasks: [{ taskId: 1, stageCode: 'S', name: '旧任务', version: 1 }]
    }
    const revised = {
      items: [{ ...original.items[0], planEnd: '2026-10-09' }],
      tasks: [{ taskId: 2, stageCode: 'S', name: '新任务', version: 1 }]
    }
    expect(compareSchedules(original, revised).map((row) => row.change)).toEqual([
      '日期调整',
      '移出计划',
      '新增'
    ])
    expect(compareSchedules(original, original)).toEqual([])
  })
})
