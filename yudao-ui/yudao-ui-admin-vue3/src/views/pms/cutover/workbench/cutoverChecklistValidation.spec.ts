import { describe, expect, it } from 'vitest'
import type { CutoverChecklistItem } from '@/api/pms/cutover'
import { findChecklistValidationItem } from '../shared/cutoverChecklistValidation'

const missing = (key: string, overrides: Partial<CutoverChecklistItem> = {}): CutoverChecklistItem => ({
  itemId: key, stableItemKey: key, itemName: key, itemTypeCode: 'BUSINESS_SURVEY',
  interfaceFormatCode: 'TEXT', interfaceSchemaSnapshot: null, itemDescription: null,
  workModeCode: 'DIRECT', required: true, applicable: true, sourceCode: 'SYSTEM_MATCHED',
  sortOrder: 1, currentResult: null, ...overrides
})

describe('checklist server validation navigation', () => {
  it('locates the named nested field even when another required item is empty', () => {
    const items = [missing('first'), missing('客户'), missing('客户 / 联系人')]
    expect(findChecklistValidationItem({ items, message: '客户 / 联系人 / 联系电话：尚未填写' })?.stableItemKey)
      .toBe('客户 / 联系人')
  })

  it('skips optional, inapplicable and completed manual items for a missing required result', () => {
    const items = [missing('optional', { required: false }), missing('inactive', { applicable: false }),
      missing('manual', { currentResult: {
        resultVersion: 1, resultSourceCode: 'MANUAL', answerSnapshot: '{}', factDescription: null,
        manualEvidenceFileReference: 'proof', collectionTaskId: null, collectionResultReferenceId: null,
        collectionResultVersion: null, loadFailureCode: null
      } }), missing('required')]
    expect(findChecklistValidationItem({ items, message: '必填清单项尚未完成' })?.stableItemKey).toBe('required')
    expect(findChecklistValidationItem({ items, message: '项目范围已变更' })).toBeUndefined()
    expect(findChecklistValidationItem({ items, message: 'inactive：尚未填写' })).toBeUndefined()
  })

  it('locates ready collection logs without saved conclusions, and external queries without saved feedback', () => {
    const result = {
      resultVersion: 1, resultSourceCode: 'COLLECTION' as const, answerSnapshot: '{"technicalStatus":"COMPLETED"}',
      factDescription: null, manualEvidenceFileReference: null, collectionTaskId: '1',
      collectionResultReferenceId: '2', collectionResultVersion: '1', loadFailureCode: null
    }
    const collection = missing('logs', { workModeCode: 'COLLECTION', currentResult: result })
    const external = missing('bulletins', {
      workModeCode: 'EXTERNAL', currentResult: { ...result, resultSourceCode: 'EXTERNAL' }
    })
    const input = { items: [collection, external], message: '必填清单项尚未完成',
      externalItems: [{ itemId: 'bulletins', stableItemKey: 'bulletins', interfaceCode: 'test',
        providerAvailable: true, manualFallback: false, resultId: '3', resultVersion: 1,
        resultSourceCode: 'EXTERNAL' as const, feedbackSaved: false, contextCurrent: true,
        selectedQueryIds: [], queries: [], bulletins: [] }] }
    expect(findChecklistValidationItem(input)?.stableItemKey).toBe('logs')
    collection.currentResult = { ...result, answerSnapshot: '{"analysis":{"value":{"normal":true}}}' }
    expect(findChecklistValidationItem(input)?.stableItemKey).toBe('bulletins')
    input.externalItems[0].feedbackSaved = true
    expect(findChecklistValidationItem(input)).toBeUndefined()
  })
})
