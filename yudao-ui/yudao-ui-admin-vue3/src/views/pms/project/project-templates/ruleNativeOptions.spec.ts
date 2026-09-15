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
