import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'

// Hide independent application UI without changing published schemas or stored survey values.
const hiddenApplicationComponents = new Set([
  'SurveyOutsourceShortcut', 'SurveyPreparationBranches', 'SurveyProcurementLinks', 'SurveyMaterialSelector'
])
export const currentSurveyRules = (rules: any[]): any[] => rules
  .filter(rule => !hiddenApplicationComponents.has(rule?.type))
  .map(rule => Array.isArray(rule?.children)
    ? { ...rule, children: currentSurveyRules(rule.children) }
    : rule)

export const hasStructuredSurveyLocation = (location?: LocationMaintainRequest) =>
  !!location &&
  (Object.values(location.address || {}).some(Boolean) ||
    !!(
      location.site?.id ||
      location.site?.code ||
      location.site?.name ||
      location.siteLocation?.id ||
      location.siteLocation?.code
    ))

// Only the Owner-provided catalog and binding decide where a form field is stored.
export const extractSurveyValues = (survey: SiteSurveyVO) => {
  const values = { ...(survey.businessValues || {}), ...(survey.extensionValues || {}) }
  const types = new Map((survey.fieldCatalog || []).map(field => [field.code, field.type]))
  return Object.fromEntries(Object.entries(survey.fieldBindings || {}).map(([key, code]) =>
    [key, values[code] ?? (['TEXT_LIST', 'OBJECT_LIST'].includes(types.get(code) || '') ? [] : null)]))
}

export const mergeSurveyValues = (survey: SiteSurveyVO, values: Record<string, any>): SiteSurveyVO => {
  const fixed = new Set((survey.fieldCatalog || []).map(field => field.code))
  const businessValues = { ...(survey.businessValues || {}) }
  const extensionValues = { ...(survey.extensionValues || {}) }
  for (const [key, value] of Object.entries(values)) {
    const code = survey.fieldBindings?.[key]
    if (!code) throw new Error(`工勘字段未绑定：${key}`)
    if (fixed.has(code)) businessValues[code] = value
    else extensionValues[code] = value
  }
  return { ...survey, businessValues, extensionValues }
}
