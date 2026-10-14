import type { Change } from '@/api/pms/integration'
import { formatDate } from '@/utils/formatTime'

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const isProjectContractResult = (change: Change) =>
  change.object === 'pm_project_contract' && isRecord(change.after?.relation)

export const deviceRelationshipOriginalFields = (change: Change) => {
  if (change.object !== 'fb_shipment_barcode_relation' || !isRecord(change.after?.originalFields)) return []
  return Object.entries(change.after.originalFields).map(([field, value]) => ({
    field,
    value: formatFieldValue(value)
  }))
}

export const dictionaryDataLabel = (change: Change): string | undefined => {
  const item = change.after?.item
  return change.object === 'fnd_basic_data' && isRecord(item) && typeof item.label === 'string'
    ? item.label
    : undefined
}

export const dictionaryDataOriginalFields = (change: Change) => {
  if (change.object !== 'fnd_basic_data' || !isRecord(change.after?.originalFields)) return []
  return Object.entries(change.after.originalFields).map(([field, value]) => ({
    field,
    value: formatFieldValue(value)
  }))
}

export const projectContractOriginalFields = (change: Change) => {
  if (!isProjectContractResult(change) || !isRecord(change.after?.originalFields)) return []
  return Object.entries(change.after.originalFields).map(([field, value]) => ({
    field,
    value: formatFieldValue(value)
  }))
}

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
  // This Owner returns the formal 17-column relationship alongside separate complete source evidence.
  const relation = change.after?.relation
  const item = change.after?.item
  const relationship = change.after?.relationship
  const contractResult = change.object === 'pm_project_contract' && isRecord(relation)
  const dictionaryResult = change.object === 'fnd_basic_data' && isRecord(item)
  const relationshipResult = change.object === 'fb_shipment_barcode_relation' && isRecord(relationship)
  const after = contractResult
    ? relation
    : dictionaryResult
      ? item
      : relationshipResult
        ? relationship
        : ['fnd_basic_data', 'fb_shipment_barcode_relation'].includes(change.object) && change.action === 'SKIPPED'
        ? {}
        : change.after || {}
  const present = (field: string, value: unknown) =>
    (contractResult || dictionaryResult || relationshipResult) &&
    ['createTime', 'updateTime', 'effectiveFrom', 'effectiveTo'].includes(field) &&
    typeof value === 'number'
      ? formatDate(value, 'YYYY-MM-DD HH:mm:ss.SSS')
      : formatFieldValue(value)
  const allFields = [...new Set([...Object.keys(before), ...Object.keys(after)])]
  // Generic tasks retain raw evidence alongside qualified target snapshots. Compare the targets only.
  const fields = Array.isArray(after._targets)
    ? allFields.filter((field) => field.includes('.'))
    : allFields
  return fields.map((field) => ({
    field,
    before: present(field, before[field]),
    after:
      after._previewVirtualIds &&
      field.endsWith('_id') &&
      typeof after[field] === 'number' &&
      after[field] < 0
        ? '待生成（预览）'
        : present(field, after[field]),
    changed: JSON.stringify(before[field]) !== JSON.stringify(after[field])
  }))
}
