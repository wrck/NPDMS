import { expect, it } from 'vitest'
import { buildBusinessListFilter } from './businessListFilter'
import type { FieldVO } from '@/api/pms/platform/businessmodel'
const fields = [
  { code: 'title', type: 'TEXT', readable: true },
  { code: 'projectId', type: 'NUMBER', readable: true },
  { code: 'enabled', type: 'BOOLEAN', readable: true },
  { code: 'secret', type: 'TEXT', readable: false }
] as FieldVO[]
it('uses a structured text filter', () => {
  expect(buildBusinessListFilter(fields, 'title', " x' OR 1=1 ")).toEqual([{ fieldCode: 'title', operator: 'LIKE', values: ["x' OR 1=1"] }])
})
it('does not round business keys or decimals', () => {
  expect(buildBusinessListFilter(fields, 'projectId', '9223372036854775806')[0].values).toEqual(['9223372036854775806'])
})
it('rejects non-readable and unknown fields', () => {
  expect(() => buildBusinessListFilter(fields, 'secret', 'x')).toThrow()
  expect(() => buildBusinessListFilter(fields, 'tenant_id', '1')).toThrow()
})
it('keeps false and rejects malformed numeric filters', () => {
  expect(buildBusinessListFilter(fields, 'enabled', 'false')[0].values).toEqual([false])
  expect(() => buildBusinessListFilter(fields, 'projectId', '1 or 1=1')).toThrow()
})
it('clears filters explicitly', () => expect(buildBusinessListFilter(fields, '', '')).toEqual([]))
