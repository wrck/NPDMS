import request from '@/config/axios'
import type { WorkBindingSpec } from './index'
import type { TemplateOperationContract } from './operationTypes'

/** The optional field is strictly parsed before editing; legacy responses keep it absent. */
export interface OperationWorkBindingSpec extends WorkBindingSpec {
  operationContract?: TemplateOperationContract
}

/** Deployment metadata, never an authorization grant or a runtime handler reference. */
export interface BusinessOperationDescriptor {
  /** Native functional permission, absent for legacy providers. Not a grant. */
  permissionCode?: string | null
  operationCode: string
  operationVersion: number
  label: string
  ownerAction: string
  checkpoints: ('PRE' | 'POST')[]
  runtimeAvailable: boolean
}

export const getBusinessOperationCatalog = (ownerContext: string, objectType: string) =>
  request.get<BusinessOperationDescriptor[]>({
    url: '/api/v1/pms/project-templates/operation-catalog',
    params: { ownerContext, objectType }
  })

export interface BusinessOperationSelection {
  status: 'RESOLVED' | 'NOT_FOUND' | 'AMBIGUOUS_OPERATION' | 'AMBIGUOUS_VERSION' | 'INVALID_REQUEST'
  selected: BusinessOperationDescriptor | null
  candidates: BusinessOperationDescriptor[]
}

/** Configuration lookup without a client-supplied version. Ambiguity must be resolved, never guessed. */
export const resolveBusinessOperation = (
  ownerContext: string, objectType: string, permissionCode: string, operationCode?: string
) => request.get<BusinessOperationSelection>({
  url: '/api/v1/pms/project-templates/operation-catalog/resolve',
  params: { ownerContext, objectType, permissionCode, operationCode }
})
