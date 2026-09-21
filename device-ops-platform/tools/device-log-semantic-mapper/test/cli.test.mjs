import assert from 'node:assert/strict'
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { generateSemanticArtifacts } from '../src/cli.mjs'

const fixturePath = fileURLToPath(new URL('./fixtures/structured-log-sample.json', import.meta.url))
const artifactNames = [
  'canonical-device-schema.json', 'show-tech.semantic-map.json', 'show-tech.device-snapshot.json',
  'projection-profiles.json', 'mapping-quality-report.json', 'README.md'
]

test('generates six parseable, secret-safe semantic artifacts', () => {
  const root = mkdtempSync(join(tmpdir(), 'device-log-semantic-'))
  const output = join(root, 'output')
  try {
    const result = generateSemanticArtifacts({ input: fixturePath, output })
    for (const name of artifactNames) assert.equal(existsSync(join(output, name)), true, name)
    for (const name of artifactNames.filter((name) => name.endsWith('.json'))) {
      assert.doesNotThrow(() => JSON.parse(readFileSync(join(output, name), 'utf8')), name)
    }
    assert.equal(result.semanticMap.observations.length, 4)
    assert.equal(result.projections.results.deviceBasic.sn, 'SYNTHETIC-SN-001')
    assert.equal(result.quality.plaintextSecretFindingCount, 0)
    assert.equal(artifactNames.map((name) => readFileSync(join(output, name), 'utf8')).join('\n').includes('fixture-secret-password'), false)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('adds every produced semantic key to the generated schema dictionary', () => {
  const root = mkdtempSync(join(tmpdir(), 'device-log-semantic-'))
  try {
    const result = generateSemanticArtifacts({ input: fixturePath, output: join(root, 'output') })
    const facts = []
    const visit = (value) => {
      if (!value || typeof value !== 'object') return
      if (value.semanticKey) facts.push(value)
      else if (Array.isArray(value)) value.forEach(visit)
      else Object.values(value).forEach(visit)
    }
    visit(result.snapshot.entities)
    for (const fact of facts) assert.ok(result.schema['x-semanticKeys'][fact.semanticKey], fact.semanticKey)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})

test('redacts generic secret assignments in evidence', async () => {
  const { redactSensitiveText } = await import('../src/rule-engine.mjs')
  const redacted = redactSensitiveText('password: fixture-secret-password\nuser operator password abc123\nprivate key = key-value\nstatus: enabled')
  assert.equal(redacted.includes('fixture-secret-password'), false)
  assert.equal(redacted.includes('abc123'), false)
  assert.equal(redacted.includes('key-value'), false)
  assert.match(redacted, /password: \[REDACTED\]/i)
})
