import type { Change } from '@/api/pms/integration'

export const formatFieldValue = (value: unknown): string => {
  if (value === undefined) return '—'
  if (value === null) return 'NULL（空值）'
  if (value === '') return '空字符串'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

// Compare every returned field, including fields added by future adapters.
export const fieldChanges = (change: Change) => {
  const before = change.before || {}
  const after = change.after || {}
  return [...new Set([...Object.keys(before), ...Object.keys(after)])].map((field) => ({
    field,
    before: formatFieldValue(before[field]),
    after: formatFieldValue(after[field]),
    changed: JSON.stringify(before[field]) !== JSON.stringify(after[field])
  }))
}
