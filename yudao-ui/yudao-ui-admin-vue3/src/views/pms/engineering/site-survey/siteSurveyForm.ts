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
export const extractSurveyValues = (survey: SiteSurveyVO) => ({
  ...Object.fromEntries(surveyFields.map((key) => [key, survey[key] ?? ''])),
  ...(survey.formExtraValues || {})
})
export const mergeSurveyValues = (
  survey: SiteSurveyVO,
  values: Record<string, any>
): SiteSurveyVO => ({
  ...survey,
  ...Object.fromEntries(surveyFields.map((key) => [key, values[key] ?? ''])),
  formExtraValues: Object.fromEntries(
    Object.entries(values).filter(([key]) => key.startsWith('extra_'))
  )
})
