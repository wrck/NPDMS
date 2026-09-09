const outsourcePath = '/pms/engineering/procurement/eng-outsource'
export const surveyPath = '/pms/engineering/preparation/eng-site-survey'
export const positiveShortcutId = (value: unknown): number | undefined => {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : undefined
}
export const outsourceShortcutRoute = (surveyId: number) => ({
  path: outsourcePath,
  query: { siteSurveyId: String(surveyId) }
})
export const outsourceDetailUrl = (id: number) =>
  `${outsourcePath}?requestId=${encodeURIComponent(String(id))}`
