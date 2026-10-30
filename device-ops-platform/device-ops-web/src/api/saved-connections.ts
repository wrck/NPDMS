import { deviceOpsApi } from '@/api/device-ops'
import type {
  ConnectionProtocol,
  SavedConnectionWriteConnection,
  TelnetPrompts
} from '@/types/collection'
import type { SerialParams, SerialPrompts } from '@/utils/serial-connection'

export interface SavedConnection {
  id: string
  namespace: string
  displayName: string
  description: string | null
  connection: {
    protocol: ConnectionProtocol
    host: string
    port: number
    username: string
    authenticationType: 'PASSWORD' | 'PRIVATE_KEY'
    executionMode: 'EXEC' | 'SHELL'
    expectedHostKeyFingerprint: string | null
    telnetPrompts: TelnetPrompts | null
    serialParams: SerialParams | null
    serialPrompts: SerialPrompts | null
    connectTimeout: string | number
  }
  credentialSaved: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface SavedConnectionWriteRequest {
  namespace: string
  displayName: string
  description: string | null
  connection: SavedConnectionWriteConnection
}

export interface SavedConnectionReplaceRequest extends SavedConnectionWriteRequest {
  version: number
}

export interface SavedConnectionRenameRequest {
  namespace: string
  version: number
  displayName: string
  description: string | null
}

export interface SavedConnectionSaveResult {
  saved: boolean
  test: {
    reachable: boolean
    stage: string | null
    durationMillis: number
    errorCode: string | null
    safeMessage: string
  }
  connection: SavedConnection | null
}

export function listSavedConnections(namespace: string, signal?: AbortSignal): Promise<SavedConnection[]> {
  return deviceOpsApi
    .get<{ items: SavedConnection[] }>('saved-connections', { params: { namespace }, signal })
    .then(({ data }) => data.items)
}

export function getSavedConnection(
  id: string,
  namespace: string,
  signal?: AbortSignal
): Promise<SavedConnection> {
  return deviceOpsApi
    .get<SavedConnection>(`saved-connections/${encodeURIComponent(id)}`, {
      params: { namespace },
      signal
    })
    .then(({ data }) => data)
}

export function verifyAndCreateSavedConnection(
  request: SavedConnectionWriteRequest
): Promise<SavedConnectionSaveResult> {
  return deviceOpsApi
    .post<SavedConnectionSaveResult>('saved-connections/verify-and-create', request)
    .then(({ data }) => data)
}

export function verifyAndReplaceSavedConnection(
  id: string,
  request: SavedConnectionReplaceRequest
): Promise<SavedConnectionSaveResult> {
  return deviceOpsApi
    .put<SavedConnectionSaveResult>(
      `saved-connections/${encodeURIComponent(id)}/verify-and-replace`,
      request
    )
    .then(({ data }) => data)
}

export function renameSavedConnection(
  id: string,
  request: SavedConnectionRenameRequest
): Promise<SavedConnection> {
  return deviceOpsApi
    .patch<SavedConnection>(`saved-connections/${encodeURIComponent(id)}`, request)
    .then(({ data }) => data)
}

export function deleteSavedConnection(id: string, namespace: string, version: number): Promise<void> {
  return deviceOpsApi
    .delete(`saved-connections/${encodeURIComponent(id)}`, { params: { namespace, version } })
    .then(() => undefined)
}
