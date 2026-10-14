import type { ProjectEquipmentProduct } from '@/api/pms/commerce/projectEquipment'

export type EquipmentRow = Record<string, string>
export const equipmentRow = (product?: ProjectEquipmentProduct): EquipmentRow => ({
  productCode: product?.productCode || '', deviceName: product?.productName || product?.productModel || '',
  quantity: product?.orderQty == null ? '' : String(product.orderQty), location: '', version: '', remark: ''
})

/** Keep each chapter's stored rows independent; never combine deployment and version remarks. */
export const readSolutionEquipment = ({ topology, section }: {
  topology: Record<string, unknown>; section: 'deploy' | 'software'
}): EquipmentRow[] => {
  const rows = Array.isArray(topology[section]) ? topology[section] as EquipmentRow[] : []
  const source = rows.length || !Array.isArray(topology.equipmentList) ? rows : topology.equipmentList as EquipmentRow[]
  return source.map(row => ({ ...equipmentRow(), ...row, deviceName: row.deviceName || row.model || '' }))
}
