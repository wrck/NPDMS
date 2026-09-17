import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const session = vi.hoisted(() => ({
  getAccessToken: vi.fn(),
  getTenantId: vi.fn(),
  getVisitTenantId: vi.fn(),
  getLegacyToken: vi.fn()
}))
vi.mock('@/utils/auth', () => ({
  getAccessToken: session.getAccessToken,
  getTenantId: session.getTenantId,
  getVisitTenantId: session.getVisitTenantId
}))
import { lowcodeSessionHeaders } from './lowcodeSession'

beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('localStorage', { getItem: session.getLegacyToken })
  vi.stubEnv('VITE_APP_TENANT_ENABLE', 'true')
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('migrated lowcode session headers', () => {
  it('prefers the current NPDMS login over a stale legacy token', () => {
    session.getAccessToken.mockReturnValue('current-session')
    session.getLegacyToken.mockReturnValue('legacy-session')
    session.getTenantId.mockReturnValue(7)
    session.getVisitTenantId.mockReturnValue(8)
    expect(lowcodeSessionHeaders()).toEqual({
      Authorization: 'Bearer current-session', 'tenant-id': '7', 'visit-tenant-id': '8'
    })
    expect(session.getLegacyToken).not.toHaveBeenCalled()
  })

  it('retains legacy login compatibility when no current session exists', () => {
    session.getLegacyToken.mockReturnValue('legacy-session')
    expect(lowcodeSessionHeaders()).toEqual({ Authorization: 'Bearer legacy-session' })
    expect(session.getLegacyToken).toHaveBeenCalledWith('pms_token')
  })

  it('does not manufacture an Authorization or visit-tenant header without a login', () => {
    session.getTenantId.mockReturnValue(7)
    session.getVisitTenantId.mockReturnValue(8)
    expect(lowcodeSessionHeaders()).toEqual({ 'tenant-id': '7' })
  })

  it('preserves tenant zero rather than dropping it as a falsy value', () => {
    session.getAccessToken.mockReturnValue('current-session')
    session.getTenantId.mockReturnValue(0)
    expect(lowcodeSessionHeaders()).toEqual({ Authorization: 'Bearer current-session', 'tenant-id': '0' })
  })

  it('honors the existing tenant configuration without losing authentication', () => {
    vi.stubEnv('VITE_APP_TENANT_ENABLE', 'false')
    session.getAccessToken.mockReturnValue('current-session')
    session.getTenantId.mockReturnValue(7)
    session.getVisitTenantId.mockReturnValue(8)
    expect(lowcodeSessionHeaders()).toEqual({ Authorization: 'Bearer current-session' })
  })
})
