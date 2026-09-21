import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { validateRules } from '../src/contracts.mjs'
import { extractFacts, inferBlockMeaning } from '../src/rule-engine.mjs'

const target = (semanticKey, dataType = 'string') => ({
  semanticKey, dataType, cardinality: 'ONE', conflictPolicy: 'HIGHEST_CONFIDENCE'
})
const selectors = [
  { type: 'COMMAND_ALIAS', value: 'show version' },
  { type: 'CONTENT_REGEX', value: '^\\s*Software Release\\s+' }
]
const versionRules = [
  { ruleId: 'software', blockRole: 'DEVICE_VERSION', roleSelectors: selectors, extractors: [{ type: 'LINE_REGEX', pattern: '^\\s*Software Release\\s+(.+)$', transforms: ['trim'] }], target: target('device.software.version') },
  { ruleId: 'memory', blockRole: 'DEVICE_VERSION', roleSelectors: selectors, extractors: [{ type: 'KEY_VALUE', key: 'SDRAM', transforms: ['trim', 'capacity-to-bytes'] }], target: target('device.hardware.memoryBytes', 'integer') },
  { ruleId: 'uptime', blockRole: 'DEVICE_VERSION', roleSelectors: selectors, extractors: [{ type: 'LINE_REGEX', pattern: 'Uptime is (.+)$', transforms: ['duration-to-seconds'] }], target: target('device.identity.uptimeSeconds', 'integer') }
]

function block(command, lines, extra = {}) {
  return { blockIndex: 1, command, headerLine: 1, contentStartLine: 2, contentEndLine: 1 + lines.length, isEmpty: lines.length === 0, lines, ...extra }
}

test('publishes a valid catalog covering all approved block roles', () => {
  const catalog = JSON.parse(readFileSync(fileURLToPath(new URL('../rules/show-tech-semantic-rules.json', import.meta.url)), 'utf8'))
  validateRules(catalog)
  const roles = new Set(catalog.rules.map((rule) => rule.blockRole))
  assert.equal(roles.size, 50)
  for (const rule of catalog.rules) {
    assert.ok(rule.roleSelectors.some((selector) => ['CONTENT_REGEX', 'TABLE_HEADERS'].includes(selector.type)), rule.ruleId)
  }
})

test('infers a version role from content when the command is renamed', () => {
  const value = block('collect system facts', ['Software Release TEST-1'])
  const observation = inferBlockMeaning(value, versionRules)
  assert.equal(observation.blockRole, 'DEVICE_VERSION')
  assert.equal(observation.status, 'OBSERVED')
  assert.ok(observation.confidence >= 0.8)
})

test('maps empty and explicitly disabled blocks to distinct states', () => {
  const bgpRule = [{ ruleId: 'bgp', blockRole: 'BGP_SUMMARY', roleSelectors: [{ type: 'COMMAND_ALIAS', value: 'show bgp' }], extractors: [], target: target('routing.protocols.bgp') }]
  assert.equal(inferBlockMeaning(block('show bgp', []), bgpRule).status, 'NO_DATA')
  const ntpRule = [{ ruleId: 'ntp', blockRole: 'NTP_STATUS', roleSelectors: [{ type: 'CONTENT_REGEX', value: 'NTP' }], extractors: [], target: target('device.management.ntp') }]
  assert.equal(inferBlockMeaning(block('status', ['NTP is not enabled.']), ntpRule).status, 'NOT_ENABLED')
})

test('converts capacity and additive duration values', () => {
  const value = block('show version', [
    'Software Release TEST-1',
    'SDRAM: 8192M bytes',
    'Device Uptime is 1 week(s), 2 day(s), 3 hour(s), 4 minute(s)'
  ])
  const result = extractFacts(value, versionRules)
  assert.equal(result.facts.find((fact) => fact.semanticKey.endsWith('memoryBytes')).value, 8589934592)
  assert.equal(result.facts.find((fact) => fact.semanticKey.endsWith('uptimeSeconds')).value, 788640)
})

test('does not emit redacted values and reports invalid conversions', () => {
  const secretRule = [{ ruleId: 'secret', blockRole: 'LOCAL_USER', roleSelectors: [], extractors: [{ type: 'LINE_REGEX', pattern: '^Password: (.+)$', transforms: ['trim'] }], target: target('security.users.password') }]
  const redacted = extractFacts(block('users', ['Password: [REDACTED]']), secretRule)
  assert.equal(redacted.facts.length, 0)
  assert.equal(redacted.warnings[0].code, 'REDACTED_VALUE')
  const invalid = extractFacts(block('show version', ['Software Release TEST', 'SDRAM: unknown']), versionRules)
  assert.equal(invalid.warnings[0].code, 'INVALID_VALUE')
})

test('redacts secrets nested inside configuration stanza objects', () => {
  const configRule = [{
    ruleId: 'config', blockRole: 'RUNNING_CONFIG', roleSelectors: [], extractors: [{ type: 'CONFIG_STANZA' }],
    target: { ...target('configuration.stanzas', 'object'), cardinality: 'MANY', conflictPolicy: 'APPEND_DISTINCT' }
  }]
  const value = block('running configuration', ['user operator password plaintext-value'], {
    parsed: { configStanzas: [{ header: 'user operator', startLine: 2, endLine: 2, lines: ['user operator password plaintext-value'] }] }
  })
  const result = extractFacts(value, configRule)
  assert.equal(result.facts.length, 1)
  assert.equal(result.facts[0].status, 'PARTIAL')
  assert.equal(result.facts[0].source.lineEnd, 2)
  assert.equal(JSON.stringify(result.facts[0]).includes('plaintext-value'), false)
  assert.equal(result.warnings[0].code, 'REDACTED_VALUE')
})

test('keeps unmatched text unparsed and flags source corruption', () => {
  assert.equal(inferBlockMeaning(block('unknown', ['opaque output']), versionRules).status, 'UNPARSED')
  assert.equal(inferBlockMeaning(block('show version', ['Software Release TE�ST']), versionRules).status, 'SOURCE_CORRUPTED')
})

test('does not choose alphabetically when generic content roles are ambiguous', () => {
  const ambiguousRules = [
    { ruleId: 'source', blockRole: 'SESSION_TOP_SOURCE', roleSelectors: [{ type: 'CONTENT_REGEX', value: 'Session Number' }], extractors: [], target: target('sessions.topSources') },
    { ruleId: 'destination', blockRole: 'SESSION_TOP_DESTINATION', roleSelectors: [{ type: 'CONTENT_REGEX', value: 'Session Number' }], extractors: [], target: target('sessions.topDestinations') }
  ]
  const observation = inferBlockMeaning(block('collect top sessions', ['IP Address: 192.0.2.1 Session Number: 10']), ambiguousRules)
  assert.equal(observation.blockRole, null)
  assert.equal(observation.status, 'UNPARSED')
  assert.deepEqual(observation.candidateBlockRoles.map((item) => item.blockRole).sort(), ['SESSION_TOP_DESTINATION', 'SESSION_TOP_SOURCE'])
})
