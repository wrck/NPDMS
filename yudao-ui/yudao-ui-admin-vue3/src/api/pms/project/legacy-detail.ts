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
