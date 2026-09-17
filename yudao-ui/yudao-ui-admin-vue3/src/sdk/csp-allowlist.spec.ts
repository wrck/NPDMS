import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { isAllowedUrl, setCspAllowlist } from './csp-allowlist'

beforeEach(() => {
  vi.stubGlobal('window', {})
  vi.stubEnv('DEV', true)
  setCspAllowlist(['cdn.example.com'])
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('remote component URL boundary', () => {
  it.each([
    'http://localhost.evil.com/widget.js',
    'https://localhost.evil.com/widget.js',
    'http://localhost@evil.com/widget.js',
    'https://localhost@evil.com/widget.js'
  ])('does not apply the localhost exception to %s', (url) => {
    expect(isAllowedUrl(url)).toBe(false)
  })

  it.each(['javascript:alert(1)', 'data:text/html,unsafe', 'file:///etc/passwd', 'blob:https://cdn.example.com/id', 'ftp://cdn.example.com/file', 'not-a-url', 'https://'])('rejects invalid or non-web URLs even without an allowlist: %s', (url) => {
    setCspAllowlist([])
    expect(isAllowedUrl(url)).toBe(false)
  })

  it('retains exact localhost development URLs with ports', () => {
    expect(isAllowedUrl('http://localhost:5173/widget.js')).toBe(true)
    expect(isAllowedUrl('https://localhost:5173/widget.js')).toBe(true)
    expect(isAllowedUrl('http://example.com/widget.js')).toBe(false)
  })

  it('retains HTTPS allowlist and wildcard subdomain behavior', () => {
    setCspAllowlist(['cdn.example.com', '*.widgets.example.com'])
    expect(isAllowedUrl('https://cdn.example.com/widget.js')).toBe(true)
    expect(isAllowedUrl('https://a.widgets.example.com/widget.js')).toBe(true)
    expect(isAllowedUrl('https://widgets.example.com/widget.js')).toBe(false)
    expect(isAllowedUrl('https://other.example.com/widget.js')).toBe(false)
  })

  it('does not carry development exceptions into production', () => {
    vi.stubEnv('DEV', false)
    expect(isAllowedUrl('http://localhost:5173/widget.js')).toBe(false)
    expect(isAllowedUrl('https://localhost:5173/widget.js')).toBe(false)
    expect(isAllowedUrl('https://cdn.example.com/widget.js')).toBe(true)
    setCspAllowlist([])
    expect(isAllowedUrl('https://public.example.com/widget.js')).toBe(true)
  })
})
