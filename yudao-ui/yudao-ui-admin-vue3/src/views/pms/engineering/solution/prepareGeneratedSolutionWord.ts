import { getDeliveryTypes } from '@/api/pms/platform/delivery'
import { createDeliveryUploadAttempt, type DeliveryUploadAttempt } from '@/components/DeliveryArtifact/uploadDeliveryFile'
import { formatSolutionInventoryWord } from './formatSolutionInventoryWord'

export const prepareGeneratedSolutionWord = async (entityId: string | number, html: string, fileName: string,
  previous?: DeliveryUploadAttempt): Promise<DeliveryUploadAttempt> => {
  if (!entityId) throw new Error('请先保存实施方案，再导出 Word')
  if (previous) return previous
  const type = (await getDeliveryTypes(true)).find(item => item.typeCode === 'IMPLEMENTATION_PLAN')
  if (!type) throw new Error('实施方案交付件类型不可用')
  // Browser globals required by the converter's documented standalone build.
  const runtime = globalThis as unknown as { global?: unknown; process?: unknown; Buffer?: unknown }
  const { Buffer } = await import('buffer/')
  runtime.global ??= globalThis
  runtime.process ??= { env: {} }
  runtime.Buffer ??= Buffer
  const { default: htmlToDocx } = await import('@turbodocx/html-to-docx')
  const content = await htmlToDocx(html, undefined, {
    pageSize: { width: 11906, height: 16838 },
    margins: { top: 1440, right: 1134, bottom: 1440, left: 1134 },
    font: 'SimSun', fontSize: 24, lang: 'zh-CN',
    table: { row: { cantSplit: true } },
    imageProcessing: { svgHandling: 'native' }
  })
  const binary = content instanceof Blob || content instanceof ArrayBuffer ? content : new Uint8Array(content).buffer
  const file = new File([await formatSolutionInventoryWord(binary)], fileName, { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
  return createDeliveryUploadAttempt({ ownerModule: 'SOL', entityType: 'solution', entityId }, type, file, fileName, 'GENERATED')
}
