import { generateUUID } from '@/utils'
import type {
  CreateContextCandidate,
  CreateCutoverTaskRequest,
  CutoverStage,
  ManualGrade,
  WireDateTime
} from '@/api/pms/cutover'

export const newIntentKey = () => generateUUID()

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
  form?: { configurationCode: string; background: string; cutoverType: string; networkMode: string | null; scheduledTime: string }
): CreateCutoverTaskRequest => {
  const project = candidate.project
  const customer = candidate.customerServiceLevel
  return {
    projectId: candidate.project.projectId,
    ...(form ? { configurationCode: form.configurationCode, background: form.background.trim(), cutoverType: form.cutoverType,
      networkMode: form.networkMode, scheduledTime: toWireDateTime(form.scheduledTime) } : {}),
    serialNumbers: candidate.devices.map((device) => device.serialNumber),
    expectedProjectContext: {
      projectId: project.projectId,
      projectVersion: project.projectVersion,
      projectCode: project.projectCode,
      projectName: project.projectName,
      customerId: project.customerId ?? null,
      customerCode: project.customerCode ?? null,
      customerName: project.customerName ?? null,
      departmentId: project.departmentId ?? null,
      departmentCode: project.departmentCode ?? null,
      departmentName: project.departmentName ?? null
    },
    expectedProjectScopeVersion: project.projectScopeVersion,
    expectedDeviceScopeWatermark: candidate.devices.map((device) => ({
      deviceId: device.deviceId,
      serialNumber: device.serialNumber,
      projectAssignmentVersion: device.projectAssignmentVersion
    })),
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
  stage === 'P2'
    ? 'ASSESSMENT'
    : stage === 'P3'
      ? 'CHECKLIST'
      : stage === 'P5'
        ? 'APPROVAL'
        : stage === 'P4'
          ? 'PLAN'
          : stage === 'P6'
            ? 'CLOSURE'
            : 'EMPTY'

export const createCutoverPlanIntentStore = (factory: () => string = newIntentKey) => {
  const keys = new Map<string, string>()
  return {
    key(intent: string) {
      const existing = keys.get(intent)
      if (existing) return existing
      const created = factory()
      keys.set(intent, created)
      return created
    },
    complete(intent: string) {
      keys.delete(intent)
    },
    reset() {
      keys.clear()
    }
  }
}

export const createCutoverPlanWriteBarrier = () => {
  let pendingRefresh: (() => Promise<void>) | null = null
  return {
    register(refresh: () => Promise<void>) {
      pendingRefresh = refresh
    },
    async beforeWrite() {
      if (!pendingRefresh) return 'PROCEED' as const
      try {
        await pendingRefresh()
        pendingRefresh = null
        return 'REFRESHED' as const
      } catch {
        return 'REFRESH_FAILED' as const
      }
    },
    reset() {
      pendingRefresh = null
    }
  }
}

export const cutoverPlanRecoveryAction = (error: unknown) => {
  const action = (error as any)?.response?.data?.data?.recoveryAction
  if (
    ['REFRESH_AGGREGATE', 'REFRESH_OWNER_FACTS', 'RETRY_SAME_KEY', 'START_NEW_INTENT'].includes(
      action
    )
  )
    return action as string
  return (error as any)?.response ? 'SURFACE_ERROR' : 'RETRY_SAME_KEY'
}

export const createCutoverWriteCoordinator = (factory: () => string = newIntentKey) => {
  const intents = createCutoverPlanIntentStore(factory)
  const barrier = createCutoverPlanWriteBarrier()
  return {
    refreshPending: () => barrier.beforeWrite(),
    async run<T>(intent: string, write: (key: string) => Promise<T>, refresh: () => Promise<void>) {
      const before = await barrier.beforeWrite()
      if (before !== 'PROCEED')
        return { writeCalled: false, refreshSucceeded: before === 'REFRESHED' } as const
      const key = intents.key(intent)
      let result: T
      try {
        result = await write(key)
      } catch (error) {
        const recovery = cutoverWriteRecoveryAction(error)
        if (['REFRESH_APPROVAL', 'REFRESH_SOURCES', 'REFRESH_AGGREGATE', 'REFRESH_OWNER_FACTS', 'REFRESH_TASK'].includes(recovery)) {
          intents.complete(intent)
          try {
            await refresh()
          } catch {
            barrier.register(refresh)
          }
        } else if (recovery === 'START_NEW_INTENT') intents.complete(intent)
        throw error
      }
      intents.complete(intent)
      try {
        await refresh()
        return { writeCalled: true, refreshSucceeded: true, result } as const
      } catch {
        barrier.register(refresh)
        return { writeCalled: true, refreshSucceeded: false, result } as const
      }
    },
    reset() {
      intents.reset()
      barrier.reset()
    }
  }
}

export const cutoverWriteRecoveryAction = (error: unknown) => {
  const action = (error as any)?.response?.data?.data?.recoveryAction
  if (['REFRESH_APPROVAL', 'REFRESH_SOURCES', 'REFRESH_AGGREGATE', 'REFRESH_OWNER_FACTS', 'REFRESH_TASK', 'RETRY_SAME_KEY', 'START_NEW_INTENT'].includes(action)) {
    return action as string
  }
  return (error as any)?.response ? 'SURFACE_ERROR' : 'RETRY_SAME_KEY'
}

export const encodeChecklistDirectAnswer = (value: string) => {
  let data: unknown
  try { data = JSON.parse(value) } catch { data = value }
  return JSON.stringify({ value: data })
}
export const decodeChecklistDirectAnswer = (answerSnapshot: string) => {
  const snapshot = JSON.parse(answerSnapshot)
  const data = snapshot.analysis ?? snapshot
  return JSON.stringify(Object.prototype.hasOwnProperty.call(data, 'value') ? data.value : data)
}
