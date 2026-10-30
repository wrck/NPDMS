import {
  decodeConf,
  decodeFields,
  encodeConf,
  encodeFields,
  setConfAndFields
} from '@/utils/formCreate'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import { sameJsonValue } from './dynamicFormRuntime'
import { nextTick, watch, type WatchStopHandle } from 'vue'

export const decodeDynamicForm = (formConfJson: JsonObject, formRulesJson: JsonObject[]) => ({
  option: decodeConf(JSON.stringify(formConfJson)) as unknown as JsonObject,
  rule: decodeFields(formRulesJson.map((rule) => JSON.stringify(rule))) as unknown as JsonObject[]
})

const exportDesigner = (designer: object) => ({
  formConfJson: JSON.parse(encodeConf(designer)) as JsonObject,
  formRulesJson: encodeFields(designer).map((rule) => JSON.parse(rule) as JsonObject)
})

type Payload = ReturnType<typeof exportDesigner>
type DesignerRef = {
  value: {
    setRule: (rules: JsonObject[] | string) => void
    setOptions?: (options: JsonObject | string) => void
  }
}
type Session = {
  source: Payload
  baseline: Payload
  suspended: boolean
  identities: Map<string, { source: unknown; baseline: JsonObject }>
  stop?: WatchStopHandle
  restoreMethods?: () => void
}
const sessions = new WeakMap<object, Session>()
const clone = <T>(value: T): T => (value === undefined ? value : JSON.parse(JSON.stringify(value)))
const isObject = (value: unknown): value is JsonObject =>
  value !== null && typeof value === 'object' && !Array.isArray(value)
type Change = {
  source: unknown
  before: unknown
  after: unknown
  identities: Session['identities']
}
const ruleId = (value: unknown) =>
  isObject(value) && typeof value.type === 'string' && typeof value._fc_id === 'string'
    ? value._fc_id
    : undefined

const retainIdentities = ({ source, before, identities }: Omit<Change, 'after'>) => {
  if (Array.isArray(before)) {
    before.forEach((item, index) =>
      retainIdentities({
        source: Array.isArray(source) ? source[index] : undefined,
        before: item,
        identities
      })
    )
  } else if (isObject(before)) {
    const id = ruleId(before)
    if (id) identities.set(id, { source: clone(source), baseline: clone(before) })
    Object.keys(before).forEach((key) =>
      retainIdentities({
        source: isObject(source) ? source[key] : undefined,
        before: before[key],
        identities
      })
    )
  }
}

// Array membership is transient during dragEnd/dragAdd/keyboard moves. Keep
// provenance for the whole editing session, while exports contain only rules
// currently present. Explicit raw imports and reload/close reset this ledger.
const reconcile = (change: Change): unknown => {
  const id = ruleId(change.after)
  const retained = id ? change.identities.get(id) : undefined
  const result = reconcileValue(
    retained && ruleId(change.before) !== id
      ? { ...change, source: retained.source, before: retained.baseline }
      : change
  )
  if (id)
    change.identities.set(id, {
      source: clone(result),
      baseline: clone(change.after as JsonObject)
    })
  return result
}

// Apply only changes made after loading. In particular, absent and false source
// values must not be replaced by the designer's materialized defaults.
const reconcileValue = ({ source, before, after, identities }: Change): unknown => {
  if (sameJsonValue(before, after)) return clone(source)
  if (Array.isArray(before) && Array.isArray(after) && Array.isArray(source)) {
    const ids = before.map((item) => (isObject(item) ? item._fc_id : undefined))
    const currentIds = after.map((item) => (isObject(item) ? item._fc_id : undefined))
    if (
      ids.every(Boolean) &&
      currentIds.every(Boolean) &&
      new Set(ids).size === ids.length &&
      new Set(currentIds).size === currentIds.length
    ) {
      return after.map((item, index) => {
        const previous = ids.indexOf(currentIds[index])
        return reconcile({
          source: source[previous],
          before: before[previous],
          after: item,
          identities
        })
      })
    }
    if (before.length === after.length && source.length === before.length) {
      return after.map((item, index) =>
        reconcile({ source: source[index], before: before[index], after: item, identities })
      )
    }
    return after.map((item) =>
      reconcile({ source: undefined, before: undefined, after: item, identities })
    )
  }
  if (isObject(before) && isObject(after) && (isObject(source) || source === undefined)) {
    const result = clone(source || {})
    new Set([...Object.keys(before), ...Object.keys(after)]).forEach((key) => {
      if (
        sameJsonValue(before[key], after[key]) &&
        Object.hasOwn(before, key) === Object.hasOwn(after, key)
      )
        return
      if (!Object.hasOwn(after, key)) delete result[key]
      else
        result[key] = reconcile({
          source: isObject(source) ? source[key] : undefined,
          before: before[key],
          after: after[key],
          identities
        })
    })
    return result
  }
  if (Array.isArray(after))
    return after.map((item) =>
      reconcile({ source: undefined, before: undefined, after: item, identities })
    )
  if (isObject(after))
    return Object.fromEntries(
      Object.entries(after).map(([key, item]) => [
        key,
        reconcile({ source: undefined, before: undefined, after: item, identities })
      ])
    )
  return clone(after)
}

const readChanges = (designer: object, session: Session) => {
  const current = exportDesigner(designer)
  session.source = reconcile({
    source: session.source,
    before: session.baseline,
    after: current,
    identities: session.identities
  }) as Payload
  session.baseline = current
  return clone(session.source)
}

export const encodeDynamicForm = (designer: object) => {
  const session = sessions.get(designer)
  return session ? readChanges(designer, session) : exportDesigner(designer)
}

export const releaseDynamicFormDesigner = (designer: object) => {
  const session = sessions.get(designer)
  session?.stop?.()
  session?.restoreMethods?.()
  sessions.delete(designer)
}

export const restoreDynamicFormDesigner = async (
  designer: object,
  formConfJson: JsonObject,
  formRulesJson: JsonObject[]
) => {
  releaseDynamicFormDesigner(designer)
  setConfAndFields(
    designer,
    JSON.stringify(formConfJson),
    formRulesJson.map((rule) => JSON.stringify(rule))
  )
  await nextTick()
  const session: Session = {
    source: clone({ formConfJson, formRulesJson }),
    baseline: exportDesigner(designer),
    suspended: false,
    identities: new Map()
  }
  retainIdentities({
    source: session.source.formRulesJson,
    before: session.baseline.formRulesJson,
    identities: session.identities
  })
  sessions.set(designer, session)
  // JsonPreview edits call these exposed methods in designer 3.4.0. Keep the
  // explicit raw JSON as the new source before its defaults are injected.
  const instance = (designer as DesignerRef).value
  const originalRule = instance.setRule
  const originalOptions = instance.setOptions
  const importJson = (part: keyof Payload, value: unknown, apply: () => void) => {
    const raw = typeof value === 'string' ? JSON.parse(value) : clone(value)
    readChanges(designer, session)
    session.suspended = true
    try {
      apply()
      session.source[part] = raw
      session.baseline = exportDesigner(designer)
      if (part === 'formRulesJson') {
        session.identities.clear()
        retainIdentities({
          source: session.source.formRulesJson,
          before: session.baseline.formRulesJson,
          identities: session.identities
        })
      }
    } finally {
      session.suspended = false
    }
  }
  instance.setRule = (value) => importJson('formRulesJson', value, () => originalRule(value))
  if (originalOptions) {
    instance.setOptions = (value) => importJson('formConfJson', value, () => originalOptions(value))
  }
  session.restoreMethods = () => {
    instance.setRule = originalRule
    if (originalOptions) instance.setOptions = originalOptions
  }
  session.stop = watch(
    () => exportDesigner(designer),
    () => {
      if (!session.suspended) readChanges(designer, session)
    },
    { deep: true, flush: 'sync' }
  )
}

export const sameDynamicFormPayload = (
  current: { formConfJson: JsonObject; formRulesJson: JsonObject[] },
  intended: { formConfJson: JsonObject; formRulesJson: JsonObject[] }
) =>
  sameJsonValue(current.formConfJson, intended.formConfJson) &&
  sameJsonValue(current.formRulesJson, intended.formRulesJson)
