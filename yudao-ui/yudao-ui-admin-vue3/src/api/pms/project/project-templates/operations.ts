import request from '@/config/axios'
import type { WorkBindingSpec } from './index'
import type { TemplateOperationContract } from './operationTypes'

/** The optional field is strictly parsed before editing; legacy responses keep it absent. */
export interface OperationWorkBindingSpec extends WorkBindingSpec {
  operationContract?: TemplateOperationContract
}

/** Deployment metadata, never an authorization grant or a runtime handler reference. */
export interface BusinessOperationDescriptor {
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
