import type { CollectionEvidence } from '@/types/collection-evidence'

/** Project authorized server evidence, never the current editor or a retry request. */
export function projectRequestSnapshot(evidence: CollectionEvidence): {
  snapshot: Record<string, unknown> | null
  omittedFields: string[]
} {
  // JSON evidence can arrive as a Vue proxy; JSON cloning also detaches nested facts.
  const copy = JSON.parse(JSON.stringify(evidence)) as CollectionEvidence
  const { source, key, version, sha256, policy, parserType, contentStatus, content } = copy.input
  const available = contentStatus === 'AVAILABLE' && typeof content === 'string'
  let snapshot = copy.submission.snapshot
  let supplemented = false
  if (copy.submission.provenance === 'RECONSTRUCTED_FACTS') {
    snapshot = {
      provenance: 'RECONSTRUCTED_FACTS',
      metadata: copy.metadata,
      script: { source, key, version, sha256, policy, parserType, ...(available ? { content } : {}) },
      executionFacts: copy.executionFacts
    }
  } else if (snapshot && isRecord(snapshot.body)) {
    const script = isRecord(snapshot.body.script) ? snapshot.body.script : {}
    delete script.content
    if (available) {
      script.content = content
      supplemented = true
    }
    snapshot.body.script = script
  }
  return {
    snapshot,
    omittedFields: copy.submission.omittedFields.filter(path => !supplemented || path !== 'body.script.content')
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
