export type ArrivalLayout = 'mobile' | 'tablet' | 'desktop' | 'wide'
export type ArrivalCommandFailure = 'REFRESH_AGGREGATE' | 'RETAIN_INTENT' | 'SURFACE_ERROR'
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
  if (response.status === 409 && response.data?.data?.recoveryAction === 'REFRESH_AGGREGATE') {
    return 'REFRESH_AGGREGATE'
  }
  return 'SURFACE_ERROR'
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
