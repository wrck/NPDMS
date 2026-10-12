import request from '@/config/axios'

export type LegacyId = string | number
export interface LegacyField {
  key: string
  label: string
}
export interface LegacyDomain {
  code: string
  label: string
  group: string
  sourceTable: string
  keyField: string
  fields: LegacyField[]
}
export interface LegacySource {
  id: LegacyId
  sourceSystem: string
  sourceProjectKey: string
  sourceContractNo: string
  snapshotId: LegacyId
  checksum: string
  sourceReadAt: string
  capturedAt: string
  counts: Record<string, number>
  domains: LegacyDomain[]
  deferredDomains: string[]
}
export interface LegacyRecord {
  id: LegacyId
  domain: string
  sourceTable: string
  sourceKey: string
  parentDomain?: string
  parentSourceKey?: string
  sourceUpdatedAt?: string
  checksum: string
  values: Record<string, string | null>
  redactedFields: string[]
  nativeStatus?: LegacyNativeStatus
}
export interface LegacyNativeTarget {
  targetContext: string
  targetObjectType: string
  targetTable: string
  targetId: LegacyId
  targetRole: string
  targetSequence: number
}
export interface LegacyNativeStatus {
  classification: string
  reasonCode?: string
  reason?: string
  targets: LegacyNativeTarget[]
}
export interface LegacyNativeDomainSummary {
  domain: string
  label: string
  sourceCount: number
  mappedCount: number
  retainedCount: number
  pendingCount: number
  targetTables: string[]
  retentionReasons: Record<string, number>
}
export interface LegacyNativeReport {
  sourceId: LegacyId
  snapshotId: LegacyId
  sourceCount: number
  mappedCount: number
  retainedCount: number
  pendingCount: number
  domains: LegacyNativeDomainSummary[]
}
export interface LegacyRecordsQuery {
  sourceId: LegacyId
  snapshotId: LegacyId
  domain: string
  sourceKey?: string
  parentDomain?: string
  parentKey?: string
  pageNo: number
  pageSize: number
}
const base = (projectId: LegacyId) => `/api/v1/pms/projects/${projectId}/legacy-detail`
export const getLegacySources = (projectId: LegacyId): Promise<LegacySource[]> =>
  request.get({ url: `${base(projectId)}/sources` })
export const getLegacyRecords = (
  projectId: LegacyId,
  params: LegacyRecordsQuery
): Promise<{ list: LegacyRecord[]; total: number }> =>
  request.get({ url: `${base(projectId)}/records`, params })
export const getLegacyNativeReport = (
  projectId: LegacyId,
  params: { sourceId: LegacyId; snapshotId: LegacyId }
): Promise<LegacyNativeReport> => request.get({ url: `${base(projectId)}/native-status`, params })
export const getLegacyNativeRecordStatus = (
  projectId: LegacyId,
  params: LegacyRecordsQuery
): Promise<Record<string, LegacyNativeStatus>> =>
  request.get({ url: `${base(projectId)}/native-record-status`, params })
export const legacyNativeStatusText = (status?: LegacyNativeStatus): string =>
  status?.classification === 'MAPPED'
    ? '已有目标记录'
    : status?.classification === 'RETAINED'
      ? '保留历史'
      : status && status.classification !== 'NOT_MIGRATED'
        ? '需核对'
        : '待承接'
export const legacyNativeTargetLabel = (table: string): string =>
  (
    ({
      proj_project: '项目主档',
      proj_project_party: '项目参与方',
      com_contract: '合同',
      com_sales_order: '销售订单',
      com_sales_order_line: '销售订单行',
      com_crm_execution_order: '执行单',
      com_shipment_package: '发货包裹',
      ast_device: '设备主档',
      ast_device_shipment: '设备发货记录',
      ast_device_project_relationship: '设备项目关系',
      ast_device_network_version_event: '设备软件版本历史',
      ast_device_warranty_record: '设备保修历史',
      imp_eng_installation: '待核对安装地址'
    }) as Record<string, string>
  )[table] || '业务实体'

// Historical BIT(1) bytes stay unchanged in evidence; render their numeric values visibly.
export const legacyValueText = (value: string | null | undefined): string =>
  value === '\u0000' ? '0' : value === '\u0001' ? '1' : (value ?? '—')

export const legacyChildren: Record<string, string[]> = {
  project: [
    'state',
    'parties',
    'members',
    'groups',
    'executions',
    'orders',
    'products',
    'shipment-facts',
    'installations',
    'tasks',
    'maintenance',
    'callbacks',
    'subcontracts',
    'notifications'
  ],
  groups: ['contracts'],
  orders: ['order-lines'],
  'software-changes': ['software-versions'],
  callbacks: ['callback-questionnaires', 'approvals'],
  questionnaires: ['questionnaire-answers'],
  'questionnaire-templates': ['questionnaire-questions'],
  'questionnaire-questions': ['questionnaire-options'],
  subcontracts: ['subcontract-payments', 'subcontract-prices', 'approvals'],
  weekly: ['weekly-items', 'weekly-feedback'],
  tasks: ['tasks'],
  instructions: ['instructions']
}

// These are source foreign keys, resolved inside the same imported snapshot.
export const legacyReferences: Record<string, Record<string, { domain: string; label: string }>> = {
  maintenance: { quesnaireId: { domain: 'questionnaires', label: '查看答卷' } },
  supervision: { quesnaireId: { domain: 'questionnaires', label: '查看答卷' } },
  'callback-questionnaires': { quesnaireId: { domain: 'questionnaires', label: '查看答卷' } },
  questionnaires: {
    quesnaireTemplateHeaderId: { domain: 'questionnaire-templates', label: '查看模板' }
  },
  'questionnaire-answers': {
    quesnaireTemplateLineId: { domain: 'questionnaire-questions', label: '查看题目' },
    questionTemplateOptId: { domain: 'questionnaire-options', label: '查看选项' }
  }
}
