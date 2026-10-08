import type { FieldFilter, FieldVO } from '@/api/pms/platform/businessmodel'

/** Build structured filters only; field names and values never become SQL text. */
export function buildBusinessListFilter(fields: FieldVO[], code: string, raw: string): FieldFilter[] {
  if (!code && !raw.trim()) return []
  const field = fields.find(field => field.code === code && field.readable)
  if (!field) throw new Error('请选择可查询的字段')
  const value = raw.trim()
  if (!value) return []
  if (field.type === 'TEXT') return [{ fieldCode: code, operator: 'LIKE', values: [value] }]
  if (field.type === 'NUMBER') {
    if (!/^[+-]?(?:\d+(?:\.\d+)?|\.\d+)$/.test(value)) throw new Error('请输入有效数字')
    // Preserve exact Long/decimal values rather than rounding through JavaScript Number.
    return [{ fieldCode: code, operator: 'EQ', values: [value] }]
  }
  if (field.type === 'BOOLEAN') {
    if (!['true', 'false', '是', '否'].includes(value)) throw new Error('布尔字段请输入 true 或 false')
    return [{ fieldCode: code, operator: 'EQ', values: [value === 'true' || value === '是'] }]
  }
  if (field.type === 'DATE' || field.type === 'DATETIME') {
    return [{ fieldCode: code, operator: 'EQ', values: [value] }]
  }
  throw new Error('该字段不支持默认筛选')
}
