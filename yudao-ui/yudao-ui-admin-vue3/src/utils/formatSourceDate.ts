import { formatDate } from '@/utils/formatTime'

const dateFields = new Set(['warrantyStartDate', 'warrantyEndDate', 'executeTime'])
const timestampFields = new Set([
  'createTime',
  'updateTime',
  'orderCreateTime',
  'customerRequiredTime',
  'projectStartTime',
  'projectRefreshTime',
  'projectCloseTime',
  'closedAt',
  'sourceEffectiveFrom',
  'sourceEffectiveTo',
  'sourceProjectStartTime',
  'sourceProjectRefreshTime',
  'sourceProjectCloseTime',
  'processTime',
  'begin_date',
  'end_date',
  'updatetime',
  'sourceShipmentTime',
  'sourceReadAt',
  'sourceUpdatedTime',
  'submitTime',
  'predictedBidTime',
  'shipmentTime',
  'syncTime',
  'sourceSyncTime',
  'sourceUpdatedAt',
  'sourceOrderCreateTime',
  'sourceCustomerRequiredTime',
  'originalSourceSyncTime',
  'sourceCreatedAt',
  'effectiveFrom',
  'effectiveTo',
  'eventPlannedAt',
  'eventActualFinishedAt',
  'warrantyStartTime',
  'warrantyEndTime',
  'sourceWarrantyStartTime',
  'sourceWarrantyEndTime',
  'originalWarrantyStartTime',
  'originalWarrantyEndTime'
])

/** Native date APIs may return epoch milliseconds; quantities and empty values keep their meaning. */
export function formatSourceDate(value: unknown, field: string): unknown {
  if (value == null || value === '' || (typeof value !== 'string' && typeof value !== 'number'))
    return value
  if (!dateFields.has(field) && !timestampFields.has(field)) return value
  const formatted = formatDate(
    value,
    dateFields.has(field) ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss.SSS'
  )
  return !formatted || formatted === 'Invalid Date' ? value : formatted
}
