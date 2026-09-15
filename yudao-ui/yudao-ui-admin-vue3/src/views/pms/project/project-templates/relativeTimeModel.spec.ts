import { expect, it, vi } from 'vitest'
import { emptyDesignerDocument } from '@/api/pms/project/project-templates'
import { initialRelativeTime, preferredWaitUnit, relativeTimeOptions, waitAmount, waitDuration, waitSeconds } from './relativeTimeModel'
vi.mock('@/config/axios', () => ({ default: {} }))

it('uses native duration conversion without introducing calendar months or changing stored mixed units', () => {
  expect(waitSeconds('P1DT2H30M')).toBe(95400)
  expect(preferredWaitUnit('P1DT2H30M')).toBe('minutes')
  expect(waitAmount('PT90M', 'hours')).toBe(1.5)
  expect(waitDuration(1.5, 'hours')).toBe('PT5400S')
  expect(waitDuration(40, 'days')).toBe('PT3456000S')
  expect(waitSeconds(waitDuration(400, 'days'))).toBe(400 * 86400)
  expect(waitDuration(0, 'minutes')).toBe('P0D')
  expect(waitDuration(undefined, 'minutes')).toBe('')
  expect(waitDuration(-1, 'minutes')).toBe('')
  for (const value of ['', 'tomorrow', 'P1M', 'P1Y', 'P1W', '-PT1H', 'PT1,5S']) expect(waitSeconds(value)).toBeUndefined()
  expect(preferredWaitUnit('PT0.5S')).toBe('seconds')
  expect(waitAmount('PT0.5S', 'seconds')).toBe(0.5)
})

it('includes all node bindings but excludes every shared consumer as a completion source', () => {
  const doc = emptyDesignerDocument()
  doc.stages = [{ nodeKey: 'prep', code: 'PREP', name: '工前准备', start: true, terminal: false,
    workBinding: { type: 'STAGE_NATIVE', parameters: {} }, permission: {}, completionRuleKey: 'wait' }]
  doc.tasks = [{ nodeKey: 'survey', code: 'SURVEY', name: '现场工勘', stageCode: 'PREP',
    workBinding: { type: 'TASK_NATIVE', parameters: {} }, permission: {}, exitRuleKey: 'wait' },
  { nodeKey: 'analysis', code: 'ANALYSIS', name: '需求分析', stageCode: 'PREP',
    workBinding: { type: 'BUSINESS_OBJECT', parameters: {} }, permission: {} }]
  let options = relativeTimeOptions(doc, 'wait')
  expect(options.activation).toBe(true)
  expect(options.sources.map(source => [source.key, source.disabled])).toEqual([['prep', true], ['survey', true], ['analysis', false]])
  expect(initialRelativeTime(options)).toEqual({ anchor: 'NODE_ACTIVATED', duration: '' })
  doc.tasks[0].admissionRuleKey = 'wait'; options = relativeTimeOptions(doc, 'wait')
  expect(options.activation).toBe(false)
  expect(initialRelativeTime(options)).toEqual({ anchor: 'NODE_COMPLETED', sourceNodeKey: '', duration: '' })
  doc.matchRuleKey = 'wait'; expect(relativeTimeOptions(doc, 'wait').available).toBe(false)
  doc.matchRuleKey = undefined; doc.closureRuleKey = 'wait'
  expect(relativeTimeOptions(doc, 'wait').activation).toBe(false)
  doc.stages = []; doc.tasks = []
  expect(relativeTimeOptions(doc, 'wait')).toEqual({ available: true, activation: false, sources: [] })
  expect(relativeTimeOptions(doc).available).toBe(false)
})
