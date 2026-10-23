import type { InjectionKey, Ref } from 'vue'
import type { AcceptanceType } from '@/api/pms/acceptance/acceptance-report'
import type { BusinessViewId } from '@/api/pms/platform/business-view/ids'

// A manual upload click survives the Host selecting the newly authorized Owner object.
export interface AcceptanceUploadIntent {
  projectId: BusinessViewId
  acceptanceId: BusinessViewId
  acceptanceType: AcceptanceType
}
export const acceptanceUploadIntentKey: InjectionKey<Ref<AcceptanceUploadIntent | undefined>> = Symbol('acceptance-upload-intent')
