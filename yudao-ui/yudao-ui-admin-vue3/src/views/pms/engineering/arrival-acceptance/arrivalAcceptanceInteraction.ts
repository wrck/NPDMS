import dayjs from 'dayjs'
import type { WireDateTime } from '@/api/pms/engineering/arrival-acceptance'

export type ArrivalLayout = 'mobile' | 'tablet' | 'desktop' | 'wide'
export type ArrivalCommandFailure =
  | 'REFRESH_AGGREGATE'
  | 'REFRESH_OWNER_FACTS'
  | 'RETAIN_INTENT'
  | 'START_NEW_INTENT'
  | 'SURFACE_ERROR'
export type ArrivalTone = 'primary' | 'success' | 'warning' | 'info' | 'danger'
export interface ArrivalPresentation {
  label: string
  milestoneComplete?: boolean
  arrivalConfirmed?: boolean
  tone: ArrivalTone
}

const progress = {
  DRAFT: { label: '草稿', milestoneComplete: false, tone: 'info' },
  PARTIALLY_ACCEPTED: { label: '部分到货', milestoneComplete: false, tone: 'warning' },
  DIFFERENCE_PENDING: { label: '差异待处理', milestoneComplete: false, tone: 'danger' },
  ACCEPTED: { label: '范围已满足，待确认', milestoneComplete: false, tone: 'success' },
  CONFIRMED: { label: '批次已确认', milestoneComplete: false, tone: 'success' }
} as const

const evidenceSync = {
  NOT_PUBLISHED: { label: '尚未发布归档', arrivalConfirmed: false, tone: 'info' },
  PUBLISHED_PENDING_ACC: {
    label: '签收已确认，等待归档审核',
    arrivalConfirmed: true,
    tone: 'warning'
  },
  ARCHIVE_PENDING_RETRY: { label: '归档发布待重试', arrivalConfirmed: true, tone: 'warning' },
  ACCEPTED_PENDING_ARCHIVE: {
    label: '归档已接受，等待完成',
    arrivalConfirmed: true,
    tone: 'warning'
  },
  ARCHIVE_ACK_PENDING_RETRY: {
    label: '已签收，归档回执待重试',
    arrivalConfirmed: true,
    tone: 'warning'
  },
  ARCHIVED: { label: '归档完成', arrivalConfirmed: true, tone: 'success' }
} as const

export const projectArrivalProgress = (status: string): ArrivalPresentation =>
  progress[status as keyof typeof progress] || {
    label: status || '未知状态',
    milestoneComplete: false,
    tone: 'info'
  }

export const evidenceSyncPresentation = (status?: string | null): ArrivalPresentation =>
  status
    ? evidenceSync[status as keyof typeof evidenceSync] || {
        label: status,
        arrivalConfirmed: false,
        tone: 'info'
      }
    : { label: '尚未上传签收证据', arrivalConfirmed: false, tone: 'info' }

export const shouldExposeArrivalAction = (
  action: string,
  allowedActions: string[],
  hasPermission: boolean
) => hasPermission && allowedActions.includes(action)

export const resolveArrivalCommandFailure = (error: unknown): ArrivalCommandFailure => {
  const response = (error as any)?.response
  if (!response) return 'RETAIN_INTENT'
  const recoveryAction = response.data?.data?.recoveryAction
  if (recoveryAction === 'REFRESH_AGGREGATE') return 'REFRESH_AGGREGATE'
  if (recoveryAction === 'REFRESH_OWNER_FACTS') return 'REFRESH_OWNER_FACTS'
  if (recoveryAction === 'RETRY_SAME_KEY') return 'RETAIN_INTENT'
  if (recoveryAction === 'START_NEW_INTENT') return 'START_NEW_INTENT'
  return 'SURFACE_ERROR'
}

export const pickerValueToWireDateTime = (value: string | number): number => {
  const epoch = typeof value === 'number' ? value : Number(value)
  if (!Number.isSafeInteger(epoch) || epoch <= 0) throw new Error('时间必须是有效的 epoch 毫秒')
  return epoch
}

export const wireDateTimeToPickerValue = (value?: WireDateTime | null): number | '' => {
  if (value === null || value === undefined || value === '') return ''
  return pickerValueToWireDateTime(value)
}

export const formatWireDateTime = (value?: WireDateTime | null): string => {
  const epoch = wireDateTimeToPickerValue(value)
  return epoch === '' ? '-' : dayjs(epoch).format('YYYY-MM-DD HH:mm:ss')
}

export const createArrivalIntentStore = (factory: () => string = () => crypto.randomUUID()) => {
  const keys = new Map<string, string>()
  return {
    key(intent: string) {
      const retained = keys.get(intent)
      if (retained) return retained
      const created = factory()
      keys.set(intent, created)
      return created
    },
    complete(intent: string) {
      keys.delete(intent)
    }
  }
}

export type ArrivalIntentStore = ReturnType<typeof createArrivalIntentStore>
export type ArrivalWriteBarrierResult = 'PROCEED' | 'REFRESHED' | 'REFRESH_FAILED'

export const createArrivalWriteBarrier = () => {
  let pendingRefresh: (() => Promise<void>) | null = null
  return {
    register(refresh: () => Promise<void>) {
      pendingRefresh = refresh
    },
    hasPending() {
      return pendingRefresh !== null
    },
    async beforeWrite(): Promise<ArrivalWriteBarrierResult> {
      if (!pendingRefresh) return 'PROCEED'
      try {
        await pendingRefresh()
        pendingRefresh = null
        return 'REFRESHED'
      } catch {
        return 'REFRESH_FAILED'
      }
    }
  }
}

export const runArrivalGuardedWrite = async <T>(options: {
  barrier: ReturnType<typeof createArrivalWriteBarrier>
  call: () => Promise<T>
  refreshAfterSuccess: (result: T) => Promise<void>
  refreshAfterConflict: () => Promise<void>
}) => {
  const barrierResult = await options.barrier.beforeWrite()
  if (barrierResult !== 'PROCEED') {
    return { writeCalled: false as const, barrierResult }
  }
  try {
    const result = await options.call()
    try {
      await options.refreshAfterSuccess(result)
      return {
        writeCalled: true as const,
        succeeded: true as const,
        result,
        refreshSucceeded: true as const
      }
    } catch {
      options.barrier.register(() => options.refreshAfterSuccess(result))
      return {
        writeCalled: true as const,
        succeeded: true as const,
        result,
        refreshSucceeded: false as const
      }
    }
  } catch (error) {
    const recovery = resolveArrivalCommandFailure(error)
    let refreshSucceeded: boolean | null = null
    if (recovery === 'REFRESH_AGGREGATE' || recovery === 'REFRESH_OWNER_FACTS') {
      try {
        await options.refreshAfterConflict()
        refreshSucceeded = true
      } catch {
        refreshSucceeded = false
      }
    }
    return {
      writeCalled: true as const,
      succeeded: false as const,
      recovery,
      refreshSucceeded
    }
  }
}

export type ArrivalIntentExecution =
  | {
      commandSucceeded: false
      recovery: ArrivalCommandFailure
      keyRetained: boolean
      refreshSucceeded: boolean | null
      retryRefresh: null
    }
  | {
      commandSucceeded: true
      recovery: null
      keyRetained: false
      refreshSucceeded: boolean
      retryRefresh: (() => Promise<void>) | null
    }

export const runArrivalIntent = async <T>(options: {
  intent: string
  store: ArrivalIntentStore
  call: (key: string) => Promise<T>
  refreshAfterSuccess: (result: T) => Promise<void>
  refreshAfterFailure: () => Promise<void>
}): Promise<ArrivalIntentExecution> => {
  const key = options.store.key(options.intent)
  let result: T
  try {
    result = await options.call(key)
  } catch (error) {
    const recovery = resolveArrivalCommandFailure(error)
    const keyRetained = recovery === 'RETAIN_INTENT'
    if (!keyRetained) options.store.complete(options.intent)
    let refreshSucceeded: boolean | null = null
    if (recovery === 'REFRESH_AGGREGATE' || recovery === 'REFRESH_OWNER_FACTS') {
      try {
        await options.refreshAfterFailure()
        refreshSucceeded = true
      } catch {
        refreshSucceeded = false
      }
    }
    return { commandSucceeded: false, recovery, keyRetained, refreshSucceeded, retryRefresh: null }
  }
  options.store.complete(options.intent)
  try {
    await options.refreshAfterSuccess(result)
    return {
      commandSucceeded: true,
      recovery: null,
      keyRetained: false,
      refreshSucceeded: true,
      retryRefresh: null
    }
  } catch {
    return {
      commandSucceeded: true,
      recovery: null,
      keyRetained: false,
      refreshSucceeded: false,
      retryRefresh: () => options.refreshAfterSuccess(result)
    }
  }
}

export type ArrivalIntentStore = ReturnType<typeof createArrivalIntentStore>
export type ArrivalWriteBarrierResult = 'PROCEED' | 'REFRESHED' | 'REFRESH_FAILED'

export const createArrivalWriteBarrier = () => {
  let pendingRefresh: (() => Promise<void>) | null = null
  return {
    register(refresh: () => Promise<void>) {
      pendingRefresh = refresh
    },
    hasPending() {
      return pendingRefresh !== null
    },
    async beforeWrite(): Promise<ArrivalWriteBarrierResult> {
      if (!pendingRefresh) return 'PROCEED'
      try {
        await pendingRefresh()
        pendingRefresh = null
        return 'REFRESHED'
      } catch {
        return 'REFRESH_FAILED'
      }
    }
  }
}

export const runArrivalGuardedWrite = async <T>(options: {
  barrier: ReturnType<typeof createArrivalWriteBarrier>
  call: () => Promise<T>
  refreshAfterConflict: () => Promise<void>
}) => {
  const barrierResult = await options.barrier.beforeWrite()
  if (barrierResult !== 'PROCEED') {
    return { writeCalled: false as const, barrierResult }
  }
  try {
    return { writeCalled: true as const, succeeded: true as const, result: await options.call() }
  } catch (error) {
    const recovery = resolveArrivalCommandFailure(error)
    let refreshSucceeded: boolean | null = null
    if (recovery === 'REFRESH_AGGREGATE' || recovery === 'REFRESH_OWNER_FACTS') {
      try {
        await options.refreshAfterConflict()
        refreshSucceeded = true
      } catch {
        refreshSucceeded = false
      }
    }
    return {
      writeCalled: true as const,
      succeeded: false as const,
      recovery,
      refreshSucceeded
    }
  }
}

export type ArrivalIntentExecution =
  | {
      commandSucceeded: false
      recovery: ArrivalCommandFailure
      keyRetained: boolean
      refreshSucceeded: boolean | null
      retryRefresh: null
    }
  | {
      commandSucceeded: true
      recovery: null
      keyRetained: false
      refreshSucceeded: boolean
      retryRefresh: (() => Promise<void>) | null
    }

export const runArrivalIntent = async <T>(options: {
  intent: string
  store: ArrivalIntentStore
  call: (key: string) => Promise<T>
  refreshAfterSuccess: (result: T) => Promise<void>
  refreshAfterFailure: () => Promise<void>
}): Promise<ArrivalIntentExecution> => {
  const key = options.store.key(options.intent)
  let result: T
  try {
    result = await options.call(key)
  } catch (error) {
    const recovery = resolveArrivalCommandFailure(error)
    const keyRetained = recovery === 'RETAIN_INTENT'
    if (!keyRetained) options.store.complete(options.intent)
    let refreshSucceeded: boolean | null = null
    if (recovery === 'REFRESH_AGGREGATE' || recovery === 'REFRESH_OWNER_FACTS') {
      try {
        await options.refreshAfterFailure()
        refreshSucceeded = true
      } catch {
        refreshSucceeded = false
      }
    }
    return { commandSucceeded: false, recovery, keyRetained, refreshSucceeded, retryRefresh: null }
  }
  options.store.complete(options.intent)
  try {
    await options.refreshAfterSuccess(result)
    return {
      commandSucceeded: true,
      recovery: null,
      keyRetained: false,
      refreshSucceeded: true,
      retryRefresh: null
    }
  } catch {
    return {
      commandSucceeded: true,
      recovery: null,
      keyRetained: false,
      refreshSucceeded: false,
      retryRefresh: () => options.refreshAfterSuccess(result)
    }
  }
}

export const truncateEvidenceName = (name: string, maximum: number) => {
  if (name.length <= maximum) return name
  return `${name.slice(0, Math.max(0, maximum - 1))}…`
}

const successorReasons: Record<string, string> = {
  SUPPLEMENT: '补签后继',
  CORRECTION: '信息纠正后继',
  DIFFERENCE_CLOSURE: '差异关闭后继',
  EXEMPTION_INVALIDATION: '豁免失效后继'
}

export const successorReasonPresentation = (reason?: string | null) =>
  reason ? successorReasons[reason] || reason : '初始到货批次'

export type ArrivalResolution = 'SUPPLEMENT' | 'KEEP_REJECTED' | 'EXEMPT' | 'CLOSE'
export const arrivalResolutionOptions = (
  aggregateStatus: string,
  resolutionStatus: string
): ArrivalResolution[] => {
  if (aggregateStatus === 'DIFFERENCE_PENDING' && resolutionStatus === 'OPEN') {
    return ['SUPPLEMENT', 'KEEP_REJECTED', 'EXEMPT', 'CLOSE']
  }
  if (aggregateStatus === 'CONFIRMED' && resolutionStatus === 'REJECTED') {
    return ['SUPPLEMENT', 'EXEMPT', 'CLOSE']
  }
  return []
}

export const arrivalAcceptanceLayout = (width: number): ArrivalLayout => {
  if (width <= 767) return 'mobile'
  if (width <= 1023) return 'tablet'
  if (width <= 1279) return 'desktop'
  return 'wide'
}
