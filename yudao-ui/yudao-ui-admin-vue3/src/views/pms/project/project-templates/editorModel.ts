import type {
  TemplateDefinitionContent,
  StageTransition
} from '@/api/pms/project/project-templates'
import type { ValidationIssue } from '@/api/pms/project/project-templates/definitions'

// PM-03: cloning never fills missing graph facts or rewrites historical contracts.
export const emptyContent = (): TemplateDefinitionContent => ({
  stages: [],
  tasks: [],
  milestones: [],
  deliverables: [],
  gates: []
})
export const cloneContent = (content?: TemplateDefinitionContent): TemplateDefinitionContent => ({
  ...emptyContent(),
  ...JSON.parse(JSON.stringify(content ?? {}))
})
export const relationsFor = (
  content: TemplateDefinitionContent,
  code: string,
  direction: 'from' | 'to'
) =>
  (content.transitions ?? []).filter(
    (edge) => (direction === 'from' ? edge.fromStageCode : edge.toStageCode) === code
  )
export const addRelation = (
  content: TemplateDefinitionContent,
  code = '',
  direction: 'from' | 'to' = 'from'
) => {
  const edge: StageTransition = {
    transitionCode: '',
    fromStageCode: direction === 'from' ? code : '',
    toStageCode: direction === 'to' ? code : '',
    priority: 0,
    default: false,
    revisionNo: 1
  }
  ;(content.transitions ??= []).push(edge)
}
export const graphIssues = (content: TemplateDefinitionContent): ValidationIssue[] => {
  const issues: ValidationIssue[] = []
  const add = (field: string, code: string, message: string) =>
    issues.push({ field, code, message })
  const stages = content.stages
  const edges = content.transitions ?? []
  if (content.transitions == null)
    add(
      'transitions',
      'MISSING_GRAPH',
      '未提供显式关系图；历史内容仍可读，不按阶段编号或排序补图。'
    )
  if (stages.filter((s) => s.start === true).length !== 1)
    add('stages', 'START_COUNT', '必须显式指定唯一开始阶段。')
  if (!stages.some((s) => s.terminal === true))
    add('stages', 'TERMINAL_REQUIRED', '必须显式指定正常收口阶段。')
  const codes = new Set<string>()
  stages.forEach((s, index) => {
    const path = `stages[${index}]`
    if (!s.stageCode || codes.has(s.stageCode))
      add(path + '.stageCode', 'DUPLICATE_OR_EMPTY', '阶段编码不可为空或重复。')
    codes.add(s.stageCode)
    if (typeof s.start !== 'boolean' || typeof s.terminal !== 'boolean')
      add(path, 'FLAGS_REQUIRED', '开始与收口均须显式选择是或否。')
    const outgoing = relationsFor(content, s.stageCode, 'from')
    if (!s.terminal && !outgoing.length)
      add(path, 'NO_SUCCESSOR', `${s.stageCode} 非收口阶段必须配置后置关系。`)
    if (s.terminal && outgoing.length)
      add(path, 'TERMINAL_HAS_SUCCESSOR', `${s.stageCode} 收口阶段不能有出向关系。`)
    if (s.start && relationsFor(content, s.stageCode, 'to').length)
      add(path, 'START_HAS_PREDECESSOR', '开始阶段不能有前置关系。')
    if (outgoing.filter((e) => e.default).length > 1)
      add(path, 'MULTIPLE_DEFAULTS', `${s.stageCode} 最多允许一个默认分支。`)
    const priorities = new Set<number>()
    outgoing
      .filter((e) => !e.default && e.conditionRuleRevisionId == null)
      .forEach((e) => {
        if (priorities.has(e.priority))
          add(path, 'PRIORITY_CONFLICT', '无条件分支不能使用相同优先级。')
        priorities.add(e.priority)
      })
  })
  const edgeCodes = new Set<string>()
  edges.forEach((edge, index) => {
    const path = `transitions[${index}]`
    if (!edge.transitionCode || edgeCodes.has(edge.transitionCode))
      add(path + '.transitionCode', 'DUPLICATE_OR_EMPTY', '关系编码不可为空或重复。')
    edgeCodes.add(edge.transitionCode)
    if (!codes.has(edge.fromStageCode) || !codes.has(edge.toStageCode))
      add(path, 'DANGLING_EDGE', '来源或目标阶段不存在。')
    if (edge.default && edge.conditionRuleRevisionId != null)
      add(path, 'DEFAULT_HAS_CONDITION', '默认分支不能同时设置条件；请显式清除条件或取消默认。')
    if (
      !Number.isInteger(edge.priority) ||
      !Number.isInteger(edge.revisionNo) ||
      edge.revisionNo < 1 ||
      typeof edge.default !== 'boolean'
    )
      add(path, 'REQUIRED', '优先级、正整数关系版本与显式默认标记必填。')
  })
  const visited = new Set<string>()
  const visiting = new Set<string>()
  const walk = (code: string) => {
    if (visiting.has(code)) {
      add('transitions', 'CYCLE', `阶段 ${code} 存在循环。`)
      return
    }
    if (visited.has(code) || !codes.has(code)) return
    visiting.add(code)
    relationsFor(content, code, 'from').forEach((e) => walk(e.toStageCode))
    visiting.delete(code)
    visited.add(code)
  }
  const start = stages.find((s) => s.start)
  if (start) walk(start.stageCode)
  stages.forEach((s) => {
    if (!visited.has(s.stageCode))
      add('stages', 'UNREACHABLE', `${s.stageCode} 无法从开始阶段到达。`)
  })
  return issues
}
export const errorText = (error: any) => {
  const message =
    error?.response?.data?.msg ||
    error?.data?.msg ||
    error?.msg ||
    error?.message ||
    (typeof error === 'string' ? error : '')
  return !message || message === 'error'
    ? '请求失败，配置未确认生效。请保留编辑内容，检查依赖或重新读取版本后再操作。'
    : message
}
// Retrying an unchanged intent after a transport failure reuses the same key.
export const commandIntent = () => {
  let signature = ''
  let key = ''
  return {
    key(intent: unknown) {
      const next = JSON.stringify(intent)
      if (next !== signature || !key) {
        signature = next
        key = crypto.randomUUID()
      }
      return key
    },
    clear() {
      signature = ''
      key = ''
    }
  }
}
