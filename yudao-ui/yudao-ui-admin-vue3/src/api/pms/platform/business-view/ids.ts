// PM-03: Java Long/Snowflake IDs arrive as decimal strings; never coerce them to Number.
export type BusinessViewId = string | number
export const isBusinessViewId = (value: unknown): value is BusinessViewId => {
  if (typeof value === 'number') return Number.isSafeInteger(value) && value > 0
  return (
    typeof value === 'string' &&
    /^[1-9][0-9]{0,18}$/.test(value) &&
    (value.length < 19 || value <= '9223372036854775807')
  )
}
export const businessViewIdKey = (value: unknown): string => {
  if (!isBusinessViewId(value)) throw new Error('业务引用必须是无损的正十进制ID。')
  return String(value)
}
export const sameBusinessViewId = (left: unknown, right: unknown) =>
  isBusinessViewId(left) && isBusinessViewId(right) && String(left) === String(right)

// Existing Owner API typings predate Long-as-string serialization. Validate at this boundary
// and preserve the exact runtime value; this is only a type bridge, NOT numeric conversion.
export const legacyOwnerId = (value: BusinessViewId): number => {
  businessViewIdKey(value)
  return value as number
}
