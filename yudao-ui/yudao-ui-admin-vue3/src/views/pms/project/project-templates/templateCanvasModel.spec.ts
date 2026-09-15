import { describe, expect, it } from 'vitest'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import {
  connectNodes,
  captureInlineRules,
  createDeliveryNode,
  dependencyEdges,
  disconnectNodes
} from './templateCanvasModel'

const document = (): TemplateDesignerDocument => ({
  schemaVersion: 2,
  match: {},
  stages: [],
  tasks: [],
  milestones: [],
  deliverables: [],
  gates: [],
  transitions: [],
  ruleAssets: [],
  rules: [],
  layout: {}
})

describe('delivery canvas edits real rule references', () => {
  it.each(['STAGE', 'TASK'] as const)('projects explicit business facts from a %s without adding a completion condition', (kind) => {
    const model = document()
    const prep = createDeliveryNode(model, 'STAGE', undefined, '工前准备', { x: 0, y: 0 })
    const source = kind === 'STAGE' ? prep : createDeliveryNode(model, 'TASK', prep.code, '现场工勘', { x: 1, y: 1 })
    const target = createDeliveryNode(model, 'STAGE', undefined, '需求分析', { x: 2, y: 2 })
    model.rules!.push({ key: 'business', name: '来源工勘已确认', kind: 'CONDITION', shared: false,
      expression: { predicate: 'BUSINESS_FACT', parameters: {
        sourceNodeKey: source.nodeKey, factCode: 'SURVEY_CONFIRMED', quantifier: 'ANY'
      } } })
    model.stages[1].admissionRuleKey = 'business'
    expect(dependencyEdges(model)).toEqual([{ key: `${source.nodeKey}->${target.nodeKey}`,
      from: source.nodeKey, to: target.nodeKey, label: '准入引用' }])
    const before = JSON.stringify(model)
    connectNodes(model, source.nodeKey, target.nodeKey)
    expect(JSON.stringify(model)).toBe(before)
    const reopened = JSON.parse(before) as TemplateDesignerDocument
    expect(dependencyEdges(reopened)).toEqual(dependencyEdges(model))
    disconnectNodes(reopened, `${source.nodeKey}->${target.nodeKey}`)
    expect(reopened.rules!.find(rule => rule.key === 'business')?.expression)
      .toEqual({ predicate: 'CONSTANT', parameters: { value: true } })
    expect(dependencyEdges(reopened)).toHaveLength(0)
  })
  it('removes all conditions for one source only from the selected consumer of a shared admission', () => {
    const model = document()
    const prep = createDeliveryNode(model, 'STAGE', undefined, '工前准备', { x: 0, y: 0 })
    const survey = createDeliveryNode(model, 'TASK', prep.code, '现场工勘', { x: 1, y: 1 })
    const other = createDeliveryNode(model, 'TASK', prep.code, '其他来源', { x: 2, y: 2 })
    const target = createDeliveryNode(model, 'STAGE', undefined, '需求分析', { x: 3, y: 3 })
    const another = createDeliveryNode(model, 'STAGE', undefined, '独立分支', { x: 4, y: 4 })
    const retained = { predicate: 'BUSINESS_FACT', parameters: {
      sourceNodeKey: other.nodeKey, factCode: 'SURVEY_CONFIRMED', quantifier: 'ALL'
    } }
    model.rules!.push({ key: 'business', name: '共享工勘依赖', kind: 'CONDITION', shared: true,
      expression: { operator: 'ALL', rules: [
        { operator: 'NOT', rules: [{ predicate: 'BUSINESS_FACT', parameters: {
          sourceNodeKey: survey.nodeKey, factCode: 'SURVEY_ARCHIVED', quantifier: 'ANY'
        } }] },
        { predicate: 'WAIT_ELAPSED', parameters: { sourceNodeKey: survey.nodeKey, anchor: 'NODE_COMPLETED', duration: 'PT1M' } },
        retained
      ] } })
    model.stages[1].admissionRuleKey = 'business'; model.stages[2].admissionRuleKey = 'business'
    expect(dependencyEdges(model)).toHaveLength(4)
    const original = JSON.stringify(model.rules!.find(rule => rule.key === 'business'))
    disconnectNodes(model, `${survey.nodeKey}->${target.nodeKey}`)
    expect(dependencyEdges(model)).toHaveLength(3)
    expect(dependencyEdges(model)).toContainEqual(expect.objectContaining({ from: survey.nodeKey, to: another.nodeKey }))
    expect(model.rules!.find(rule => rule.key === model.stages[1].admissionRuleKey)?.expression)
      .toEqual({ operator: 'ALL', rules: [retained] })
    expect(JSON.stringify(model.rules!.find(rule => rule.key === 'business'))).toBe(original)
  })
  it('projects relative waits as dependencies and disconnects only a private copy of shared rules', () => {
    const model = document()
    const prep = createDeliveryNode(model, 'STAGE', undefined, 'prep', { x: 0, y: 0 })
    const source = createDeliveryNode(model, 'TASK', prep.code, 'survey', { x: 0, y: 0 })
    createDeliveryNode(model, 'STAGE', undefined, 'after', { x: 1, y: 1 })
    createDeliveryNode(model, 'STAGE', undefined, 'another', { x: 2, y: 2 })
    model.rules!.push({ key: 'wait', name: '工勘后等待', kind: 'CONDITION', shared: true,
      expression: { operator: 'ALL', rules: [
        { predicate: 'WAIT_ELAPSED', parameters: { anchor: 'NODE_COMPLETED', sourceNodeKey: source.nodeKey, duration: 'PT30M' } },
        { predicate: 'CONSTANT', parameters: { value: false } }
      ] } })
    model.stages[1].admissionRuleKey = 'wait'; model.stages[2].admissionRuleKey = 'wait'
    expect(dependencyEdges(model)).toHaveLength(2)
    const original = JSON.stringify(model.rules!.find(rule => rule.key === 'wait'))
    connectNodes(model, source.nodeKey, model.stages[1].nodeKey)
    expect(model.stages[1].admissionRuleKey).toBe('wait')
    disconnectNodes(model, `${source.nodeKey}->${model.stages[1].nodeKey}`)
    expect(dependencyEdges(model)).toEqual([expect.objectContaining({ from: source.nodeKey, to: model.stages[2].nodeKey })])
    expect(model.stages[1].admissionRuleKey).not.toBe('wait')
    expect(model.rules!.find(rule => rule.key === model.stages[1].admissionRuleKey)?.expression)
      .toEqual({ operator: 'ALL', rules: [{ predicate: 'CONSTANT', parameters: { value: false } }] })
    expect(JSON.stringify(model.rules!.find(rule => rule.key === 'wait'))).toBe(original)
  })
  it('copies a shared admission before connecting when its consumers have the same name', () => {
    const model = document()
    const source = createDeliveryNode(model, 'STAGE', undefined, '工勘', { x: 0, y: 0 })
    const target = createDeliveryNode(model, 'STAGE', undefined, '同名阶段', { x: 1, y: 1 })
    createDeliveryNode(model, 'STAGE', undefined, '同名阶段', { x: 2, y: 2 })
    const anotherSource = createDeliveryNode(model, 'STAGE', undefined, '需求', { x: 3, y: 3 })
    connectNodes(model, source.nodeKey, target.nodeKey)
    const sharedKey = model.stages[1].admissionRuleKey
    model.stages[2].admissionRuleKey = sharedKey
    const original = JSON.stringify(model.rules?.find(rule => rule.key === sharedKey))
    connectNodes(model, anotherSource.nodeKey, target.nodeKey)
    expect(model.stages[1].admissionRuleKey).not.toBe(sharedKey)
    expect(model.stages[2].admissionRuleKey).toBe(sharedKey)
    expect(JSON.stringify(model.rules?.find(rule => rule.key === sharedKey))).toBe(original)
  })
  it('does not mark a saved version dirty merely because the server includes a null inline rule', () => {
    const model = document()
    const stage = createDeliveryNode(model, 'STAGE', undefined, 'saved', { x: 1, y: 2 })
    Reflect.set(stage, 'completionRule', null)
    const saved = JSON.stringify(model)
    captureInlineRules(model)
    expect(JSON.stringify(model)).toBe(saved)
  })
  it('creates independent nodes and makes a connection an admission condition', () => {
    const model = document()
    const a = createDeliveryNode(model, 'STAGE', undefined, 'a', { x: 100, y: 100 })
    const b = createDeliveryNode(model, 'STAGE', undefined, 'b', { x: 300, y: 100 })
    connectNodes(model, a.nodeKey, b.nodeKey)
    expect(dependencyEdges(model)).toHaveLength(1)
    expect(model.stages[1].admissionRuleKey).toBeTruthy()
    expect(
      model.rules?.find((rule) => rule.key === model.stages[1].admissionRuleKey)?.expression
    ).toMatchObject({ predicate: 'STATE', parameters: { refCode: `${a.code}_COMPLETED` } })
    disconnectNodes(model, dependencyEdges(model)[0].key)
    expect(dependencyEdges(model)).toHaveLength(0)
  })
  it('allows external tasks as sources but never modifies them as targets from another stage canvas', () => {
    const model = document()
    const a = createDeliveryNode(model, 'STAGE', undefined, 'a', { x: 0, y: 0 })
    const b = createDeliveryNode(model, 'STAGE', undefined, 'b', { x: 0, y: 0 })
    const own = createDeliveryNode(model, 'TASK', a.code, 'a1', { x: 20, y: 20 })
    const external = createDeliveryNode(model, 'TASK', b.code, 'b1', { x: 40, y: 40 })
    const before = JSON.stringify(model)
    expect(() => connectNodes(model, own.nodeKey, external.nodeKey, a.code)).toThrow(
      '只能作为依赖来源'
    )
    expect(JSON.stringify(model)).toBe(before)
    connectNodes(model, external.nodeKey, own.nodeKey, a.code)
    expect(
      model.tasks.find((task) => task.nodeKey === external.nodeKey)?.admissionRuleKey
    ).toBeUndefined()
    expect(model.tasks.find((task) => task.nodeKey === own.nodeKey)?.admissionRuleKey).toBeTruthy()
  })
})
