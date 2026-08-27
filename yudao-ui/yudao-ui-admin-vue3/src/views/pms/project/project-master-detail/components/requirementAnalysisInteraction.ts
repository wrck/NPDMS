import type { FileArtifactVO, FileVersionVO } from '@/api/pms/platform/file'
import type {
  PatchRequirementAnalysisSectionReqVO,
  RequirementAnalysisAttachmentVO,
  RequirementAnalysisAttachmentSyncStatus,
  RequirementAnalysisOptionVO,
  RequirementAnalysisSectionVO
} from '@/api/pms/engineering/requirement-analysis'
import type { FileSelection } from '@/components/PmsFileArtifact'

export const sameRequirementValue = (left: unknown, right: unknown) =>
  JSON.stringify(left) === JSON.stringify(right)

export const containsEmbeddedMedia = (html: string) =>
  /<(?:img|video|audio|source|picture|iframe|object|embed)\b/i.test(html)

export interface RequirementAnalysisSectionEditState {
  sectionId: number
  bodyDirty: boolean
  attachmentSyncStatus: RequirementAnalysisAttachmentSyncStatus
  guardsNavigation: boolean
}

export type RequirementAnalysisTransitionDecision =
  | 'ALLOW'
  | 'SAVE_ATTACHMENT_SET'
  | 'CONFIRM_DISCARD_BODY'
  | 'BLOCK_UNKNOWN_ATTACHMENT_FACTS'

export const requirementAnalysisTransitionDecision = (
  state: RequirementAnalysisSectionEditState
): RequirementAnalysisTransitionDecision => {
  if (state.guardsNavigation && state.attachmentSyncStatus === 'UNKNOWN') {
    return 'BLOCK_UNKNOWN_ATTACHMENT_FACTS'
  }
  if (state.guardsNavigation && state.attachmentSyncStatus === 'PENDING') {
    return 'SAVE_ATTACHMENT_SET'
  }
  return state.bodyDirty ? 'CONFIRM_DISCARD_BODY' : 'ALLOW'
}

export const parseSectionOptions = (section: RequirementAnalysisSectionVO) => {
  try {
    const schema = JSON.parse(section.schemaSnapshot || '{}') as {
      optionSnapshot?: RequirementAnalysisOptionVO[]
      options?: RequirementAnalysisOptionVO[]
    }
    return schema.optionSnapshot || schema.options || []
  } catch {
    return []
  }
}

export const parseSectionValue = (section: RequirementAnalysisSectionVO): any => {
  const fallback = section.fieldType === 'MULTI_SELECT' ? [] : null
  if (
    section.valueSnapshot === null ||
    section.valueSnapshot === undefined ||
    section.valueSnapshot === ''
  ) {
    return fallback
  }
  try {
    return JSON.parse(section.valueSnapshot)
  } catch {
    return section.valueSnapshot
  }
}

export type AttachmentIntentRecovery =
  | 'NONE'
  | 'CONFIRMED'
  | 'RETRY'
  | 'CONFLICT'

const attachmentVector = (attachments: RequirementAnalysisAttachmentVO[]) =>
  attachments
    .map(({ artifactId, versionNo, referenceKey, fileFactVersion, scopeVersion }) => ({
      artifactId,
      versionNo,
      referenceKey,
      fileFactVersion,
      scopeVersion
    }))
    .sort((left, right) => left.referenceKey.localeCompare(right.referenceKey))

export const resolveAttachmentIntentRecovery = (
  savedSnapshot: RequirementAnalysisAttachmentVO[],
  currentActiveFacts: RequirementAnalysisAttachmentVO[] | null,
  retainedTarget: RequirementAnalysisAttachmentVO[] | null
): AttachmentIntentRecovery => {
  if (!retainedTarget) return 'NONE'
  if (sameRequirementValue(attachmentVector(savedSnapshot), attachmentVector(retainedTarget))) {
    return 'CONFIRMED'
  }
  if (
    currentActiveFacts !== null &&
    sameRequirementValue(attachmentVector(currentActiveFacts), attachmentVector(retainedTarget))
  ) {
    return 'RETRY'
  }
  return 'CONFLICT'
}

export const buildSectionPatch = (
  expectedPreparationVersion: number,
  expectedContentVersion: number,
  expectedProjectVersion: number,
  currentValue: unknown,
  baselineValue: unknown,
  currentAttachments: RequirementAnalysisAttachmentVO[],
  baselineAttachments: RequirementAnalysisAttachmentVO[],
  forceAttachmentSubmit = false
) => {
  const patch: PatchRequirementAnalysisSectionReqVO = {
    submittedFields: [],
    expectedPreparationVersion,
    expectedContentVersion,
    expectedProjectVersion
  }
  if (!sameRequirementValue(currentValue, baselineValue)) {
    patch.submittedFields.push('value')
    patch.value = currentValue
  }
  const toSubmittedAttachment = ({
    artifactId,
    versionNo,
    referenceKey,
    fileFactVersion,
    scopeVersion
  }: RequirementAnalysisAttachmentVO) => ({
    artifactId,
    versionNo,
    referenceKey,
    fileFactVersion,
    scopeVersion
  })
  const currentAttachmentVector = currentAttachments
    .map(toSubmittedAttachment)
    .sort((left, right) => left.referenceKey.localeCompare(right.referenceKey))
  const baselineAttachmentVector = baselineAttachments
    .map(toSubmittedAttachment)
    .sort((left, right) => left.referenceKey.localeCompare(right.referenceKey))
  if (
    forceAttachmentSubmit ||
    !sameRequirementValue(currentAttachmentVector, baselineAttachmentVector)
  ) {
    patch.submittedFields.push('attachments')
    patch.attachments = currentAttachmentVector
  }
  return patch
}

export const buildRequirementAttachment = (
  selection: FileSelection,
  artifact: FileArtifactVO,
  version: FileVersionVO,
  frozenReferenceKey?: string
): RequirementAnalysisAttachmentVO => ({
  artifactId: selection.artifactId,
  versionNo: selection.versionNo,
  referenceId: selection.referenceId,
  referenceKey: frozenReferenceKey || selection.referenceKey,
  name: artifact.name,
  fileFactVersion: {
    artifactVersion: artifact.artifactVersion,
    referenceVersion: artifact.reference.referenceVersion,
    availabilityVersion: version.availabilityVersion
  },
  scopeVersion: artifact.reference.scopeVersion
})

export const createRequirementIntentStore = (factory: () => string = () => crypto.randomUUID()) => {
  const keys = new Map<string, string>()
  return {
    key(intent: string) {
      const existing = keys.get(intent)
      if (existing) return existing
      const created = factory()
      keys.set(intent, created)
      return created
    },
    complete(intent: string) {
      keys.delete(intent)
    }
  }
}

export const requirementIntentOf = (action: string, payload: unknown) =>
  `${action}:${JSON.stringify(payload)}`

export const requirementAnalysisLayout = (width: number) => {
  if (width <= 767) return 'mobile'
  if (width <= 1023) return 'tablet'
  return 'desktop'
}

export const patchRequirementSectionAndReload = async (
  patch: () => Promise<unknown>,
  reload: () => Promise<void>
) => {
  await patch()
  await reload()
}
