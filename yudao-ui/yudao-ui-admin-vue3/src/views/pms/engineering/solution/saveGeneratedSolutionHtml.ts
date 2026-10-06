import { getDeliveryTypes } from '@/api/pms/platform/delivery'
import { createDeliveryUploadAttempt, type DeliveryUploadAttempt } from '@/components/DeliveryArtifact/uploadDeliveryFile'

/** Both existing HTML output operations use the same explicit save-before-download boundary. */
export const prepareGeneratedSolutionHtml = async (entityId: number, html: string, fileName: string,
  previous?: DeliveryUploadAttempt): Promise<DeliveryUploadAttempt> => {
  if (!entityId) throw new Error('请先保存实施方案，再生成交付件')
  const type = (await getDeliveryTypes(true)).find(item => item.typeCode === 'IMPLEMENTATION_PLAN')
  if (!type) throw new Error('实施方案交付件类型不可用')
  const attempt = previous || createDeliveryUploadAttempt({ ownerModule: 'SOL', entityType: 'solution', entityId },
    type, new File([html], fileName, { type: 'text/html' }), fileName, 'GENERATED')
  return attempt
}
