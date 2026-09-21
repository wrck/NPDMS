import axios, { type AxiosRequestConfig } from 'axios'

import { getAccessToken } from '@/auth/oidc'
import { loadRuntimeConfig } from '@/config/runtime'
import type {
  CollectionDetails,
  ConnectionRequest,
  DeviceProjection,
  ProjectProjection,
  SubmitGenericCollectionRequest,
  SubmitCollectionRequest
} from '@/types/collection'
import type {
  CollectionSemanticResult,
  ParserSelectionOptions
} from '@/types/parser'

export type ProjectKey = string

export function createIdempotencyKey(): string {
  return crypto.randomUUID()
}

export const deviceOpsApi = axios.create({ timeout: 30_000 })

deviceOpsApi.interceptors.request.use(async (request) => {
  const config = await loadRuntimeConfig()
  request.baseURL = config.apiBaseUrl

  const token = await getAccessToken()
  if (token) {
    request.headers.Authorization = `Bearer ${token}`
  }
  if (request.method?.toUpperCase() === 'POST') {
    request.headers['Idempotency-Key'] =
      request.headers['Idempotency-Key'] ?? createIdempotencyKey()
  }
  return request
})

export function projectApiPath(projectKey: ProjectKey, resource: string): string {
  return `projects/${encodeURIComponent(projectKey)}/${resource}`
}

export function getProjectResource<T>(
  projectKey: ProjectKey,
  resource: string,
  config?: AxiosRequestConfig
): Promise<T> {
  return deviceOpsApi
    .get<T>(projectApiPath(projectKey, resource), config)
    .then(({ data }) => data)
}

export function findProjects(query = ''): Promise<ProjectProjection[]> {
  return deviceOpsApi
    .get<ProjectProjection[]>('master-data/projects', { params: { query } })
    .then(({ data }) => data)
}

export function findDevices(projectKey: ProjectKey, query = ''): Promise<DeviceProjection[]> {
  return deviceOpsApi
    .get<DeviceProjection[]>(
      `master-data/projects/${encodeURIComponent(projectKey)}/devices`,
      { params: { query } }
    )
    .then(({ data }) => data)
}

export function submitCollection(
  projectKey: ProjectKey,
  request: SubmitCollectionRequest,
  signal?: AbortSignal
): Promise<{ collectionId: string; existing: boolean }> {
  return deviceOpsApi
    .post<{ collectionId: string; existing: boolean }>(
      projectApiPath(projectKey, 'collections'),
      request,
      { signal }
    )
    .then(({ data }) => data)
}

export function getCollection(
  projectKey: ProjectKey,
  namespace: string,
  collectionId: string,
  signal?: AbortSignal
): Promise<CollectionDetails> {
  return getProjectResource<CollectionDetails>(
    projectKey,
    `collections/${encodeURIComponent(collectionId)}`,
    { params: { namespace }, signal }
  )
}

export function testConnection(
  request: ConnectionRequest,
  signal?: AbortSignal
): Promise<ConnectionTestResult> {
  return deviceOpsApi
    .post<ConnectionTestResult>('connections/test', request, { signal })
    .then(({ data }) => data)
}

export interface ConnectionTestResult {
  reachable: boolean
  stage: string | null
  durationMillis: number
  errorCode: string | null
  safeMessage: string
}

export function submitGenericCollection(
  request: SubmitGenericCollectionRequest,
  signal?: AbortSignal
): Promise<{ collectionId: string; existing: boolean }> {
  return deviceOpsApi
    .post<{ collectionId: string; existing: boolean }>('collections', request, { signal })
    .then(({ data }) => data)
}

export function getGenericCollection(
  namespace: string,
  collectionId: string,
  signal?: AbortSignal
): Promise<CollectionDetails> {
  return deviceOpsApi
    .get<CollectionDetails>(`collections/${encodeURIComponent(collectionId)}`, {
      params: { namespace },
      signal
    })
    .then(({ data }) => data)
}

export function getParserSelectionOptions(signal?: AbortSignal): Promise<ParserSelectionOptions> {
  return deviceOpsApi
    .get<ParserSelectionOptions>('parser-options', { signal })
    .then(({ data }) => data)
}

export function getCollectionSemanticResults(
  namespace: string,
  collectionId: string,
  signal?: AbortSignal
): Promise<CollectionSemanticResult[]> {
  return deviceOpsApi
    .get<CollectionSemanticResult[]>(
      `collections/${encodeURIComponent(collectionId)}/semantic-results`,
      { params: { namespace }, signal }
    )
    .then(({ data }) => data)
}

export interface ScheduleRequest {
  namespace: string
  projectKey: string
  projectHint: string
  deviceKeyHints: string[]
  scriptKey: string
  scriptVersion: string
  cron: string
  timezone: string
  callbackUri: string
  enabled: boolean
}

export function upsertSchedule(
  projectKey: ProjectKey,
  scheduleKey: string,
  request: ScheduleRequest
): Promise<void> {
  return deviceOpsApi
    .put(
      projectApiPath(projectKey, `schedules/${encodeURIComponent(scheduleKey)}`),
      request,
      { headers: { 'Idempotency-Key': createIdempotencyKey() } }
    )
    .then(() => undefined)
}
