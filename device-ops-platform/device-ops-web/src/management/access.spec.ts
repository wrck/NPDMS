import { describe, expect, it, vi } from 'vitest'
vi.mock('@/config/runtime', () => ({ loadRuntimeConfig: vi.fn() }))
vi.mock('@/auth/oidc', () => ({ getUserManager: vi.fn() }))
import { loadRuntimeConfig } from '@/config/runtime'
import { getUserManager } from '@/auth/oidc'
import { loadScopes } from './access'
describe('management scope hints', () => {
  it('uses granted OIDC scope rather than requested config scope', async () => {
    vi.mocked(loadRuntimeConfig).mockResolvedValue({ authMode: 'oauth2', oidcScope: 'parser:release:write' } as never)
    vi.mocked(getUserManager).mockResolvedValue({ getUser: async () => ({ scope: 'parser:release:read' }) } as never)
    expect(await loadScopes()).toEqual(['parser:release:read'])
  })
})
