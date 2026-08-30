import request from '@/config/axios'

export type WireLong = number | string
export type ArrivalStatus =
  | 'DRAFT'
  | 'PARTIALLY_ACCEPTED'
  | 'DIFFERENCE_PENDING'
  | 'ACCEPTED'
  | 'CONFIRMED'

export interface FileFactVersion {
  artifactVersion: number
  referenceVersion: number
  availabilityVersion: number
}

export interface FileRevision {
  artifactId: WireLong
  referenceKey: string
  versionNo: number
  scopeVersion: WireLong
  fileFactVersion: FileFactVersion
  hash: string
}

export type ArrivalScope =
  | { scopeType: 'DEVICE'; deviceId: WireLong }
  | {
      scopeType: 'ORDER_MODEL_QUANTITY'
      orderLineId: WireLong
      productCode: string | null
      modelCode: string | null
      quantity: number
      unitCode: string
    }

export type ArrivalDraftLine =
  | {
      scopeType: 'DEVICE'
      lineId: WireLong | null
      expectedLineVersion: number | null
      deviceId: WireLong
      received: boolean
    }
  | {
      scopeType: 'ORDER_MODEL_QUANTITY'
      lineId: WireLong | null
      expectedLineVersion: number | null
      orderLineId: WireLong
      productCode: string | null
      modelCode: string | null
      acceptedQuantity: number
      unitCode: string
    }

export interface ArrivalListItem {
  id: WireLong
  projectId: WireLong
  batchCode: string
  logisticsNo: string
  arrivedAt: string
  signerName: string
  status: ArrivalStatus
  evidenceSyncStatus: string | null
  version: number
  allowedActions: string[]
  createTime: string
}

export interface ArrivalLine {
  id: WireLong
  lineNo: number
  lineRevision: number
  scopeType: 'DEVICE' | 'ORDER_MODEL_QUANTITY'
  deviceId: WireLong | null
  deviceAssignmentVersion: WireLong | null
  orderLineId: WireLong | null
  productCode: string | null
  modelCode: string | null
  expectedQuantity: number | null
  acceptedQuantity: number | null
  unitCode: string | null
  status: string
  version: number
}

export interface ArrivalDifference {
  id: WireLong
  arrivalLineId: WireLong
  differenceNo: number
  revisionNo: number
  differenceType: string
  resolutionStatus: string
  reason: string
  riskDescription: string | null
  scopeSnapshot: ArrivalScope
  approvedBy: WireLong | null
  approvedAt: string | null
  exemptionExpiresAt: string | null
  evidenceId: WireLong | null
  evidenceRevision: number | null
  current: boolean
  projectFactVersion: WireLong | null
  factImpactType: string | null
  version: number
}

export interface ArrivalEvidence {
  evidenceId: WireLong
  currentRevision: number
  artifactId: WireLong
  referenceKey: string
  fileVersionNo: number
  fileFactVersion: FileFactVersion
  fileScopeVersion: WireLong
  fileHash: string
  syncStatus: string
  nextRetryAt: string | null
  retryCount: number
}

export interface ArrivalDetail extends Omit<ArrivalListItem, 'evidenceSyncStatus' | 'createTime'> {
  deliveryScopeVersion: WireLong
  scopeWatermark: {
    deliveryScopeVersion: WireLong
    deviceAssignmentVersions: Array<{
      deviceId: WireLong
      projectAssignmentVersion: WireLong
    }>
  }
  evidenceId: WireLong | null
  evidenceRevision: number | null
  projectFactVersion: WireLong | null
  predecessorAcceptanceId: WireLong | null
  successorReason: string | null
  submittedBy: WireLong | null
  submittedAt: string | null
  confirmedBy: WireLong | null
  confirmedAt: string | null
  currentLines: ArrivalLine[]
  differences: ArrivalDifference[]
  evidence: ArrivalEvidence | null
}

export interface ArrivalPage {
  list: ArrivalListItem[]
  total: WireLong
}

export interface ArrivalCommandResult {
  id: WireLong
  projectId: WireLong
  status: ArrivalStatus
  version: number
  deliveryScopeVersion: WireLong
  changedLineIds: WireLong[]
  evidenceId: WireLong | null
  evidenceRevision: number | null
  projectFactVersion: WireLong | null
  evidenceSyncStatus: string | null
  eventId: string | null
  successorAcceptanceId: WireLong | null
  allowedActions: string[]
}

export interface CreateArrivalRequest {
  projectId: WireLong
  batchCode: string
  logisticsNo: string
  arrivedAt: string
  signerName: string
  expectedDeliveryScopeVersion: WireLong
}

export type PatchArrivalRequest = Partial<{
  logisticsNo: string
  arrivedAt: string
  signerName: string
  lines: ArrivalDraftLine[]
  evidenceRevision: FileRevision
}>

export interface RaiseDifferenceRequest {
  arrivalLineId: WireLong
  expectedLineVersion: number
  differenceTypeCode: string
  scopeSnapshot: ArrivalScope
  reason: string
  riskDescription: string | null
  evidenceRevision: FileRevision
}

export type ResolveDifferenceRequest =
  | {
      resolutionType: 'SUPPLEMENT'
      differenceId: WireLong
      expectedDifferenceRevision: number
      expectedDifferenceVersion: number
      supplementScope: ArrivalScope
      reason: string
      evidenceRevision: FileRevision
    }
  | {
      resolutionType: 'KEEP_REJECTED' | 'CLOSE'
      differenceId: WireLong
      expectedDifferenceRevision: number
      expectedDifferenceVersion: number
      reason: string
      evidenceRevision: FileRevision
    }
  | {
      resolutionType: 'EXEMPT'
      differenceId: WireLong
      expectedDifferenceRevision: number
      expectedDifferenceVersion: number
      reason: string
      riskDescription: string
      expiresAt: string
      evidenceRevision: FileRevision
    }
  | {
      resolutionType: 'CORRECT_INFORMATION'
      expectedSourceVersion: number
      reason: string
      correctionPatch: {
        logisticsNo: string
        arrivedAt: string
        signerName: string
        lines: ArrivalDraftLine[]
      }
      evidenceRevision: FileRevision
    }

const baseUrl = '/api/v1/pms/arrival-acceptances'

export const getArrivalPage = (params: {
  projectId?: WireLong
  batchCode?: string
  status?: ArrivalStatus
  pageNo: number
  pageSize: number
}) => request.get<ArrivalPage>({ url: baseUrl, params })

export const getArrivalDetail = (id: WireLong) =>
  request.get<ArrivalDetail>({ url: `${baseUrl}/${id}` })

export const createArrival = (data: CreateArrivalRequest, idempotencyKey: string) =>
  request.post<ArrivalCommandResult>({
    url: baseUrl,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const patchArrival = (id: WireLong, expectedVersion: number, data: PatchArrivalRequest) =>
  request.put<ArrivalCommandResult>({
    method: 'PATCH',
    url: `${baseUrl}/${id}`,
    data,
    headers: { 'If-Match': String(expectedVersion) }
  })

const action = <T>(
  id: WireLong,
  name: string,
  expectedVersion: number,
  idempotencyKey: string,
  data: unknown = {}
) =>
  request.post<T>({
    url: `${baseUrl}/${id}/actions/${name}`,
    data,
    headers: { 'If-Match': String(expectedVersion), 'Idempotency-Key': idempotencyKey }
  })

export const submitArrival = (id: WireLong, expectedVersion: number, idempotencyKey: string) =>
  action<ArrivalCommandResult>(id, 'submit', expectedVersion, idempotencyKey)

export const confirmArrival = (id: WireLong, expectedVersion: number, idempotencyKey: string) =>
  action<ArrivalCommandResult>(id, 'confirm', expectedVersion, idempotencyKey)

export const raiseArrivalDifference = (
  id: WireLong,
  expectedVersion: number,
  data: RaiseDifferenceRequest,
  idempotencyKey: string
) => action(id, 'raise-difference', expectedVersion, idempotencyKey, data)

export const resolveArrivalDifference = (
  id: WireLong,
  expectedVersion: number,
  data: ResolveDifferenceRequest,
  idempotencyKey: string
) => action(id, 'resolve-difference', expectedVersion, idempotencyKey, data)
