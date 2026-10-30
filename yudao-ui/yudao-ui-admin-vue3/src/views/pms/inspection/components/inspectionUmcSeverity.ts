import type { TagProps } from 'element-plus'
import type { ResultView } from '@/api/pms/inspection/tasks'

export const umcSeverityLabel = (row: ResultView) => {
  const level = row.externalRule?.level
  if (level === 3) return '致命'
  if (level === 2) return '重要'
  if (level === 1) return '一般'
  if (level != null) return '未知'
  return row.severityName && !row.severityName.startsWith('UMC') ? row.severityName : '未提供'
}

// 沿用巡检执行的级别颜色：致命 danger、重要 warning，其余 info。
export const umcSeverityType = (row: ResultView): TagProps['type'] => {
  const code =
    row.externalRule?.level == null ? row.severityCode : `UMC_LEVEL_${row.externalRule.level}`
  return code === 'FATAL' || code === 'UMC_LEVEL_3'
    ? 'danger'
    : code === 'SEVERE' || code === 'UMC_LEVEL_2'
      ? 'warning'
      : 'info'
}
