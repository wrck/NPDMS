import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buildCustomerPublicUrl, getCustomerPublicBase } from './customerPublicBase'
const config = vi.hoisted(() => ({ getConfigKey: vi.fn() }))
vi.mock('@/api/infra/config', () => config)
beforeEach(() => vi.resetAllMocks())
describe('客户外发地址配置', () => {
  it('读取数据库配置并保留路径前缀及租户参数', async () => {
    config.getConfigKey.mockResolvedValue('https://client.example.com/pms/')
    const base = await getCustomerPublicBase()
    expect(config.getConfigKey).toHaveBeenCalledWith('pms.customer.public-base-url')
    expect(buildCustomerPublicUrl(base, '/training-confirm/token', 1)).toBe('https://client.example.com/pms/training-confirm/token?tenantId=1')
  })
  it.each(['', 'javascript:alert(1)', 'https://user:pass@example.com', 'https://example.com/?x=1'])('拒绝未配置或不安全地址 %s', async value => {
    config.getConfigKey.mockResolvedValue(value)
    await expect(getCustomerPublicBase()).rejects.toThrow()
  })
  it('拒绝跨域形式的签字路径', () => {
    expect(() => buildCustomerPublicUrl('https://client.example.com', '//other.example/token', 1)).toThrow()
  })
})
