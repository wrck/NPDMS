import request from '@/config/axios'

/** 3.1 工期建议计划时间规则（Demo 页面9 / Excel 3.1） */
export interface StageSuggestionRuleVO {
  id?: number
  stageCode: string
  signingMethod?: string | null
  sourceType: string
  referenceStageCode?: string | null
  offsetMonths: number
  offsetDays: number
  remark?: string | null
  enabled?: boolean
  createTime?: string
}

export const getStageSuggestionRulePage = (params: PageParam & { stageCode?: string; signingMethod?: string; sourceType?: string }) => {
  return request.get({ url: '/pms/stage-plan-suggestion-rule/page', params })
}

export const getStageSuggestionRule = (id: number) => {
  return request.get({ url: '/pms/stage-plan-suggestion-rule/get', params: { id } })
}

export const createStageSuggestionRule = (data: StageSuggestionRuleVO) => {
  return request.post({ url: '/pms/stage-plan-suggestion-rule/create', data })
}

export const updateStageSuggestionRule = (data: StageSuggestionRuleVO) => {
  return request.put({ url: '/pms/stage-plan-suggestion-rule/update', data })
}

export const deleteStageSuggestionRule = (id: number) => {
  return request.delete({ url: '/pms/stage-plan-suggestion-rule/delete', params: { id } })
}
