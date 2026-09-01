import type {
  CutoverBindingRule,
  CutoverChecklistItem,
  CutoverConfiguration,
  CutoverValidationError
} from '@/api/pms/cutover/cutover-config'

export const DUAL_BASELINES = {
  VSM: 17,
  SILENT_DUAL: 25,
  DRP_DUAL: 23,
  NORMAL_DUAL: 24,
  CLUSTER: 8
} as const

export const MATRIX_LEVEL_CODES = ['A', 'B', 'C'] as const

export const DUAL_LABELS: Record<keyof typeof DUAL_BASELINES, string> = {
  VSM: 'VSM双机',
  SILENT_DUAL: '静默双机',
  DRP_DUAL: 'DRP双机',
  NORMAL_DUAL: '普通双机',
  CLUSTER: '集群'
}

export const RISK_CATEGORY_LABELS: Record<string, string> = {
  CURRENT_VERSION_BULLETIN: '当前版本技术公告',
  TARGET_VERSION_BULLETIN: '升级后版本技术公告',
  DUAL_CONFIG_CONSISTENCY: '双机必要配置一致性',
  FILTER_NAT_QOS_COMPILE_COUNT: '包过滤/NAT/QoS编译数',
  COMPILE_LIMIT_ASSESSMENT: '编译限制评估',
  SESSION_SYNC: '会话同步',
  DUAL_CONTROLLER_VERSION: '框式双主控版本',
  PACKAGE_MD5: '软件包MD5',
  MAJOR_PROJECT_SPARES: '重大项目备件',
  SYSTEM_LOG: '系统日志',
  DIAGNOSTIC_LOG: '诊断日志',
  RUNNING_VERSION_BACKUP: '运行版本备份',
  HOT_PATCH_BACKUP: '热补丁备份',
  LICENSE_BACKUP: 'License备份',
  CONFIG_BACKUP: '配置备份',
  DYNAMIC_TABLE_COLLECTION: '动态表项采集',
  MTU_JUMBO_FRAME: 'MTU与超大帧',
  HUNDRED_G_FEC: '百G口FEC',
  LONG_CONNECTION: '长连接',
  SECOND_PASS_DEVICE: '二次过设备',
  STP: 'STP',
  F5_DEFAULT: 'F5默认配置',
  ADWARE_DEFAULT: 'Adware默认配置',
  ROOM_OPERATION_COMMITMENT: '机房操作承诺书'
}

export const SURVEY_CATEGORY_LABELS: Record<string, string> = {
  CUTOVER_BACKGROUND: '割接背景',
  BUSINESS_SUMMARY: '涉及业务简述',
  IMPACT_SCOPE: '割接动作影响范围',
  CONTINUITY_REQUIREMENT: '业务连续性要求',
  INTERRUPTION_COUNT: '业务中断次数',
  CURRENT_TOPOLOGY: '现网拓扑图',
  DEVICE_LOCATION_PLAN: '设备部署位置规划',
  INTERFACE_INTERCONNECT_PLAN: '设备接口互联规划',
  IP_VLAN_PLAN: '设备IP地址与VLAN规划',
  PERFORMANCE_BASELINE: '性能基线',
  CONNECTIVITY_TEST_CASE: '业务连通性测试例核验',
  VENDOR_CONFIG_TRANSLATION: '友商配置全量翻译检查'
}

export interface MatrixProjection {
  items: CutoverChecklistItem[]
  rules: CutoverBindingRule[]
}

export interface BulkBindingCommand {
  ruleKeys: string[]
  cutoverTypeCodes?: string[]
  networkModeCodes?: string[]
  deviceTypeCodes?: string[]
  levelCodes?: string[]
  requiredResult?: boolean
  priority?: number
  enabled?: boolean
}

const project = (
  config: CutoverConfiguration,
  accepts: (item: CutoverChecklistItem) => boolean
): MatrixProjection => {
  const items = config.items.filter(accepts)
  const itemKeys = new Set(items.map((item) => item.stableItemKey))
  return {
    items,
    rules: config.bindingRules.filter((rule) => itemKeys.has(rule.stableItemKey))
  }
}

export const projectRiskMatrix = (config: CutoverConfiguration) =>
  project(config, (item) => item.itemType === 'RISK' || item.itemType === 'DUAL_MACHINE_CHECK')

export const projectSurveyMatrix = (config: CutoverConfiguration) =>
  project(config, (item) => item.itemType === 'BUSINESS_SURVEY')

export const validationTarget = (errors: CutoverValidationError[]) => {
  const location = errors[0]?.location || ''
  if (location.startsWith('risk.')) return 'risk'
  if (location.startsWith('survey.')) return 'survey'
  return 'validation'
}

export const applyBulkBinding = (config: CutoverConfiguration, command: BulkBindingCommand) => {
  const selected = new Set(command.ruleKeys)
  config.bindingRules
    .filter((rule) => selected.has(rule.stableRuleKey))
    .forEach((rule) => {
      const conditions = { ...rule.dimensionConditions }
      if (command.cutoverTypeCodes !== undefined)
        conditions.CUTOVER_TYPE = [...command.cutoverTypeCodes]
      if (command.networkModeCodes !== undefined)
        conditions.NETWORK_MODE = [...command.networkModeCodes]
      if (command.deviceTypeCodes !== undefined)
        conditions.DEVICE_TYPE = [...command.deviceTypeCodes]
      if (command.levelCodes !== undefined) conditions.CUTOVER_LEVEL = [...command.levelCodes]
      rule.dimensionConditions = conditions
      if (command.requiredResult !== undefined) rule.requiredResult = command.requiredResult
      if (command.priority !== undefined) rule.priority = command.priority
      if (command.enabled !== undefined) rule.enabled = command.enabled
    })
}

export const ensureCutoverBackgroundSchema = (schema: Record<string, unknown>) => {
  schema.fields = [
    { code: 'solvesOnlineIssue' },
    {
      code: 'issueTicketNo',
      visibleWhen: { field: 'solvesOnlineIssue', equals: true }
    },
    {
      code: 'issueHandler',
      visibleWhen: { field: 'solvesOnlineIssue', equals: true }
    },
    { code: 'repeatCutover' },
    {
      code: 'firstCutoverOwner',
      visibleWhen: { field: 'repeatCutover', equals: true }
    },
    { code: 'backgroundDescription' }
  ]
}
