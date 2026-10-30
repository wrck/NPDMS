const conditions: Record<string, string> = {
  CLOSURE_POLICY_FROZEN: '本项目闭环规则已冻结',
  PROJECT_ACTIVE: '项目处于进行中',
  DESCENDANTS_CLOSED: '下级项目已满足闭环条件',
  TERMINAL_STAGE: '已到达最后实际阶段',
  STAGE_COMPLETION: '阶段完成条件已满足',
  ALL_TASKS_DONE: '必需任务均已完成',
  TASK_CONTRACT_CURRENT: '任务执行约定有效',
  TASK_BUSINESS_FACTS: '任务关联业务结果已核验',
  TASK_NATIVE_CONTRACT: '任务完成方式符合执行约定',
  TASK_END_USER_FACTS: '用户信息录入已完成',
  TASK_RESULT_EVIDENCE: '任务结果证据已核验',
  EXIT_GATE_REFERENCES: '退出条件已关联核验来源',
  EXIT_GATE_OWNER_UNAVAILABLE: '退出条件来源可用',
  EXIT_GATE_SATISFIED: '退出条件已满足'
}
export const closureConditionLabel = (code: string) => conditions[code] || '其他闭环条件'
export const closureReasonLabel = (reason?: string) =>
  !reason
    ? '—'
    : conditions[reason] ||
      (/^[A-Z][A-Z0-9_:.-]*$/.test(reason) ? '请查看技术详情中的原始校验原因' : reason)
export const closureNodeLabel = (key: string) =>
  ({ serviceManagerReview: '服务经理审核', materialReview: '材料审核' })[key] || '其他审核节点'
