import type { ComputedRef, InjectionKey } from 'vue'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'

export const ruleNativeOptionsKey: InjectionKey<ComputedRef<string[] | undefined>> =
  Symbol('rule-native-options')

/** A shared condition must be usable at every consuming slot; never rewrite its expression. */
export function nativeOptions(document: TemplateDesignerDocument, key?: string): string[] {
  if (!key || document.matchRuleKey === key || document.closureRuleKey === key
    || document.transitions.some(edge => edge.conditionRuleKey === key)) return []
  const allowed = new Set(['STAGE_NATIVE_STATUS', 'TASK_NATIVE_STATUS'])
  let used = false
  for (const [nodes, type] of [[document.stages, 'STAGE_NATIVE'], [document.tasks, 'TASK_NATIVE']] as const) {
    for (const node of nodes) {
      if (node.admissionRuleKey === key) return []
      if (node.completionRuleKey !== key && node.exitRuleKey !== key) continue
      used = true
      for (const predicate of allowed)
        if (node.workBinding?.type !== type || predicate !== `${type}_STATUS`) allowed.delete(predicate)
    }
  }
  return used ? [...allowed] : []
}
