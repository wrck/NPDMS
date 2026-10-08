// 迁移工具台客户端：批次台账、只读预演与迁移证据Owner合同（暂存/认领/对账/问题结案）。
// 全部按服务端契约调用；幂等键与并发依据由调用方携带，租户由服务端上下文确定。
import request from '@/config/axios'

const baseUrl = '/api/v1/pms/migration'

export interface MigrationBatchVO {
  id: number
  ownerContextCode: string
  purposeCode: string
  releaseId: string
  sourceSystem: string
  sourceTable: string
  manifestSchemaVersion: string
  expectedRowCount: number
  contentSha256: string
  exportedAt: string
  previousBatchId?: number
  previousIssueId?: number
  status: 'IMPORTING' | 'STAGED_READY' | 'RECONCILING' | 'COMPLETED' | 'FAILED'
  sourceCount: number
  mappedCount: number
  issueCount: number
  retainedCount: number
  failureCode?: string
  ruleVersion?: string
  version: number
  createTime: string
}

export interface MigrationSourceRecordVO {
  sourceRecordId: number
  batchId: number
  sourceSystem: string
  sourceTable: string
  sourcePk: string
  sourceBusinessKey?: string
  sourcePayloadJson: string
  sourceChecksum: string
  extractedAt: string
  resultType?: string
}

export interface PreviewRecordInput {
  sourcePk: string
  sourceChecksum?: string
}

export interface PreviewTargetVO {
  resultType: string
  targetContext: string
  targetObjectType: string
  targetTable: string
  targetId: number
  targetRole: string
  targetSequence: number
  resultKey?: string
}

export interface PreviewItemVO {
  sourcePk: string
  classification:
    | 'NO_EXISTING_SOURCE'
    | 'CHECKSUM_CONFLICT'
    | 'UNMAPPED'
    | 'RETAINED'
    | 'MAPPED'
    | 'AMBIGUOUS'
  latestSourceRecordId?: number
  latestBatchId?: number
  latestSourceChecksum?: string
  targets: PreviewTargetVO[]
}

export interface PreviewResultVO {
  sourceSystem: string
  sourceTable: string
  items: PreviewItemVO[]
  noExistingSourceCount: number
  checksumConflictCount: number
  unmappedCount: number
  retainedCount: number
  mappedCount: number
  ambiguousCount: number
}

// ========== 只读 ==========

export const getMigrationBatchPage = (params: {
  pageNo: number
  pageSize: number
  ownerContextCode?: string
  purposeCode?: string
  sourceSystem?: string
  status?: string
}) => request.get<{ list: MigrationBatchVO[]; total: number }>({ url: `${baseUrl}/batches/page`, params })

export const getMigrationSourceRecords = (batchId: number, afterSourceRecordId?: number, limit = 100) =>
  request.get<{ records: MigrationSourceRecordVO[]; nextAfterSourceRecordId?: number }>({
    url: `${baseUrl}/batches/${batchId}/source-records`,
    params: { afterSourceRecordId, limit }
  })

export const migrationPreview = (data: {
  sourceSystem: string
  sourceTable: string
  records: PreviewRecordInput[]
}) => request.post<PreviewResultVO>({ url: `${baseUrl}/preview`, data })

// ========== 暂存阶段 ==========

export interface BatchCreateInput {
  ownerContextCode: string
  purposeCode: string
  releaseId: string
  sourceSystem: string
  sourceTable: string
  manifestSchemaVersion: string
  expectedRowCount: number
  contentSha256: string
  exportedAt: string
  previousBatchId?: number
  previousIssueId?: number
  idempotencyKey: string
  correlationId: string
}

export const createMigrationBatch = (data: BatchCreateInput) =>
  request.post<MigrationBatchVO>({ url: `${baseUrl}/batches`, data })

export const appendMigrationSourceRecords = (
  batchId: number,
  data: {
    records: {
      sourcePk: string
      sourceBusinessKey?: string
      sourcePayload: Record<string, unknown>
      sourceChecksum: string
      extractedAt: string
    }[]
    correlationId: string
  }
) => request.post<MigrationSourceRecordVO[]>({ url: `${baseUrl}/batches/${batchId}/source-records`, data })

export const markMigrationStagedReady = (
  batchId: number,
  data: {
    expectedBatchVersion: number
    decision: 'READY' | 'FAIL_IMPORT'
    manifestRowCount?: number
    manifestSchemaVersion?: string
    manifestContentSha256?: string
    failureCode?: string
    idempotencyKey: string
    correlationId: string
  }
) => request.post<MigrationBatchVO>({ url: `${baseUrl}/batches/${batchId}/actions/staged-ready`, data })

// ========== 对账阶段 ==========

export const claimMigrationBatch = (data: {
  ownerContextCode: string
  purposeCode: string
  sourceSystems: string[]
  sourceTables: string[]
}) => request.post<{ claimed: boolean; batch?: MigrationBatchVO }>({ url: `${baseUrl}/actions/claim`, data })

export interface MappingTargetInput {
  targetContext: string
  targetObjectType: string
  targetTable: string
  targetId: number
  targetRole: string
  targetSequence: number
}

export const appendMigrationMapping = (
  batchId: number,
  data: {
    sourceRecordId: number
    resultType: 'MAPPED' | 'RETAINED'
    targets: MappingTargetInput[]
    idempotencyKey: string
    correlationId: string
  }
) =>
  request.post<{ sourceRecordId: number; resultType: string; mappingIds: number[] }>({
    url: `${baseUrl}/batches/${batchId}/mappings`,
    data
  })

export const appendMigrationIssue = (
  batchId: number,
  data: {
    sourceRecordId: number
    issueKey: string
    issueType: string
    rawBusinessKey?: string
    candidateTargetIds?: number[]
    rawPayload?: Record<string, unknown>
    idempotencyKey: string
    correlationId: string
  }
) => request.post<Record<string, unknown>>({ url: `${baseUrl}/batches/${batchId}/issues`, data })

export interface MigrationIssueVO {
  issueId: number
  batchId: number
  sourceRecordId: number
  issueKey: string
  issueType: string
  rawBusinessKey?: string
  candidateTargetIds?: number[]
  rawPayload?: string
  status: 'OPEN' | 'CLOSED'
  resolverUserId?: number
  ruleVersion?: string
  targetResult?: string
  resolvedAt?: string
  version: number
}

export const getMigrationIssues = (batchId: number) =>
  request.get<MigrationIssueVO[]>({ url: `${baseUrl}/batches/${batchId}/issues` })

export const completeMigrationReconciliation = (
  batchId: number,
  data: {
    expectedBatchVersion: number
    expectedMappedCount: number
    expectedIssueCount: number
    expectedRetainedCount: number
    ruleVersion: string
    idempotencyKey: string
    correlationId: string
  }
) => request.post<MigrationBatchVO>({ url: `${baseUrl}/batches/${batchId}/actions/complete-reconciliation`, data })

export const closeMigrationIssue = (
  issueId: string | number,
  data: {
    ruleVersion: string
    targetResult: Record<string, unknown>
    idempotencyKey: string
    correlationId: string
  }
) => request.post<Record<string, unknown>>({ url: `${baseUrl}/issues/${issueId}/actions/close`, data })
