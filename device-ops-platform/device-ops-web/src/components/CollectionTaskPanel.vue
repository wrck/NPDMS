<script setup lang="ts">
import { formatLocalTime, statusLabel } from '@/management/presentation'
import axios from 'axios'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, toRaw, watch } from 'vue'
import { useCollectionEvidence } from '@/composables/use-collection-evidence'
import type { InputContentStatus } from '@/types/collection-evidence'

import {
  getCollection,
  getCollectionSemanticResults,
  getGenericCollection,
  submitCollection,
  submitGenericCollection
} from '@/api/device-ops'
import { streamCollectionOutput } from '@/api/collection-output-stream'
import {
  redactRequestSnapshot,
  scrubRequestCredentials
} from '@/security/transient-credentials'
import PanelHeader from '@/components/PanelHeader.vue'
import SemanticResultPanel from '@/components/SemanticResultPanel.vue'
import type {
  CollectionDetails,
  CollectionOutputEvent,
  CollectionStatus,
  CollectionSubmission
} from '@/types/collection'
import type { CollectionSemanticResult } from '@/types/parser'
import {
  downloadInputRecord,
  downloadSessionRecord
} from '@/utils/session-export'
import { blocksForTarget } from '@/utils/command-output-blocks'
import { projectRequestSnapshot } from '@/utils/request-snapshot'

const props = withDefaults(defineProps<{
  readonly?: boolean
  layout?: 'standalone' | 'workbench'
}>(), { readonly: false, layout: 'standalone' })
const emit = defineEmits<{ submit: [] }>()

interface TargetStreamState {
  stdout: string
  stderr: string
  stdoutReceivedBytes: number
  stderrReceivedBytes: number
  pageCount: number
  truncated: boolean
  renderLimited: boolean
  stdoutEventChars: number
  stderrEventChars: number
}

const busy = ref(false)
const polling = ref(false)
const queryCompleted = ref(false)
const errorMessage = ref('')
const collectionId = ref('')
const details = ref<CollectionDetails>()
const semanticResults = ref<CollectionSemanticResult[]>([])
const frozenRequest = ref<Record<string, unknown>>()
const scriptInput = ref('')
const localInputStatus = ref<InputContentStatus>('UNAVAILABLE')
const localSubmission = ref(false)
const { evidence, status: evidenceStatus, load: loadEvidence, reset: resetEvidence } = useCollectionEvidence()
const inputStatus = computed<InputContentStatus>(() => evidenceStatus.value === 'RESTRICTED' ? 'RESTRICTED' : evidence.value?.input.contentStatus ?? (collectionId.value ? 'UNAVAILABLE' : localInputStatus.value))
const availableInput = computed(() => evidence.value ? evidence.value.input.content ?? '' : scriptInput.value)
const projectedRequest = computed(() => evidenceStatus.value === 'READY' && evidence.value ? projectRequestSnapshot(evidence.value) : undefined)
const requestSnapshot = computed(() => projectedRequest.value?.snapshot ?? (!collectionId.value && localSubmission.value ? frozenRequest.value : undefined))
const requestOmittedFields = computed(() => projectedRequest.value?.omittedFields ?? [])
const requestProvenance = computed(() => evidence.value?.submission.provenance === 'CAPTURED_SUBMISSION' ? '原提交快照（已脱敏）' : evidence.value ? '历史执行事实（非原提交请求）' : localSubmission.value ? '本次提交尝试（本地脱敏，非已受理任务原请求）' : '原提交证据尚未载入')
function retryEvidence() {
  if (!queryContext.value || !collectionId.value || !frozenRequest.value) return
  void loadEvidence({ ...queryContext.value, collectionId: collectionId.value, namespace: String(frozenRequest.value.namespace) })
}
const deviceLabel = ref('device')
const queryContext = ref<{ mode: 'project' | 'generic'; projectKey?: string }>()
const activeOutputTab = ref('stdout')
const streamState = ref<
  'IDLE' | 'CONNECTING' | 'LIVE' | 'RECONNECTING' | 'CLOSED'
>('IDLE')
const lastSequence = ref(0)
const targetStreams = ref(new Map<number, TargetStreamState>())
const isFollowing = ref(true)
const stdoutTerminal = ref<HTMLElement>()
const stderrTerminal = ref<HTMLElement>()
let pollTimer: number | undefined
let pollController: AbortController | undefined
let submissionController: AbortController | undefined
let streamController: AbortController | undefined
let reconnectTimer: number | undefined
let reconnectAttempt = 0
let streamGeneration = 0
let streamCompleted = false
let restoringFromLocation = false
let pollingGeneration = 0

const POLL_INTERVAL_MILLIS = 1_500
const RECONNECT_DELAYS_MILLIS = [500, 1_000, 2_000, 5_000, 10_000]
const MAX_RENDERED_OUTPUT_CHARS = 8 * 1024 * 1024
const ACTIVE_TASK_QUERY_KEYS = ['collectionId', 'namespace', 'mode', 'projectKey']

const terminalStatuses = new Set<CollectionStatus>([
  'SUCCEEDED',
  'PARTIAL_SUCCESS',
  'FAILED',
  'TIMED_OUT',
  'CANCELLED'
])
const stages = ['快照', '连接', '执行', '解析', '完成']
const activeStage = computed(() => {
  const status = details.value?.status ?? 'QUEUED'
  const stageByStatus: Record<CollectionStatus, number> = {
    QUEUED: 0,
    CONNECTING: 1,
    EXECUTING: 2,
    PARSING: 3,
    SUCCEEDED: 4,
    PARTIAL_SUCCESS: 4,
    FAILED: 4,
    TIMED_OUT: 4,
    CANCELLED: 4
  }
  return stageByStatus[status]
})
function blockTone(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'RUNNING' || status === 'PENDING') return 'warning'
  if (status === 'FAILED' || status === 'TIMED_OUT') return 'danger'
  return 'info'
}
const latestParsedFacts = computed<Record<string, string>>(
  () => details.value?.targets[0]?.parsedFacts ?? {}
)
const latestCollectionDetails = computed(() => details.value)
const receivedBytes = computed(() =>
  Array.from(targetStreams.value.values()).reduce(
    (total, target) =>
      total + target.stdoutReceivedBytes + target.stderrReceivedBytes,
    0
  )
)
const pageCount = computed(() =>
  Array.from(targetStreams.value.values()).reduce(
    (maximum, target) => Math.max(maximum, target.pageCount),
    0
  )
)
const outputTruncated = computed(() =>
  Array.from(targetStreams.value.values()).some((target) => target.truncated)
)
const clientOutputLimited = computed(() =>
  Array.from(targetStreams.value.values()).some((target) => target.renderLimited)
)
const streamedTargetEntries = computed(() =>
  Array.from(targetStreams.value.entries()).sort(([left], [right]) => left - right)
)

interface StreamedTargetIdentity {
  label: string
  endpoint?: string
}

function recordValue(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined
}

function nonBlankText(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function streamedTargetIdentity(targetId: number): StreamedTargetIdentity {
  const request = frozenRequest.value
  const projectTarget = Array.isArray(request?.targets)
    ? recordValue(request.targets[targetId - 1])
    : undefined
  const connection = projectTarget ?? recordValue(request?.connection)
  const device =
    recordValue(projectTarget?.device) ??
    recordValue(recordValue(request?.context)?.device)
  const endpoint = nonBlankText(connection?.host)
  const submittedLabel = nonBlankText(deviceLabel.value)
  const label =
    nonBlankText(device?.deviceName) ??
    nonBlankText(device?.deviceKey) ??
    (submittedLabel !== 'device' && submittedLabel !== 'restored-collection'
      ? submittedLabel
      : undefined) ??
    endpoint ??
    '正在加载设备信息'
  return { label, endpoint }
}

const streamStatusText = computed(() => {
  if (streamState.value === 'LIVE') return '实时连接中'
  if (streamState.value === 'RECONNECTING') return '正在重连'
  if (streamState.value === 'CONNECTING') return '正在连接实时输出'
  if (
    streamState.value === 'CLOSED' &&
    details.value &&
    terminalStatuses.has(details.value.status)
  ) {
    return '实时采集完成'
  }
  if (streamState.value === 'CLOSED') return '实时连接已关闭'
  return '等待实时输出'
})
const formattedReceivedBytes = computed(() => formatBytes(receivedBytes.value))
const showReturnToBottom = computed(
  () =>
    (activeOutputTab.value === 'stdout' || activeOutputTab.value === 'stderr') &&
    !isFollowing.value
)
const taskState = computed(() => {
  if (busy.value && !details.value) return '正在创建任务并获取状态'
  if (!frozenRequest.value) return '尚未下发采集任务'
  if (!collectionId.value) return '提交结果尚未确认'
  if (details.value?.status === 'PARTIAL_SUCCESS') return '部分成功，请查看各目标输出与解析结果'
  if (details.value?.status === 'SUCCEEDED') return '采集成功'
  if (details.value?.status === 'FAILED') return '采集失败'
  if (details.value?.status === 'TIMED_OUT') return '采集超时'
  if (details.value?.status === 'CANCELLED') return '采集已取消'
  if (details.value) return statusLabel(details.value.status)
  return '任务已下发，正在等待首个状态'
})

function resetStreamOutput() {
  lastSequence.value = 0
  targetStreams.value = new Map()
  isFollowing.value = true
  streamState.value = 'IDLE'
}

function emptyTargetStream(): TargetStreamState {
  return {
    stdout: '',
    stderr: '',
    stdoutReceivedBytes: 0,
    stderrReceivedBytes: 0,
    pageCount: 0,
    truncated: false,
    renderLimited: false,
    stdoutEventChars: 0,
    stderrEventChars: 0
  }
}

function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`
  const units = ['KiB', 'MiB', 'GiB']
  let size = value
  let unit = -1
  do {
    size /= 1024
    unit += 1
  } while (size >= 1024 && unit < units.length - 1)
  return `${size >= 10 ? size.toFixed(1) : size.toFixed(2)} ${units[unit]}`
}

function activeTerminalElement(): HTMLElement | undefined {
  if (activeOutputTab.value === 'stdout') return stdoutTerminal.value
  if (activeOutputTab.value === 'stderr') return stderrTerminal.value
  return undefined
}

function isNearBottom(element: HTMLElement | undefined): boolean {
  if (!element) return true
  return element.scrollHeight - element.scrollTop - element.clientHeight <= 32
}

function handleTerminalScroll(tab: 'stdout' | 'stderr') {
  if (activeOutputTab.value !== tab) return
  isFollowing.value = isNearBottom(activeTerminalElement())
}

function scrollActiveToBottom() {
  const element = activeTerminalElement()
  if (!element) return
  element.scrollTop = element.scrollHeight
  isFollowing.value = true
}

function followAfterOutputChange(shouldFollow: boolean) {
  void nextTick(() => {
    if (shouldFollow) scrollActiveToBottom()
    else isFollowing.value = isNearBottom(activeTerminalElement())
  })
}

function boundedOutput(value: string): { content: string; limited: boolean } {
  return value.length <= MAX_RENDERED_OUTPUT_CHARS
    ? { content: value, limited: false }
    : { content: value.slice(0, MAX_RENDERED_OUTPUT_CHARS), limited: true }
}

function mergeReplayedChunk(
  current: string,
  content: string,
  eventStart: number
): { content: string; limited: boolean } {
  const overlapLength = Math.min(
    content.length,
    Math.max(0, current.length - eventStart)
  )
  if (
    overlapLength > 0 &&
    current.slice(eventStart, eventStart + overlapLength) !==
      content.slice(0, overlapLength)
  ) {
    // 轮询快照比事件流先到且内容无法安全对齐时，保留持久化快照，等待后续轮询校准。
    return { content: current, limited: false }
  }
  return boundedOutput(current + content.slice(overlapLength))
}

function appendOutputEvent(event: CollectionOutputEvent) {
  if (event.sequence <= lastSequence.value) return
  const outputTab = event.stream === 'STDOUT' ? 'stdout' : 'stderr'
  const shouldFollow =
    activeOutputTab.value === outputTab && isNearBottom(activeTerminalElement())
  const current = targetStreams.value.get(event.targetId) ?? emptyTargetStream()
  const next: TargetStreamState = { ...current }
  if (event.stream === 'STDOUT') {
    const merged = mergeReplayedChunk(
      current.stdout,
      event.content,
      current.stdoutEventChars
    )
    next.stdout = merged.content
    next.stdoutEventChars += event.content.length
    next.stdoutReceivedBytes = Math.max(
      current.stdoutReceivedBytes,
      event.receivedBytes
    )
    next.renderLimited ||= merged.limited
  } else {
    const merged = mergeReplayedChunk(
      current.stderr,
      event.content,
      current.stderrEventChars
    )
    next.stderr = merged.content
    next.stderrEventChars += event.content.length
    next.stderrReceivedBytes = Math.max(
      current.stderrReceivedBytes,
      event.receivedBytes
    )
    next.renderLimited ||= merged.limited
  }
  next.pageCount = Math.max(current.pageCount, event.pageCount)
  next.truncated ||= event.truncated
  const updated = new Map(targetStreams.value)
  updated.set(event.targetId, next)
  targetStreams.value = updated
  lastSequence.value = event.sequence
  reconnectAttempt = 0
  if (activeOutputTab.value === outputTab) followAfterOutputChange(shouldFollow)
}

function targetOutputTruncated(targetId: number, serverTruncated: boolean): boolean {
  const streamed = targetStreams.value.get(targetId)
  return serverTruncated || Boolean(streamed?.truncated || streamed?.renderLimited)
}

function reconcileDetails(response: CollectionDetails, authoritative: boolean) {
  const activeElement = activeTerminalElement()
  const shouldFollow = isNearBottom(activeElement)
  let outputChanged = false
  const reconciled = authoritative
    ? new Map<number, TargetStreamState>()
    : new Map(targetStreams.value)
  for (const target of response.targets) {
    const current = targetStreams.value.get(target.targetId) ?? emptyTargetStream()
    const next: TargetStreamState = { ...current }
    if (authoritative) next.renderLimited = false
    if (authoritative || target.stdout.length > current.stdout.length) {
      const bounded = boundedOutput(target.stdout)
      outputChanged ||= bounded.content !== current.stdout
      next.stdout = bounded.content
      next.renderLimited ||= bounded.limited
    }
    if (authoritative || target.stderr.length > current.stderr.length) {
      const bounded = boundedOutput(target.stderr)
      outputChanged ||= bounded.content !== current.stderr
      next.stderr = bounded.content
      next.renderLimited ||= bounded.limited
    }
    next.truncated = authoritative ? target.truncated : current.truncated || target.truncated
    next.stdoutReceivedBytes = Math.max(
      current.stdoutReceivedBytes,
      new TextEncoder().encode(target.stdout).byteLength
    )
    next.stderrReceivedBytes = Math.max(
      current.stderrReceivedBytes,
      new TextEncoder().encode(target.stderr).byteLength
    )
    reconciled.set(target.targetId, next)
  }
  targetStreams.value = reconciled
  details.value = structuredClone(response)
  if (outputChanged) followAfterOutputChange(shouldFollow)
}

function clearActiveTaskLocation() {
  const url = new URL(window.location.href)
  ACTIVE_TASK_QUERY_KEYS.forEach((key) => url.searchParams.delete(key))
  window.history.replaceState(
    window.history.state,
    '',
    `${url.pathname}${url.search}${url.hash}`
  )
}

function saveActiveTaskLocation() {
  if (!collectionId.value || !frozenRequest.value || !queryContext.value) return
  const namespace = String(frozenRequest.value.namespace ?? '').trim()
  if (!namespace) return
  const url = new URL(window.location.href)
  ACTIVE_TASK_QUERY_KEYS.forEach((key) => url.searchParams.delete(key))
  url.searchParams.set('collectionId', collectionId.value)
  url.searchParams.set('namespace', namespace)
  url.searchParams.set('mode', queryContext.value.mode)
  if (queryContext.value.projectKey) {
    url.searchParams.set('projectKey', queryContext.value.projectKey)
  }
  window.history.replaceState(
    window.history.state,
    '',
    `${url.pathname}${url.search}${url.hash}`
  )
}

function stopStreaming(nextState: 'IDLE' | 'CLOSED' = 'CLOSED') {
  streamGeneration += 1
  if (reconnectTimer !== undefined) window.clearTimeout(reconnectTimer)
  reconnectTimer = undefined
  streamController?.abort()
  streamController = undefined
  streamCompleted = nextState === 'CLOSED'
  streamState.value = nextState
}

function scheduleReconnect(generation: number) {
  if (
    generation !== streamGeneration ||
    streamCompleted ||
    !polling.value ||
    terminalStatuses.has(details.value?.status ?? 'QUEUED')
  ) {
    return
  }
  streamState.value = 'RECONNECTING'
  const delay =
    RECONNECT_DELAYS_MILLIS[
      Math.min(reconnectAttempt, RECONNECT_DELAYS_MILLIS.length - 1)
    ]
  reconnectAttempt += 1
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = undefined
    void connectOutputStream(generation, true)
  }, delay)
}

async function connectOutputStream(generation: number, reconnecting: boolean) {
  if (
    generation !== streamGeneration ||
    !collectionId.value ||
    !frozenRequest.value ||
    !queryContext.value
  ) {
    return
  }
  const namespace = String(frozenRequest.value.namespace ?? '')
  const controller = new AbortController()
  streamController = controller
  try {
    await streamCollectionOutput(
      queryContext.value,
      namespace,
      collectionId.value,
      lastSequence.value,
      controller.signal,
      {
        onOutput: (event) => {
          if (generation === streamGeneration) appendOutputEvent(event)
        },
        onComplete: () => {
          if (generation !== streamGeneration) return
          streamCompleted = true
          streamState.value = 'CLOSED'
        },
        onState: (state) => {
          if (generation !== streamGeneration) return
          streamState.value =
            state === 'CONNECTING' && reconnecting ? 'RECONNECTING' : state
        },
        onError: () => {
          // 轮询继续作为事实兜底；连接退出后由统一重连策略更新状态。
        }
      }
    )
    if (
      generation === streamGeneration &&
      !controller.signal.aborted &&
      !streamCompleted
    ) {
      scheduleReconnect(generation)
    }
  } catch {
    if (generation === streamGeneration && !controller.signal.aborted) {
      scheduleReconnect(generation)
    }
  } finally {
    if (streamController === controller) streamController = undefined
  }
}

function startStreaming() {
  stopStreaming('IDLE')
  reconnectAttempt = 0
  streamCompleted = false
  const generation = streamGeneration
  void connectOutputStream(generation, false)
}

function fetchDetails(signal: AbortSignal): Promise<CollectionDetails> {
  if (!collectionId.value || !frozenRequest.value || !queryContext.value) {
    return Promise.reject(new Error('任务定位信息不完整'))
  }
  const namespace = String(frozenRequest.value.namespace)
  return queryContext.value.mode === 'project' && queryContext.value.projectKey
    ? getCollection(
        queryContext.value.projectKey,
        namespace,
        collectionId.value,
        signal
      )
    : getGenericCollection(namespace, collectionId.value, signal)
}

function fetchSemanticResults(signal: AbortSignal): Promise<CollectionSemanticResult[]> {
  if (!collectionId.value || !frozenRequest.value) {
    return Promise.reject(new Error('解析任务定位信息不完整'))
  }
  return getCollectionSemanticResults(
    String(frozenRequest.value.namespace),
    collectionId.value,
    signal
  )
}

async function start(submission: CollectionSubmission): Promise<boolean> {
  if (props.readonly || busy.value) return false
  const originalRequest = submission.request
  submission = structuredClone(toRaw(submission))
  stopPolling()
  resetEvidence()
  localSubmission.value = true
  localInputStatus.value = 'AVAILABLE'
  queryCompleted.value = false
  clearActiveTaskLocation()
  resetStreamOutput()
  busy.value = true
  errorMessage.value = ''
  details.value = undefined
  semanticResults.value = []
  collectionId.value = ''
  frozenRequest.value = redactRequestSnapshot(submission.request)
  scriptInput.value = submission.request.script.content
  deviceLabel.value = submission.deviceLabel
  queryContext.value =
    submission.mode === 'project'
      ? { mode: 'project', projectKey: submission.projectKey }
      : { mode: 'generic' }
  let accepted = false
  const controller = new AbortController()
  const generation = pollingGeneration
  submissionController = controller
  try {
    const result =
      submission.mode === 'project'
        ? await submitCollection(
            submission.projectKey,
            submission.request,
            controller.signal
          )
        : await submitGenericCollection(submission.request, controller.signal)
    if (controller.signal.aborted || generation !== pollingGeneration) return false
    collectionId.value = result.collectionId
    accepted = true
    retryEvidence()
    saveActiveTaskLocation()
    polling.value = true
    startStreaming()
    await poll()
  } catch {
    if (!controller.signal.aborted && generation === pollingGeneration) errorMessage.value = '提交结果未确认，请先在任务中心核对，避免重复执行。'
  } finally {
    scrubRequestCredentials(submission.request)
    scrubRequestCredentials(originalRequest)
    if (submissionController === controller) {
      submissionController = undefined
      busy.value = false
    }
  }
  return accepted
}

async function poll() {
  if (!collectionId.value || !frozenRequest.value || !queryContext.value) return
  const generation = pollingGeneration
  polling.value = true
  const controller = new AbortController()
  pollController = controller
  try {
    const response = await fetchDetails(controller.signal)
    if (controller.signal.aborted || generation !== pollingGeneration) return
    restoringFromLocation = false
    reconcileDetails(response, false)
    if (terminalStatuses.has(response.status)) {
      let finalResponse = response
      try {
        finalResponse = await fetchDetails(controller.signal)
      } catch {
        if (controller.signal.aborted) return
      }
      if (controller.signal.aborted || generation !== pollingGeneration) return
      reconcileDetails(finalResponse, true)
      stopStreaming()
      const parserResults = await fetchSemanticResults(controller.signal)
      if (controller.signal.aborted || generation !== pollingGeneration) return
      semanticResults.value = structuredClone(parserResults)
      const parserStillRunning = parserResults.some(
        (item) => !['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(item.state)
      )
      if (parserStillRunning && polling.value && generation === pollingGeneration) {
        pollTimer = window.setTimeout(poll, POLL_INTERVAL_MILLIS)
        return
      }
      queryCompleted.value = true
      polling.value = false
      return
    }
    if (polling.value && generation === pollingGeneration) {
      pollTimer = window.setTimeout(poll, POLL_INTERVAL_MILLIS)
    }
  } catch (error) {
    if (!controller.signal.aborted) {
      if (
        restoringFromLocation &&
        axios.isAxiosError(error) &&
        (error.response?.status === 400 || error.response?.status === 404)
      ) {
        clearActiveTaskLocation()
        restoringFromLocation = false
        stopPolling()
        collectionId.value = ''
        frozenRequest.value = undefined
        details.value = undefined
        semanticResults.value = []
        resetStreamOutput()
        errorMessage.value = 'URL 中的采集任务不存在或定位信息无效，已停止恢复。'
        return
      }
      errorMessage.value = '状态查询暂时失败。可以继续查看，不会重复提交任务。'
      if (polling.value && generation === pollingGeneration) {
        pollTimer = window.setTimeout(poll, POLL_INTERVAL_MILLIS)
      }
    }
  } finally {
    if (pollController === controller) pollController = undefined
  }
}

function stopPolling() {
  pollingGeneration += 1
  if (pollTimer !== undefined) window.clearTimeout(pollTimer)
  pollTimer = undefined
  pollController?.abort()
  pollController = undefined
  submissionController?.abort()
  submissionController = undefined
  busy.value = false
  stopStreaming()
  polling.value = false
}

function resumePolling() {
  if (
    !polling.value &&
    collectionId.value &&
    !queryCompleted.value
  ) {
    errorMessage.value = ''
    polling.value = true
    if (!terminalStatuses.has(details.value?.status ?? 'QUEUED')) startStreaming()
    void poll()
  }
}

function restoreActiveTask() {
  const url = new URL(window.location.href)
  const hasTaskQuery = ACTIVE_TASK_QUERY_KEYS.some((key) => url.searchParams.has(key))
  if (!hasTaskQuery) return
  const restoredCollectionId = url.searchParams.get('collectionId')?.trim() ?? ''
  const namespace = url.searchParams.get('namespace')?.trim() ?? ''
  const mode = url.searchParams.get('mode')
  const projectKey = url.searchParams.get('projectKey')?.trim() || undefined
  const valid =
    Boolean(restoredCollectionId) &&
    Boolean(namespace) &&
    (mode === 'project' || mode === 'generic') &&
    (mode !== 'project' || Boolean(projectKey))
  if (!valid) {
    clearActiveTaskLocation()
    return
  }

  collectionId.value = restoredCollectionId
  semanticResults.value = []
  resetEvidence()
  localSubmission.value = false
  localInputStatus.value = 'UNAVAILABLE'
  frozenRequest.value = { namespace }
  deviceLabel.value = 'restored-collection'
  queryContext.value = {
    mode,
    ...(projectKey ? { projectKey } : {})
  }
  scriptInput.value = ''
  queryCompleted.value = false
  restoringFromLocation = true
  retryEvidence()
  polling.value = true
  resetStreamOutput()
  startStreaming()
  void poll()
}

function saveInput() {
  if (inputStatus.value === 'AVAILABLE') downloadInputRecord(deviceLabel.value, availableInput.value)
}

function saveSession() {
  if (details.value) {
    downloadSessionRecord(
      deviceLabel.value,
      availableInput.value,
      details.value,
      '', // The API does not attest the winner's original submission time.
      inputStatus.value,
      evidence.value?.metadata.createdAt
    )
  }
}

defineExpose({
  start,
  stopPolling,
  latestParsedFacts,
  semanticResults,
  latestCollectionDetails
})
watch(activeOutputTab, () => {
  void nextTick(() => {
    isFollowing.value = isNearBottom(activeTerminalElement())
  })
})
onMounted(restoreActiveTask)
onBeforeUnmount(() => { stopPolling(); resetEvidence() })
</script>

<template>
  <el-card class="task-panel" :class="`task-panel--${props.layout}`" shadow="never" aria-labelledby="task-panel-title" aria-live="polite">
    <template #header>
    <PanelHeader
      id="task-panel-title"
      title="任务输出"
      icon="⌁"
      :summary="collectionId ? `采集 ID / ${collectionId}` : undefined"
    />
    </template>

    <div class="task-panel__dispatch">
      <div class="task-panel__state">
        <span class="task-panel__state-label">当前状态</span>
        <strong :title="details?.status">{{ taskState }}</strong>
      </div>
      <div class="task-panel__actions">
        <el-button v-if="!props.readonly" type="primary" size="large" :loading="busy" @click="emit('submit')">
          {{ busy ? '正在连接' : '连接并执行采集' }}
        </el-button>
        <el-button v-if="polling" plain @click="stopPolling">停止查看</el-button>
        <el-button
          v-else-if="collectionId && !queryCompleted"
          plain
          @click="resumePolling"
        >
          继续查看
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="errorMessage"
      class="inline-error"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    />

    <ol v-if="frozenRequest" class="status-rail" aria-label="采集状态轨迹">
      <li
        v-for="(stage, index) in stages"
        :key="stage"
        :class="{ complete: index < activeStage, active: index === activeStage }"
        :aria-current="index === activeStage ? 'step' : undefined"
      >
        <span>{{ stage }}</span>
      </li>
    </ol>

    <div class="task-panel__downloads">
      <div v-if="frozenRequest" class="task-panel__telemetry" role="status" aria-live="polite">
        <span
          class="task-panel__stream-state"
          :class="`task-panel__stream-state--${streamState.toLowerCase()}`"
        >
          <i aria-hidden="true"></i>
          {{ streamStatusText }}
        </span>
        <span>累计已接收 {{ formattedReceivedBytes }}</span>
        <span>自动续页最多 {{ pageCount }} 次</span>
        <span
          v-if="outputTruncated || clientOutputLimited"
          class="task-panel__truncate-state"
        >
          输出已截断
        </span>
      </div>
      <span class="task-panel__downloads-label">证据下载</span>
      <span v-if="frozenRequest && inputStatus === 'UNAVAILABLE'">原始脚本不可用；历史提交时间未知。</span>
      <span v-if="inputStatus === 'RESTRICTED'">证据访问受限（RESTRICTED），无法下载输入记录。</span>
      <span v-if="evidenceStatus === 'LOADING'">正在载入原始证据…</span>
      <el-button v-if="evidenceStatus === 'ERROR' || evidenceStatus === 'RESTRICTED'" size="small" @click="retryEvidence">重试证据</el-button>
      <el-button v-if="frozenRequest && inputStatus === 'AVAILABLE'" size="small" @click="saveInput">
        下载输入记录
      </el-button>
      <el-button v-if="details" size="small" @click="saveSession">下载完整记录</el-button>
    </div>

    <div v-if="busy && !details" class="task-panel__empty" aria-busy="true">
      <span class="task-panel__prompt" aria-hidden="true">&gt;_</span>
      <strong>正在创建任务并获取状态</strong>
      <span>等待设备输出</span>
    </div>
    <el-empty
      v-else-if="!frozenRequest"
      class="task-panel__empty"
      :description="props.readonly ? '请从记录列表选择记录' : '尚未下发采集任务'"
    />
    <el-tabs v-else v-model="activeOutputTab" class="task-output-tabs">
      <el-tab-pane label="标准输出" name="stdout">
        <div class="terminal-output-shell">
          <div
            ref="stdoutTerminal"
            class="terminal-output"
            @scroll="handleTerminalScroll('stdout')"
          >
            <template v-if="details?.targets.length">
              <section
                v-for="target in details.targets"
                :key="target.targetId"
                class="terminal-output__target"
              >
                <header>
                  <strong>
                    {{
                      target.contextSnapshot.device?.deviceName ||
                      target.contextSnapshot.device?.deviceKey ||
                      target.endpointSnapshot.host
                    }}
                  </strong>
                  <span>{{ target.endpointSnapshot.host }}</span>
                </header>
                <p
                  v-if="targetOutputTruncated(target.targetId, target.truncated)"
                  class="terminal-output__warning"
                  role="status"
                >
                  输出已截断
                </p>
                <el-collapse class="command-output-blocks">
                  <el-collapse-item
                    v-for="block in blocksForTarget(target)"
                    :key="`${target.targetId}:${block.commandIndex}`"
                    :name="block.commandIndex"
                  >
                    <template #title>
                      <span class="command-output-blocks__title">
                        <strong>{{ block.legacy ? '历史合并输出' : `命令 ${block.commandIndex}` }}</strong>
                        <code v-if="block.commandText">{{ block.commandText }}</code>
                        <el-tag size="small" :type="block.legacy ? 'info' : blockTone(block.status)">{{ block.legacy ? 'UNKNOWN · 命令状态未知' : block.status }}</el-tag>
                        <span v-if="block.pageCount">分页 {{ block.pageCount }}</span>
                      </span>
                    </template>
                    <p v-if="block.truncated" class="terminal-output__warning">输出已截断</p>
                    <pre>{{ block.stdout || '无标准输出' }}</pre>
                  </el-collapse-item>
                </el-collapse>
              </section>
            </template>
            <template v-else-if="streamedTargetEntries.length">
              <section
                v-for="entry in streamedTargetEntries"
                :key="entry[0]"
                class="terminal-output__target"
              >
                <header>
                  <strong>{{ streamedTargetIdentity(entry[0]).label }}</strong>
                  <span>{{ streamedTargetIdentity(entry[0]).endpoint || '等待任务详情' }}</span>
                </header>
                <p
                  v-if="entry[1].truncated || entry[1].renderLimited"
                  class="terminal-output__warning"
                  role="status"
                >
                  输出已截断
                </p>
                <pre>{{ entry[1].stdout || '无标准输出' }}</pre>
              </section>
            </template>
            <pre v-else>正在等待标准输出…</pre>
          </div>
          <button
            v-if="showReturnToBottom && activeOutputTab === 'stdout'"
            type="button"
            class="terminal-output__follow"
            @click="scrollActiveToBottom"
          >
            回到底部
          </button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="错误输出" name="stderr">
        <div class="terminal-output-shell">
          <div
            ref="stderrTerminal"
            class="terminal-output"
            @scroll="handleTerminalScroll('stderr')"
          >
            <template v-if="details?.targets.length">
              <section
                v-for="target in details.targets"
                :key="target.targetId"
                class="terminal-output__target"
              >
                <header>
                  <strong>
                    {{
                      target.contextSnapshot.device?.deviceName ||
                      target.contextSnapshot.device?.deviceKey ||
                      target.endpointSnapshot.host
                    }}
                  </strong>
                  <span>{{ target.endpointSnapshot.host }}</span>
                </header>
                <p v-if="target.outcome" class="terminal-output__warning">{{ target.outcome }}</p>
                <el-collapse class="command-output-blocks">
                  <el-collapse-item
                    v-for="block in blocksForTarget(target)"
                    :key="`${target.targetId}:${block.commandIndex}`"
                    :name="block.commandIndex"
                  >
                    <template #title>
                      <span class="command-output-blocks__title">
                        <strong>{{ block.legacy ? '历史合并输出' : `命令 ${block.commandIndex}` }}</strong>
                        <code v-if="block.commandText">{{ block.commandText }}</code>
                        <el-tag size="small" :type="block.legacy ? 'info' : blockTone(block.status)">{{ block.legacy ? 'UNKNOWN · 命令状态未知' : block.status }}</el-tag>
                      </span>
                    </template>
                    <p v-if="block.outcome" class="terminal-output__warning">{{ block.outcome }}</p>
                    <pre>{{ block.stderr || '无错误输出' }}</pre>
                  </el-collapse-item>
                </el-collapse>
              </section>
            </template>
            <template v-else-if="streamedTargetEntries.length">
              <section
                v-for="entry in streamedTargetEntries"
                :key="entry[0]"
                class="terminal-output__target"
              >
                <header>
                  <strong>{{ streamedTargetIdentity(entry[0]).label }}</strong>
                  <span>{{ streamedTargetIdentity(entry[0]).endpoint || '等待任务详情' }}</span>
                </header>
                <pre>{{ entry[1].stderr || '无错误输出' }}</pre>
              </section>
            </template>
            <pre v-else>正在等待错误输出…</pre>
          </div>
          <button
            v-if="showReturnToBottom && activeOutputTab === 'stderr'"
            type="button"
            class="terminal-output__follow"
            @click="scrollActiveToBottom"
          >
            回到底部
          </button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="解析事实" name="facts">
        <div class="facts">
          <SemanticResultPanel :key="collectionId" :results="semanticResults" :collection-details="details" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="请求快照" name="request">
        <div class="request-evidence">
          <el-alert :title="requestProvenance" type="info" :closable="false" />
          <p v-if="evidenceStatus === 'ERROR'">证据查询失败，请重试</p>
          <p v-if="evidenceStatus === 'RESTRICTED'">证据访问受限</p>
          <p v-if="evidence">库创建时间：{{ formatLocalTime(evidence.metadata.createdAt) }}（本地时区）</p>
          <p v-if="requestOmittedFields.length">已省略字段：{{ requestOmittedFields.join('、') }}</p>
          <pre v-if="requestSnapshot && evidenceStatus !== 'RESTRICTED'" class="request-snapshot">{{ JSON.stringify(requestSnapshot, null, 2) }}</pre>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<style scoped>
.task-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  border-color: #26363d;
  background: var(--ops-terminal);
  color: #dce8e7;
  box-shadow: 0 0.4375rem 1.125rem rgb(27 48 56 / 12%);
}

.task-panel--standalone {
  min-height: 32rem;
  height: clamp(32rem, 72vh, 64rem);
}

.task-panel--workbench {
  height: 100%;
  flex: 1 1 0;
}

.task-panel > :deep(.el-card__header),
.task-panel :deep(.el-card__body) > :not(.task-output-tabs):not(.task-panel__empty) {
  flex-shrink: 0;
}

@media (max-height: 850px), (max-width: 1199px) {
  .task-panel--workbench {
    height: 42rem;
    flex: 0 0 auto;
  }
}

.task-panel :deep(.el-card__header),
.task-panel :deep(.el-card__body) {
  padding: 0;
  border: 0;
}

.task-panel :deep(.el-card__body) {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
}

.task-panel :deep(.panel-header) {
  min-height: 2.625rem;
  flex: 0 0 2.625rem;
  align-items: center;
  margin: 0;
  padding: 0 0.75rem;
  border-bottom: 1px solid #26363d;
  background: #101f25;
}

.task-panel :deep(.panel-header__icon) {
  padding: 0;
  color: #43cdbc;
  font-size: 0.875rem;
}

.task-panel :deep(.panel-header__eyebrow) {
  display: none;
}

.task-panel :deep(.panel-header h2) {
  color: #e5eeee;
  font-size: 0.8125rem;
}

.task-panel :deep(.panel-header__summary) {
  color: #7f949c;
  font-size: 0.75rem;
}

.task-panel__dispatch,
.task-panel__actions,
.task-panel__downloads,
.terminal-output__target > header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.task-panel__dispatch {
  justify-content: space-between;
  min-height: 3rem;
  flex: 0 0 auto;
  padding: 0.375rem 0.75rem;
  border-bottom: 1px solid #25343b;
  background: #0f1c22;
}

.task-panel__state {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.5rem;
}

.task-panel__state-label,
.task-panel__status-code,
.task-panel__downloads-label,
.task-panel__telemetry,
.terminal-output__target > header span {
  color: #7f949c;
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
}

.task-panel__state strong {
  overflow: hidden;
  color: #dce8e7;
  font-size: 0.75rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-panel__status-code {
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
}

.task-panel__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.task-panel__actions :deep(.el-button) {
  min-height: 1.875rem;
  padding: 0.375rem 0.625rem;
  font-size: 0.75rem;
}

.task-panel__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.task-panel__scope {
  flex: 0 0 auto;
  margin: 0;
  padding: 0.25rem 0.75rem;
  border-bottom: 1px solid #25343b;
  color: #586f77;
  font-size: 0.75rem;
  line-height: 1.35;
}

.task-panel__downloads {
  flex-wrap: wrap;
  justify-content: flex-end;
  min-height: 2rem;
  flex: 0 0 auto;
  margin: 0;
  padding: 0.25rem 0.75rem;
  border-bottom: 1px solid #25343b;
}

.task-panel__downloads-label {
  color: #7f949c;
}

.task-panel__telemetry {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  align-items: center;
  gap: 0.625rem;
  margin-right: auto;
  white-space: nowrap;
}

.task-panel__stream-state {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  color: #9bb0b6;
}

.task-panel__stream-state i {
  width: 0.375rem;
  height: 0.375rem;
  border-radius: 50%;
  background: #617982;
}

.task-panel__stream-state--live {
  color: #63d5c7;
}

.task-panel__stream-state--live i {
  background: #43cdbc;
  box-shadow: 0 0 0 0.1875rem rgb(67 205 188 / 12%);
}

.task-panel__stream-state--connecting i,
.task-panel__stream-state--reconnecting i {
  background: #f3c978;
  animation: stream-pulse 1.2s ease-in-out infinite;
}

.task-panel__truncate-state {
  color: #f3c978;
}

.task-panel__downloads :deep(.el-button) {
  --el-button-bg-color: transparent;
  --el-button-border-color: #344850;
  --el-button-text-color: #9bb0b6;
  --el-button-hover-bg-color: #172b32;
  --el-button-hover-border-color: #3bc7b6;
  --el-button-hover-text-color: #dce8e7;
  min-height: 1.5rem;
  padding: 0.1875rem 0.4375rem;
  font-size: 0.75rem;
}

.task-panel__empty {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  place-content: center;
  gap: 0.375rem;
  padding: 1rem;
  background:
    linear-gradient(rgb(255 255 255 / 2%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 2%) 1px, transparent 1px),
    var(--ops-terminal);
  background-size: 1.5rem 1.5rem;
  color: #7f949c;
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  text-align: center;
}

.task-panel__empty strong {
  color: #c8ddda;
  font-size: 0.75rem;
}

.task-panel__empty :deep(.el-empty__description p) {
  color: #c8ddda;
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  line-height: 1.5;
}

.task-panel__prompt {
  color: #43cdbc;
  font-size: 1rem;
}

.task-output-tabs {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  margin: 0;
  background: var(--ops-terminal);
}

.task-output-tabs > :deep(.el-tabs__header) {
  flex: 0 0 1.875rem;
  margin: 0;
  border-bottom: 1px solid #25343b;
  background: #0f1c22;
}

.task-output-tabs > :deep(.el-tabs__header .el-tabs__nav-wrap::after) {
  display: none;
}

.task-output-tabs > :deep(.el-tabs__header .el-tabs__item) {
  height: 1.875rem;
  padding: 0 0.75rem;
  color: #617982;
  font-size: 0.75rem;
}

.task-output-tabs > :deep(.el-tabs__header .el-tabs__item.is-active) {
  color: #63d5c7;
}

.task-output-tabs > :deep(.el-tabs__header .el-tabs__active-bar) {
  background: #3bc7b6;
}

.task-output-tabs > :deep(.el-tabs__content),
.task-output-tabs > :deep(.el-tabs__content > .el-tab-pane) {
  min-height: 0;
  height: 100%;
  flex: 1 1 auto;
  overflow: hidden;
}

.terminal-output-shell {
  position: relative;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.terminal-output,
.request-snapshot {
  min-height: 100%;
  height: 100%;
  max-height: none;
  overflow: auto;
  margin: 0;
  background: var(--ops-terminal);
  color: #f4f8f9;
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
}

.terminal-output > pre,
.terminal-output__target pre,
.request-snapshot {
  margin: 0;
  padding: 1rem;
  font-size: 0.75rem;
  line-height: 1.65;
  white-space: pre-wrap;
}

.terminal-output__target + .terminal-output__target {
  border-top: 1px solid #52616b;
}

.terminal-output__target > header {
  justify-content: space-between;
  padding: 0.625rem 1rem;
  border-bottom: 1px solid #52616b;
}

.terminal-output__target > header span {
  color: #aebdc4;
}

.terminal-output__warning {
  margin: 0;
  padding: 0.625rem 1rem;
  border-bottom: 1px solid #52616b;
  color: #f3c978;
  font-size: 0.75rem;
}

.terminal-output__follow {
  position: absolute;
  right: 0.75rem;
  bottom: 0.75rem;
  min-height: 1.75rem;
  padding: 0.25rem 0.625rem;
  border: 1px solid #3bc7b6;
  border-radius: 0.25rem;
  background: #12352f;
  color: #b8eee7;
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  cursor: pointer;
  box-shadow: 0 0.25rem 0.75rem rgb(0 0 0 / 28%);
}

.terminal-output__follow:hover,
.terminal-output__follow:focus-visible {
  background: #17453d;
  color: #fff;
  outline: 2px solid #63d5c7;
  outline-offset: 2px;
}

.facts {
  min-height: 100%;
  height: 100%;
  overflow: auto;
  padding: 1rem;
  border: 1px solid var(--line);
  background: var(--paper);
}

.facts section + section {
  margin-top: 1rem;
}

.facts h3 {
  margin: 0 0 0.75rem;
  font-size: 0.875rem;
}

.facts p {
  margin: 0;
  color: var(--slate);
}

.facts dl {
  margin-bottom: 0;
}

.command-output-blocks {
  border: 0;
}

.command-output-blocks__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.5rem;
}

.command-output-blocks__title code {
  overflow: hidden;
  color: #b9d5d2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.command-output-blocks :deep(.el-collapse-item__header),
.command-output-blocks :deep(.el-collapse-item__wrap) {
  border-color: rgb(143 168 166 / 20%);
  background: transparent;
  color: inherit;
}

.command-output-blocks :deep(.el-collapse-item__content) {
  padding-bottom: 0.75rem;
  color: inherit;
}

.terminal-output > pre,
.terminal-output__target pre {
  background: #101820;
  color: #f4f8f9;
  overflow-wrap: anywhere;
}

.facts {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding: 1rem;
  background: var(--el-bg-color, #fff);
  color: var(--el-text-color-primary, #243746);
}

.request-evidence {
  display: flex;
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: #101820;
  color: #f4f8f9;
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  scrollbar-color: #52616b #101820;
}

.request-evidence :deep(.el-alert) {
  flex-shrink: 0;
  padding: 0.375rem 1rem;
  border-bottom: 1px solid #52616b;
  border-radius: 0;
  background: #101820;
  color: #aebdc4;
  --el-alert-title-font-size: 12px;
}

.request-evidence > p {
  margin: 0;
  padding: 0.25rem 1rem;
  color: #aebdc4;
  font-size: 0.75rem;
  overflow-wrap: anywhere;
}

.request-evidence .request-snapshot {
  flex: 1;
  height: auto;
  min-height: 0;
  border-top: 1px solid #52616b;
  background: #101820;
  color: #f4f8f9;
  overflow-wrap: anywhere;
}

.task-panel .status-rail li {
  color: #aebdc4;
}

.task-panel .status-rail li.complete {
  color: #63d5c7;
}

.task-panel .status-rail li.active {
  color: #f4f8f9;
}

@keyframes stream-pulse {
  50% {
    opacity: 0.35;
  }
}

@media (max-width: 767px) {
  .task-panel {
    min-height: 24rem;
  }

  .task-panel__dispatch {
    align-items: stretch;
    flex-direction: column;
  }

  .task-panel__actions {
    justify-content: flex-start;
  }

  .task-panel__telemetry {
    width: 100%;
    flex-basis: 100%;
    overflow-x: auto;
  }
}
</style>
