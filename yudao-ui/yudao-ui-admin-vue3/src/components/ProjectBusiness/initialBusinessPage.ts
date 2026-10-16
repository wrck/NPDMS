import type { BusinessId, BusinessPageInitialData, ProjectBusinessApi } from '@/api/pms/platform/business'
import type { FieldFilter } from '@/api/pms/platform/businessmodel'
import { getCurrentUserId, getTenantId, getVisitTenantId } from '@/utils/auth'
import { businessBindingFilters, businessBindingSelectors } from './businessBinding'

const caller = () => JSON.stringify([getCurrentUserId(), getTenantId(), getVisitTenantId()])
const filtersKey = (filters: FieldFilter[]) => JSON.stringify([...filters].sort((a, b) => a.fieldCode.localeCompare(b.fieldCode)))

/** Adapts the Owner's initial state to existing page components. It never starts or schedules reads. */
export function initialBusinessPage(api: ProjectBusinessApi, data: BusinessPageInitialData) {
  const actor = caller(), started = Date.now()
  const configuration = data.request.configurationName ?? data.configuration.defaultProfile
  const parameters = data.request.bindingParameters ? JSON.parse(data.request.bindingParameters) : {}
  const selectors = businessBindingSelectors(parameters, data.model.fields)
  const expectedFilters = filtersKey(businessBindingFilters(selectors))
  const forms = new Map(data.forms.map(form => [String(form.entity.ref.entityId), form]))
  const pages = new Set<string>()
  let modelUsed = false, configurationUsed = false, entityUsed = false, contextUsed = false, closed = false
  const valid = () => !closed && caller() === actor && Date.now() - started < 4000
  const fresh = <T>(read: () => T): T => { closed = true; return read() }
  const client: ProjectBusinessApi = { ...api,
    model: name => {
      if (!modelUsed && valid() && (name == null || name === configuration)) {
        modelUsed = true
        return Promise.resolve(data.model)
      }
      return fresh(() => api.model(name))
    },
    fieldConfiguration: () => {
      if (!configurationUsed && valid()) { configurationUsed = true; return Promise.resolve(data.configuration) }
      return fresh(() => api.fieldConfiguration())
    },
    page: (...args) => {
      const [pageNo, pageSize, filters, sorts, projectId, name] = args
      const key = `${pageNo}:${pageSize}`, start = (pageNo - 1) * pageSize, end = start + pageSize
      if (valid() && !pages.has(key) && pageNo > 0 && pageSize > 0 && !sorts?.length
          && String(projectId) === String(data.request.projectId) && (name == null || name === configuration)
          && filtersKey(filters) === expectedFilters
          && (data.records.list.length >= end || data.records.list.length === Number(data.records.total))) {
        pages.add(key)
        return Promise.resolve({ list: data.records.list.slice(start, end), total: data.records.total })
      }
      return fresh(() => api.page(...args))
    },
    get: id => {
      const form = forms.get(String(id))
      if (!entityUsed && valid() && form && String(id) === String(data.request.initialEntityId)) {
        entityUsed = true
        return Promise.resolve(form.entity)
      }
      return fresh(() => api.get(id))
    },
    form: id => {
      const form = forms.get(String(id))
      if (valid() && form) { forms.delete(String(id)); return Promise.resolve(form) }
      return fresh(() => api.form(id))
    }
  }
  for (const name of ['create', 'update', 'remove', 'action', 'saveFieldConfiguration', 'startApproval',
    'cancelApproval', 'upload', 'editDelivery', 'removeDelivery'] as const) {
    client[name] = ((...args: unknown[]) => fresh(() => (api[name] as (...values: unknown[]) => unknown)(...args))) as never
  }
  return { api: client, takeContext: (projectId: BusinessId) => {
    if (contextUsed || !valid() || String(projectId) !== String(data.request.projectId)) return
    contextUsed = true
    return data.context
  } }
}
