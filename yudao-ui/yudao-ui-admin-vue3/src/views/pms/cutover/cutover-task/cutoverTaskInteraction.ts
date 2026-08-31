import type {
  CreateContextCandidate,
  CreateCutoverTaskRequest,
  CutoverStage,
  ManualGrade,
  WireDateTime
} from '@/api/pms/cutover/cutover-task'

export const newIntentKey = () => crypto.randomUUID()

export const parseSerials = (value: string) => {
  const values = value
    .split(/[\s,，;；]+/)
    .map((item) => item.trim())
    .filter(Boolean)
  return [...new Map(values.map((item) => [item.toLocaleUpperCase(), item])).values()]
}

export const toWireDateTime = (value: string): WireDateTime => {
  const timestamp = new Date(value).getTime()
  if (!Number.isSafeInteger(timestamp) || timestamp <= 0) throw new Error('请选择有效的计划时间')
  return timestamp
}

export const formatWireDateTime = (value: WireDateTime | null) => {
  if (value === null) return '—'
  const timestamp = typeof value === 'string' ? Number(value) : value
  if (!Number.isSafeInteger(timestamp) || timestamp <= 0) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(timestamp))
}

export const buildCreateRequest = (
  candidate: CreateContextCandidate,
  form: {
    serialNumbers: string[]
    configurationCode: string
    taskName: string
    background: string
    cutoverType: string
    networkMode: string | null
    scheduledTime: string
  }
): CreateCutoverTaskRequest => {
  const { projectScopeVersion, ...expectedProjectContext } = candidate.project
  const customer = candidate.customerServiceLevel
  return {
    projectId: candidate.project.projectId,
    configurationCode: form.configurationCode,
    serialNumbers: form.serialNumbers,
    taskName: form.taskName.trim(),
    background: form.background.trim(),
    cutoverType: form.cutoverType,
    networkMode: form.networkMode,
    scheduledTime: toWireDateTime(form.scheduledTime),
    expectedProjectContext,
    expectedProjectScopeVersion: projectScopeVersion,
    expectedDeviceScopeWatermark: candidate.devices,
    expectedReadinessSnapshotId: candidate.implementationReadiness.snapshotId,
    expectedReadinessSnapshotVersion: candidate.implementationReadiness.snapshotVersion,
    expectedCustomerServiceLevelStatus: customer.status,
    expectedCustomerServiceLevelRevisionId: customer.serviceLevelRevisionId,
    expectedCustomerServiceLevelCode: customer.serviceLevelCode,
    expectedCustomerServiceLevelFactVersion: customer.factVersion,
    expectedCustomerServiceLevelEffectiveFrom: customer.effectiveFrom,
    expectedCustomerServiceLevelEffectiveTo: customer.effectiveTo
  }
}

export const gradeDestination = (grade: ManualGrade | null) =>
  grade === 'D' ? 'P4 方案编制' : grade ? 'P3 现场调研' : '保存后不推进阶段'

export const activeCutoverStagePanel = (stage: CutoverStage | null) =>
  stage === 'P2' ? 'ASSESSMENT' : stage === 'P3' || stage === 'P4' ? 'CHECKLIST' : 'EMPTY'

export const encodeChecklistDirectAnswer = (value: string) => JSON.stringify({ value })

export const decodeChecklistDirectAnswer = (answerSnapshot: string) => {
  const snapshot = JSON.parse(answerSnapshot) as { value?: unknown }
  if (!snapshot || typeof snapshot !== 'object' || typeof snapshot.value !== 'string') {
    throw new Error('清单直接填写结果不是受支持的 JSON 快照')
  }
  return snapshot.value
}
