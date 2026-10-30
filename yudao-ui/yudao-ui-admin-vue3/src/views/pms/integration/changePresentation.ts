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
  const allFields = [...new Set([...Object.keys(before), ...Object.keys(after)])]
  // Generic tasks retain raw evidence alongside qualified target snapshots. Compare the targets only.
  const fields = Array.isArray(after._targets)
    ? allFields.filter((field) => field.includes('.'))
    : allFields
  return fields.map((field) => ({
    field,
    before: formatFieldValue(before[field]),
    after:
      after._previewVirtualIds &&
      field.endsWith('_id') &&
      typeof after[field] === 'number' &&
      after[field] < 0
        ? '待生成（预览）'
        : formatFieldValue(after[field]),
    changed: JSON.stringify(before[field]) !== JSON.stringify(after[field])
  }))
}
