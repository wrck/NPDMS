import { deviceOpsApi } from '@/api/device-ops'

export type CredentialAuthenticationType = 'PASSWORD' | 'PRIVATE_KEY'

export interface SavedCredential {
  id: string
  namespace: string
  name: string
  authenticationType: CredentialAuthenticationType
  keyVersion: string
  createdAt: string
  updatedAt: string
}

export interface CredentialWriteRequest {
  namespace: string
  name: string
  authenticationType: CredentialAuthenticationType
  password?: string
  privateKey?: string
  passphrase?: string
}

export function listCredentials(namespace: string): Promise<{
  available: boolean
  items: SavedCredential[]
}> {
  return deviceOpsApi
    .get<{ available: boolean; items: SavedCredential[] }>('credentials', {
      params: { namespace }
    })
    .then(({ data }) => data)
}

export function createCredential(request: CredentialWriteRequest): Promise<SavedCredential> {
  return deviceOpsApi.post<SavedCredential>('credentials', request).then(({ data }) => data)
}

export function replaceCredential(
  id: string,
  request: CredentialWriteRequest
): Promise<SavedCredential> {
  return deviceOpsApi
    .put<SavedCredential>(`credentials/${encodeURIComponent(id)}`, request)
    .then(({ data }) => data)
}

export function deleteCredential(id: string, namespace: string): Promise<void> {
  return deviceOpsApi
    .delete(`credentials/${encodeURIComponent(id)}`, { params: { namespace } })
    .then(() => undefined)
}
