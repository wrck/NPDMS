import request from '@/config/axios'

export interface CommercePageReqVO extends PageParam {
  status?: string
}

export interface ContractPageReqVO extends CommercePageReqVO {
  companyCode?: string
  contractNo?: string
  contractType?: string
  customer?: string
  sourceSystem?: string
}

export interface ContractDetailRespVO {
  contract: ContractRespVO
  relatedOrders: SalesOrderRespVO[]
  projectRelations: Array<{
    id: number
    projectId: number
    relationRole: string
    status: string
    version: number
  }>
  sourceSystem: string
  sourceVersion: string
  sourceSyncTime?: string
  sourceUpdatedAt?: string
}

export interface ContractRespVO {
  id: number
  companyCode: string
  companyName?: string
  contractNo: string
  contractType?: string
  customerCode?: string
  customerName?: string
  contractName?: string
  currencyCode?: string
  sourceVersion: string
  sourceUpdatedAt?: string
  status: string
  version: number
}

export interface ContractRelationReqVO {
  projectId: number
  relationRole?: string
  reason: string
}

export interface SalesOrderPageReqVO extends CommercePageReqVO {
  companyCode?: string
  orderNo?: string
  orderType?: string
  customer?: string
}

export interface SalesOrderRespVO {
  id: number
  sourceSystem: string
  sourceVersion: string
  companyCode: string
  companyName?: string
  orderType: string
  orderNo: string
  customerCode?: string
  customerName?: string
  status: string
  version: number
}

export interface SalesOrderLinePageReqVO extends PageParam {
  orderId?: number
  companyCode?: string
  orderType?: string
  orderNo?: string
  lineNo?: string
  itemCode?: string
  productCode?: string
  quantityStatus?: string
  status?: string
}

export interface SalesOrderLineRespVO {
  id: number
  orderId: number
  sourceSystem: string
  sourceVersion: string
  companyCode: string
  orderType: string
  orderNo: string
  lineNo: string
  itemCode: string
  itemDesc?: string
  productCode?: string
  orderQty: number
  openQty: number
  deliveredQty: number
  unitCode: string
  unitScale: number
  quantityStatus: string
  status: string
  version: number
}

export interface DeliveryScopeDetailRespVO {
  id: number
  sequence: number
  serialNo?: string
  productCode?: string
  deviceTypeCode?: string
  allocatedQuantity: number
  status: string
}

export interface DeliveryScopeRespVO {
  id: number
  projectId: number
  projectCode: string
  orderLineId: number
  orderNo: string
  lineNo: string
  itemCode: string
  allocatedQuantity: number
  scopeStatus: string
  allocationVersion: number
  allocationSource: string
  changeReason?: string
  officeDepartmentId: number
  officeDepartmentCode: string
  officeDepartmentName: string
  officeDepartmentVersion: number
  effectiveFrom: string
  effectiveTo?: string
  version: number
  details: DeliveryScopeDetailRespVO[]
}

export interface DeliveryScopePageReqVO extends PageParam {
  projectId?: number
  orderLineId?: number
  includeHistory?: boolean
}

export interface DeliveryScopePreviewReqVO {
  projectId: number
  expectedProjectVersion: number
  expectedProjectScopeVersion: number
  orderLineId: number
  expectedOrderLineSourceVersion: string
  proposedQuantity: number
  serialNumbers: string[]
}

export interface DeliveryScopePreviewResult {
  projectId: number
  projectVersion: number
  projectCode: string
  officeDepartmentId: number
  officeDepartmentCode: string
  officeDepartmentName: string
  officeDepartmentVersion: number
  orderLineId: number
  orderLineSourceVersion: string
  orderQuantity: number
  allocatedQuantity: number
  availableQuantity: number
  proposedQuantity: number
  allowed: boolean
  validationErrors: string[]
  occupiedScopes: Array<{
    deliveryScopeId: number
    projectId: number
    allocatedQuantity: number
    allocationVersion: number
    scopeStatus: string
  }>
}

export interface DeliveryScopeAssignReqVO extends Omit<
  DeliveryScopePreviewReqVO,
  'expectedProjectVersion' | 'proposedQuantity'
> {
  allocatedQuantity: number
  reason: string
}

export interface DeliveryScopeChangeReqVO {
  projectId: number
  expectedProjectVersion: number
  expectedProjectScopeVersion: number
  expectedOrderLineSourceVersion: string
  reason: string
}

export interface DeliveryScopeAdjustReqVO extends DeliveryScopeChangeReqVO {
  proposedQuantity: number
  serialNumbers: string[]
}

export interface DeliveryScopeCommandResult {
  deliveryScopeId: number
  allocationVersion: number
  replayed: boolean
}

const baseUrl = '/api/v1/pms'

export const getContractPage = (params: ContractPageReqVO) =>
  request.get({ url: `${baseUrl}/contracts`, params })

export const getContract = (id: number) =>
  request.get<ContractDetailRespVO>({ url: `${baseUrl}/contracts/${id}` })

export const relateContractProject = (
  contractId: number,
  data: ContractRelationReqVO,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/contracts/${contractId}/project-relations`,
    data,
    headers: { 'Idempotency-Key': idempotencyKey }
  })

export const getSalesOrderPage = (params: SalesOrderPageReqVO) =>
  request.get({ url: `${baseUrl}/sales-orders`, params })

export const getSalesOrderLinePage = (params: SalesOrderLinePageReqVO) =>
  request.get({ url: `${baseUrl}/order-lines`, params })

export const getDeliveryScopePage = (params: DeliveryScopePageReqVO) =>
  request.get({ url: `${baseUrl}/delivery-scopes`, params })

export const previewDeliveryScope = (data: DeliveryScopePreviewReqVO) =>
  request.post({ url: `${baseUrl}/delivery-scopes/actions/preview`, data })

export const assignDeliveryScope = (
  data: DeliveryScopeAssignReqVO,
  projectVersion: number,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/delivery-scopes/actions/assign`,
    data,
    headers: { 'If-Match': String(projectVersion), 'Idempotency-Key': idempotencyKey }
  })

export const adjustDeliveryScope = (
  id: number,
  data: DeliveryScopeAdjustReqVO,
  allocationVersion: number,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/delivery-scopes/${id}/actions/adjust`,
    data,
    headers: { 'If-Match': String(allocationVersion), 'Idempotency-Key': idempotencyKey }
  })

export const releaseDeliveryScope = (
  id: number,
  data: DeliveryScopeChangeReqVO,
  allocationVersion: number,
  idempotencyKey: string
) =>
  request.post({
    url: `${baseUrl}/delivery-scopes/${id}/actions/release`,
    data,
    headers: { 'If-Match': String(allocationVersion), 'Idempotency-Key': idempotencyKey }
  })
