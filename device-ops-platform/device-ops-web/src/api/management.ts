import { deviceOpsApi } from './device-ops'
import type { CollectionFilters, CollectionSummary, ManagementSettings, Page, ScriptContent, ScriptSummary } from '@/types/management'
const get = <T>(resource: string, params: object, signal?: AbortSignal) => deviceOpsApi.get<T>(`management/${resource}`, { params, signal }).then(r => r.data)
export const managementApi = {
  collections: (params: CollectionFilters, signal?: AbortSignal) => get<Page<CollectionSummary>>('collections', params, signal),
  overview: (params: CollectionFilters = {}, signal?: AbortSignal) => get<{ total: number; byStatus: Record<string, number> }>('overview', params, signal),
  scripts: (params: Pick<CollectionFilters, 'namespace' | 'project' | 'page' | 'size'>, signal?: AbortSignal) => get<Page<ScriptSummary>>('scripts', params, signal),
  content: (collectionId: string, signal?: AbortSignal) => get<ScriptContent>('scripts/content', { collectionId }, signal),
  settings: (signal?: AbortSignal) => get<ManagementSettings>('settings', {}, signal)
}
