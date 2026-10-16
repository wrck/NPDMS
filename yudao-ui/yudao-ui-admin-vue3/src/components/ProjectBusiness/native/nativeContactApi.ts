import type { BusinessId } from '@/api/pms/platform/business'
import type { NativeBusinessBridge } from './nativeBusinessBridge'
import type { NativeValues } from './nativeBusinessContext'

export const nativeContactApi = (bridge: NativeBusinessBridge) => {
  const values = (projectId: BusinessId, data: NativeValues) => ({ ...data, projectId, $business: {
    ...data.$business, expectedProjectVersion: data.expectedProjectVersion,
    ...(data.sourceContactId != null ? { sourceContactId: data.sourceContactId } : {}),
    ...(data.confirmNoPrimary != null ? { confirmNoPrimary: data.confirmNoPrimary } : {}) } })
  return {
    getProjectPage: (projectId: BusinessId, params: NativeValues) => bridge.page({ ...params, projectId }),
    createProjectContact: (projectId: BusinessId, data: NativeValues, key: string) => bridge.create(values(projectId, data), key),
    updateProjectContact: (projectId: BusinessId, data: NativeValues) => bridge.update(values(projectId, data)),
    deleteProjectContact: (_projectId: BusinessId, data: NativeValues, _projectVersion: number, confirmNoPrimary: boolean) =>
      bridge.remove(data.id, data.version, undefined, { confirmNoPrimary }),
    restoreProjectContact: (projectId: BusinessId, id: BusinessId, version: number, projectVersion: number, status: number) =>
      bridge.restore(id, version, { projectId, projectVersion, status })
  }
}
