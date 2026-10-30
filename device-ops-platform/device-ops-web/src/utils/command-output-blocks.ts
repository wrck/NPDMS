import type {
  CollectionOutputEvent,
  CollectionTargetDetails,
  CommandOutputBlock
} from '@/types/collection'

export type CommandBlockKey = `${number}:${number}`

export function commandBlockKey(targetId: number, commandIndex: number): CommandBlockKey {
  return `${targetId}:${commandIndex}`
}

export function mergeCommandEvent(
  block: CommandOutputBlock,
  event: CollectionOutputEvent
): CommandOutputBlock {
  if (event.commandIndex !== block.commandIndex) return block
  const field = event.stream === 'STDOUT' ? 'stdout' : 'stderr'
  const current = block[field]
  const overlap = overlapLength(current, event.content)
  return {
    ...block,
    [field]: current + event.content.slice(overlap),
    receivedBytes: Math.max(block.receivedBytes, event.receivedBytes),
    pageCount: Math.max(block.pageCount, event.pageCount),
    truncated: block.truncated || event.truncated
  }
}

export function legacyCommandBlock(target: CollectionTargetDetails): CommandOutputBlock {
  return {
    commandIndex: 1,
    commandText: '',
    status: target.status === 'TIMED_OUT' ? 'TIMED_OUT' : target.status === 'CANCELLED' ? 'CANCELLED' : target.status === 'FAILED' ? 'FAILED' : 'SUCCEEDED',
    stdout: target.stdout ?? '',
    stderr: target.stderr ?? '',
    receivedBytes: new TextEncoder().encode(`${target.stdout ?? ''}${target.stderr ?? ''}`).byteLength,
    pageCount: 0,
    truncated: target.truncated,
    exitCode: target.exitCode,
    outcome: target.outcome,
    parsedFacts: target.parsedFacts ?? {},
    parseWarnings: [],
    legacy: true
  }
}

export function blocksForTarget(target: CollectionTargetDetails): CommandOutputBlock[] {
  return target.commandBlocks?.length ? target.commandBlocks : [legacyCommandBlock(target)]
}

export function renderCommandTranscript(blocks: CommandOutputBlock[]): string {
  return blocks
    .map((block) => {
      const title = block.legacy
        ? '===== 历史合并输出 ====='
        : `===== command ${block.commandIndex}: ${block.commandText} =====`
      const facts = JSON.stringify(block.parsedFacts ?? {})
      const warnings = JSON.stringify(block.parseWarnings ?? [])
      return [
        title,
        `status=${block.legacy ? 'UNKNOWN' : block.status} pages=${block.pageCount} exitCode=${block.exitCode ?? ''}`,
        `facts=${facts}`,
        `warnings=${warnings}`,
        '--- stdout ---',
        block.stdout ?? '',
        '--- stderr ---',
        block.stderr ?? ''
      ].join('\n')
    })
    .join('\n\n')
}

function overlapLength(current: string, incoming: string): number {
  const maximum = Math.min(current.length, incoming.length)
  for (let size = maximum; size > 0; size -= 1) {
    if (current.endsWith(incoming.slice(0, size))) return size
  }
  return 0
}
