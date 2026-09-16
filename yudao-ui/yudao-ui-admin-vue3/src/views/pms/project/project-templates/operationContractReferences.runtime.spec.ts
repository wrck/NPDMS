import { describe, expect, it } from 'vitest'
import { cloneDesignerDocument, emptyDesignerDocument } from '@/api/pms/project/project-templates'
import type { OperationWorkBindingSpec } from '@/api/pms/project/project-templates/operations'
import { ruleUses } from './versionRuleModel'
import { withOperationContract, readOperationContract } from './operationContract'

const contract = () => ({ version: 1 as const, operations: [{
  operationCode: 'SOL.SITE_SURVEY.CONFIRM', operationVersion: 1,
  pre: { mode: 'RULE' as const, ruleKey: 'shared' }, post: { mode: 'NONE' as const }
}] })

describe('operation rules in existing designer documents', () => {
  it('keeps legacy absence and preserves a new contract through the actual API clone', () => {
    const document = emptyDesignerDocument()
    const binding: OperationWorkBindingSpec = { type: 'BUSINESS_OBJECT', targetContextCode: 'SOL', targetObjectType: 'SITE_SURVEY' }
    document.stages.push({ nodeKey: 's', code: 'S', name: '阶段', start: true, terminal: true,
      workBinding: binding, permission: { policyRef: 'OWNER' } })
    expect(JSON.stringify(cloneDesignerDocument(document))).not.toContain('operationContract')
    document.stages[0].workBinding = withOperationContract(binding, contract())
    const copied = cloneDesignerDocument(document)
    const stored = (copied.stages[0].workBinding as OperationWorkBindingSpec).operationContract
    expect(readOperationContract(stored)).toEqual(contract())
    expect(stored).not.toBe((document.stages[0].workBinding as OperationWorkBindingSpec).operationContract)
  })

  it('includes PRE and POST references in shared-rule impact reporting', () => {
    const document = emptyDesignerDocument()
    document.rules = [{ key: 'shared', name: '公共规则', kind: 'CONDITION', shared: true,
      expression: { predicate: 'CONSTANT', parameters: { value: true } } }]
    const binding: OperationWorkBindingSpec = withOperationContract({ type: 'BUSINESS_OBJECT' }, contract())
    document.stages.push({ nodeKey: 's', code: 'S', name: '阶段', start: true, terminal: true,
      workBinding: binding, permission: { policyRef: 'OWNER' } })
    const users = ruleUses(document, 'shared')
    expect(users).toEqual(['阶段 阶段（S） · SOL.SITE_SURVEY.CONFIRM · 前置'])
  })
})
