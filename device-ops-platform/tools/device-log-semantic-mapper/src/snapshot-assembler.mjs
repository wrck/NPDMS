const FORBIDDEN_SEGMENTS = new Set(['__proto__', 'prototype', 'constructor'])

function pathSegments(semanticKey) {
  const segments = String(semanticKey ?? '').split('.')
  if (segments.length < 1 || segments.some((segment) => !segment || FORBIDDEN_SEGMENTS.has(segment))) {
    throw new TypeError(`unsafe semantic path: ${semanticKey}`)
  }
  return segments
}

function targetPath(snapshot, semanticKey) {
  const segments = pathSegments(semanticKey)
  return segments[0] === 'collection'
    ? { root: snapshot, segments }
    : { root: snapshot.entities, segments }
}

function compareFacts(left, right) {
  const confidenceDifference = (left.confidence ?? 0) - (right.confidence ?? 0)
  if (confidenceDifference) return confidenceDifference
  return (left.source?.lineStart ?? -1) - (right.source?.lineStart ?? -1)
}

function stableValueKey(value) {
  if (!value || typeof value !== 'object') return `${typeof value}:${String(value)}`
  const ordered = Object.fromEntries(Object.keys(value).sort().map((key) => [key, value[key]]))
  return JSON.stringify(ordered)
}

function assignFact(snapshot, fact) {
  const { root, segments } = targetPath(snapshot, fact.semanticKey)
  let cursor = root
  for (const segment of segments.slice(0, -1)) {
    const existing = cursor[segment]
    if (existing === undefined) cursor[segment] = {}
    else if (!existing || typeof existing !== 'object' || Array.isArray(existing) || 'semanticKey' in existing) {
      throw new TypeError(`semantic path collides with a scalar: ${fact.semanticKey}`)
    }
    cursor = cursor[segment]
  }

  const leaf = segments.at(-1)
  if (fact.cardinality === 'MANY') {
    if (cursor[leaf] === undefined) cursor[leaf] = []
    if (!Array.isArray(cursor[leaf])) throw new TypeError(`semantic path cardinality collision: ${fact.semanticKey}`)
    const valueKey = stableValueKey(fact.value)
    if (!cursor[leaf].some((item) => stableValueKey(item.value) === valueKey)) cursor[leaf].push(fact)
    return
  }

  const current = cursor[leaf]
  if (current === undefined) {
    cursor[leaf] = fact
    return
  }
  if (Array.isArray(current)) throw new TypeError(`semantic path cardinality collision: ${fact.semanticKey}`)

  const winner = compareFacts(fact, current) >= 0 ? fact : current
  const discarded = winner === fact ? current : fact
  cursor[leaf] = winner
  snapshot.quality.conflicts.push({
    semanticKey: fact.semanticKey,
    policy: fact.conflictPolicy,
    winnerRuleId: winner.ruleId,
    discardedRuleId: discarded.ruleId,
    winnerSource: winner.source,
    discardedSource: discarded.source
  })
}

export function assembleSnapshot(context, observations, facts, warnings = []) {
  const snapshot = {
    schemaVersion: '1.0.0',
    collection: { ...context },
    entities: {
      device: {
        identity: {}, software: {}, hardware: {}, environment: {}, performance: {},
        management: {}, highAvailability: {}
      },
      interfaces: [],
      addresses: {},
      routing: {},
      sessions: {},
      security: {},
      logs: {},
      configuration: {}
    },
    observations: structuredClone(observations),
    projections: {},
    unmapped: observations.filter((item) => item.status === 'UNPARSED').map((item) => item.source),
    quality: {
      observationCount: observations.length,
      factCount: facts.length,
      mappedFactCount: 0,
      warningCount: warnings.length,
      conflicts: [],
      warnings: structuredClone(warnings)
    }
  }

  for (const fact of facts) {
    assignFact(snapshot, structuredClone(fact))
    snapshot.quality.mappedFactCount += 1
  }
  return snapshot
}
