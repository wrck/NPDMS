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

export const currentSurveyRules = (rules: any[]): any[] => {
  const otherPort = rules.find(rule => rule?.field === 'networkPort')
  return rules
    .filter(
      (rule) =>
        !retiredSupplementFields.has(rule?.field) &&
        rule?.props?.innerText !== '原工勘补充说明（保留原字段内容）'
    )
    .map((rule) => {
      const current = Array.isArray(rule?.children)
        ? { ...rule, children: currentSurveyRules(rule.children) } : rule
      if (!['extra_networkPortTypes', 'networkPortTypes'].includes(rule?.field)) return current
      const options = [...(rule.options || [])]
      if (!options.some(option => option.value === '100GE_FIBER')) {
        const other = options.findIndex(option => option.value === 'OTHER')
        options.splice(other < 0 ? options.length : other, 0, { label: '100G光口', value: '100GE_FIBER' })
      }
      if (!options.some(option => option.value === 'OTHER')) options.push({ label: '其他', value: 'OTHER' })
      return { ...current, options, control: [...(rule.control || []), {
        handle: (value: unknown) => Array.isArray(value) && value.includes('OTHER'),
        rule: [{ ...otherPort, type: 'input', field: 'networkPort', title: '其他网口说明',
          props: { ...otherPort?.props, type: 'textarea', rows: 2, maxlength: 500, showWordLimit: true,
            placeholder: '请输入其他网口类型' } }]
      }] }
    })
}

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
