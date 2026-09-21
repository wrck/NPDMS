import type {
  ConnectionRequest,
  SubmitCollectionRequest,
  SubmitGenericCollectionRequest
} from '@/types/collection'

type CredentialRequest = SubmitCollectionRequest | SubmitGenericCollectionRequest

function deleteCredentials(target: Record<string, unknown>): void {
  delete target.password
  delete target.privateKey
  delete target.passphrase
}

export function redactRequestSnapshot(request: CredentialRequest): Record<string, unknown> {
  const snapshot = structuredClone(request) as unknown as Record<string, unknown>
  if (Array.isArray(snapshot.targets)) {
    ;(snapshot.targets as Array<Record<string, unknown>>).forEach(deleteCredentials)
  } else if (snapshot.connection && typeof snapshot.connection === 'object') {
    deleteCredentials(snapshot.connection as Record<string, unknown>)
  }
  delete snapshot.callbackUrl
  if (snapshot.context && typeof snapshot.context === 'object') {
    delete (snapshot.context as Record<string, unknown>).extensions
  }
  if (Array.isArray(snapshot.targets)) {
    snapshot.targets.forEach((target: Record<string, unknown>) => { delete target.extensions })
  }
  const script = snapshot.script as Record<string, unknown>
  delete script.parserConfig
  return snapshot
}

export function scrubRequestCredentials(request: CredentialRequest): void {
  const targets = 'targets' in request ? request.targets : [request.connection]
  targets.forEach((target) => {
    if (isSavedConnectionReference(target)) return
    if ('password' in target) target.password = ''
    if ('privateKey' in target) target.privateKey = ''
    if ('passphrase' in target) target.passphrase = ''
  })
}

function isSavedConnectionReference(
  connection: ConnectionRequest | SubmitCollectionRequest['targets'][number]
): connection is { savedConnectionId: string; credentialNamespace: string } {
  return 'savedConnectionId' in connection && Boolean(connection.savedConnectionId)
}
