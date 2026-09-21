const FORBIDDEN_SEGMENTS = new Set(['__proto__', 'prototype', 'constructor'])

function safeSegments(path) {
  const segments = String(path ?? '').split('.')
  if (segments.length < 1 || segments.some((segment) => !segment || FORBIDDEN_SEGMENTS.has(segment))) {
    throw new TypeError(`unsafe projection path: ${path}`)
  }
  return segments
}

function resolveSemanticValue(snapshot, semanticPath) {
  const segments = safeSegments(semanticPath)
  let cursor = segments[0] === 'collection' ? snapshot : snapshot.entities
  for (const segment of segments) {
    if (cursor === undefined || cursor === null) return undefined
    cursor = cursor[segment]
  }
  if (Array.isArray(cursor)) return cursor.map((item) => item?.value ?? item)
  return cursor?.semanticKey ? cursor.value : cursor
}

export function applyProjection(snapshot, profile) {
  const result = {}
  for (const [fieldName, semanticPath] of Object.entries(profile.fields)) {
    safeSegments(fieldName.includes('.') ? fieldName : `projection.${fieldName}`)
    const value = resolveSemanticValue(snapshot, semanticPath)
    if (value !== undefined) result[fieldName] = value
    else if (profile.missingValuePolicy === 'NULL') result[fieldName] = null
  }
  snapshot.projections[profile.projectionId] = result
  return result
}

export function applyProjectionProfiles(snapshot, profiles) {
  return Object.fromEntries(profiles.map((profile) => [profile.projectionId, applyProjection(snapshot, profile)]))
}
