export const stageLabels: Record<string, string> = {
  P2: '割接等级确认', P3: '调研及风险考察', P4: '割接方案', P5: '割接审批', P6: '割接跟踪及闭环'
}
export const statusLabels: Record<string, string> = {
  GRADE_CONFIRMING: '人工分级中', SURVEYING: '现场调研', PLAN_DRAFTING: '方案编制',
  APPROVING: '审批中', CLOSURE_IN_PROGRESS: '跟踪与闭环', ARCHIVED: '已归档'
}
export const sourceLabels: Record<string, string> = {
  SELF_CREATED: '一线自建', ITR: 'ITR', PROJECT_EVENT: 'PMS'
}
