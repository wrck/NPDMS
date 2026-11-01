import { getConfigKey } from '@/api/infra/config'

export const CUSTOMER_PUBLIC_BASE_KEY = 'pms.customer.public-base-url'

export async function getCustomerPublicBase(): Promise<string> {
  const value = await getConfigKey(CUSTOMER_PUBLIC_BASE_KEY)
  if (typeof value !== 'string' || !value.trim()) throw new Error('请先在配置管理中设置客户外部访问地址')
  try {
    const url = new URL(value.trim())
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash)
      throw new Error('invalid')
    return url.href.replace(/\/+$/, '')
  } catch {
    throw new Error('客户外部访问地址无效，请在配置管理中设置有效的 HTTP(S) 地址')
  }
}

export function buildCustomerPublicUrl(base: string, signPath: string, tenantId: string | number): string {
  if (!signPath.startsWith('/') || signPath.startsWith('//')) throw new Error('客户确认链接无效')
  const url = new URL(base + signPath)
  url.searchParams.set('tenantId', String(tenantId))
  return url.href
}
