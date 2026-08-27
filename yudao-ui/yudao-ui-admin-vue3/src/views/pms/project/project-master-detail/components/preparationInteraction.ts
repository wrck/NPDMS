import type { FileArtifactVO, FileVersionVO } from '@/api/pms/platform/file'
import type {
  EvidenceReference,
  PatchPreparationItemReqVO
} from '@/api/pms/engineering/preparation'
import type { FileSelection } from '@/components/PmsFileArtifact'

export const sameValue = (left: unknown, right: unknown) =>
  JSON.stringify(left) === JSON.stringify(right)

export const setChanged = <K extends keyof PatchPreparationItemReqVO>(
  patch: PatchPreparationItemReqVO,
  key: K,
  current: PatchPreparationItemReqVO[K],
  baseline: PatchPreparationItemReqVO[K]
) => {
  if (!sameValue(current, baseline)) patch[key] = current
}

export const buildEvidenceReference = (
  selection: FileSelection,
  artifact: FileArtifactVO,
  version: FileVersionVO,
  frozenReferenceKey?: string
): EvidenceReference => ({
  artifactId: selection.artifactId,
  versionNo: selection.versionNo,
  referenceKey: frozenReferenceKey || selection.referenceKey,
  fileFactVersion: {
    artifactVersion: artifact.artifactVersion,
    referenceVersion: artifact.reference.referenceVersion,
    availabilityVersion: version.availabilityVersion
  },
  scopeVersion: artifact.reference.scopeVersion
})

export const createIntentKeyStore = (factory: () => string = () => crypto.randomUUID()) => {
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

export const intentOf = (action: string, payload: unknown) => `${action}:${JSON.stringify(payload)}`
