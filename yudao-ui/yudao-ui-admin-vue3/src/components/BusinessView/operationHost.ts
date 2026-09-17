import { computed, inject, markRaw, onBeforeUnmount, shallowRef, watch, type InjectionKey, type ShallowRef } from 'vue'
import { inspectOperationCapabilities, type OperationCapabilities, type CapabilityQuery } from '@/api/pms/project/execution-operations'
import request from '@/config/axios'
import { OperationClient, routeSelection, type Result, type Selection } from './operationClient'
import type { BusinessViewTarget } from './registry'

export const operationClientKey: InjectionKey<ShallowRef<OperationClient | undefined>> = Symbol('operation-client')
export const useOperationClient = () => inject(operationClientKey, shallowRef<OperationClient>())
const aliases: Record<string, string[]> = {
  'SOL.SITE_SURVEY.CREATE': ['CREATE'], 'SOL.SITE_SURVEY.UPDATE': ['UPDATE'],
  'SOL.SITE_SURVEY.DELETE': ['DELETE'], 'SOL.SITE_SURVEY.CONFIRM': ['CONFIRM'],
  'SOL.SITE_SURVEY.REJECT': ['REJECT'], 'SOL.SITE_SURVEY.ARCHIVE': ['ARCHIVE'],
  'SOL.REQUIREMENT_ANALYSIS.CREATE': ['CREATE', 'CREATE_INITIAL_DRAFT'],
  'SOL.REQUIREMENT_ANALYSIS.SAVE': ['PATCH_FORM'],
  'SOL.REQUIREMENT_ANALYSIS.COMPLETE': ['COMPLETE'],
  'SOL.REQUIREMENT_ANALYSIS.COPY': ['CREATE_DRAFT'],
  'ACC.ACCEPTANCE_REPORT.CREATE_DRAFT': ['UPDATE'],
  'ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT': ['UPDATE'],
  'ACC.ACCEPTANCE_REPORT.PUBLISH': ['PUBLISH'], 'ACC.ACCEPTANCE_REPORT.REVOKE': ['REVOKE']
}
export function operationAliases(code: string): string[] { return aliases[code] || [] }
export function editingTargetKey(target: BusinessViewTarget): string {
  const view = target.registration, context = target.resolvedContext
  // Execution versions and registry enable/disable updates do not change an Owner editing buffer.
  return JSON.stringify([view.id, view.componentKey, view.componentVersion, view.ownerContext, view.entityType,
    view.viewSource, view.dynamicFormRevisionId, context.project?.id, context.instanceId, context.businessObjectId, context.taskId, context.stageExecution?.stageId])
}
export function useOperationHost(active: ShallowRef<BusinessViewTarget>, changed: () => void) {
  const observation = shallowRef<OperationCapabilities>()
  const client = shallowRef<OperationClient>()
  const receipt = shallowRef<Result>()
  const failure = shallowRef('')
  const checking = shallowRef(false)
  const requiresReopen = shallowRef(false)
  const mode = shallowRef<'INDEPENDENT' | 'CHECKING' | 'LEGACY' | 'CONTROLLED'>('CHECKING')
  let generation = 0, disposed = false, timer: ReturnType<typeof setTimeout> | undefined, poll = 0
  const query = (): CapabilityQuery | undefined => {
    const context = active.value.resolvedContext
    const projectId = context.project?.id
    const nodeId = context.taskExecution?.taskId ?? context.taskId ?? context.stageExecution?.stageId
    if (projectId == null || nodeId == null) return undefined
    return { projectId, nodeKind: context.taskId != null || context.taskExecution ? 'TASK' : 'STAGE', nodeId,
      objectId: context.businessObjectId }
  }
  const submitted = (result: Result) => {
    receipt.value = result
    poll = 0
    if (timer) clearTimeout(timer)
    // Let the Owner finish updating its saved buffer before refreshing metadata.
    timer = setTimeout(() => { if (!disposed) void refresh(true) }, 0)
  }
  const refresh = async (follow = false) => {
    const target = query(), sequence = ++generation
    if (!target) {
      client.value?.invalidate(); client.value = undefined; observation.value = undefined
      mode.value = 'INDEPENDENT'; checking.value = false; return
    }
    checking.value = true
    try {
      const value = await inspectOperationCapabilities(target)
      if (disposed || sequence !== generation) return
      if (String(value.node.projectId) !== String(target.projectId) || String(value.node.id) !== String(target.nodeId)
        || value.node.kind !== target.nodeKind) throw new Error('EXECUTION_CONTEXT_MISMATCH')
      const previous = observation.value
      observation.value = value
      failure.value = ''
      const nextMode = value.reason === 'LEGACY_BINDING' ? 'LEGACY' : 'CONTROLLED'
      if (mode.value !== 'CHECKING' && mode.value !== nextMode) {
        requiresReopen.value = true
        client.value?.invalidate()
        throw new Error('EXECUTION_CONTRACT_CHANGED_REOPEN_REQUIRED')
      }
      if (requiresReopen.value) throw new Error('EXECUTION_CONTRACT_CHANGED_REOPEN_REQUIRED')
      if (nextMode === 'LEGACY') {
        client.value?.invalidate(); client.value = undefined; mode.value = 'LEGACY'
      } else {
        mode.value = 'CONTROLLED'
        if (!value.execution) { client.value?.invalidate(); client.value = undefined }
        else if (!client.value?.matches(value.execution)) {
          client.value?.invalidate()
          client.value = markRaw(new OperationClient(target, value.execution, {
            inspect: input => inspectOperationCapabilities(input),
            submit: (code, command, key) => request.post<Result>({ url: `/api/v1/pms/project-execution/operations/${encodeURIComponent(code)}`,
              data: command, headers: { 'Idempotency-Key': key } }),
            newKey: () => crypto.randomUUID(), submitted
          }))
        }
      }
      if (previous && previous.node.status !== value.node.status) changed()
      if (follow && ++poll < 6 && !['DONE', 'CLOSED', 'COMPLETED'].includes(value.node.status)) {
        timer = setTimeout(() => void refresh(true), Math.min(1000 * 2 ** poll, 8000))
      }
    } catch (error) {
      if (!disposed && sequence === generation) {
        failure.value = error instanceof Error ? error.message : 'EXECUTION_CAPABILITY_UNAVAILABLE'
        // Never fall back to an unguarded API after a new-contract request fails.
      }
    } finally { if (!disposed && sequence === generation) checking.value = false }
  }
  const decorated = computed(() => {
    const context = active.value.resolvedContext
    if (mode.value !== 'CONTROLLED' || !client.value || !observation.value?.execution) return context
    const selected = routeSelection(observation.value.execution as Selection, client.value)
    return { ...context, ...(selected.task ? { taskExecution: selected.task } : { stageExecution: selected.stage }) } as typeof context
  })
  const allowedActions = computed(() => {
    const target = active.value
    if (requiresReopen.value) return target.allowedActions.includes('QUERY') ? ['QUERY'] : []
    if (mode.value === 'INDEPENDENT' || mode.value === 'LEGACY') return target.allowedActions
    const readable = target.allowedActions.includes('QUERY') ? ['QUERY'] : []
    if (checking.value || failure.value || observation.value?.reason || !client.value) return readable
    const selectedObject = target.resolvedContext.businessObjectId != null
    const ownerAliases = new Set(target.allowedActions)
    const result = new Set(readable)
    for (const action of observation.value?.actions || []) {
      const candidates = operationAliases(action.operationCode)
      // A list has no selected object: preserve only Owner-provided list actions. The command inspects its actual row.
      const permitted = selectedObject ? action.allowed : action.executionPermitted && action.runtimeAvailable
        && ['MATCHED', 'NO_ADDITIONAL_RULE'].includes(action.pre.outcome)
        && (action.ownerPermitted || candidates.some(alias => ownerAliases.has(alias)) || ownerAliases.has('MANAGE'))
      if (permitted) candidates.forEach(alias => result.add(alias))
    }
    // File permissions are not implied by report publication or a business command.
    if (ownerAliases.has('FILE_WRITE')) result.add('FILE_WRITE')
    return [...result]
  })
  const reopen = async () => {
    client.value?.invalidate(); client.value = undefined
    requiresReopen.value = false; mode.value = 'CHECKING'; failure.value = ''
    receipt.value = undefined
    await refresh()
  }
  let previousTarget = ''
  watch(() => {
    const target = query()
    return JSON.stringify([target, active.value.resolvedContext.taskExecution, active.value.resolvedContext.stageExecution])
  }, () => {
    const target = query()
    const identity = JSON.stringify(target && [target.projectId, target.nodeKind, target.nodeId])
    if (identity !== previousTarget) {
      client.value?.invalidate(); client.value = undefined
      mode.value = 'CHECKING'; requiresReopen.value = false; receipt.value = undefined
      previousTarget = identity
    }
    poll = 0; if (timer) clearTimeout(timer); void refresh()
  }, { immediate: true })
  onBeforeUnmount(() => { disposed = true; generation++; client.value?.invalidate(); if (timer) clearTimeout(timer) })
  return { observation, client, receipt, failure, checking, mode, decorated, allowedActions, requiresReopen, refresh, reopen }
}
