import { describe, it, expect } from 'vitest'
import {
  shortPlan,
  deadlineHint,
  taskPlanIssue,
  stageItemIssue,
  fitTaskPlanIntoStage,
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
  it('阶段区间更新时把越界任务平移回区间并保持天数，超长收紧、无窗口与倒挂窗口不动', () => {
    const stage = { planStart: '2026-07-01', planEnd: '2026-07-31' }
    const make = (planStart: string, planEnd: string) => ({
      taskId: 1,
      stageCode: 'S1',
      name: '任务',
      version: 1,
      planStart,
      planEnd
    })
    // 结束越界：平移到区间尾并保持天数
    const late = make('2026-09-01', '2026-09-08')
    expect(fitTaskPlanIntoStage(late, stage)).toBe(true)
    expect(late.planStart).toBe('2026-07-24')
    expect(late.planEnd).toBe('2026-07-31')
    // 整体早于区间：平移到区间头并保持天数
    const early = make('2026-05-25', '2026-05-29')
    expect(fitTaskPlanIntoStage(early, stage)).toBe(true)
    expect(early.planStart).toBe('2026-07-01')
    expect(early.planEnd).toBe('2026-07-05')
    // 跨越且超长：收紧为整个区间
    const spanning = make('2026-06-20', '2026-08-10')
    expect(fitTaskPlanIntoStage(spanning, stage)).toBe(true)
    expect(spanning.planStart).toBe('2026-07-01')
    expect(spanning.planEnd).toBe('2026-07-31')
    // 区间内任务、未安排、阶段无日期、倒挂窗口均不动
    const inside = make('2026-07-02', '2026-07-04')
    expect(fitTaskPlanIntoStage(inside, stage)).toBe(false)
    expect(inside.planStart).toBe('2026-07-02')
    expect(fitTaskPlanIntoStage({ ...make('2026-07-02', '2026-07-04'), planStart: undefined!, planEnd: undefined! }, stage)).toBe(false)
    expect(fitTaskPlanIntoStage(make('2026-07-02', '2026-07-04'), undefined!)).toBe(false)
    expect(fitTaskPlanIntoStage(make('2026-07-02', '2026-07-04'), { planStart: '2026-07-31', planEnd: '2026-07-01' })).toBe(false)
  })
  it('阶段安排检查与后端提交校验同规则同文案：倒挂、基线窗口、串行边、计划验收', () => {
    const stage = (planStart?: string, planEnd?: string) => ({
      phaseId: 2,
      phaseCode: 'S4',
      phaseName: '实施部署',
      planStart,
      planEnd
    })
    const plan = {
      baselineStart: '2026-04-05',
      baselineEnd: '2026-10-31',
      inputSnapshot: JSON.stringify({
        serialEdges: [{ fromStageId: 1, toStageId: 2 }],
        // 合同计划验收晚于工期结束是常态：实施应尽早完成，越早于验收越好
        stages: [{ stageId: 2, stageCode: 'S4', acceptanceTime: '2026-12-02T00:00:00' }]
      }),
      items: [
        { phaseId: 1, phaseCode: 'S2', phaseName: '施工计划制定与审批', planStart: '2026-09-08', planEnd: '2026-09-10' },
        stage('2026-09-11', '2026-10-31')
      ]
    }
    expect(stageItemIssue(stage('2026-09-11', '2026-10-31') as any, plan as any)).toBe('')
    expect(stageItemIssue(stage() as any, plan as any)).toBe('待安排日期')
    expect(stageItemIssue({ ...stage('2026-10-01') } as any, plan as any)).toBe('请同时填写开始和结束日期')
    expect(stageItemIssue(stage('2026-10-10', '2026-10-01') as any, plan as any)).toBe(
      '计划结束时间早于计划开始时间'
    )
    expect(stageItemIssue(stage('2026-04-01', '2026-05-01') as any, plan as any)).toBe(
      '计划时间超出工期基线窗口（2026-04-05 ~ 2026-10-31）'
    )
    // 晚于工期结束但早于合同验收：只报基线窗口，不报验收（与后端先基线后验收的顺序一致）
    expect(stageItemIssue(stage('2026-11-05', '2026-11-20') as any, plan as any)).toBe(
      '计划时间超出工期基线窗口（2026-04-05 ~ 2026-10-31）'
    )
    // 串行边：开始早于前驱结束报后端同文案，当天衔接合法
    expect(stageItemIssue(stage('2026-09-09', '2026-09-20') as any, plan as any)).toBe(
      '计划开始不得早于前一阶段【施工计划制定与审批】计划结束'
    )
    expect(stageItemIssue(stage('2026-09-10', '2026-09-20') as any, plan as any)).toBe('')
    // 前驱未排期时跳过串行边，不误报
    const noPrev = { ...plan, items: [{ ...plan.items[0], planStart: undefined!, planEnd: undefined! }, stage('2026-09-09', '2026-09-20')] }
    expect(stageItemIssue(stage('2026-09-09', '2026-09-20') as any, noPrev as any)).toBe('')
    // 验收时间落在工期内（如阶段初验）时，晚于它才报验收；早于验收结束不报
    const midAcceptance = {
      ...plan,
      inputSnapshot: JSON.stringify({
        serialEdges: [{ fromStageId: 1, toStageId: 2 }],
        stages: [{ stageId: 2, stageCode: 'S4', acceptanceTime: '2026-10-20T00:00:00' }]
      })
    }
    expect(stageItemIssue(stage('2026-10-01', '2026-10-25') as any, midAcceptance as any)).toBe(
      '计划结束不得晚于计划验收时间'
    )
    expect(stageItemIssue(stage('2026-10-01', '2026-10-20') as any, midAcceptance as any)).toBe('')
    // 无快照或坏快照只做基础检查，不报次序与验收
    expect(stageItemIssue(stage('2026-09-09', '2026-09-20') as any, undefined as any)).toBe('')
    expect(
      stageItemIssue(stage('2026-09-09', '2026-09-20') as any, { ...plan, inputSnapshot: '{bad' } as any)
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
