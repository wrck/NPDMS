import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import {
  assertObservationStatus,
  validateProjectionProfiles,
  validateRules,
  validateStructuredLog
} from '../src/contracts.mjs'

const fixture = JSON.parse(readFileSync(fileURLToPath(new URL('./fixtures/structured-log-sample.json', import.meta.url)), 'utf8'))

const schemas = [
  ['canonical-device-schema.json', 'urn:device-ops:canonical-device:1.0.0'],
  ['mapping-rule-schema.json', 'urn:device-ops:mapping-rule:1.0.0'],
  ['projection-profile-schema.json', 'urn:device-ops:projection-profile:1.0.0']
]

test('validates the synthetic structured log', () => {
  assert.equal(validateStructuredLog(fixture), fixture)
})

test('publishes parseable schemas with stable identifiers', () => {
  for (const [name, id] of schemas) {
    const schema = JSON.parse(readFileSync(fileURLToPath(new URL(`../schema/${name}`, import.meta.url)), 'utf8'))
    assert.equal(schema.$schema, 'https://json-schema.org/draft/2020-12/schema')
    assert.equal(schema.$id, id)
  }
})

test('reports the exact path for a missing command', () => {
  const invalid = structuredClone(fixture)
  delete invalid.stdout.blocks[0].command
  assert.throws(() => validateStructuredLog(invalid), /\$\.stdout\.blocks\[0\]\.command/)
})

test('rejects an invalid observation status', () => {
  assert.throws(() => assertObservationStatus('FAILED'), /\$\.status/)
})

test('rejects duplicate rule identifiers', () => {
  const rule = {
    ruleId: 'duplicate', blockRole: 'DEVICE_VERSION', roleSelectors: [{ type: 'COMMAND_ALIAS', value: 'version' }], extractors: [],
    target: { semanticKey: 'device.software.version', cardinality: 'ONE', dataType: 'string', conflictPolicy: 'HIGHEST_CONFIDENCE' }
  }
  assert.throws(() => validateRules({ schemaVersion: '1.0.0', rules: [rule, rule] }), /\.rules\[1\]\.ruleId/)
})

test('rejects invalid external selector and target definitions', () => {
  assert.throws(() => validateRules({ schemaVersion: '1.0.0', rules: [{
    ruleId: 'invalid', blockRole: 'DEVICE_VERSION', roleSelectors: [{ type: 'CONTENT_REGEX', value: '[' }], extractors: [],
    target: { semanticKey: 'device.software.version', cardinality: 'ONE', dataType: 'scalar', conflictPolicy: 'HIGHEST_CONFIDENCE' }
  }] }), /regular expression/)
})

test('rejects an unknown projection policy', () => {
  assert.throws(
    () => validateProjectionProfiles({ schemaVersion: '1.0.0', profiles: [{ projectionId: 'basic', fields: { sn: 'device.identity.serialNumber' }, missingValuePolicy: 'EMPTY' }] }),
    /missingValuePolicy/
  )
})
