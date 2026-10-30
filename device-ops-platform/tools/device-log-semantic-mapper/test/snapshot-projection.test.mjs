import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { validateProjectionProfiles } from '../src/contracts.mjs'
import { applyProjection, applyProjectionProfiles } from '../src/projection-engine.mjs'
import { assembleSnapshot } from '../src/snapshot-assembler.mjs'

const profileCatalog = JSON.parse(readFileSync(fileURLToPath(new URL('../projections/projection-profiles.json', import.meta.url)), 'utf8'))

function fact(semanticKey, value, overrides = {}) {
  return {
    semanticKey,
    value,
    rawValue: String(value),
    dataType: typeof value === 'number' ? 'integer' : 'string',
    unit: null,
    status: 'OBSERVED',
    confidence: 0.9,
    cardinality: 'ONE',
    conflictPolicy: 'HIGHEST_CONFIDENCE',
    source: { blockIndex: 1, command: 'version', lineStart: 10, lineEnd: 10, rawText: String(value) },
    ruleId: `rule-${semanticKey}`,
    ...overrides
  }
}

test('publishes valid projection profiles', () => {
  assert.equal(validateProjectionProfiles(profileCatalog), profileCatalog)
})

test('assembles evidence-backed facts and quick projections', () => {
  const snapshot = assembleSnapshot(
    { collectionId: 'collection-1' },
    [],
    [
      fact('device.identity.serialNumber', 'SN-001'),
      fact('device.software.version', 'V1'),
      fact('device.hardware.memoryBytes', 8589934592)
    ]
  )
  const projections = applyProjectionProfiles(snapshot, profileCatalog.profiles)

  assert.equal(snapshot.entities.device.identity.serialNumber.value, 'SN-001')
  assert.equal(snapshot.entities.device.identity.serialNumber.source.lineStart, 10)
  assert.deepEqual(projections.deviceBasic, {
    sn: 'SN-001', softVersion: 'V1', platformVersion: null, cpuModel: null, memoryBytes: 8589934592
  })
})

test('resolves one-value conflicts by confidence then later source line', () => {
  const snapshot = assembleSnapshot({}, [], [
    fact('device.software.version', 'old', { confidence: 0.8, source: { blockIndex: 1, command: 'a', lineStart: 2, lineEnd: 2, rawText: 'old' } }),
    fact('device.software.version', 'high', { confidence: 0.95, source: { blockIndex: 2, command: 'b', lineStart: 3, lineEnd: 3, rawText: 'high' } }),
    fact('device.software.version', 'latest', { confidence: 0.95, source: { blockIndex: 3, command: 'c', lineStart: 4, lineEnd: 4, rawText: 'latest' } })
  ])

  assert.equal(snapshot.entities.device.software.version.value, 'latest')
  assert.equal(snapshot.quality.conflicts.length, 2)
})

test('keeps distinct many-values in source order', () => {
  const snapshot = assembleSnapshot({}, [], [
    fact('configuration.stanzas', { header: 'interface x' }, { cardinality: 'MANY', conflictPolicy: 'APPEND_DISTINCT' }),
    fact('configuration.stanzas', { header: 'interface x' }, { cardinality: 'MANY', conflictPolicy: 'APPEND_DISTINCT' }),
    fact('configuration.stanzas', { header: 'interface y' }, { cardinality: 'MANY', conflictPolicy: 'APPEND_DISTINCT' })
  ])
  assert.deepEqual(snapshot.entities.configuration.stanzas.map((item) => item.value.header), ['interface x', 'interface y'])
})

test('supports array entity roots such as interfaces', () => {
  const snapshot = assembleSnapshot({}, [], [
    fact('interfaces', { name: 'ge0', address: '192.0.2.1' }, { cardinality: 'MANY', conflictPolicy: 'APPEND_DISTINCT' })
  ])
  assert.equal(snapshot.entities.interfaces[0].value.name, 'ge0')
})

test('rejects unsafe semantic and projection paths', () => {
  assert.throws(() => assembleSnapshot({}, [], [fact('device.__proto__.polluted', 'yes')]), /unsafe semantic path/)
  assert.throws(() => applyProjection(assembleSnapshot({}, [], []), {
    projectionId: 'unsafe', fields: { value: 'device.constructor.name' }, missingValuePolicy: 'NULL'
  }), /unsafe projection path/)
  assert.equal({}.polluted, undefined)
})
