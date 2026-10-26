import type { ComputedRef, InjectionKey } from 'vue'

// Reuse names already authorized and displayed by this approval instance's timeline.
export interface ApprovalPeople {
  processInstanceId: string
  businessKey: string
  names: Record<string, string>
}
export const approvalPeopleKey: InjectionKey<ComputedRef<ApprovalPeople>> = Symbol('approvalPeople')
