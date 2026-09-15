import dayjs from 'dayjs'
import duration from 'dayjs/plugin/duration'
import type { ComputedRef, InjectionKey } from 'vue'
import type { JsonObject, TemplateDesignerDocument } from '@/api/pms/project/project-templates'

dayjs.extend(duration)
export const waitUnits = { days: '天', hours: '小时', minutes: '分钟', seconds: '秒' } as const
export type WaitUnit = keyof typeof waitUnits
export interface RelativeTimeOptions {
  available: boolean
  activation: boolean
  sources: { key: string; label: string; disabled: boolean }[]
}
export const relativeTimeOptionsKey: InjectionKey<ComputedRef<RelativeTimeOptions | undefined>> =
  Symbol('relative-time-options')

/** Version-local sources include manual and unbound nodes, not only business-bound nodes. */
export function relativeTimeOptions(document: TemplateDesignerDocument, key?: string): RelativeTimeOptions {
  const nodes = [
    ...document.stages.map(node => ({ node, kind: '阶段' })),
    ...document.tasks.map(node => ({ node, kind: '任务' }))
  ]
  const consumers = new Set(nodes.filter(({ node }) => key &&
    [node.admissionRuleKey, node.completionRuleKey, node.exitRuleKey].includes(key)).map(({ node }) => node.nodeKey))
  const available = !!key && (consumers.size > 0 || document.closureRuleKey === key)
    && document.matchRuleKey !== key && !document.transitions.some(edge => edge.conditionRuleKey === key)
  return {
    available,
    activation: available && document.closureRuleKey !== key && !nodes.some(({ node }) => node.admissionRuleKey === key),
    sources: nodes.map(({ node, kind }) => ({ key: node.nodeKey,
      label: `${kind} · ${node.name || node.code}（${node.code}）`, disabled: consumers.has(node.nodeKey) }))
  }
}

export function initialRelativeTime(options?: RelativeTimeOptions): JsonObject {
  return options && !options.activation
    ? { anchor: 'NODE_COMPLETED', sourceNodeKey: '', duration: '' }
    : { anchor: 'NODE_ACTIVATED', duration: '' }
}

/** Parse for presentation only; original JSON is untouched until the user edits the amount. */
export function waitSeconds(value: unknown): number | undefined {
  if (typeof value !== 'string' || !value || value.includes('-') || value.includes(',')) return undefined
  // Day.js also supports calendar units, but the backend's elapsed-time contract does not.
  if (['Y', 'M', 'W'].some(unit => value.split('T', 1)[0].includes(unit))) return undefined
  const parsed = dayjs.duration(value)
  const seconds = parsed.asSeconds()
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined
}

export function preferredWaitUnit(value: unknown): WaitUnit {
  const seconds = waitSeconds(value)
  if (!seconds) return 'minutes'
  return (Object.keys(waitUnits) as WaitUnit[]).find(unit => Number.isInteger(seconds / dayjs.duration({ [unit]: 1 }).asSeconds())) ?? 'seconds'
}

export const waitAmount = (value: unknown, unit: WaitUnit): number | undefined => {
  const seconds = waitSeconds(value)
  return seconds === undefined ? undefined : seconds / dayjs.duration({ [unit]: 1 }).asSeconds()
}

export function waitDuration(amount: number | undefined, unit: WaitUnit): string {
  if (amount === undefined || !Number.isFinite(amount) || amount < 0) return ''
  // Object construction avoids normalizing long waits into calendar months/years.
  // https://day.js.org/docs/en/durations/creating
  // https://day.js.org/docs/en/durations/as-iso-string
  return dayjs.duration({ seconds: dayjs.duration({ [unit]: amount }).asSeconds() }).toISOString()
}
