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
  pendingChanged?(uncertain: boolean): void
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
/** A parsed, definite server refusal is different from a lost response. Unknown errors stay recoverable. */
export class OperationRejected extends Error {
  readonly code: number
  constructor(code: number, message: string) { super(message); this.name = 'OperationRejected'; this.code = code }
}
/** Append after the existing Axios JSON transforms; do not replace Snowflake-safe decoding. */
export function captureOperationResponse(value: unknown): unknown {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const data = value as { code?: unknown; msg?: unknown }
    if (typeof data.code === 'number' && Number.isInteger(data.code) && typeof data.msg === 'string'
      && ![0, 200, 401, 408, 429, 500, 501, 502, 503, 504, 901].includes(data.code)
      && !/IN_PROGRESS|in progress/i.test(data.msg)) throw new OperationRejected(data.code, data.msg)
  }
  return value
}
interface Pending {
  code: string; fingerprint: string; key: string; implicit: boolean; sent: boolean
  command?: Command; result?: Result; promise?: Promise<Result>
}
/** One editing session pins a round. Retiring it forbids new work, not recovery of an already sent command. */
export class OperationClient {
  private readonly anchor: string
  private live = true
  private inFlight = 0
  private readonly pending = new Map<string, Pending>()
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
  hasUncertain(): boolean { return [...this.pending.values()].some(entry => entry.sent && !entry.result) }
  async retryPending(): Promise<Result[]> {
    const results: Result[] = []
    for (const [index, entry] of [...this.pending]) {
      if (entry.sent && !entry.result) results.push(await this.run(index, entry))
    }
    return results
  }
  async execute(intent: Intent): Promise<Result> {
    if (!intent.operationCode || !intent.input || Array.isArray(intent.input)) throw new Error('OPERATION_INPUT_INVALID')
    if ('execution' in intent.input || 'tenantId' in intent.input || 'actorId' in intent.input) throw new Error('UNTRUSTED_EXECUTION_INPUT')
    const input = copy(intent.input)
    const fingerprint = stableJson([intent.operationCode, intent.objectId == null ? null : String(intent.objectId),
      typeof intent.expectedBusinessVersion === 'number' ? intent.expectedBusinessVersion : null, input])
    const index = intent.key ? `key:${intent.operationCode}:${intent.key}` : fingerprint
    let entry = this.pending.get(index)
    if (entry && entry.fingerprint !== fingerprint) throw new Error('OPERATION_KEY_CONFLICT')
    if (!entry && this.hasUncertain()) throw new Error('OPERATION_RESULT_UNCERTAIN_RETRY_REQUIRED')
    if (!this.live && !entry?.sent) throw new Error('EXECUTION_CONTEXT_CHANGED_REOPEN_REQUIRED')
    if (!entry) {
      entry = { code: intent.operationCode, fingerprint, key: intent.key || this.transport.newKey(), implicit: !intent.key, sent: false }
      this.pending.set(index, entry)
    }
    return this.run(index, entry, async () => {
      const expectedVersion = typeof intent.expectedBusinessVersion === 'function'
        ? await intent.expectedBusinessVersion() : intent.expectedBusinessVersion
      const capability = await this.transport.inspect({ ...this.target, objectId: intent.objectId == null ? undefined : String(intent.objectId) })
      if (!this.live || !this.matches(capability.execution)) throw new Error('EXECUTION_CONTEXT_CHANGED_REOPEN_REQUIRED')
      if (String(capability.node.projectId) !== String(this.target.projectId) || String(capability.node.id) !== String(this.target.nodeId)
        || capability.node.kind !== this.target.nodeKind) throw new Error('EXECUTION_CONTEXT_MISMATCH')
      const actions = capability.actions.filter(action => action.operationCode === intent.operationCode && action.operationVersion === 1)
      if (capability.reason || actions.length !== 1 || !actions[0].allowed) throw new Error(capability.reason || actions[0]?.reason || 'OPERATION_NOT_ALLOWED')
      if (intent.objectId != null && (!Number.isInteger(expectedVersion) || Number(expectedVersion) < 0 || !capability.ownerFactVersion)) throw new Error('BUSINESS_VERSION_REQUIRED')
      return { ...this.target, execution: copy(capability.execution!), objectId: intent.objectId == null ? undefined : String(intent.objectId),
        expectedBusinessVersion: expectedVersion, expectedObjectFactVersion: capability.ownerFactVersion, input }
    })
  }
  private run(index: string, entry: Pending, prepare?: () => Promise<Command>): Promise<Result> {
    if (entry.promise) return entry.promise
    const recovery = entry.sent
    this.inFlight++
    entry.promise = (async () => {
      if (!entry.command) {
        if (!this.live || !prepare) throw new Error('EXECUTION_CONTEXT_CHANGED_REOPEN_REQUIRED')
        entry.command = await prepare()
      }
      if (!this.live && !entry.sent) throw new Error('EXECUTION_CONTEXT_CHANGED_REOPEN_REQUIRED')
      // Retried requests never acquire a new key, context, payload or capability observation.
      entry.sent = true
      const result = await this.transport.submit(entry.code, copy(entry.command), entry.key)
      entry.result = copy(result)
      // A later identical click without an explicit key is a new business intent, not a permanent replay.
      if (entry.implicit) this.pending.delete(index)
      try { this.transport.submitted?.(result) } catch { /* Projection failure cannot undo a committed command. */ }
      return result
    })().catch(error => {
      // A rejection of a recovery request cannot disprove that the earlier attempt committed.
      if (!recovery && error instanceof OperationRejected) this.pending.delete(index)
      throw error
    }).finally(() => {
      entry.promise = undefined; this.inFlight--
      if (!entry.sent) this.pending.delete(index)
      try { this.transport.pendingChanged?.(this.hasUncertain()) } catch { /* Metadata only. */ }
    })
    return entry.promise
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
