// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElTable } from 'element-plus'
import { afterEach, expect, it, vi } from 'vitest'
import type { FieldVO } from '@/api/pms/platform/businessmodel'
import Panel from './ContentHistoryPanel.vue'
import { getEntityData } from '@/api/pms/platform/businessmodel'
import { listRevisions, saveRevision } from '@/api/pms/platform/entityversion'
vi.mock('@/api/pms/platform/businessmodel', () => ({ getEntityData: vi.fn() }))
vi.mock('@/api/pms/platform/entityversion', () => ({
  listRevisions: vi.fn(),
  saveRevision: vi.fn(),
  createRevision: vi.fn(),
  completeRevision: vi.fn(),
  discardRevision: vi.fn()
}))
vi.mock('./useBusinessEntity', () => ({
  serverErrorMessage: (_error: unknown, fallback: string) => fallback
}))
let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})
it('keeps source values when typed controls render and only changes the field the user edits', async () => {
  const revision = {
    ref: {
      entity: { tenantId: 1, ownerModule: 'PROJ', entityType: 'Project', entityId: 100 },
      revisionId: 200
    },
    revisionNo: 1,
    state: 'DRAFT' as const,
    effective: false,
    version: 0
  }
  const values = {
    decimal: '12.500',
    zero: 0,
    absentNumber: null,
    unmatchedNumber: 'legacy-number',
    enabled: false,
    absentBoolean: null,
    unmatchedBoolean: 'legacy-boolean',
    date: '2026-10-09',
    datetime: '2026-10-09T00:01:02',
    labels: ['first', 'second'],
    objects: [{ key: 'original' }],
    text: 'original-text'
  }
  const definitions: [string, FieldVO['type']][] = [
    ['decimal', 'NUMBER'],
    ['zero', 'NUMBER'],
    ['absentNumber', 'NUMBER'],
    ['unmatchedNumber', 'NUMBER'],
    ['enabled', 'BOOLEAN'],
    ['absentBoolean', 'BOOLEAN'],
    ['unmatchedBoolean', 'BOOLEAN'],
    ['date', 'DATE'],
    ['datetime', 'DATETIME'],
    ['labels', 'TEXT_LIST'],
    ['objects', 'OBJECT_LIST'],
    ['text', 'TEXT']
  ]
  const fields: FieldVO[] = definitions.map(([code, type]) => ({
    code,
    type,
    name: code,
    required: false,
    readable: true,
    writable: true
  }))
  vi.mocked(listRevisions).mockResolvedValue([revision])
  vi.mocked(saveRevision).mockResolvedValue(revision)
  vi.mocked(getEntityData).mockResolvedValue({
    ref: revision.ref.entity,
    available: true,
    fieldValues: values
  })
  wrapper = mount(Panel, {
    props: { ownerModule: 'PROJ', entityType: 'Project', entityId: 100, fields },
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  wrapper.findComponent(ElTable).vm.$emit('current-change', revision)
  await flushPromises()
  const save = wrapper.findAll('button').find((button) => button.text() === '保存修订')!
  await save.trigger('click')
  await flushPromises()
  expect(vi.mocked(saveRevision).mock.calls.at(-1)?.[4].fields).toEqual(values)
  const text = wrapper.findAll('.el-form-item').find((item) => item.text().includes('text'))!
  await text.find('input').setValue('edited-text')
  await save.trigger('click')
  await flushPromises()
  expect(vi.mocked(saveRevision).mock.calls.at(-1)?.[4].fields).toEqual({
    ...values,
    text: 'edited-text'
  })
})
