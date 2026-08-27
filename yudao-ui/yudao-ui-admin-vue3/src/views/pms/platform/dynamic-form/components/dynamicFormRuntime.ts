import type {
  DynamicFormAction,
  DynamicFormFileFactVO,
  JsonObject
} from '@/api/pms/platform/dynamic-form'

const clone = <T>(value: T): T => {
  if (Array.isArray(value)) return value.map(clone) as T
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, clone(item)])) as T
  }
  return value
}

const canonicalJson = (value: unknown): unknown => {
  if (Array.isArray(value)) return value.map(canonicalJson)
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, item]) => [key, canonicalJson(item)])
    )
  }
  return value
}

export const sameJsonValue = (left: unknown, right: unknown) =>
  JSON.stringify(canonicalJson(left)) === JSON.stringify(canonicalJson(right))

const childrenOf = (rule: JsonObject): JsonObject[][] => {
  const children = rule.children
  return Array.isArray(children) ? [children as JsonObject[]] : []
}

export const collectValueFields = (rules: JsonObject[]) => {
  const ordinary = new Set<string>()
  const controlled = new Set<string>()
  const visit = (items: JsonObject[]) => {
    items.forEach((rule) => {
      const field = typeof rule.field === 'string' ? rule.field.trim() : ''
      if (field) (rule.type === 'PmsFileArtifact' ? controlled : ordinary).add(field)
      childrenOf(rule).forEach(visit)
    })
  }
  visit(rules)
  return { ordinary, controlled }
}

export const buildInstanceRuntime = (
  rules: JsonObject[],
  context: {
    instanceId: number
    templateRevisionId: number
    controlledFiles: Record<string, DynamicFormFileFactVO[]>
    allowedActions: DynamicFormAction[]
  }
) => {
  const runtimeRules = clone(rules)
  const fields = collectValueFields(runtimeRules)
  const visit = (items: JsonObject[]) => {
    items.forEach((rule) => {
      if (rule.type === 'PmsFileArtifact' && typeof rule.field === 'string') {
        rule.props = {
          ...((rule.props as JsonObject | undefined) || {}),
          instanceId: context.instanceId,
          templateRevisionId: context.templateRevisionId,
          fieldKey: rule.field,
          currentFacts: context.controlledFiles[rule.field] || [],
          allowedActions: context.allowedActions
        }
      }
      childrenOf(rule).forEach(visit)
    })
  }
  visit(runtimeRules)
  return { rules: runtimeRules, ...fields }
}

export const changedOrdinaryValues = (
  current: JsonObject,
  baseline: JsonObject,
  ordinaryFields: Set<string>
) => {
  const changed: JsonObject = {}
  ordinaryFields.forEach((field) => {
    if (!Object.prototype.hasOwnProperty.call(current, field)) return
    if (!sameJsonValue(current[field], baseline[field])) changed[field] = current[field]
  })
  return changed
}

export const stableCommandIntent = (scope: string, payload: unknown) => {
  const storageKey = `pms:fplt002:${scope}`
  const serialized = JSON.stringify(payload)
  const existing = sessionStorage.getItem(storageKey)
  if (existing) {
    const parsed = JSON.parse(existing) as { payload: string; key: string }
    if (parsed.payload === serialized) {
      return { key: parsed.key, clear: () => sessionStorage.removeItem(storageKey) }
    }
  }
  const key = crypto.randomUUID()
  sessionStorage.setItem(storageKey, JSON.stringify({ payload: serialized, key }))
  return { key, clear: () => sessionStorage.removeItem(storageKey) }
}

export const reconcileInstancePatch = (authoritative: JsonObject, intended: JsonObject) => {
  const committed = Object.entries(intended).every(([key, value]) =>
    sameJsonValue(authoritative[key], value)
  )
  return { committed, values: committed ? authoritative : { ...authoritative, ...intended } }
}
