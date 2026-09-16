import { get, post } from '@/utils/request'

export interface EditLockInfo {
  configType: string
  configId: number
  userId?: number
  userName?: string
  acquiredAt?: string
  expireAt?: string
  acquired: boolean
  message?: string
}

export function acquireLock(configType: string, configId: number, userId: number, userName?: string) {
  return post<EditLockInfo>('/api/lowcode/edit-lock/acquire', null, { params: { configType, configId, userId, userName } })
}

export function renewLock(configType: string, configId: number, userId: number) {
  return post<EditLockInfo>('/api/lowcode/edit-lock/renew', null, { params: { configType, configId, userId } })
}

export function releaseLock(configType: string, configId: number, userId: number) {
  return post<void>('/api/lowcode/edit-lock/release', null, { params: { configType, configId, userId } })
}

export function getLock(configType: string, configId: number) {
  return get<EditLockInfo>('/api/lowcode/edit-lock', { configType, configId })
}
