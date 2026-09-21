export type InputContentStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'RESTRICTED'
export interface EvidenceLocation {
  collectionId: string
  namespace: string
  mode: 'project' | 'generic'
  projectKey?: string
}
export interface CollectionEvidence {
  metadata: { collectionId: string; namespace: string; projectKey?: string | null; externalRequestId?: string | null; activityType?: string | null; createdAt: string | null }
  input: { source: string; key: string; version: string; policy: string; parserType: string; sha256: string; contentStatus: 'AVAILABLE' | 'UNAVAILABLE'; content: string | null }
  submission: { provenance: 'CAPTURED_SUBMISSION' | 'RECONSTRUCTED_FACTS'; snapshot: Record<string, unknown> | null; omittedFields: string[] }
  executionFacts: { targets: Array<{ targetId: number; deviceKey?: string | null; protocol: string; host: string; port: number; username: string; hostKeyFingerprint?: string | null; status: string }>; semanticParsing: { logType: string; releaseId: string; inputFormat: string; resultConsumerId: string } | null }
}
