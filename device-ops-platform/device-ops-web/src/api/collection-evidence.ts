import { deviceOpsApi } from '@/api/device-ops'
import type { CollectionEvidence, EvidenceLocation } from '@/types/collection-evidence'

export async function getCollectionEvidence(location: EvidenceLocation, signal: AbortSignal): Promise<CollectionEvidence> {
  const prefix = location.mode === 'project' && location.projectKey
    ? `projects/${encodeURIComponent(location.projectKey)}/` : ''
  const response = await deviceOpsApi.get<CollectionEvidence>(`${prefix}collections/${encodeURIComponent(location.collectionId)}/evidence`, {
    params: { namespace: location.namespace }, signal
  })
  return response.data
}
