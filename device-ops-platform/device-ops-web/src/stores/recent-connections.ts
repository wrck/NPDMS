import type { ConnectionProtocol } from '@/types/collection'

export const RECENT_CONNECTIONS_KEY = 'device-ops:recent-connections:v1'

export interface RecentConnection {
  protocol: ConnectionProtocol
  host: string
  port: number
  username: string
  hostKeyFingerprint?: string
  deviceLabel?: string
  usedAt: string
}

function identity(item: RecentConnection): string {
  return [item.protocol, item.host.trim().toLowerCase(), item.port, item.username.trim()].join('|')
}

function isRecentConnection(value: unknown): value is RecentConnection {
  if (!value || typeof value !== 'object') return false
  const item = value as Partial<RecentConnection>
  return (
    (item.protocol === 'SSH2' || item.protocol === 'TELNET') &&
    typeof item.host === 'string' &&
    Number.isInteger(item.port) &&
    typeof item.username === 'string' &&
    typeof item.usedAt === 'string'
  )
}

export function loadRecentConnections(): RecentConnection[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(RECENT_CONNECTIONS_KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter(isRecentConnection).slice(0, 20) : []
  } catch {
    return []
  }
}

function persist(items: RecentConnection[]): RecentConnection[] {
  const safeItems = items.slice(0, 20).map((item) => ({
    protocol: item.protocol,
    host: item.host,
    port: item.port,
    username: item.username,
    ...(item.hostKeyFingerprint ? { hostKeyFingerprint: item.hostKeyFingerprint } : {}),
    ...(item.deviceLabel ? { deviceLabel: item.deviceLabel } : {}),
    usedAt: item.usedAt
  }))
  localStorage.setItem(RECENT_CONNECTIONS_KEY, JSON.stringify(safeItems))
  return safeItems
}

export function rememberRecentConnection(
  current: RecentConnection,
  items = loadRecentConnections()
): RecentConnection[] {
  const key = identity(current)
  return persist([current, ...items.filter((item) => identity(item) !== key)])
}

export function removeRecentConnection(
  current: RecentConnection,
  items = loadRecentConnections()
): RecentConnection[] {
  const key = identity(current)
  return persist(items.filter((item) => identity(item) !== key))
}

export function clearRecentConnections(): RecentConnection[] {
  localStorage.removeItem(RECENT_CONNECTIONS_KEY)
  return []
}
