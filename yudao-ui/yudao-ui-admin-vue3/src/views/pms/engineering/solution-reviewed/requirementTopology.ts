import type { View } from '@/api/pms/engineering/requirement-analysis/entity'
import { getPreviewContent } from '@/api/pms/platform/file'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

export const requirementTopologyPurpose = 'FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS'
export const requirementTopologyFiles = (source: View | null) => source?.attachments
  ?.filter(set => set.key.purposeCode === requirementTopologyPurpose)
  .flatMap(set => set.activeFacts)
  .filter(file => file.referenceStatus === 'ACTIVE') || []

/** Read the exact frozen source reference. A failed page must fail the export, not silently disappear. */
export const requirementTopologyImages = async (source: View): Promise<string[]> => {
  const files = requirementTopologyFiles(source)
  if (!files.length) throw new Error('所引用需求版本暂无有效拓扑附件')
  if (files.some(file => file.availabilityStatus !== 'AVAILABLE'))
    throw new Error('所引用需求版本存在不可用拓扑附件，请核对完整来源后再导出')
  const images: string[] = []
  for (const file of files) {
    const blob = await getPreviewContent(file.artifactId, file.versionNo, {
      ownerContext: 'SOL', objectType: 'REQUIREMENT_ANALYSIS_REVISION',
      objectId: String(source.revision.ref.revisionId), purposeCode: requirementTopologyPurpose,
      referenceKey: file.referenceKey
    })
    if (blob.type.startsWith('image/')) {
      const { Buffer } = await import('buffer/')
      images.push(`data:${blob.type};base64,${Buffer.from(await blob.arrayBuffer()).toString('base64')}`)
    } else if (blob.type.split(';')[0] === 'application/pdf') {
      const pdfjs = await import('pdfjs-dist')
      pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
      const loading = pdfjs.getDocument({ data: new Uint8Array(await blob.arrayBuffer()), stopAtErrors: true })
      try {
        const pdf = await loading.promise
        for (let number = 1; number <= pdf.numPages; number++) {
          const page = await pdf.getPage(number)
          const unit = page.getViewport({ scale: 1 })
          const viewport = page.getViewport({ scale: Math.min(2, 1600 / Math.max(unit.width, unit.height)) })
          const canvas = document.createElement('canvas')
          canvas.width = Math.ceil(viewport.width)
          canvas.height = Math.ceil(viewport.height)
          await page.render({ canvas, viewport }).promise
          images.push(canvas.toDataURL('image/png'))
          page.cleanup()
          canvas.width = canvas.height = 0
        }
      } finally { await loading.destroy() }
    } else throw new Error('该拓扑附件暂不支持图形导出，请使用图片、PDF或Visio文件')
  }
  return images
}
