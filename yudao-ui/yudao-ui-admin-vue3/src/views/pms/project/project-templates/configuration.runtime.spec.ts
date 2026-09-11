import { describe, expect, it } from 'vitest'
import type {
  DesignerStageNode,
  DesignerTaskNode,
  TemplateDesignerDocument
} from '@/api/pms/project/project-templates'
import { addRelation, cloneContent, emptyContent, graphIssues, relationsFor } from './editorModel'

const nativeRule = () => ({
  expression: { predicate: 'TASK_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } }
})
const stageRule = () => ({
  expression: { predicate: 'STAGE_NATIVE_STATUS', parameters: { requiredStatus: 'DONE' } }
})
const permission = () => ({ policyRef: 'PROJECT_TASK_NATIVE_DEFAULT' })
const stage = (code: string, start: boolean, terminal: boolean, sortOrder: number): DesignerStageNode => ({
  nodeKey: `stage:${code}`,
  code,
  name: code,
  sortOrder,
  start,
  terminal,
  workBinding: { type: 'STAGE_NATIVE', parameters: {} },
  permission: { policyRef: 'PROJECT_STAGE_NATIVE_DEFAULT' },
  completionRule: stageRule()
})
const task = (code: string, stageCode: string): DesignerTaskNode => ({
  nodeKey: `task:${code}`,
  code,
  name: code,
  stageCode,
  workBinding: { type: 'TASK_NATIVE', parameters: {} },
  permission: permission(),
  completionRule: nativeRule()
})
const graph = (): TemplateDesignerDocument => ({
  ...emptyContent(),
  stages: [stage('S0', true, false, 0), stage('S4', false, true, 10)],
  tasks: [task('T1', 'S4')],
  transitions: [{
    edgeKey: 'transition:S0-S4',
    code: 'S0-S4',
    fromStageCode: 'S0',
    toStageCode: 'S4',
    priority: 0,
    defaultBranch: true
  }]
})

describe('PM-03 Designer V2 graph contract', () => {
  it('uses one explicit transition collection for predecessor and successor views', () => {
    const content = graph()
    expect(relationsFor(content, 'S0', 'from')[0]).toBe(relationsFor(content, 'S4', 'to')[0])
    relationsFor(content, 'S0', 'from')[0].priority = 7
    expect(content.transitions[0].priority).toBe(7)
  })

  it('never infers missing graph edges from display order or stage codes', () => {
    const content: TemplateDesignerDocument = {
      ...emptyContent(),
      stages: [stage('S0', true, false, 100), stage('S4', false, true, 0)]
    }
    expect(content.transitions).toEqual([])
    const codes = graphIssues(content).map((issue) => issue.code)
    expect(codes).toContain('NO_SUCCESSOR')
    expect(codes).toContain('UNREACHABLE')
    expect(content.transitions).toEqual([])
  })

  it('adds only an explicit edge with a stable edge key', () => {
    const content: TemplateDesignerDocument = {
      ...emptyContent(),
      stages: [stage('S0', true, false, 0), stage('S4', false, true, 10)]
    }
    addRelation(content, 'S0', 'from')
    expect(content.transitions).toHaveLength(1)
    expect(content.transitions[0]).toMatchObject({
      fromStageCode: 'S0',
      toStageCode: '',
      priority: 0,
      defaultBranch: false
    })
    expect(content.transitions[0].edgeKey).toMatch(/^transition:/)
  })

  it('reports cycle, dangling edges, duplicate defaults and conditional default branches without fixing them', () => {
    const content = graph()
    content.transitions[0].defaultBranch = true
    content.transitions[0].condition = {
      expression: { predicate: 'TASK', parameters: { refCode: 'T1' } }
    }
    content.transitions.push(
      {
        edgeKey: 'transition:back', code: 'BACK', fromStageCode: 'S4', toStageCode: 'S0',
        priority: 0, defaultBranch: false
      },
      {
        edgeKey: 'transition:bad', code: 'BAD', fromStageCode: 'S0', toStageCode: 'missing',
        priority: 1, defaultBranch: true
      }
    )
    const codes = graphIssues(content).map((issue) => issue.code)
    expect(codes).toEqual(expect.arrayContaining([
      'CYCLE', 'DANGLING_EDGE', 'MULTIPLE_DEFAULTS', 'DEFAULT_HAS_CONDITION'
    ]))
    expect(content.transitions).toHaveLength(3)
  })

  it('requires stable unique node and edge keys', () => {
    const content = graph()
    content.stages[1].nodeKey = content.stages[0].nodeKey
    content.transitions.push({ ...content.transitions[0], code: 'SECOND' })
    const codes = graphIssues(content).map((issue) => issue.code)
    expect(codes).toContain('DUPLICATE_OR_EMPTY')
  })

  it('deep clones DesignerDocument and keeps source revision ids as provenance only', () => {
    const content = graph()
    content.tasks[0].source = {
      definitionRevisionId: 11,
      workBindingRevisionId: 12,
      permissionPolicyRevisionId: 13,
      completionRuleRevisionId: 14
    }
    const copy = cloneContent(content)
    copy.tasks[0].workBinding.type = 'BUSINESS_COMPONENT'
    copy.tasks[0].source!.workBindingRevisionId = undefined

    expect(content.tasks[0].workBinding.type).toBe('TASK_NATIVE')
    expect(content.tasks[0].source?.workBindingRevisionId).toBe(12)
    expect(copy.schemaVersion).toBe(2)
  })
})
