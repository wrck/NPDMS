import type { ComputedRef, InjectionKey } from 'vue'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'

export const ruleNativeOptionsKey: InjectionKey<ComputedRef<string[] | undefined>> =
  Symbol('rule-native-options')

/** A shared condition must be usable at every consuming slot; never rewrite its expression. */
export function nativeOptions(document: TemplateDesignerDocument, key?: string): string[] {
  if (!key || document.matchRuleKey === key || document.closureRuleKey === key
    || document.transitions.some(edge => edge.conditionRuleKey === key)) return []
  const allowed = new Set(['STAGE_NATIVE_STATUS', 'TASK_NATIVE_STATUS', 'PROJECT_CONTACT_EXISTS'])
  let used = false
  for (const [nodes, type] of [[document.stages, 'STAGE_NATIVE'], [document.tasks, 'TASK_NATIVE']] as const) {
    for (const node of nodes) {
      if (node.admissionRuleKey === key) return []
      if (node.completionRuleKey !== key && node.exitRuleKey !== key) continue
      used = true
      for (const predicate of allowed) {
        if (predicate === 'PROJECT_CONTACT_EXISTS') {
          if (type !== 'TASK_NATIVE' || node.completionRuleKey !== key || node.exitRuleKey === key
            || node.workBinding?.type !== 'PAGE' || node.workBinding.parameters?.routePath !== '/customer-asset/customer-contact') allowed.delete(predicate)
        } else if (![type, 'PAGE'].includes(node.workBinding?.type ?? '') || predicate !== `${type}_STATUS`) allowed.delete(predicate)
      }
    }
  }
  return used ? [...allowed] : []
}
