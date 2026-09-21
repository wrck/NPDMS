import type { AcceptanceActivityVO } from '@/api/pms/acceptance/acceptance-report'

export const activityAllowsReportWrite = (activity?: AcceptanceActivityVO) => activity?.activityStatus === 'PENDING'
  || activity?.originKind === 'DIRECT' && activity.activityStatus === 'COMPLETED'
