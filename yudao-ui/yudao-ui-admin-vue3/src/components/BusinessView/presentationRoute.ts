import { isBusinessViewId } from '@/api/pms/platform/business-view/ids'

export interface PagePresentation { pageUrl: string; query: Readonly<Record<string, string>> }
/** The single code-owned directory for embedded routes; the Vue registry adds only static components. */
export const businessPageRoutes = {
  ACC_ACCEPTANCE_REPORT: { componentKey: 'ACC_ACCEPTANCE_REPORT', componentVersion: '1', ownerContext: 'ACC', entityType: 'ACCEPTANCE', viewSource: 'PAGE', pageUrl: '/pms/project/acceptance-report' },
  SOL_SITE_SURVEY: { componentKey: 'SOL_SITE_SURVEY', componentVersion: '1', ownerContext: 'SOL', entityType: 'SITE_SURVEY', viewSource: 'PAGE', pageUrl: '/pms/delivery-business/site-survey' },
  PROJ_REQUIREMENT_ANALYSIS: { componentKey: 'PROJ_REQUIREMENT_ANALYSIS', componentVersion: '1', ownerContext: 'SOL', entityType: 'REQUIREMENT_ANALYSIS', viewSource: 'PAGE', pageUrl: '/pms/delivery-business/requirement-analysis' },
  PLN_CONSTRUCTION_PLAN: { componentKey: 'PLN_CONSTRUCTION_PLAN', componentVersion: '1', ownerContext: 'PLN', entityType: 'CONSTRUCTION_PLAN', viewSource: 'PAGE', pageUrl: '/pms/delivery-business/duration' }
} as const

export interface PageIdentity { ownerContext: string; entityType: string; componentKey: string; componentVersion: string; viewSource: string }
export interface PageContext { project?: { id?: string | number }; businessObjectId?: string | number }

/** Only echoes the selected Owner context. Never use these values to construct props or command input. */
export function validatePagePresentation(value: PagePresentation, path: string | undefined, context: PageContext): string {
  if (!path || value.pageUrl !== path || !value.query || typeof value.query !== 'object' || Array.isArray(value.query))
    throw new Error('PRESENTATION_ROUTE_MISMATCH')
  if (!isBusinessViewId(context.project?.id)) throw new Error('PRESENTATION_PROJECT_REQUIRED')
  const expected: Record<string, string | undefined> = {
    projectId: String(context.project.id),
    objectId: context.businessObjectId == null ? undefined : String(context.businessObjectId)
  }
  if (context.businessObjectId != null && !isBusinessViewId(context.businessObjectId)) throw new Error('PRESENTATION_OBJECT_INVALID')
  const parameters = Object.entries(value.query).map(([key, item]) => {
    if (!Object.hasOwn(expected, key) || typeof item !== 'string' || item !== expected[key])
      throw new Error('PRESENTATION_CONTEXT_MISMATCH')
    return `${encodeURIComponent(key)}=${encodeURIComponent(item)}`
  })
  return path + (parameters.length ? `?${parameters.join('&')}` : '')
}

export function pagePresentationKey(value?: PagePresentation): string {
  return value ? JSON.stringify([value.pageUrl, Object.entries(value.query).sort(([a], [b]) => a.localeCompare(b))]) : ''
}

export function validateBusinessPage(target: { registration: PageIdentity; resolvedContext: PageContext }, presentation: PagePresentation): void {
  const view = target.registration
  const matches = Object.values(businessPageRoutes).filter(item => item.componentKey === view.componentKey
    && item.componentVersion === view.componentVersion && item.ownerContext === view.ownerContext
    && item.entityType === view.entityType && item.viewSource === view.viewSource)
  if (matches.length !== 1) throw new Error('PRESENTATION_ROUTE_NOT_INSTALLED')
  validatePagePresentation(presentation, matches[0].pageUrl, target.resolvedContext)
}
