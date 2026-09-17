interface DesignerField {
  prop: string
  defaultValue?: unknown
}

/** Copying a field must not bind the new control to an existing answer. */
export function uniqueCopyProp(prop: string, fields: readonly DesignerField[]): string {
  const used = new Set(fields.map((field) => field.prop))
  const base = `${prop}_copy`
  let candidate = base
  let suffix = 2
  while (used.has(candidate)) candidate = `${base}${suffix++}`
  return candidate
}

/** Form configs are JSON; clone defaults so preview edits never mutate them. */
export function createPreviewData(fields: readonly DesignerField[]): Record<string, unknown> {
  return Object.fromEntries(
    fields.map((field) => [field.prop, JSON.parse(JSON.stringify(field.defaultValue ?? ''))])
  )
}
