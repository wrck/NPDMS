const SELECTOR_SCORES = Object.freeze({
  COMMAND_ALIAS: 0.95,
  COMMAND_REGEX: 0.85,
  CONTENT_REGEX: 0.8,
  TABLE_HEADERS: 0.9
})

const SECRET_MARKERS = /\[REDACTED\]|\*{4,}/i
const DISABLED_TEXT = /\b(?:not enabled|disabled|not configured|not used|process not enabled)\b/i
const SENSITIVE_ASSIGNMENT = /(\b(?:password|passphrase|secret|community|private[- ]?key|credential)\b\s*(?::|=|\s)\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|\S+)/gim

export function redactSensitiveText(value) {
  return String(value ?? '').replace(SENSITIVE_ASSIGNMENT, (_, label) => `${label}[REDACTED]`)
}

function sanitizeSensitiveValue(value) {
  if (typeof value === 'string') {
    const sanitized = redactSensitiveText(value)
    return { value: sanitized, redacted: sanitized !== value || SECRET_MARKERS.test(value) }
  }
  if (Array.isArray(value)) {
    const items = value.map(sanitizeSensitiveValue)
    return { value: items.map((item) => item.value), redacted: items.some((item) => item.redacted) }
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value).map(([key, item]) => [key, sanitizeSensitiveValue(item)])
    return {
      value: Object.fromEntries(entries.map(([key, item]) => [key, item.value])),
      redacted: entries.some(([, item]) => item.redacted)
    }
  }
  return { value, redacted: false }
}

function normalizeCommand(value) {
  return String(value ?? '').trim().replace(/\s+/g, ' ').toLowerCase()
}

function safeRegex(pattern, flags = 'i') {
  try {
    return new RegExp(pattern, flags)
  } catch (error) {
    throw new TypeError(`invalid regular expression: ${pattern}`, { cause: error })
  }
}

function selectorMatches(selector, block) {
  const command = normalizeCommand(block.command)
  const content = block.lines.join('\n')
  switch (selector.type) {
    case 'COMMAND_ALIAS':
      return command === normalizeCommand(selector.value)
    case 'COMMAND_REGEX':
      return safeRegex(selector.value).test(command)
    case 'CONTENT_REGEX':
      return safeRegex(selector.value, 'im').test(content)
    case 'TABLE_HEADERS':
      return Array.isArray(selector.values)
        && selector.values.length > 0
        && selector.values.every((header) => content.toLowerCase().includes(String(header).toLowerCase()))
    default:
      return false
  }
}

function combinedConfidence(scores) {
  return Math.min(1, 1 - scores.reduce((product, score) => product * (1 - score), 1))
}

function sourceEvidence(block, lineOffset = null, rawText = '', lineEndOffset = lineOffset) {
  const lineStart = lineOffset === null || block.contentStartLine === null
    ? block.contentStartLine ?? null
    : block.contentStartLine + lineOffset
  const lineEnd = lineOffset === null || block.contentStartLine === null
    ? block.contentEndLine ?? lineStart
    : block.contentStartLine + (lineEndOffset ?? lineOffset)
  return {
    blockIndex: block.blockIndex,
    command: block.command,
    lineStart,
    lineEnd,
    rawText: redactSensitiveText(rawText)
  }
}

export function inferBlockMeaning(block, rules) {
  const roleMatches = new Map()
  for (const rule of rules) {
    const scores = []
    for (const selector of rule.roleSelectors) {
      if (selectorMatches(selector, block)) scores.push(SELECTOR_SCORES[selector.type])
    }
    if (!scores.length) continue
    const role = roleMatches.get(rule.blockRole) ?? { scores: new Map(), rules: [] }
    rule.roleSelectors.forEach((selector) => {
      if (selectorMatches(selector, block)) {
        role.scores.set(JSON.stringify(selector), SELECTOR_SCORES[selector.type])
      }
    })
    role.rules.push(rule)
    roleMatches.set(rule.blockRole, role)
  }

  const ranked = [...roleMatches.entries()]
    .map(([blockRole, match]) => ({
      blockRole,
      confidence: combinedConfidence([...match.scores.values()]),
      rules: match.rules
    }))
    .sort((left, right) => right.confidence - left.confidence || left.blockRole.localeCompare(right.blockRole))
  const winner = ranked[0]
  const ambiguous = winner && ranked[1] && Math.abs(winner.confidence - ranked[1].confidence) < Number.EPSILON
  const content = block.lines.join('\n')
  const base = {
    blockIndex: block.blockIndex,
    command: block.command,
    blockRole: winner?.confidence >= 0.6 && !ambiguous ? winner.blockRole : null,
    status: 'UNPARSED',
    confidence: winner?.confidence ?? 0,
    matchedRuleIds: ambiguous ? [] : winner?.rules.map((rule) => rule.ruleId) ?? [],
    targetSemanticKeys: ambiguous ? [] : [...new Set(winner?.rules.map((rule) => rule.target.semanticKey) ?? [])],
    candidateBlockRoles: ranked.map(({ blockRole, confidence }) => ({ blockRole, confidence })),
    source: sourceEvidence(block, null, content)
  }
  if (!winner || winner.confidence < 0.6 || ambiguous) return base
  if (block.isEmpty || !block.lines.some((line) => line.trim())) return { ...base, status: 'NO_DATA' }
  if (content.includes('\uFFFD')) return { ...base, status: 'SOURCE_CORRUPTED' }
  if (DISABLED_TEXT.test(content)) return { ...base, status: 'NOT_ENABLED' }
  return { ...base, status: 'OBSERVED' }
}

function parseCapacity(rawValue) {
  const match = String(rawValue).trim().match(/^([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?)B?(?:\s*bytes?)?$/i)
  if (!match) throw new TypeError('capacity value is invalid')
  const unit = match[2].toUpperCase()
  const powers = { '': 0, K: 1, M: 2, G: 3, T: 4 }
  if (!(unit in powers)) throw new TypeError('capacity unit is invalid')
  return Math.round(Number(match[1]) * (1024 ** powers[unit]))
}

function parseDuration(rawValue) {
  const units = { week: 604800, day: 86400, hour: 3600, minute: 60, second: 1 }
  let total = 0
  let matches = 0
  for (const match of String(rawValue).matchAll(/([0-9]+)\s*(weeks?|days?|hours?|minutes?|seconds?)(?:\(s\))?/gi)) {
    const unit = match[2].toLowerCase().replace(/s$/, '')
    total += Number(match[1]) * units[unit]
    matches += 1
  }
  if (!matches) throw new TypeError('duration value is invalid')
  return total
}

function transformValue(rawValue, transforms = []) {
  let value = rawValue
  for (const transform of transforms) {
    switch (transform) {
      case 'trim': value = String(value).trim(); break
      case 'lowercase': value = String(value).toLowerCase(); break
      case 'integer': value = Number.parseInt(String(value).trim(), 10); break
      case 'decimal': value = Number.parseFloat(String(value).trim()); break
      case 'boolean-enable-disable': {
        const normalized = String(value).trim().toLowerCase()
        if (['enable', 'enabled', 'up', 'in use', 'true', 'yes'].includes(normalized)) value = true
        else if (['disable', 'disabled', 'down', 'not used', 'false', 'no'].includes(normalized)) value = false
        else throw new TypeError('boolean value is invalid')
        break
      }
      case 'capacity-to-bytes': value = parseCapacity(value); break
      case 'duration-to-seconds': value = parseDuration(value); break
      case 'datetime': {
        const date = new Date(String(value).trim())
        if (Number.isNaN(date.getTime())) throw new TypeError('datetime value is invalid')
        value = date.toISOString()
        break
      }
      default: throw new TypeError(`unsupported transform: ${transform}`)
    }
    if (typeof value === 'number' && !Number.isFinite(value)) throw new TypeError(`${transform} produced a non-finite number`)
  }
  return value
}

function extractCandidates(block, extractor) {
  if (extractor.type === 'LINE_REGEX') {
    const regex = safeRegex(extractor.pattern)
    return block.lines.flatMap((line, index) => {
      const match = line.match(regex)
      return match ? [{ rawValue: match[extractor.group ?? 1] ?? match[0], line, lineOffset: index }] : []
    })
  }
  if (extractor.type === 'KEY_VALUE') {
    const key = String(extractor.key).trim().toLowerCase()
    return block.lines.flatMap((line, index) => {
      const separator = line.indexOf(':')
      if (separator < 1 || line.slice(0, separator).trim().toLowerCase() !== key) return []
      return [{ rawValue: line.slice(separator + 1), line, lineOffset: index }]
    })
  }
  if (extractor.type === 'STATUS_TEXT') {
    const regex = safeRegex(extractor.pattern)
    return block.lines.flatMap((line, index) => regex.test(line)
      ? [{ rawValue: extractor.value ?? line.trim(), line, lineOffset: index }]
      : [])
  }
  if (extractor.type === 'CONFIG_STANZA') {
    const stanzas = block.parsed?.configStanzas ?? []
    return stanzas.map((stanza) => ({
      rawValue: { header: stanza.header, startLine: stanza.startLine, endLine: stanza.endLine, lines: stanza.lines },
      line: stanza.lines.join('\n'),
      lineOffset: Math.max(0, stanza.startLine - (block.contentStartLine ?? stanza.startLine)),
      lineEndOffset: Math.max(0, stanza.endLine - (block.contentStartLine ?? stanza.endLine))
    }))
  }
  if (extractor.type === 'TABLE') {
    const tables = block.parsed?.tables ?? []
    return tables.flatMap((table) => table.rows.map((row) => ({
      rawValue: row.values,
      line: JSON.stringify(row.values),
      lineOffset: Math.max(0, row.lineNumber - (block.contentStartLine ?? row.lineNumber))
    })))
  }
  return []
}

export function extractFacts(block, matchedRules) {
  const facts = []
  const warnings = []
  for (const rule of matchedRules) {
    for (const extractor of rule.extractors) {
      for (const candidate of extractCandidates(block, extractor)) {
        const sanitized = sanitizeSensitiveValue(candidate.rawValue)
        if (typeof candidate.rawValue === 'string' && sanitized.redacted) {
          warnings.push({ ruleId: rule.ruleId, code: 'REDACTED_VALUE', lineNumber: (block.contentStartLine ?? 0) + candidate.lineOffset })
          continue
        }
        try {
          const value = transformValue(sanitized.value, extractor.transforms)
          if (sanitized.redacted) {
            warnings.push({ ruleId: rule.ruleId, code: 'REDACTED_VALUE', lineNumber: (block.contentStartLine ?? 0) + candidate.lineOffset })
          }
          facts.push({
            semanticKey: rule.target.semanticKey,
            value,
            rawValue: sanitized.value,
            dataType: rule.target.dataType,
            unit: rule.target.unit ?? null,
            status: sanitized.redacted ? 'PARTIAL' : 'OBSERVED',
            confidence: rule.confidence ?? 1,
            cardinality: rule.target.cardinality,
            conflictPolicy: rule.target.conflictPolicy,
            source: sourceEvidence(block, candidate.lineOffset, candidate.line, candidate.lineEndOffset),
            ruleId: rule.ruleId
          })
        } catch (error) {
          warnings.push({ ruleId: rule.ruleId, code: 'INVALID_VALUE', message: error.message, lineNumber: (block.contentStartLine ?? 0) + candidate.lineOffset })
        }
      }
    }
  }
  return { facts, warnings }
}
