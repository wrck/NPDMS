import type { ChecklistFileHandle } from '@/api/pms/cutover'

export const manualEvidenceFiles = (result?: { resultSourceCode: string; answerSnapshot: string } | null): ChecklistFileHandle[] => {
  if (result?.resultSourceCode !== 'MANUAL') return []
  try {
    const value = JSON.parse(result.answerSnapshot)
    const files = Array.isArray(value?.files) ? value.files : [value?.file || value]
    return files.filter((file: ChecklistFileHandle) => file?.artifactId && file.versionNo && file.referenceKey)
  } catch { return [] }
}
