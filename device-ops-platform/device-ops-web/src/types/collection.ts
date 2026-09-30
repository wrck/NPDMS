import type { SemanticParsingRequest } from '@/types/parser'
import type { SerialParams, SerialPrompts } from '@/utils/serial-connection'

export type CollectionStatus =
  | 'QUEUED'
  | 'CONNECTING'
  | 'EXECUTING'
  | 'PARSING'
  | 'SUCCEEDED'
  | 'PARTIAL_SUCCESS'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'CANCELLED'

export interface ProjectProjection {
  namespace: string
  projectKey: string
  projectName: string
  projectCode: string
}

export interface DeviceProjection {
  deviceKey: string
  deviceName: string
  vendor: string
  model: string
}

export interface ScriptDraft {
  source: 'LOCAL_MANAGED' | 'EXTERNAL_DELIVERED' | 'ADHOC_INLINE'
  key: string
  version: string
  content: string
  sha256: string
  policy: 'EXECUTION_ONLY' | 'REGISTER_VERSION'
  parserType: 'NONE' | 'JSON' | 'KEY_VALUE'
  parserConfig: string
}

export type ConnectionProtocol = 'SSH2' | 'TELNET' | 'SERIAL'
export type ExecutionMode = 'EXEC' | 'SHELL'
export type ConnectionSource = 'PROJECT_DEVICE' | 'QUICK' | 'RECENT'

export interface TelnetPrompts {
  login: string
  password: string
  command: string
  lineEnding: 'AUTO' | 'CRLF' | 'CR' | 'LF'
}

interface DirectConnectionFields {
  host: string
  port: number
  username: string
  connectTimeoutSeconds: number
}

export type TransientConnectionRequest = DirectConnectionFields &
  (
    | {
        protocol: 'SSH2'
        authenticationType: 'PASSWORD'
        executionMode: ExecutionMode
        hostKeyFingerprint?: string
        telnetPrompts?: never
        password: string
        privateKey?: never
        passphrase?: never
      }
    | {
        protocol: 'SSH2'
        authenticationType: 'PRIVATE_KEY'
        executionMode: ExecutionMode
        hostKeyFingerprint?: string
        telnetPrompts?: never
        password?: never
        privateKey: string
        passphrase?: string
      }
    | {
        protocol: 'TELNET'
        authenticationType: 'PASSWORD'
        executionMode: 'SHELL'
        hostKeyFingerprint?: never
        telnetPrompts: TelnetPrompts
        password: string
        privateKey?: never
        passphrase?: never
      }
    | {
        protocol: 'SERIAL'
        authenticationType: 'PASSWORD'
        executionMode: 'SHELL'
        hostKeyFingerprint?: never
        telnetPrompts?: never
        serialParams: SerialParams
        serialPrompts: SerialPrompts
        password: string
        privateKey?: never
        passphrase?: never
      }
  )

export type SavedConnectionWriteConnection = DirectConnectionFields &
  (
    | {
        protocol: 'SSH2'
        authenticationType: 'PASSWORD'
        executionMode: ExecutionMode
        hostKeyFingerprint?: string
        telnetPrompts?: never
        password?: string
        privateKey?: never
        passphrase?: never
      }
    | {
        protocol: 'SSH2'
        authenticationType: 'PRIVATE_KEY'
        executionMode: ExecutionMode
        hostKeyFingerprint?: string
        telnetPrompts?: never
        password?: never
        privateKey?: string
        passphrase?: string
      }
    | {
        protocol: 'TELNET'
        authenticationType: 'PASSWORD'
        executionMode: 'SHELL'
        hostKeyFingerprint?: never
        telnetPrompts: TelnetPrompts
        password?: string
        privateKey?: never
        passphrase?: never
      }
    | {
        protocol: 'SERIAL'
        authenticationType: 'PASSWORD'
        executionMode: 'SHELL'
        hostKeyFingerprint?: never
        telnetPrompts?: never
        serialParams: SerialParams
        serialPrompts: SerialPrompts
        password?: string
        privateKey?: never
        passphrase?: never
      }
  )

export interface SavedConnectionReference {
  savedConnectionId: string
  credentialNamespace: string
}

export type ConnectionRequest = SavedConnectionReference | TransientConnectionRequest

interface CollectionTargetContext {
  project: ProjectProjection
  device: DeviceProjection
  extensions: Record<string, string>
}

type DirectCollectionReferenceExclusion = {
  savedConnectionId?: never
  credentialNamespace?: never
}

export type DirectCollectionTarget =
  | (Extract<TransientConnectionRequest, { protocol: 'SSH2' }> &
      DirectCollectionReferenceExclusion)
  | (Extract<TransientConnectionRequest, { protocol: 'TELNET' }> &
      DirectCollectionReferenceExclusion & { executionMode: 'SHELL' })
  | (Extract<TransientConnectionRequest, { protocol: 'SERIAL' }> &
      DirectCollectionReferenceExclusion)

type SavedCollectionTarget = SavedConnectionReference & {
  executionMode?: never
  protocol?: never
  host?: never
  port?: never
  username?: never
  authenticationType?: never
  hostKeyFingerprint?: never
  telnetPrompts?: never
  serialParams?: never
  serialPrompts?: never
  connectTimeoutSeconds?: never
  password?: never
  privateKey?: never
  passphrase?: never
}

export type CollectionTargetRequest = CollectionTargetContext &
  (SavedCollectionTarget | DirectCollectionTarget)

export interface SubmitCollectionRequest {
  namespace: string
  project: ProjectProjection
  targets: CollectionTargetRequest[]
  script: ScriptDraft
  externalRequestId?: string
  activityType: string
  commandTimeoutSeconds: number
  parseTimeoutSeconds: number
  leaseGraceSeconds: number
  semanticParsing?: SemanticParsingRequest
}

export interface GenericCollectionContext {
  project?: ProjectProjection
  device?: DeviceProjection
  extensions: Record<string, string>
}

export interface SubmitGenericCollectionRequest {
  namespace: string
  context?: GenericCollectionContext
  connection: ConnectionRequest
  script: ScriptDraft
  externalRequestId?: string
  activityType: string
  commandTimeoutSeconds: number
  parseTimeoutSeconds: number
  leaseGraceSeconds: number
  semanticParsing?: SemanticParsingRequest
}

export type CollectionSubmission =
  | {
      mode: 'project'
      projectKey: string
      request: SubmitCollectionRequest
      deviceLabel: string
    }
  | {
      mode: 'generic'
      request: SubmitGenericCollectionRequest
      deviceLabel: string
    }

export interface CollectionDetails {
  collectionId: string
  namespace: string
  projectKey?: string
  externalRequestId?: string
  activityType?: string
  status: CollectionStatus
  script: Omit<ScriptDraft, 'content' | 'policy' | 'parserConfig'>
  targets: CollectionTargetDetails[]
}

export interface CollectionTargetDetails {
  targetId: number
  contextSnapshot: {
    project?: ProjectProjection
    device?: DeviceProjection
    extensions: Record<string, string>
  }
  endpointSnapshot: {
    protocol: ConnectionProtocol
    host: string
    port: number
    username: string
    hostKeyFingerprint?: string
  }
  status: CollectionStatus
  stdout: string
  stderr: string
  exitCode?: number
  truncated: boolean
  parsedFacts: Record<string, string>
  outcome?: string
  commandBlocks: CommandOutputBlock[]
}

export type CommandBlockStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'CANCELLED'

export interface CommandOutputBlock {
  commandIndex: number
  commandText: string
  status: CommandBlockStatus
  stdout: string
  stderr: string
  receivedBytes: number
  pageCount: number
  truncated: boolean
  exitCode?: number
  outcome?: string
  parsedFacts: Record<string, string>
  parseWarnings: string[]
  startedAt?: string
  completedAt?: string
  legacy: boolean
}

export interface CollectionOutputEvent {
  targetId: number
  sequence: number
  commandIndex: number | null
  stream: 'STDOUT' | 'STDERR'
  content: string
  receivedBytes: number
  pageCount: number
  truncated: boolean
  createdAt: string
}
