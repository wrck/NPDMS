import { expect, it } from 'vitest'
import type { CollectionEvidence } from '@/types/collection-evidence'
import { projectRequestSnapshot } from './request-snapshot'

function evidence(source: string, historical = false): CollectionEvidence {
  return {
    metadata: { collectionId: 'winner', namespace: 'ns', createdAt: null },
    input: { source, key: 'frozen-key', version: 'v1', sha256: 'hash', policy: 'EXECUTION_ONLY', parserType: 'NONE', contentStatus: 'AVAILABLE', content: 'show frozen\n完整正文' },
    submission: { provenance: historical ? 'RECONSTRUCTED_FACTS' : 'CAPTURED_SUBMISSION', snapshot: historical ? null : { schemaVersion: 1, body: { script: { key: 'original-key', custom: 'keep' }, connection: { host: 'safe-host' } } }, omittedFields: ['body.script.content', 'body.script.parserConfig', 'body.connection.password', 'body.script.content.extra'] },
    executionFacts: { targets: [], semanticParsing: null }
  }
}

it.each(['EXTERNAL_DELIVERED', 'ADHOC_INLINE', 'LOCAL_MANAGED'])('inlines frozen AVAILABLE content for %s without mutating evidence', source => {
  const input = evidence(source)
  const before = structuredClone(input)
  const result = projectRequestSnapshot(input)
  expect(result.snapshot).toMatchObject({ schemaVersion: 1, body: { script: { key: 'original-key', custom: 'keep', content: input.input.content } } })
  expect(result.omittedFields).toEqual(input.submission.omittedFields.slice(1))
  expect(input).toEqual(before)
})
it.each(['EXTERNAL_DELIVERED', 'ADHOC_INLINE', 'LOCAL_MANAGED'])('projects historical %s evidence without fabricating a request body', source => {
  const input = evidence(source, true)
  const result = projectRequestSnapshot(input)
  expect(result.snapshot).toEqual({ provenance: 'RECONSTRUCTED_FACTS', metadata: input.metadata, script: { source, key: 'frozen-key', version: 'v1', sha256: 'hash', policy: 'EXECUTION_ONLY', parserType: 'NONE', content: input.input.content }, executionFacts: input.executionFacts })
  expect(result.snapshot).not.toHaveProperty('body')
  expect(result.omittedFields).toEqual(input.submission.omittedFields)
})
it.each([false, true])('preserves empty AVAILABLE and omits unavailable content (historical=%s)', historical => {
  const input = evidence('ADHOC_INLINE', historical)
  input.input.content = ''
  expect(JSON.stringify(projectRequestSnapshot(input).snapshot)).toContain('"content":""')
  input.input.contentStatus = 'UNAVAILABLE'
  input.input.content = 'SECRET_SENTINEL'
  const result = projectRequestSnapshot(input)
  expect(JSON.stringify(result)).not.toContain('SECRET_SENTINEL')
  expect(JSON.stringify(result.snapshot)).not.toContain('"content":')
  expect(result.omittedFields).toEqual(input.submission.omittedFields)
  input.input.contentStatus = 'AVAILABLE'
  input.input.content = null
  expect(JSON.stringify(projectRequestSnapshot(input).snapshot)).not.toContain('"content":')
})
it('does not expose content from a captured body when server input is unavailable', () => {
  const input = evidence('LOCAL_MANAGED')
  ;(input.submission.snapshot!.body as Record<string, unknown>).script = { content: 'SECRET_SENTINEL', key: 'keep' }
  input.input.contentStatus = 'UNAVAILABLE'
  expect(JSON.stringify(projectRequestSnapshot(input))).not.toContain('SECRET_SENTINEL')
})
it('does not fabricate a captured request when snapshot is missing', () => {
  const input = evidence('LOCAL_MANAGED')
  input.submission.snapshot = null
  expect(projectRequestSnapshot(input)).toEqual({ snapshot: null, omittedFields: input.submission.omittedFields })
})
