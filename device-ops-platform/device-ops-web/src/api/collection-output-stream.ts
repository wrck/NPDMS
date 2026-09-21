import { getAccessToken } from '@/auth/oidc'
import { loadRuntimeConfig } from '@/config/runtime'
import type { CollectionOutputEvent } from '@/types/collection'

export interface OutputStreamHandlers {
  onOutput(event: CollectionOutputEvent): void
  onComplete(): void
  onState(state: 'CONNECTING' | 'LIVE' | 'RECONNECTING' | 'CLOSED'): void
  onError(error: unknown): void
}

interface SseFrame {
  event: string
  id: string
  data: string[]
}

function createFrame(): SseFrame {
  return { event: '', id: '', data: [] }
}

function buildStreamUrl(
  apiBaseUrl: string | undefined,
  context: { mode: 'project' | 'generic'; projectKey?: string },
  namespace: string,
  collectionId: string,
  afterSequence: number
): URL {
  if (context.mode === 'project' && !context.projectKey?.trim()) {
    throw new Error('项目任务缺少 projectKey')
  }
  const apiBase = apiBaseUrl?.trim() || '/api/v1'
  const baseUrl = new URL(
    apiBase.endsWith('/') ? apiBase : `${apiBase}/`,
    window.location.origin
  )
  const collectionPath =
    context.mode === 'project'
      ? `projects/${encodeURIComponent(context.projectKey ?? '')}/collections`
      : 'collections'
  const url = new URL(
    `${collectionPath}/${encodeURIComponent(collectionId)}/output-events`,
    baseUrl
  )
  url.searchParams.set('namespace', namespace)
  url.searchParams.set('after', String(afterSequence))
  return url
}

function parseOutputEvent(frame: SseFrame): CollectionOutputEvent {
  const parsed = JSON.parse(frame.data.join('\n')) as Partial<CollectionOutputEvent>
  if (
    typeof parsed.targetId !== 'number' ||
    !Number.isSafeInteger(parsed.targetId) ||
    parsed.targetId < 1 ||
    typeof parsed.sequence !== 'number' ||
    !Number.isSafeInteger(parsed.sequence) ||
    parsed.sequence < 1 ||
    (parsed.commandIndex !== null &&
      (typeof parsed.commandIndex !== 'number' ||
        !Number.isSafeInteger(parsed.commandIndex) ||
        parsed.commandIndex < 1)) ||
    (parsed.stream !== 'STDOUT' && parsed.stream !== 'STDERR') ||
    typeof parsed.content !== 'string' ||
    typeof parsed.receivedBytes !== 'number' ||
    !Number.isSafeInteger(parsed.receivedBytes) ||
    parsed.receivedBytes < 0 ||
    typeof parsed.pageCount !== 'number' ||
    !Number.isSafeInteger(parsed.pageCount) ||
    parsed.pageCount < 0 ||
    typeof parsed.truncated !== 'boolean' ||
    typeof parsed.createdAt !== 'string' ||
    !parsed.createdAt
  ) {
    throw new Error('输出事件格式无效')
  }
  if (frame.id && (!/^\d+$/.test(frame.id) || Number(frame.id) !== parsed.sequence)) {
    throw new Error('输出事件游标与事件序号不一致')
  }
  return parsed as CollectionOutputEvent
}

export async function streamCollectionOutput(
  context: { mode: 'project' | 'generic'; projectKey?: string },
  namespace: string,
  collectionId: string,
  afterSequence: number,
  signal: AbortSignal,
  handlers: OutputStreamHandlers
): Promise<void> {
  handlers.onState('CONNECTING')
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
  try {
    const [config, token] = await Promise.all([loadRuntimeConfig(), getAccessToken()])
    if (signal.aborted) {
      handlers.onState('CLOSED')
      return
    }
    const headers = new Headers({
      Accept: 'text/event-stream',
      'Cache-Control': 'no-cache'
    })
    if (token) headers.set('Authorization', `Bearer ${token}`)

    const response = await fetch(
      buildStreamUrl(config.apiBaseUrl, context, namespace, collectionId, afterSequence),
      { headers, signal }
    )
    if (!response.ok) {
      throw new Error(`实时输出连接失败（HTTP ${response.status}）`)
    }
    const mediaType = response.headers
      .get('content-type')
      ?.split(';', 1)[0]
      .trim()
      .toLowerCase()
    if (mediaType !== 'text/event-stream') {
      throw new Error(
        `实时输出协议无效（Content-Type: ${mediaType || 'missing'}）`
      )
    }
    if (!response.body) {
      throw new Error('实时输出响应不包含可读数据流')
    }

    handlers.onState('LIVE')
    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let frame = createFrame()
    let completed = false

    const dispatchFrame = () => {
      if (!frame.data.length) {
        frame = createFrame()
        return
      }
      const eventName = frame.event || 'message'
      if (eventName === 'output') {
        handlers.onOutput(parseOutputEvent(frame))
      } else if (eventName === 'complete') {
        completed = true
        handlers.onComplete()
      }
      frame = createFrame()
    }

    const processLine = (line: string) => {
      if (!line) {
        dispatchFrame()
        return
      }
      if (line.startsWith(':')) return
      const separator = line.indexOf(':')
      const field = separator < 0 ? line : line.slice(0, separator)
      let value = separator < 0 ? '' : line.slice(separator + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      if (field === 'event') frame.event = value
      if (field === 'id' && !value.includes('\0')) frame.id = value
      if (field === 'data') frame.data.push(value)
    }

    const processBuffer = (final: boolean) => {
      while (buffer.length) {
        const lineEnd = buffer.search(/[\r\n]/)
        if (lineEnd < 0) return
        if (buffer[lineEnd] === '\r' && lineEnd === buffer.length - 1 && !final) return
        const delimiterLength =
          buffer[lineEnd] === '\r' && buffer[lineEnd + 1] === '\n' ? 2 : 1
        const line = buffer.slice(0, lineEnd)
        buffer = buffer.slice(lineEnd + delimiterLength)
        processLine(line)
        if (completed) return
      }
    }

    while (!completed) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      processBuffer(false)
    }
    buffer += decoder.decode()
    processBuffer(true)

    if (completed) {
      handlers.onState('CLOSED')
      return
    }
    throw new Error('实时输出连接已断开')
  } catch (error) {
    await reader?.cancel().catch(() => undefined)
    if (signal.aborted) {
      handlers.onState('CLOSED')
      return
    }
    handlers.onError(error)
    throw error
  }
}
