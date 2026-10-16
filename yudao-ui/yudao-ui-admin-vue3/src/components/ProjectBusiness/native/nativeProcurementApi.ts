import type { BusinessId } from '@/api/pms/platform/business'
import type { NativeBusinessBridge } from './nativeBusinessBridge'
import type { NativeValues } from './nativeBusinessContext'

const names = { outsource: 'OutsourceRequest', material: 'MaterialRequisition', procurement: 'ExternalProcurement', exchange: 'MaterialExchange' }
export const nativeProcurementApi = (kind: string, bridge: NativeBusinessBridge) => {
  const name = names[kind as keyof typeof names]
  if (!name) return undefined
  const values = (data: NativeValues) => ({ ...data,
    ...(kind === 'exchange' && data.serials !== undefined
      ? { $business: { ...data.$business, serials: data.serials } } : {}),
    ...(kind === 'outsource' && data.siteSurveyExecution !== undefined
      ? { $business: { ...data.$business, siteSurveyExecution: data.siteSurveyExecution } } : {}) })
  return {
    [`get${name}Page`]: (query: NativeValues) => bridge.page(query),
    [`get${name}`]: async (id: BusinessId) => (await bridge.read(id)).value,
    [`create${name}`]: (data: NativeValues) => bridge.create(values(data)),
    [`update${name}`]: (data: NativeValues) => bridge.update(values(data)),
    [`delete${name}`]: (id: BusinessId, execution?: NativeValues) => bridge.remove(id, undefined, undefined,
      kind === 'outsource' && execution ? { execution } : undefined),
    [`submit${name}`]: (id: BusinessId) => bridge.command('submit', id),
    [`withdraw${name}`]: (id: BusinessId) => bridge.command('withdraw', id),
    [`terminate${name}`]: (id: BusinessId) => bridge.command('terminate', id),
    [`approve${name}`]: (data: NativeValues) => bridge.command('approve', data.id,
      { action: data.approveAction, opinion: data.approveOpinion }, data.version)
  }
}
