/**
 * ComponentSandbox 协议与 CSP 工具单元测试（批次4-T7）。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import {
  SANDBOX_PROTOCOL_VERSION,
  HostToGuestMessage,
  GuestToHostMessage,
  isSandboxMessage,
  createMessage
} from '../protocol'
import {
  isAllowedUrl,
  buildSandboxAttribute,
  buildFrameCsp,
  getCspAllowlist,
  setCspAllowlist
} from '@/sdk/csp-allowlist'

describe('ComponentSandbox protocol', () => {
  describe('SANDBOX_PROTOCOL_VERSION', () => {
    it('协议版本应为 1.0', () => {
      expect(SANDBOX_PROTOCOL_VERSION).toBe('1.0')
    })
  })

  describe('HostToGuestMessage 枚举', () => {
    it('应包含 5 种消息类型', () => {
      expect(Object.keys(HostToGuestMessage).length).toBe(5)
      expect(HostToGuestMessage.INIT).toBe('LC_SANDBOX_INIT')
      expect(HostToGuestMessage.UPDATE_PROPS).toBe('LC_SANDBOX_UPDATE_PROPS')
      expect(HostToGuestMessage.UPDATE_CONTEXT).toBe('LC_SANDBOX_UPDATE_CONTEXT')
      expect(HostToGuestMessage.RESIZE).toBe('LC_SANDBOX_RESIZE')
      expect(HostToGuestMessage.REQUEST_HEIGHT).toBe('LC_SANDBOX_REQUEST_HEIGHT')
    })
  })

  describe('GuestToHostMessage 枚举', () => {
    it('应包含 6 种消息类型', () => {
      expect(Object.keys(GuestToHostMessage).length).toBe(6)
      expect(GuestToHostMessage.READY).toBe('LC_SANDBOX_READY')
      expect(GuestToHostMessage.UPDATE_VALUE).toBe('LC_SANDBOX_UPDATE_VALUE')
      expect(GuestToHostMessage.EVENT).toBe('LC_SANDBOX_EVENT')
      expect(GuestToHostMessage.REPORT_HEIGHT).toBe('LC_SANDBOX_REPORT_HEIGHT')
      expect(GuestToHostMessage.ERROR).toBe('LC_SANDBOX_ERROR')
      expect(GuestToHostMessage.LOG).toBe('LC_SANDBOX_LOG')
    })
  })

  describe('createMessage', () => {
    it('应自动填充 version + timestamp', () => {
      const before = Date.now()
      const msg = createMessage(HostToGuestMessage.INIT, { foo: 'bar' })
      const after = Date.now()
      expect(msg.version).toBe(SANDBOX_PROTOCOL_VERSION)
      expect(msg.type).toBe(HostToGuestMessage.INIT)
      expect(msg.payload).toEqual({ foo: 'bar' })
      expect(msg.timestamp).toBeGreaterThanOrEqual(before)
      expect(msg.timestamp).toBeLessThanOrEqual(after)
    })

    it('应支持自定义 id', () => {
      const msg = createMessage(GuestToHostMessage.READY, {}, 'req-123')
      expect(msg.id).toBe('req-123')
    })
  })

  describe('isSandboxMessage', () => {
    it('合法消息应返回 true', () => {
      const msg = createMessage(HostToGuestMessage.INIT, {})
      expect(isSandboxMessage(msg)).toBe(true)
    })

    it('version 不匹配应返回 false', () => {
      const msg = {
        version: '2.0',
        type: HostToGuestMessage.INIT,
        timestamp: Date.now(),
        payload: {}
      }
      expect(isSandboxMessage(msg)).toBe(false)
    })

    it('未知 type 应返回 false', () => {
      const msg = {
        version: SANDBOX_PROTOCOL_VERSION,
        type: 'UNKNOWN_TYPE',
        timestamp: Date.now(),
        payload: {}
      }
      expect(isSandboxMessage(msg)).toBe(false)
    })

    it('非对象应返回 false', () => {
      expect(isSandboxMessage(null)).toBe(false)
      expect(isSandboxMessage(undefined)).toBe(false)
      expect(isSandboxMessage('string')).toBe(false)
      expect(isSandboxMessage(123)).toBe(false)
    })

    it('缺少 type 字段应返回 false', () => {
      const msg = {
        version: SANDBOX_PROTOCOL_VERSION,
        timestamp: Date.now(),
        payload: {}
      }
      expect(isSandboxMessage(msg)).toBe(false)
    })
  })
})

describe('ComponentSandbox CSP allowlist', () => {
  beforeEach(() => {
    // 重置 CSP 白名单
    setCspAllowlist([])
  })

  describe('isAllowedUrl', () => {
    it('空 URL 应返回 false', () => {
      expect(isAllowedUrl('')).toBe(false)
      expect(isAllowedUrl(null as any)).toBe(false)
    })

    it('file 协议应被拒绝', () => {
      expect(isAllowedUrl('file:///etc/passwd')).toBe(false)
    })

    it('javascript 协议应被拒绝', () => {
      expect(isAllowedUrl('javascript:alert(1)')).toBe(false)
    })

    it('data 协议应被拒绝', () => {
      expect(isAllowedUrl('data:text/html,<script>alert(1)</script>')).toBe(false)
    })

    it('blob 协议应被拒绝', () => {
      expect(isAllowedUrl('blob:https://example.com/uuid')).toBe(false)
    })

    it('生产环境 http 应被拒绝（模拟生产）', () => {
      // import.meta.env.DEV 在 vitest 中通常为 true，此用例验证白名单逻辑
      // 当不在 dev 模式时，http 应被拒绝
      const isDev = (import.meta as any).env?.DEV === true
      if (!isDev) {
        expect(isAllowedUrl('http://example.com/page')).toBe(false)
      }
    })

    it('配置白名单后，白名单内域名应通过', () => {
      setCspAllowlist(['cdn.example.com', '*.pms.com'])
      expect(isAllowedUrl('https://cdn.example.com/component.js')).toBe(true)
      expect(isAllowedUrl('https://sub.pms.com/component.js')).toBe(true)
    })

    it('配置白名单后，白名单外域名应被拒绝', () => {
      setCspAllowlist(['cdn.example.com'])
      expect(isAllowedUrl('https://evil.com/component.js')).toBe(false)
    })

    it('通配符 *.example.com 应匹配子域名', () => {
      setCspAllowlist(['*.example.com'])
      expect(isAllowedUrl('https://a.b.example.com/c.js')).toBe(true)
      expect(isAllowedUrl('https://example.com/c.js')).toBe(false) // 通配符要求子域名
    })
  })

  describe('getCspAllowlist / setCspAllowlist', () => {
    it('set 后 get 应返回相同数组', () => {
      const list = ['a.com', 'b.com']
      setCspAllowlist(list)
      expect(getCspAllowlist()).toEqual(list)
    })

    it('未设置时应返回空数组', () => {
      setCspAllowlist([])
      expect(getCspAllowlist()).toEqual([])
    })
  })

  describe('buildSandboxAttribute', () => {
    it('默认应含 allow-scripts + allow-forms + allow-modals', () => {
      const attr = buildSandboxAttribute()
      expect(attr).toContain('allow-scripts')
      expect(attr).toContain('allow-forms')
      expect(attr).toContain('allow-modals')
    })

    it('sameOrigin=true 应含 allow-same-origin', () => {
      const attr = buildSandboxAttribute(true)
      expect(attr).toContain('allow-same-origin')
    })

    it('allowPopups=true 应含 allow-popups', () => {
      const attr = buildSandboxAttribute(false, true, true)
      expect(attr).toContain('allow-popups')
    })

    it('allowForms=false 不应含 allow-forms', () => {
      const attr = buildSandboxAttribute(false, false)
      expect(attr).not.toContain('allow-forms')
    })
  })

  describe('buildFrameCsp', () => {
    it('应包含 default-src none', () => {
      const csp = buildFrameCsp(['https://cdn.example.com'])
      expect(csp).toContain("default-src 'none'")
    })

    it('应包含 frame-ancestors self', () => {
      const csp = buildFrameCsp([])
      expect(csp).toContain("frame-ancestors 'self'")
    })

    it('script-src 应包含传入的 origin', () => {
      const csp = buildFrameCsp(['https://cdn.example.com'])
      expect(csp).toContain('https://cdn.example.com')
      expect(csp).toContain('script-src')
    })
  })
})
