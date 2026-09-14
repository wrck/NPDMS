import type { JsonObject, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import type {
  DeliveryCanvasEdge,
  DeliveryCanvasNode,
  DeliveryNodeKind
} from './TemplateFlowCanvas.vue'
import { constantRule, copyVersionRule, createVersionRule, ruleUses } from './versionRuleModel'

export type DeliveryNode =
  | TemplateDesignerDocument['stages'][number]
  | TemplateDesignerDocument['tasks'][number]
  | TemplateDesignerDocument['milestones'][number]
  | TemplateDesignerDocument['deliverables'][number]
  | TemplateDesignerDocument['gates'][number]
export const allNodes = (
  document: TemplateDesignerDocument
): { kind: DeliveryNodeKind; node: DeliveryNode }[] => [
  ...document.stages.map((node) => ({ kind: 'STAGE' as const, node })),
  ...document.tasks.map((node) => ({ kind: 'TASK' as const, node })),
  ...document.milestones.map((node) => ({ kind: 'MILESTONE' as const, node })),
  ...document.deliverables.map((node) => ({ kind: 'DELIVERABLE' as const, node })),
  ...document.gates.map((node) => ({ kind: 'GATE' as const, node }))
]

export function createDeliveryNode(
  document: TemplateDesignerDocument,
  kind: DeliveryNodeKind,
  stageCode: string | undefined,
  suggested: string | undefined,
  point: { x: number; y: number }
): DeliveryNode {
  if (kind === 'TASK' && !stageCode) throw new Error('请先进入阶段任务画布')
  const prefixes = { STAGE: 'STG', TASK: 'TSK', MILESTONE: 'MS', DELIVERABLE: 'DEL', GATE: 'GATE' }
  const names = {
    STAGE: '新阶段',
    TASK: '新任务',
    MILESTONE: '新里程碑',
    DELIVERABLE: '新交付件',
    GATE: '新门禁'
  }
  const keys = new Set(allNodes(document).map((item) => item.node.code))
  let sequence = 1
  while (keys.has(`${prefixes[kind]}${sequence}`)) sequence++
  const base = {
    nodeKey: `${kind.toLowerCase()}:${suggested ?? crypto.randomUUID()}`,
    code: `${prefixes[kind]}${sequence}`,
    name: names[kind]
  }
  let node: DeliveryNode
  if (kind === 'STAGE' || kind === 'TASK') {
    const predicate = kind === 'STAGE' ? 'STAGE_NATIVE_STATUS' : 'TASK_NATIVE_STATUS'
    const expression = { predicate, parameters: { requiredStatus: 'DONE' } }
    const common = {
      ...base,
      workBinding: { type: kind === 'STAGE' ? 'STAGE_NATIVE' : 'TASK_NATIVE', parameters: {} },
      permission: { policySnapshot: { requiredActions: ['VIEW'] } },
      completionRule: { expression },
      completionRuleKey: createVersionRule(document, `${base.name}·完成`, expression).key
    }
    if (kind === 'STAGE') {
      node = { ...common, start: true, terminal: false, sortOrder: document.stages.length }
      document.stages.push(node)
    } else {
      node = { ...common, stageCode: stageCode!, sortOrder: document.tasks.length }
      document.tasks.push(node)
    }
    Reflect.deleteProperty(node, 'completionRule')
  } else if (kind === 'MILESTONE') {
    node = { ...base, stageCode, timing: 'STAGE_EXIT', criteria: '' }
    document.milestones.push(node)
  } else if (kind === 'DELIVERABLE') {
    node = { ...base, stageCode, required: false }
    document.deliverables.push(node)
  } else {
    node = { ...base, stageCode, gateType: 'EXIT', references: [] }
    document.gates.push(node)
  }
  ;((document.layout ??= {}).nodes ??= {})[node.nodeKey] = point
  return node
}

export function captureInlineRules(document: TemplateDesignerDocument) {
  for (const node of [...document.stages, ...document.tasks]) {
    if (!node.completionRuleKey && node.completionRule?.expression)
      node.completionRuleKey = createVersionRule(
        document,
        `${node.name}·完成`,
        node.completionRule.expression
      ).key
    if (node.completionRuleKey && node.completionRule != null) Reflect.deleteProperty(node, 'completionRule')
  }
}

const childRules = (node: JsonObject): JsonObject[] =>
  Array.isArray(node.rules)
    ? node.rules.filter(
        (value): value is JsonObject =>
          !!value && typeof value === 'object' && !Array.isArray(value)
      )
    : []

function references(expression: JsonObject): { predicate: string; refCode: string }[] {
  if (expression.operator) return childRules(expression).flatMap(references)
  const parameters = expression.parameters
  if (!parameters || typeof parameters !== 'object' || Array.isArray(parameters)) return []
  return ['TASK', 'STATE'].includes(String(expression.predicate)) &&
    typeof parameters.refCode === 'string'
    ? [{ predicate: String(expression.predicate), refCode: parameters.refCode }]
    : []
}

export function dependencyEdges(document: TemplateDesignerDocument): DeliveryCanvasEdge[] {
  const result = new Map<string, DeliveryCanvasEdge>()
  for (const target of [...document.stages, ...document.tasks]) {
    const rule = document.rules?.find((item) => item.key === target.admissionRuleKey)
    if (!rule?.expression) continue
    for (const ref of references(rule.expression)) {
      const source =
        ref.predicate === 'TASK'
          ? document.tasks.find((node) => node.code === ref.refCode)
          : document.stages.find((node) => `${node.code}_COMPLETED` === ref.refCode)
      if (!source) continue
      const key = `${source.nodeKey}->${target.nodeKey}`
      result.set(key, { key, from: source.nodeKey, to: target.nodeKey, label: '准入引用' })
    }
  }
  return [...result.values()]
}

export function connectNodes(
  document: TemplateDesignerDocument,
  from: string,
  to: string,
  stageCode?: string
) {
  if (from === to) throw new Error('不能依赖自身完成后才能准入')
  const source = allNodes(document).find((item) => item.node.nodeKey === from)
  const target = [...document.stages, ...document.tasks].find((item) => item.nodeKey === to)
  if (stageCode && (!target || !('stageCode' in target) || target.stageCode !== stageCode))
    throw new Error('引用节点只能作为依赖来源，不能从当前子画布修改外部节点的准入')
  if (!source || !target || !['STAGE', 'TASK'].includes(source.kind))
    throw new Error('流程依赖连接阶段或任务；其他对象在节点规则中引用')
  if (dependencyEdges(document).some((edge) => edge.from === from && edge.to === to)) return
  let rule = document.rules?.find((item) => item.key === target.admissionRuleKey)
  if (target.admissionRuleKey && !rule) throw new Error('当前版本缺少准入规则，请先修复引用')
  if (rule && rule.kind !== 'CONDITION') throw new Error('请先为策略输出配置明确的比较条件')
  if (rule && ruleUses(document, rule.key).length > 1) {
    rule = copyVersionRule(document, rule.key)
    target.admissionRuleKey = rule.key
  }
  const reference: JsonObject = {
    predicate: source.kind === 'STAGE' ? 'STATE' : 'TASK',
    parameters: {
      refCode: source.kind === 'STAGE' ? `${source.node.code}_COMPLETED` : source.node.code
    }
  }
  if (!rule) {
    target.admissionRuleKey = createVersionRule(document, `${target.name}·准入`, reference).key
  } else if (
    rule.expression?.predicate === 'CONSTANT' &&
    (rule.expression.parameters as JsonObject)?.value === true
  )
    rule.expression = reference
  else
    rule.expression = { operator: 'ALL', rules: [rule.expression ?? constantRule(true), reference] }
  if ('start' in target) target.start = false
  projectTransitions(document)
}

export function disconnectNodes(document: TemplateDesignerDocument, key: string) {
  const edge = dependencyEdges(document).find((item) => item.key === key)
  if (!edge) return
  const source = allNodes(document).find((item) => item.node.nodeKey === edge.from)
  const target = [...document.stages, ...document.tasks].find((node) => node.nodeKey === edge.to)
  if (!source || !target) return
  let rule = document.rules?.find((item) => item.key === target.admissionRuleKey)
  if (!rule?.expression) return
  if (ruleUses(document, rule.key).length > 1) {
    rule = copyVersionRule(document, rule.key)
    target.admissionRuleKey = rule.key
  }
  const refCode = source.kind === 'STAGE' ? `${source.node.code}_COMPLETED` : source.node.code
  const strip = (expression: JsonObject): JsonObject | undefined => {
    if (expression.operator) {
      const children = childRules(expression)
        .map(strip)
        .filter((item): item is JsonObject => !!item)
      return children.length ? { ...expression, rules: children } : undefined
    }
    return (expression.parameters as JsonObject)?.refCode === refCode &&
      expression.predicate === (source.kind === 'STAGE' ? 'STATE' : 'TASK')
      ? undefined
      : expression
  }
  rule.expression = strip(rule.expression!) ?? constantRule(true)
  projectTransitions(document)
}

/** Transitions are a read model of the same stage admission references, not another editable policy. */
export function projectTransitions(document: TemplateDesignerDocument) {
  document.transitions = dependencyEdges(document).flatMap((edge) => {
    const from = document.stages.find((node) => node.nodeKey === edge.from)
    const to = document.stages.find((node) => node.nodeKey === edge.to)
    return from && to
      ? [
          {
            edgeKey: `edge:${from.code}:${to.code}`,
            code: `E_${from.code}_${to.code}`,
            fromStageCode: from.code,
            toStageCode: to.code,
            priority: 1,
            defaultBranch: false
          }
        ]
      : []
  })
}

export function toCanvasNode(
  document: TemplateDesignerDocument,
  kind: DeliveryNodeKind,
  node: DeliveryNode
): DeliveryCanvasNode {
  return {
    key: node.nodeKey,
    code: node.code,
    name: node.name,
    kind,
    ...document.layout?.nodes?.[node.nodeKey]
  }
}
