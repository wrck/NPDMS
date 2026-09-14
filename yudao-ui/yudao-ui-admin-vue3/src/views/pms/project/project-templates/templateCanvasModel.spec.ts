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
