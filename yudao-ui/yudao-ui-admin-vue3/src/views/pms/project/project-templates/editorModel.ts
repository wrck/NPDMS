import type {
  DesignerTransitionNode,
  TemplateDesignerDocument
} from '@/api/pms/project/project-templates'
import { cloneDesignerDocument, emptyDesignerDocument } from '@/api/pms/project/project-templates'
import type { ValidationIssue } from '@/api/pms/project/project-templates/definitions'

// DesignerDocument is the only writable template model. These helpers never infer missing graph facts.
export const emptyContent = emptyDesignerDocument
export const cloneContent = cloneDesignerDocument

export const relationsFor = (
  content: TemplateDesignerDocument,
  code: string,
  direction: 'from' | 'to'
) =>
  content.transitions.filter(
    (edge) => (direction === 'from' ? edge.fromStageCode : edge.toStageCode) === code
  )

const nextTransitionCode = (content: TemplateDesignerDocument, seed: string) => {
  const used = new Set(content.transitions.map((edge) => edge.code))
  const base = `TR_${(seed || 'EDGE').replace(/[^A-Za-z0-9_]/g, '').toUpperCase()}`
  let index = 1
  while (used.has(`${base}_${index}`)) index++
  return `${base}_${index}`
}

export const addRelation = (
  content: TemplateDesignerDocument,
  code = '',
  direction: 'from' | 'to' = 'from'
) => {
  const transitionCode = nextTransitionCode(content, code)
  const edge: DesignerTransitionNode = {
    edgeKey: `transition:${crypto.randomUUID()}`,
    code: transitionCode,
    fromStageCode: direction === 'from' ? code : '',
    toStageCode: direction === 'to' ? code : '',
    priority: 0,
    defaultBranch: false
  }
  content.transitions.push(edge)
}

export const graphIssues = (content: TemplateDesignerDocument): ValidationIssue[] => {
  const issues: ValidationIssue[] = []
  const add = (field: string, code: string, message: string) =>
    issues.push({ field, code, message })
  const stages = content.stages
  const edges = content.transitions
  if (stages.filter((s) => s.start === true).length !== 1)
    add('stages', 'START_COUNT', '必须显式指定唯一开始阶段。')
  if (!stages.some((s) => s.terminal === true))
    add('stages', 'TERMINAL_REQUIRED', '必须显式指定正常收口阶段。')

  const codes = new Set<string>()
  const nodeKeys = new Set<string>()
  stages.forEach((stage, index) => {
    const path = `stages[${index}]`
    if (!stage.nodeKey || nodeKeys.has(stage.nodeKey))
      add(path + '.nodeKey', 'DUPLICATE_OR_EMPTY', '阶段稳定节点键不可为空或重复。')
    nodeKeys.add(stage.nodeKey)
    if (!stage.code || codes.has(stage.code))
      add(path + '.code', 'DUPLICATE_OR_EMPTY', '阶段编码不可为空或重复。')
    codes.add(stage.code)
    if (typeof stage.start !== 'boolean' || typeof stage.terminal !== 'boolean')
      add(path, 'FLAGS_REQUIRED', '开始与收口均须显式选择是或否。')
    const outgoing = relationsFor(content, stage.code, 'from')
    if (!stage.terminal && !outgoing.length)
      add(path, 'NO_SUCCESSOR', `${stage.code} 非收口阶段必须配置后置关系。`)
    if (stage.terminal && outgoing.length)
      add(path, 'TERMINAL_HAS_SUCCESSOR', `${stage.code} 收口阶段不能有出向关系。`)
    if (stage.start && relationsFor(content, stage.code, 'to').length)
      add(path, 'START_HAS_PREDECESSOR', '开始阶段不能有前置关系。')
    if (outgoing.filter((edge) => edge.defaultBranch).length > 1)
      add(path, 'MULTIPLE_DEFAULTS', `${stage.code} 最多允许一个默认分支。`)
  })

  const edgeCodes = new Set<string>()
  const edgeKeys = new Set<string>()
  edges.forEach((edge, index) => {
    const path = `transitions[${index}]`
    if (!edge.edgeKey || edgeKeys.has(edge.edgeKey))
      add(path + '.edgeKey', 'DUPLICATE_OR_EMPTY', '关系稳定键不可为空或重复。')
    edgeKeys.add(edge.edgeKey)
    if (!edge.code || edgeCodes.has(edge.code))
      add(path + '.code', 'DUPLICATE_OR_EMPTY', '关系编码不可为空或重复。')
    edgeCodes.add(edge.code)
    if (!codes.has(edge.fromStageCode) || !codes.has(edge.toStageCode))
      add(path, 'DANGLING_EDGE', '来源或目标阶段不存在。')
    if (edge.fromStageCode === edge.toStageCode)
      add(path, 'SELF_EDGE', '阶段不能连接到自身。')
    if (edge.defaultBranch && edge.condition?.expression)
      add(path, 'DEFAULT_HAS_CONDITION', '默认分支不能同时配置条件。')
    if (!Number.isInteger(edge.priority) || typeof edge.defaultBranch !== 'boolean')
      add(path, 'REQUIRED', '优先级与显式默认标记必填。')
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
    relationsFor(content, code, 'from').forEach((edge) => walk(edge.toStageCode))
    visiting.delete(code)
    visited.add(code)
  }
  const start = stages.find((stage) => stage.start)
  if (start) walk(start.code)
  stages.forEach((stage) => {
    if (!visited.has(stage.code))
      add('stages', 'UNREACHABLE', `${stage.code} 无法从开始阶段到达。`)
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
