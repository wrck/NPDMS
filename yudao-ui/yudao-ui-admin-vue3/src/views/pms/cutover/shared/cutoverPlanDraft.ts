import type { CutoverPlanFileFact, CutoverPlanSourceSnapshot, WireDateTime, WritableCutoverPlanContent } from '@/api/pms/cutover'

// The editor can hold an incomplete upload; the business API still requires a file and confirmation.
export type CutoverPlanDraftContent = Exclude<WritableCutoverPlanContent, { editMode: 'FULL_FILE_UPLOAD' }> | {
  editMode: 'FULL_FILE_UPLOAD'
  scheduledTime?: WireDateTime | null
  fileArtifactFact: CutoverPlanFileFact | null
  ownershipConfirmed: boolean
}

export const initialOnlinePlan = (source: CutoverPlanSourceSnapshot): CutoverPlanDraftContent => source.grade === 'D'
  ? { editMode: 'ONLINE_TEMPLATE_SIMPLE_D', steps: [] }
  : { editMode: 'ONLINE_TEMPLATE_STANDARD', overview: {
    projectDescription: '', scheduleTable: [], preTopologyFile: null, postTopologyFile: null,
    networkConfigurationFile: null, deviceSummary: JSON.parse(JSON.stringify(source.devices))
  }, steps: [], riskMitigations: [], supportArrangements: [] }
