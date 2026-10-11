import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'

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

export const surveyFields = [
  'powerSupply',
  'cabinet',
  'networkPort',
  'fiber',
  'module',
  'cable',
  'ground',
  'constructionResource',
  'conclusion',
  'remark'
] as const
const retiredSupplementFields = new Set<string>([
  'powerSupply',
  'cabinet',
  'networkPort',
  'fiber',
  'module',
  'cable',
  'ground',
  'constructionResource'
])

export const currentSurveyRules = (rules: any[]): any[] =>
  rules
    .filter(
      (rule) =>
        !retiredSupplementFields.has(rule?.field) &&
        rule?.props?.innerText !== '原工勘补充说明（保留原字段内容）'
    )
    .map((rule) =>
      Array.isArray(rule?.children)
        ? { ...rule, children: currentSurveyRules(rule.children) }
        : rule
    )

export const extractSurveyValues = (survey: SiteSurveyVO) => ({
  ...Object.fromEntries(surveyFields.map((key) => [key, survey[key] ?? ''])),
  ...(survey.formExtraValues || {})
})
export const mergeSurveyValues = (
  survey: SiteSurveyVO,
  values: Record<string, any>
): SiteSurveyVO => ({
  ...survey,
  ...Object.fromEntries(surveyFields.map((key) => [key, values[key] ?? survey[key] ?? ''])),
  formExtraValues: Object.fromEntries(
    Object.entries(values).filter(([key]) => key.startsWith('extra_'))
  )
})
