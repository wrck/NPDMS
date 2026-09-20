import request from '@/config/axios'

/** 阶段施工计划批次（PLN-01/04，Demo 3.1） */
export interface StagePlanTaskVO {
  taskId: number
  parentTaskId?: number
  stageCode: string
  name: string
  version: number
  planStart?: string
  planEnd?: string
  acceptanceTime?: string
}
export interface StagePlanBatchVO {
  tasks?: StagePlanTaskVO[]
  inputSnapshot?: string

  id?: number
  projectId?: number
  status?: number
  durationRevisionId?: number
  baselineStart?: string
  baselineEnd?: string
  remark?: string
  bpmProcessInstanceId?: string
  submittedAt?: string
  effectiveAt?: string
  rejectReason?: string
  version?: number
  createTime?: string
  items: StagePlanItemVO[]
}

/** 阶段计划明细行 */
export interface StagePlanItemVO {
  id?: number
  phaseId?: number
  phaseCode?: string
  phaseName?: string
  sort?: number
  suggestedStart?: string
  suggestedEnd?: string
  planStart?: string
  planEnd?: string
  remark?: string
}

// 查询阶段施工计划批次分页
export const getStagePlanBatchPage = (params: PageParam & { projectId?: number; status?: number }) => {
  return request.get({ url: '/pms/imp-stage-plan/page', params })
}

// 查询阶段施工计划批次详情（含阶段明细与工期基线窗口）
export const getStagePlanBatch = (id: number) => {
  return request.get({ url: '/pms/imp-stage-plan/get', params: { id } })
}

// 创建阶段施工计划草稿（读取项目阶段事实生成明细）
export const createStagePlanBatch = (projectId: number) => {
  return request.post({ url: '/pms/imp-stage-plan/create', params: { projectId } })
}

// 按冻结路径和阶段占比倒排，直签项目以回款节点计划验收时间为锚点。
export const autoEstimateStagePlanBatch = (id: number) => {
  return request.put({ url: `/pms/imp-stage-plan/${id}/auto-estimate` })
}

// 人工调整阶段计划时间（仅草稿/已驳回，校验重叠与基线窗口）
export const updateStagePlanItems = (data: {
  id: number
  version: number
  remark?: string
  tasks?: StagePlanTaskVO[]
  items: { id: number; planStart: string; planEnd: string; remark?: string }[]
}) => {
  return request.put({ url: '/pms/imp-stage-plan/update-items', data })
}

// 提交审批（发起 BPM 流程，审批通过后生效并回写阶段计划日期）
export const submitStagePlanBatch = (id: number, approverUserId: number) => {
  return request.put({ url: `/pms/imp-stage-plan/${id}/submit`, params: { approverUserId } })
}

/** 超期阶段行（PLN-03，仅生效计划版本的计算结果） */
export interface StagePlanOverdueRowVO {
  projectId?: number
  batchId?: number
  phaseId?: number
  phaseName?: string
  planEnd?: string
  overdueDays?: number
  phaseStatus?: number
}

/** 超期统计汇总 */
export interface StagePlanOverdueSummaryVO {
  overdueProjectCount?: number
  overdueStageCount?: number
}

// 超期阶段清单（仅生效计划版本，计算结果不允许人工修改）
export const getOverdueStages = (projectId?: number) => {
  return request.get({ url: '/pms/imp-stage-plan/overdue-stages', params: { projectId } })
}

// 超期统计汇总（与超期阶段清单同口径）
export const getOverdueSummary = (projectId?: number) => {
  return request.get({ url: '/pms/imp-stage-plan/overdue-summary', params: { projectId } })
}
