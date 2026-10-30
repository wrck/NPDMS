import * as DeliveryApi from '@/api/pms/platform/delivery'
import type { DeliverableSummaryItemVO } from '@/api/pms/engineering/deliverable'

export const resolveSummaryPreview = async (row: DeliverableSummaryItemVO) => {
  const locator = row.materialLocator
  if (!locator) throw new Error('缺少材料来源')
  const materials = await DeliveryApi.listMaterials(locator.ownerModule, locator.entityType, locator.entityId, locator.typeCode)
  const material = materials.find(item => String(item.id) === String(locator.materialId))
  if (!material || material.status !== 'ACTIVE' || material.materialKind !== 'FILE'
    || material.fileArtifactId == null || material.fileVersionNo == null || !material.fileBusinessKey) {
    throw new Error('文件已变化或不可预览')
  }
  return material as typeof material & Required<Pick<typeof material, 'fileArtifactId' | 'fileVersionNo' | 'fileBusinessKey'>>
}
