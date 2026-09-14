import type { ComputedRef, InjectionKey } from 'vue'
import type { TemplateDesignerDocument } from '@/api/pms/project/project-templates'

export interface RuleBusinessSource {
  key: string
  label: string
  ownerContext?: string
  objectType?: string
}

export const ruleBusinessSourcesKey: InjectionKey<ComputedRef<RuleBusinessSource[]>> =
  Symbol('rule-business-sources')

export const businessSources = (document: TemplateDesignerDocument): RuleBusinessSource[] =>
  [
    ...document.stages.map((node) => ({ node, kind: '阶段' })),
    ...document.tasks.map((node) => ({ node, kind: '任务' }))
  ]
    .filter(({ node }) => ['BUSINESS_OBJECT', 'BUSINESS_COMPONENT'].includes(node.workBinding?.type ?? ''))
    .map(({ node, kind }) => ({
      key: node.nodeKey,
      label: `${kind} · ${node.name || node.code}`,
      ownerContext: node.workBinding?.targetContextCode,
      objectType: node.workBinding?.targetObjectType
    }))
