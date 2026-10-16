import type { NativeBusinessBridge } from './nativeBusinessBridge'
import type { BusinessId } from '@/api/pms/platform/business'
import type { NativeValues } from './nativeBusinessContext'
import request from '@/config/axios'
import type { BusinessOperationReceipt } from '@/api/pms/platform/businessmodel'

const names: Record<string, string> = { arrival: 'Arrival', installation: 'Installation', configuration: 'Configuration', jointTest: 'JointTest' }
export const nativeEngineeringApi = (kind: string, bridge: NativeBusinessBridge, original: object) => {
  const name = names[kind]
  if (!name) return undefined
  const get = async (id: BusinessId) => {
    const { value } = await bridge.read(id)
    if (kind === 'arrival') {
      // The file reference belongs to the arrival owner and is not a writable model field.
      const receipt = await (original as typeof import('@/api/pms/engineering/arrival')).getArrival(id as number)
      for (const key of ['signDocumentArtifactId', 'signDocumentVersionNo', 'signDocumentReferenceVersion']) value[key] = receipt[key]
    }
    return value
  }
  const values = (data: NativeValues) => kind === 'installation' && data.locationMaintenance
    ? { ...data, $business: { ...data.$business, locationMaintenance: data.locationMaintenance } } : data
  return {
    [`get${name}Page`]: (query: NativeValues) => bridge.page(query),
    [`get${name}`]: get,
    [`create${name}`]: (data: NativeValues) => bridge.create(values(data)),
    [`update${name}`]: (data: NativeValues) => bridge.update(values(data)),
    [`delete${name}`]: (id: BusinessId) => bridge.remove(id),
    [`start${name}`]: (id: BusinessId) => bridge.command('start', id),
    [`complete${name}`]: (id: BusinessId) => bridge.command('complete', id),
    [`markAbnormal${name}`]: (id: BusinessId) => bridge.command('abnormal', id),
    signArrival: (id: BusinessId) => bridge.command('sign', id),
    passJointTest: (id: BusinessId) => bridge.command('pass', id),
    failJointTest: (id: BusinessId, exceptionRecord: string) => bridge.command('fail', id, { exceptionRecord }),
    createInstallationBatch: async (data: NativeValues) => {
      await bridge.ready()
      const { equipmentIds, installTime: _time, installerUserId: _installer, ...shared } = data
      let ids: BusinessId[] = []
      await bridge.createBatch({ values: bridge.input(values(shared)), equipmentIds }, async (body, key) => {
        const result = await request.post<{ receipt: BusinessOperationReceipt; ids: BusinessId[] }>({
          url: `${bridge.api.base}/batch`, data: { ...body, idempotencyKey: key }, silentError: true })
        ids = result.ids
        return result.receipt
      })
      return ids
    }
  }
}
