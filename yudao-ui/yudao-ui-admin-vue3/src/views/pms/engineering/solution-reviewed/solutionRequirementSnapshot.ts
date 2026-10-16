import type { View } from '@/api/pms/engineering/requirement-analysis/entity'

export interface SolutionRequirementSnapshot {
  sourceRevisionRef: View['revision']['ref']
  sourceRevisionNo: number
  sourceRevisionVersion: number
  sourceRevisionState: View['revision']['state']
  sourceEffective: boolean
  values: Record<string, unknown>
}

const referenceFields = [
  'networkTopology', 'transmissionCurrentOptions', 'trafficNewConnections',
  'trafficConcurrency', 'trafficThroughput'
] as const

const envelope = (sectionData?: string | null): Record<string, unknown> => {
  try {
    const value = sectionData ? JSON.parse(sectionData) : null
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  } catch { return {} }
}

export const readSolutionRequirementSnapshot = (sectionData?: string | null): SolutionRequirementSnapshot | undefined => {
  const value = envelope(sectionData).requirementReference as SolutionRequirementSnapshot | undefined
  if (!value?.sourceRevisionRef?.revisionId || !value.sourceRevisionRef.entity
      || !value.values || typeof value.values !== 'object' || Array.isArray(value.values)) return undefined
  return value
}

export const freezeSolutionRequirementSnapshot = (sectionData: string | undefined | null, source?: View | null): string | undefined | null => {
  if (!source?.revision.ref) return sectionData
  const snapshot: SolutionRequirementSnapshot = {
    sourceRevisionRef: JSON.parse(JSON.stringify(source.revision.ref)),
    sourceRevisionNo: source.revision.revisionNo,
    sourceRevisionVersion: source.revision.version,
    sourceRevisionState: source.revision.state,
    sourceEffective: source.revision.effective,
    values: Object.fromEntries(referenceFields.filter(key => source.values[key] !== undefined)
      .map(key => [key, JSON.parse(JSON.stringify(source.values[key]))]))
  }
  return JSON.stringify({ ...envelope(sectionData), requirementReference: snapshot })
}
