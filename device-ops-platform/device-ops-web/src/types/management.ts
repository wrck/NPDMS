import type { ParserCoordinate, ParseTaskState } from './parser'

export interface Page<T> { items: T[]; total: number; page: number; size: number }
export interface CollectionFilters { namespace?: string; project?: string; device?: string; status?: string; from?: string; to?: string; page?: number; size?: number }
export interface CollectionSummary {
  collectionId: string; namespace: string; projectKey: string | null; externalRequestId: string | null
  activityType: string; status: string | null; createdAt: string | null; targetCount: number
  scriptSource: string | null; scriptKey: string | null; scriptVersion: string | null; scriptSha256: string | null
}
export interface ScriptSummary { namespace: string; scriptKey: string; version: string; source: string; sha256: string; parserType: string; collectionId: string; contentReadable: boolean }
export interface ScriptContent { namespace: string; scriptKey: string; version: string; source: string; sha256: string; content: string }
export interface ManagementSettings {
  platformName: string; apiVersion: string; readOnly: true; authMode: 'local' | 'oauth2'; localDebug: boolean
  subject: string; allNamespaces: boolean; namespaces: string[]; projects: string[]; scopes: string[]; maxParserInputBytes: number
  capabilities: { scheduleEnabled: boolean; callbackEnabled: boolean; masterDataEnabled: boolean; telnetEnabled: boolean; serialEnabled: boolean; credentialStorageAvailable: boolean }
}
export interface LogType { logType: string; displayName: string; description: string | null; createdAt: string; updatedAt: string }
export interface ReleaseValidation { releaseId: string; draftRevision: number; passed: boolean; caseCount: number; failures: { caseId: string; errorCode: string; message: string }[]; validatedAt: string }
export interface ParserRelease { releaseId: string; logType: string; releaseVersion: string; state: 'DRAFT' | 'PUBLISHED' | 'DISABLED'; coordinate: ParserCoordinate; draftRevision: number; validation: ReleaseValidation | null; createdAt: string; publishedAt: string | null }
export interface ReleaseRequest {
  releaseId?: string
  manifest: { manifestVersion: string; logType: string; releaseVersion: string; inputAdapter: string; inputSchemaVersion: string; outputSchemaVersion: string; engineVersion: string; ruleVersion: string; projectionVersion: string; extension?: { extensionId: string; extensionVersion: string } | null }
  rulesJson?: string; projectionsJson: string; modelProfilesJson?: string; ruleSetJsonByPath?: Record<string, string>
  verificationCases: { caseId: string; inputContent: string; expectedResultJson: string }[]
}
export interface ParseTask { taskId: string; logType: string; releaseId: string; coordinate: ParserCoordinate; state: ParseTaskState; waitReason: string | null; attemptCount: number; nextAttemptAt: string | null; resultId: string | null; createdAt: string; updatedAt: string }
export interface RuntimeStatus { workerId: string; supportedCoordinates: { engineVersion: string; extensionId?: string; extensionVersion?: string }[]; executorActiveCount: number; executorQueuedCount: number; executorAvailableSlots: number; latestHeartbeatAt: string | null; waitingCounts: Record<string, number> }
