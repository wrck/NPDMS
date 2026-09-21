export const OBSERVATION_STATUSES = Object.freeze([
  'OBSERVED',
  'NO_DATA',
  'NOT_ENABLED',
  'REDACTED',
  'PARTIAL',
  'UNPARSED',
  'SOURCE_CORRUPTED',
  'EXECUTION_FAILED'
])

const EXTRACTOR_TYPES = new Set(['LINE_REGEX', 'KEY_VALUE', 'TABLE', 'CONFIG_STANZA', 'STATUS_TEXT'])
const SELECTOR_TYPES = new Set(['COMMAND_ALIAS', 'COMMAND_REGEX', 'CONTENT_REGEX', 'TABLE_HEADERS'])
const CARDINALITIES = new Set(['ONE', 'MANY'])
const CONFLICT_POLICIES = new Set(['HIGHEST_CONFIDENCE', 'APPEND_DISTINCT'])
const MISSING_POLICIES = new Set(['NULL', 'OMIT'])
const DATA_TYPES = new Set(['string', 'integer', 'decimal', 'boolean', 'datetime', 'object'])
const TRANSFORMS = new Set(['trim', 'lowercase', 'integer', 'decimal', 'boolean-enable-disable', 'capacity-to-bytes', 'duration-to-seconds', 'datetime'])

function fail(path, expectation) {
  throw new TypeError(`${path} ${expectation}`)
}

function requireObject(value, path) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail(path, 'must be an object')
  return value
}

function requireString(value, path) {
  if (typeof value !== 'string' || !value.trim()) fail(path, 'must be a non-blank string')
  return value
}

function requireArray(value, path) {
  if (!Array.isArray(value)) fail(path, 'must be an array')
  return value
}

export function validateStructuredLog(value) {
  const root = requireObject(value, '$')
  const stdout = requireObject(root.stdout, '$.stdout')
  const blocks = requireArray(stdout.blocks, '$.stdout.blocks')
  blocks.forEach((block, index) => {
    const path = `$.stdout.blocks[${index}]`
    requireObject(block, path)
    requireString(block.command, `${path}.command`)
    if (!Number.isInteger(block.blockIndex) || block.blockIndex < 1) fail(`${path}.blockIndex`, 'must be a positive integer')
    if (!Array.isArray(block.lines)) fail(`${path}.lines`, 'must be an array')
    if (block.lines.some((line) => typeof line !== 'string')) fail(`${path}.lines`, 'must contain only strings')
    if (!Number.isInteger(block.headerLine) || block.headerLine < 1) fail(`${path}.headerLine`, 'must be a positive integer')
  })
  return value
}

export function validateRules(value) {
  const root = requireObject(value, '$')
  if (root.schemaVersion !== '1.0.0') fail('$.schemaVersion', 'must equal 1.0.0')
  const rules = requireArray(root.rules, '$.rules')
  const ids = new Set()
  rules.forEach((rule, index) => {
    const path = `$.rules[${index}]`
    requireObject(rule, path)
    const ruleId = requireString(rule.ruleId, `${path}.ruleId`)
    if (ids.has(ruleId)) fail(`${path}.ruleId`, 'must be unique')
    ids.add(ruleId)
    requireString(rule.blockRole, `${path}.blockRole`)
    const selectors = requireArray(rule.roleSelectors, `${path}.roleSelectors`)
    if (!selectors.length) fail(`${path}.roleSelectors`, 'must not be empty')
    selectors.forEach((selector, selectorIndex) => {
      const selectorPath = `${path}.roleSelectors[${selectorIndex}]`
      if (!SELECTOR_TYPES.has(selector?.type)) fail(`${selectorPath}.type`, 'is unsupported')
      if (selector.type === 'TABLE_HEADERS') {
        const values = requireArray(selector.values, `${selectorPath}.values`)
        if (!values.length || values.some((item) => typeof item !== 'string' || !item.trim())) fail(`${selectorPath}.values`, 'must contain non-blank strings')
      } else {
        const pattern = requireString(selector.value, `${selectorPath}.value`)
        if (['COMMAND_REGEX', 'CONTENT_REGEX'].includes(selector.type)) {
          try { new RegExp(pattern) } catch { fail(`${selectorPath}.value`, 'must be a valid regular expression') }
        }
      }
    })
    requireArray(rule.extractors, `${path}.extractors`).forEach((extractor, extractorIndex) => {
      const extractorPath = `${path}.extractors[${extractorIndex}]`
      if (!EXTRACTOR_TYPES.has(extractor?.type)) fail(`${extractorPath}.type`, 'is unsupported')
      if (['LINE_REGEX', 'STATUS_TEXT'].includes(extractor.type)) {
        const pattern = requireString(extractor.pattern, `${extractorPath}.pattern`)
        try { new RegExp(pattern) } catch { fail(`${extractorPath}.pattern`, 'must be a valid regular expression') }
      }
      if (extractor.type === 'KEY_VALUE') requireString(extractor.key, `${extractorPath}.key`)
      requireArray(extractor.transforms ?? [], `${extractorPath}.transforms`).forEach((transform, transformIndex) => {
        if (!TRANSFORMS.has(transform)) fail(`${extractorPath}.transforms[${transformIndex}]`, 'is unsupported')
      })
    })
    requireObject(rule.target, `${path}.target`)
    requireString(rule.target.semanticKey, `${path}.target.semanticKey`)
    if (!CARDINALITIES.has(rule.target.cardinality)) fail(`${path}.target.cardinality`, 'is unsupported')
    if (!DATA_TYPES.has(rule.target.dataType)) fail(`${path}.target.dataType`, 'is unsupported')
    if (!CONFLICT_POLICIES.has(rule.target.conflictPolicy)) fail(`${path}.target.conflictPolicy`, 'is unsupported')
  })
  return value
}

export function validateProjectionProfiles(value) {
  const root = requireObject(value, '$')
  if (root.schemaVersion !== '1.0.0') fail('$.schemaVersion', 'must equal 1.0.0')
  const ids = new Set()
  requireArray(root.profiles, '$.profiles').forEach((profile, index) => {
    const path = `$.profiles[${index}]`
    const projectionId = requireString(profile?.projectionId, `${path}.projectionId`)
    if (ids.has(projectionId)) fail(`${path}.projectionId`, 'must be unique')
    ids.add(projectionId)
    requireObject(profile.fields, `${path}.fields`)
    for (const [name, semanticPath] of Object.entries(profile.fields)) {
      requireString(name, `${path}.fields key`)
      requireString(semanticPath, `${path}.fields.${name}`)
    }
    if (!MISSING_POLICIES.has(profile.missingValuePolicy)) fail(`${path}.missingValuePolicy`, 'is unsupported')
  })
  return value
}

export function assertObservationStatus(status, path = '$.status') {
  if (!OBSERVATION_STATUSES.includes(status)) fail(path, 'is unsupported')
  return status
}
