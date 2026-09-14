import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { completeUpload, detachReference } from './index'
import { buildInstanceRuntime } from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'

vi.mock('@/config/axios', () => ({ default: { post: vi.fn(), delete: vi.fn() } }))
describe('file execution request contract', () => {
  beforeEach(() => vi.clearAllMocks())
  it('encodes upload context as a JSON multipart part and removal context only in the request body', async () => {
    const context = { task: { executionId: '2099473011264401410' } }
    completeUpload(1, 2, new File(['a'], 'test.txt'), 'intent', undefined, context)
    const form = vi.mocked(request.post).mock.calls[0][0].data as FormData
    const part = form.get('ownerExecutionContext') as Blob
    expect(part.type).toBe('application/json')
    expect(JSON.parse(await part.text())).toEqual(context)
    expect(form.get('sessionId')).toBe('2')
    detachReference(3, 4, { ownerContext: 'PLATFORM', objectType: 'DYNAMIC_FORM_INSTANCE',
      objectId: '31', purposeCode: 'FORM_FIELD_ATTACHMENT/evidence', referenceKey: 'slot-a' },
    '材料重复', 'intent', context)
    expect(vi.mocked(request.delete).mock.calls[0][0]).toMatchObject({
      data: { ownerExecutionContext: context, reason: '材料重复' },
      headers: { 'If-Match': '4', 'Idempotency-Key': 'intent' }
    })
  })
  it('adds context only to copied runtime file rules, not the source or ordinary field values', () => {
    const rules = [{ type: 'PmsFileArtifact', field: 'evidence' }, { type: 'input', field: 'name' }]
    const context = { stage: { executionId: '13' } }
    const runtime = buildInstanceRuntime(rules, { instanceId: 1, templateRevisionId: 2,
      controlledFiles: {}, allowedActions: ['PATCH_INSTANCE'], ownerExecutionContext: context })
    context.stage.executionId = '99'
    expect(runtime.rules[0].props).toMatchObject({ ownerExecutionContext: { stage: { executionId: '13' } } })
    expect(rules[0]).not.toHaveProperty('props')
    expect(runtime.rules[1]).not.toHaveProperty('props')
  })
})
