import dayjs from 'dayjs'
import type {
  StagePlanBatchVO,
  StagePlanItemVO,
  StagePlanTaskVO
} from '@/api/pms/engineering/stage-plan'

export const shortPlan = (plan?: StagePlanBatchVO) =>
  !!plan?.baselineStart &&
  !!plan.baselineEnd &&
  dayjs(plan.baselineEnd).isBefore(dayjs(plan.baselineStart).add(3, 'month'), 'day')

export const deadlineHint = (end?: string, today = dayjs().format('YYYY-MM-DD')) => {
  if (!end || !dayjs(end).isValid()) return ''
  const days = dayjs(end).startOf('day').diff(dayjs(today).startOf('day'), 'day')
  if (days < 0) return `本版本计划截止日已过 ${-days} 天，请核对当前执行计划。`
  if (days === 0) return '今天是本版本计划截止日，请确认任务执行与验收安排。'
  return dayjs(end).isBefore(dayjs(today).add(3, 'month'), 'day')
    ? `距本版本计划截止日还有 ${days} 天，不足3个日历月，请落实前序任务并确认发货安排。`
    : ''
}

export const taskPlanIssue = (task: StagePlanTaskVO, stage?: StagePlanItemVO) => {
  if (!task.planStart && !task.planEnd) return '待安排日期'
  if (!task.planStart || !task.planEnd) return '请同时填写开始和结束日期'
  if (!stage?.planStart || !stage.planEnd) return '阶段尚未排期'
  if (task.planEnd < task.planStart) return '任务结束早于开始'
  if (task.planStart < stage.planStart || task.planEnd > stage.planEnd)
    return '任务日期超出所属阶段'
  if (task.acceptanceTime && task.planEnd > task.acceptanceTime.slice(0, 10))
    return '任务结束晚于计划验收日期'
  return ''
}

export interface ScheduleRow {
  key: string
  kind: 'stage' | 'task'
  name: string
  stage: StagePlanItemVO
  task?: StagePlanTaskVO
  value: StagePlanItemVO | StagePlanTaskVO
  children?: ScheduleRow[]
}

/** Keep the actual objects as edit targets and retain ancestors when filtering. */
export const buildScheduleRows = (
  items: StagePlanItemVO[],
  tasks: StagePlanTaskVO[],
  keyword = ''
): ScheduleRow[] => {
  const term = keyword.trim().toLocaleLowerCase()
  const filter = (row: ScheduleRow): ScheduleRow | undefined => {
    if (!term || row.name.toLocaleLowerCase().includes(term)) return row
    const children = row.children?.map(filter).filter((value): value is ScheduleRow => !!value)
    return children?.length ? { ...row, children } : undefined
  }
  return items
    .map((stage) => {
      const scoped = tasks.filter((task) => task.stageCode === stage.phaseCode)
      const ids = new Set(scoped.map((task) => task.taskId)),
        visited = new Set<number>()
      const visit = (task: StagePlanTaskVO): ScheduleRow | undefined => {
        if (visited.has(task.taskId)) return undefined
        visited.add(task.taskId)
        const children = scoped
          .filter((child) => child.parentTaskId === task.taskId)
          .map(visit)
          .filter((value): value is ScheduleRow => !!value)
        return {
          key: `task-${task.taskId}`,
          kind: 'task',
          name: task.name,
          stage,
          task,
          value: task,
          children
        }
      }
      const children = scoped
        .filter((task) => !task.parentTaskId || !ids.has(task.parentTaskId))
        .map(visit)
        .filter((value): value is ScheduleRow => !!value)
      // Corrupt/missing parents must not silently hide a task from the plan.
      scoped.forEach((task) => {
        const row = visit(task)
        if (row) children.push(row)
      })
      return filter({
        key: `stage-${stage.phaseId ?? stage.phaseCode}`,
        kind: 'stage',
        name: stage.phaseName || stage.phaseCode || '未命名阶段',
        stage,
        value: stage,
        children
      })
    })
    .filter((value): value is ScheduleRow => !!value)
}

export const scheduleRowIssue = (row: ScheduleRow) => {
  if (row.task) return taskPlanIssue(row.task, row.stage)
  const { planStart, planEnd, suggestedEnd } = row.stage
  if (!planStart && !planEnd) return '待安排日期'
  if (!planStart || !planEnd) return '请同时填写开始和结束日期'
  if (planEnd < planStart) return '阶段结束早于开始'
  if (suggestedEnd && planEnd > suggestedEnd) return '晚于建议最迟完成时间'
  return ''
}

export const scheduleRange = (items: StagePlanItemVO[], tasks: StagePlanTaskVO[]) => {
  const dates = [...items, ...tasks]
    .flatMap((item) => [item.planStart, item.planEnd])
    .filter((value): value is string => !!value && dayjs(value).isValid())
    .sort()
  return { start: dates[0], end: dates.at(-1) }
}

export const timelineBar = (
  start: string | undefined,
  end: string | undefined,
  range: { start?: string; end?: string }
) => {
  if (!start || !end || !range.start || !range.end || end < start) return undefined
  const total = dayjs(range.end).diff(dayjs(range.start), 'day') + 1
  const offset = dayjs(start).diff(dayjs(range.start), 'day')
  const length = dayjs(end).diff(dayjs(start), 'day') + 1
  if (!Number.isFinite(total) || total <= 0 || offset < 0 || length <= 0 || offset + length > total)
    return undefined
  return { left: `${(offset / total) * 100}%`, width: `${(length / total) * 100}%` }
}

export const compareSchedules = (before: StagePlanBatchVO, after: StagePlanBatchVO) => {
  const entries = (plan: StagePlanBatchVO) =>
    new Map([
      ...plan.items.map(
        (item) =>
          [
            `stage-${item.phaseId}`,
            {
              name: item.phaseName || item.phaseCode || '阶段',
              start: item.planStart,
              end: item.planEnd
            }
          ] as const
      ),
      ...(plan.tasks || []).map(
        (task) =>
          [
            `task-${task.taskId}`,
            { name: task.name, start: task.planStart, end: task.planEnd }
          ] as const
      )
    ])
  const original = entries(before),
    revised = entries(after)
  const format = (value?: { start?: string; end?: string }) =>
    value ? `${value.start || '未安排'} 至 ${value.end || '未安排'}` : '不在此版本中'
  return [...new Set([...original.keys(), ...revised.keys()])].flatMap((key) => {
    const left = original.get(key),
      right = revised.get(key)
    if (left && right && left.start === right.start && left.end === right.end) return []
    return [
      {
        kind: key.startsWith('stage-') ? '阶段' : '任务',
        name: right?.name || left!.name,
        before: format(left),
        after: format(right),
        change: !left ? '新增' : !right ? '移出计划' : '日期调整'
      }
    ]
  })
}
