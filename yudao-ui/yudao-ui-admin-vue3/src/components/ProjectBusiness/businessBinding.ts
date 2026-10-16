import type { FieldFilter, FieldVO } from '@/api/pms/platform/businessmodel'

export type BusinessBindingParameters = Record<string, unknown>
type Selectors = Record<string, string | number | boolean | null>
const routing = new Set(['businessViewRevisionId', 'contextMapping', 'instanceResolutionStrategy', 'configurationName'])
export const hasBusinessBindingSelectors = (parameters: BusinessBindingParameters) =>
  Object.keys(parameters).some(code => !routing.has(code))

/** The task/stage snapshot supplies the same field selectors used by server association. */
export const businessBindingSelectors = (parameters: BusinessBindingParameters, fields: FieldVO[]) => {
  const selectors: Selectors = {}
  for (const [code, value] of Object.entries(parameters)) {
    if (routing.has(code)) continue
    if (!fields.some(field => field.code === code && field.readable) || value !== null && !['string', 'number', 'boolean'].includes(typeof value))
      throw new Error(`业务绑定条件不可用：${code}`)
    selectors[code] = value as Selectors[string]
  }
  return selectors
}
export const businessBindingFilters = (selectors: Selectors): FieldFilter[] =>
  Object.entries(selectors).map(([fieldCode, value]) => ({ fieldCode, operator: 'EQ', values: [value] }))

export const requireBusinessBinding = (values: Record<string, unknown>, selectors: BusinessBindingParameters) => {
  for (const [code, value] of Object.entries(selectors)) {
    if (value === null ? values[code] != null : String(values[code]) !== String(value)) throw new Error(`业务记录不符合当前办理范围：${code}`)
  }
}
