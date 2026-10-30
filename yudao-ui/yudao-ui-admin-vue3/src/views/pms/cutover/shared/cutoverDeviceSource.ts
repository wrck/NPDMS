import type { CutoverChecklistItem, DeviceScopeEntry } from '@/api/pms/cutover'
import type { DeviceCurrentSummaryVO } from '@/api/pms/asset/device'
import { locationSnapshotText } from '@/utils/locationSnapshot'

export const hasTaskDeviceRows = (item: CutoverChecklistItem) =>
  ['SURVEY_DEVICE_LOCATION_PLAN', 'SURVEY_PERFORMANCE_BASELINE'].includes(item.stableItemKey)

const nameFields: Record<string, string> = {
  SURVEY_INTERFACE_INTERCONNECT_PLAN: 'localDevice',
  SURVEY_IP_VLAN_PLAN: 'deviceName'
}
const deviceFields = (device?: DeviceCurrentSummaryVO): Record<string, string | undefined> => ({
  deviceName: device?.name,
  localDevice: device?.name,
  location: locationSnapshotText(device?.locationSnapshot),
  productCode: device?.productCode,
  productModel: device?.productModel,
  productName: device?.productName,
  currentSoftwareVersion: device?.conpVersion,
  currentVersion: device?.conpVersion
})
const blank = (value: unknown) => value == null || typeof value === 'string' && !value.trim()

export const needsDeviceListFields = (input: { item: CutoverChecklistItem; value?: string }) => {
  const { item } = input
  if (!item.applicable || item.workModeCode !== 'DIRECT') return false
  try {
    const columns: Array<{ code: string }> = JSON.parse(item.interfaceSchemaSnapshot || '{}')?.columns || []
    const fields = columns.filter(column => Object.prototype.hasOwnProperty.call(deviceFields(), column.code))
    if (!fields.length) return false
    const value = input.value ? JSON.parse(input.value) : null
    const rows: Array<Record<string, unknown>> = Array.isArray(value) ? value : Array.isArray(value?.rows) ? value.rows : []
    if (nameFields[item.stableItemKey]) return !rows.length || rows.every(row => Object.values(row || {}).every(blank))
    return columns.some(column => column.code === 'serialNumber')
      && rows.some(row => !blank(row.serialNumber) && fields.some(field => blank(row[field.code])))
  } catch { return false }
}

/** Mutable survey rows use the task's fixed device scope; submitted results are never passed here. */
export const rebuildTaskDeviceAnswer = (input: {
  item: CutoverChecklistItem
  value?: string
  devices: DeviceScopeEntry[]
  deviceList?: Record<string, DeviceCurrentSummaryVO>
}) => {
  if (!input.item.applicable || input.item.workModeCode !== 'DIRECT'
    || (input.item.currentResult && input.item.currentResult.resultSourceCode !== 'DIRECT')) return input.value
  let schema: { columns?: Array<{ code: string }> }
  let value: any = null
  try {
    schema = JSON.parse(input.item.interfaceSchemaSnapshot || '{}')
    if (input.value) value = JSON.parse(input.value)
  } catch { return input.value }
  if (!schema?.columns?.length) return input.value
  const fixedRows = hasTaskDeviceRows(input.item)
  const serialTable = schema.columns.some(column => column.code === 'serialNumber')
  const nameField = nameFields[input.item.stableItemKey]
  if (!serialTable && !nameField) return input.value
  const rows: Array<Record<string, any>> = Array.isArray(value) ? value : Array.isArray(value?.rows) ? value.rows : []
  const metadata = (device: DeviceScopeEntry) => {
    const row = input.deviceList?.[String(device.deviceId)]
    return row && String(row.deviceId) === String(device.deviceId) && row.sn === device.serialNumber ? row : undefined
  }
  const fill = (row: Record<string, any>, device?: DeviceScopeEntry) => {
    if (!device) return row
    const fields = deviceFields(metadata(device))
    const next = { ...row }
    for (const column of schema.columns || []) {
      if (blank(next[column.code]) && !blank(fields[column.code])) next[column.code] = fields[column.code]
    }
    return next
  }
  const next = fixedRows && serialTable ? input.devices.map(device => fill({
    ...rows.find(row => row && typeof row.serialNumber === 'string' && row.serialNumber.trim() === device.serialNumber),
    serialNumber: device.serialNumber
  }, device)) : nameField && rows.every(row => Object.values(row || {}).every(blank))
    ? input.devices.filter(device => !blank(metadata(device)?.name)).map(device => fill({}, device))
    : rows.map(row => fill(row, input.devices.find(device => serialTable ? row?.serialNumber === device.serialNumber
      : row?.[nameField] === metadata(device)?.name || row?.[nameField] === device.serialNumber)))
  if (!fixedRows && !next.length) return input.value
  return JSON.stringify(value && !Array.isArray(value) && Array.isArray(value.rows) ? { ...value, rows: next } : next)
}
