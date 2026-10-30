import { expect, it } from 'vitest'
import { redactRequestSnapshot } from './transient-credentials'
import type { SubmitGenericCollectionRequest } from '@/types/collection'
it('omits opaque secret-bearing fields from local snapshots without mutating the submitted body', () => {
  const body = { namespace: 'ns', callbackUrl: 'https://secret@example.test', context: { extensions: { token: 'secret' }, device: { deviceKey: 'safe' } }, connection: { host: 'safe-host', password: 'secret', privateKey: 'secret', passphrase: 'secret' }, script: { content: 'show frozen', parserConfig: 'secret', key: 'safe-key' } } as unknown as SubmitGenericCollectionRequest
  const snapshot = redactRequestSnapshot(body)
  expect(JSON.stringify(snapshot)).not.toContain('secret')
  expect(snapshot).toMatchObject({ namespace: 'ns', connection: { host: 'safe-host' }, script: { key: 'safe-key', content: 'show frozen' } })
  expect(body.connection).toHaveProperty('password', 'secret')
  expect(body.script.content).toBe('show frozen')
})
