/** Browser routing only. The server reauthorizes every command; this client is not a permit. */
export type Id = string | number
export interface ExecutionVector { projectId: Id; taskId?: Id; stageId?: Id; executionContractId: Id; contractVersion: number; planVersionId: Id; executionId: Id }
export type Selection = { task?: ExecutionVector | null; stage?: ExecutionVector | null }
export interface Target { projectId: Id; nodeKind: 'TASK' | 'STAGE'; nodeId: Id }
export interface Result { ownerContext: string; objectType: string; objectId: string; revisionId?: string | null; objectVersion: number; businessFactVersion: string; resultCode: string; response: unknown; replayed: boolean }
export interface Capability {
  node: { projectId: Id; kind: 'TASK' | 'STAGE'; id: Id; status: string }
  execution?: Selection | null
  ownerFactVersion?: string | null
  reason?: string | null
  actions: Array<{ operationCode: string; operationVersion: number; allowed: boolean; reason?: string | null }>
}
export interface Intent { operationCode: string; objectId?: Id; expectedBusinessVersion?: number | (() => Promise<number>); input: Record<string, unknown>; key?: string }
export interface Command extends Target { execution: Selection; objectId?: string; expectedBusinessVersion?: number; expectedObjectFactVersion?: string | null; input: Record<string, unknown> }
export interface Transport {
  inspect(query: Target & { objectId?: string }): Promise<Capability>
  submit(code: string, command: Command, key: string): Promise<Result>
  newKey(): string
  submitted?(result: Result): void
}
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value))
export function selectionIdentity(selection: Selection | null | undefined): string {
  if (!selection || (!!selection.task === !!selection.stage)) throw new Error('EXECUTION_CONTEXT_REQUIRED')
  const row = selection.task || selection.stage!
  const kind = selection.task ? 'TASK' : 'STAGE'
  const fields = [row.projectId, kind, row.taskId ?? row.stageId, row.executionContractId, row.contractVersion, row.planVersionId, row.executionId]
  if (fields.some(value => value == null || String(value).length === 0)) throw new Error('EXECUTION_IDENTITY_INCOMPLETE')
  return JSON.stringify(fields.map(String))
}
export function stableJson(value: unknown): string {
  const normalize = (item: unknown): unknown => {
    if (Array.isArray(item)) return item.map(normalize)
    if (item && typeof item === 'object') return Object.fromEntries(Object.entries(item).filter(([, value]) => value !== undefined).sort(([a], [b]) => a.localeCompare(b)).map(([key, value]) => [key, normalize(value)]))
    return item
  }
  return JSON.stringify(normalize(value))
}
/** One mounted node/round owns a client. Editing buffers can retain an invalidated old client, never a new round. */
export class OperationClient {
  private readonly anchor: string
  private live = true
  private inFlight = 0
  private readonly pending = new Map<string, { fingerprint: string; key: string; command?: Command; result?: Result; promise?: Promise<Result> }>()
  readonly target: Target
  private readonly transport: Transport
  constructor(target: Target, selection: Selection, transport: Transport) {
    this.target = copy(target)
    this.transport = transport
    this.anchor = selectionIdentity(selection)
    const row = selection.task || selection.stage!
    if (String(row.projectId) !== String(target.projectId) || String(row.taskId ?? row.stageId) !== String(target.nodeId)
      || (selection.task ? 'TASK' : 'STAGE') !== target.nodeKind) throw new Error('EXECUTION_CONTEXT_MISMATCH')
  }
  matches(selection: Selection | null | undefined): boolean {
    try { return this.live && selectionIdentity(selection) === this.anchor } catch { return false }
  }
  invalidate(): void { this.live = false }
  isBusy(): boolean { return this.inFlight > 0 }
  async execute(intent: Intent): Promise<Result> {
    if (!this.live) throw new Error('EXECUTION_CONTEXT_CHANGED_REOPEN_REQUIRED')
    if (!intent.operationCode || !intent.input || Array.isArray(intent.input)) throw new Error('OPERATION_INPUT_INVALID')
    if ('execution' in intent.input || 'tenantId' in intent.input || 'actorId' in intent.input) throw new Error('UNTRUSTED_EXECUTION_INPUT')
    const input = copy(intent.input)
    const fingerprint = stableJson([intent.operationCode, intent.objectId == null ? null : String(intent.objectId),
      typeof intent.expectedBusinessVersion === 'number' ? intent.expectedBusinessVersion : null, input])
    const index = intent.key ? `key:${intent.operationCode}:${intent.key}` : fingerprint
    let entry = this.pending.get(index)
    if (entry && entry.fingerprint !== fingerprint) throw new Error('OPERATION_KEY_CONFLICT')
    if (entry?.promise) return entry.promise
    if (!entry) { entry = { fingerprint, key: intent.key || this.transport.newKey() }; this.pending.set(index, entry) }
    const selected = entry
    this.inFlight++
    selected.promise = (async () => {
      if (!selected.command) {
        const expectedVersion = typeof intent.expectedBusinessVersion === 'function'
          ? await intent.expectedBusinessVersion() : intent.expectedBusinessVersion
        const capability = await this.transport.inspect({ ...this.target, objectId: intent.objectId == null ? undefined : String(intent.objectId) })
        if (!this.live || !this.matches(capability.execution)) throw new Error('EXECUTION_CONTEXT_CHANGED_REOPEN_REQUIRED')
        if (String(capability.node.projectId) !== String(this.target.projectId) || String(capability.node.id) !== String(this.target.nodeId)
          || capability.node.kind !== this.target.nodeKind) throw new Error('EXECUTION_CONTEXT_MISMATCH')
        const actions = capability.actions.filter(action => action.operationCode === intent.operationCode && action.operationVersion === 1)
        if (capability.reason || actions.length !== 1 || !actions[0].allowed) throw new Error(capability.reason || actions[0]?.reason || 'OPERATION_NOT_ALLOWED')
        if (intent.objectId != null && (!Number.isInteger(expectedVersion) || Number(expectedVersion) < 0 || !capability.ownerFactVersion)) throw new Error('BUSINESS_VERSION_REQUIRED')
        selected.command = { ...this.target, execution: copy(capability.execution!), objectId: intent.objectId == null ? undefined : String(intent.objectId),
          expectedBusinessVersion: expectedVersion, expectedObjectFactVersion: capability.ownerFactVersion, input }
      }
      if (!this.live) throw new Error('EXECUTION_CONTEXT_CHANGED_REOPEN_REQUIRED')
      // Once sent, an uncertain result is retried with this exact envelope and key, without a new capability check.
      const result = await this.transport.submit(intent.operationCode, copy(selected.command), selected.key)
      selected.result = copy(result)
      if (this.live) { try { this.transport.submitted?.(result) } catch { /* Metadata refresh cannot turn a committed command into a failure. */ } }
      return result
    })().finally(() => { selected.promise = undefined; this.inFlight-- })
    return selected.promise
  }
}
/** Enumerable symbols survive object spread, but JSON never transmits this local routing reference. */
const routing = Symbol('project-operation-client')
export function routeSelection<T extends Selection>(selection: T, client: OperationClient): T {
  return { ...selection, ...(selection.task ? { task: { ...selection.task, [routing]: client } } : { stage: { ...selection.stage, [routing]: client } }) } as T
}
export function selectionClient(selection: Selection | null | undefined): OperationClient | undefined {
  const read = (row: object | null | undefined) => row ? Reflect.get(row, routing) as OperationClient | undefined : undefined
  const task = read(selection?.task), stage = read(selection?.stage)
  if (task && stage) throw new Error('EXECUTION_CONTEXT_AMBIGUOUS')
  return task || stage
}
