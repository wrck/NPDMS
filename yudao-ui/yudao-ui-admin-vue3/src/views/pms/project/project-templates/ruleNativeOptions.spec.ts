import { expect, it, vi } from 'vitest'
import { emptyDesignerDocument, type DesignerStageNode, type DesignerTaskNode } from '@/api/pms/project/project-templates'
import { nativeOptions } from './ruleNativeOptions'
vi.mock('@/config/axios', () => ({ default: {} }))

const stage = (): DesignerStageNode => ({ nodeKey: 'prep', code: 'PREP', name: '工前准备', start: true,
  terminal: true, workBinding: { type: 'STAGE_NATIVE', parameters: {} }, permission: {}, completionRuleKey: 'ready' })
const task = (): DesignerTaskNode => ({ nodeKey: 'survey', code: 'SURVEY', name: '现场工勘', stageCode: 'PREP',
  workBinding: { type: 'TASK_NATIVE', parameters: {} }, permission: {}, exitRuleKey: 'ready' })

it('offers only the matching manual predicate and follows binding changes without changing rules', () => {
  const document = emptyDesignerDocument(); document.stages.push(stage())
  expect(nativeOptions(document, 'ready')).toEqual(['STAGE_NATIVE_STATUS'])
  document.stages[0].workBinding.type = 'APPROVAL'
  expect(nativeOptions(document, 'ready')).toEqual([])
  document.stages = []; document.tasks.push(task())
  expect(nativeOptions(document, 'ready')).toEqual(['TASK_NATIVE_STATUS'])
  document.tasks[0].workBinding.type = 'PAGE'
  expect(nativeOptions(document, 'ready')).toEqual(['TASK_NATIVE_STATUS'])
  document.tasks[0].workBinding.type = 'BUSINESS_OBJECT'
  expect(nativeOptions(document, 'ready')).toEqual([])
})

it('intersects all shared consumers including admission, closure and matching', () => {
  const document = emptyDesignerDocument(); document.stages.push(stage(), { ...stage(), nodeKey: 'second' })
  expect(nativeOptions(document, 'ready')).toEqual(['STAGE_NATIVE_STATUS'])
  document.tasks.push(task()); expect(nativeOptions(document, 'ready')).toEqual([])
  document.tasks = []; document.stages[1].admissionRuleKey = 'ready'
  expect(nativeOptions(document, 'ready')).toEqual([])
  document.stages[1].admissionRuleKey = undefined; document.matchRuleKey = 'ready'
  expect(nativeOptions(document, 'ready')).toEqual([])
  document.matchRuleKey = undefined; document.closureRuleKey = 'ready'
  expect(nativeOptions(document, 'ready')).toEqual([])
  expect(nativeOptions(document, 'missing')).toEqual([])
  expect(nativeOptions(document)).toEqual([])
})

it('allows automatic contacts only at the contact page completion slot', () => {
  const document = emptyDesignerDocument(); const node = task(); node.exitRuleKey = undefined; node.completionRuleKey = 'ready'
  node.workBinding = { type: 'PAGE', parameters: { routePath: '/customer-asset/customer-contact' } }; document.tasks.push(node)
  expect(nativeOptions(document, 'ready')).toContain('PROJECT_CONTACT_EXISTS')
  node.exitRuleKey = 'ready'; expect(nativeOptions(document, 'ready')).not.toContain('PROJECT_CONTACT_EXISTS')
  node.exitRuleKey = undefined; node.workBinding.parameters = { routePath: '/other' }
  expect(nativeOptions(document, 'ready')).not.toContain('PROJECT_CONTACT_EXISTS')
})

it('offers approval closure only for a terminal closure task completion rule', () => {
  const document = emptyDesignerDocument(); const terminal = stage(); terminal.completionRuleKey = 'stage-ready'
  document.stages.push(terminal)
  const node = task(); node.exitRuleKey = undefined; node.completionRuleKey = 'ready'
  node.workBinding = { type: 'PAGE', parameters: { routePath: '/pms/project/normal-closure' } }; document.tasks.push(node)
  expect(nativeOptions(document, 'ready')).toContain('NORMAL_CLOSURE_APPROVED')
  terminal.terminal = false; expect(nativeOptions(document, 'ready')).not.toContain('NORMAL_CLOSURE_APPROVED')
  terminal.terminal = true; node.exitRuleKey = 'ready'; expect(nativeOptions(document, 'ready')).not.toContain('NORMAL_CLOSURE_APPROVED')
  node.exitRuleKey = undefined; document.closureRuleKey = 'ready'; expect(nativeOptions(document, 'ready')).not.toContain('NORMAL_CLOSURE_APPROVED')
})

it('allows external end users only at the project member page completion slot', () => {
  const document = emptyDesignerDocument(); const node = task(); node.exitRuleKey = undefined; node.completionRuleKey = 'ready'
  node.workBinding = { type: 'PAGE', parameters: { routePath: '/pms/project-management/project-master-detail' } }; document.tasks.push(node)
  expect(nativeOptions(document, 'ready')).toContain('PROJECT_END_USER_EXISTS')
  expect(nativeOptions(document, 'ready')).not.toContain('PROJECT_CONTACT_EXISTS')
  node.exitRuleKey = 'ready'; expect(nativeOptions(document, 'ready')).not.toContain('PROJECT_END_USER_EXISTS')
  node.exitRuleKey = undefined; node.workBinding.parameters = { routePath: '/customer-asset/customer-contact' }
  expect(nativeOptions(document, 'ready')).not.toContain('PROJECT_END_USER_EXISTS')
})
