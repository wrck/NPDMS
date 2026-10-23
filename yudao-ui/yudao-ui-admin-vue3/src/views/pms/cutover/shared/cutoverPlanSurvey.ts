import type { CutoverChecklistItem, CutoverPlanFileFact } from '@/api/pms/cutover'

const answer = (item?: CutoverChecklistItem) => {
  if (!item?.applicable || !item.currentResult?.answerSnapshot) return null
  try { return JSON.parse(item.currentResult.answerSnapshot) } catch { return null }
}

export const surveyPlanDescription = (items: CutoverChecklistItem[]) => {
  const background = answer(items.find(item => item.stableItemKey === 'SURVEY_CUTOVER_BACKGROUND'))?.value?.backgroundDescription
  const business = answer(items.find(item => item.stableItemKey === 'SURVEY_BUSINESS_SUMMARY'))?.value
  return [[background, '割接背景'], [business, '涉及业务']]
    .filter(([value]) => typeof value === 'string' && value.trim())
    .map(([value, label]) => `${label}：${value}`).join('\n')
}

export const surveyPlanFile = (items: CutoverChecklistItem[], key: string) => {
  const item = items.find(row => row.stableItemKey === key && row.currentResult?.resultSourceCode === 'MANUAL')
  const file = answer(item)?.file as CutoverPlanFileFact | undefined
  return item && file?.artifactId && file.versionNo && file.referenceKey ? { itemId: item.itemId, file } : null
}
