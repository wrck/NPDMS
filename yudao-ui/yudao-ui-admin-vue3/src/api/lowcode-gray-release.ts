// src/api/lowcode-gray-release.ts
import { get, post } from '@/utils/request'

export interface LowCodeGrayRelease {
  id: number
  configType: string
  configId: number
  configCode?: string
  version: number
  publishRecordId?: number
  grayPercentage: number
  tenantWhitelist?: string
  status: 'GRAYING' | 'FULL' | 'ROLLED_BACK'
  grayStartedAt?: string
  fullReleasedAt?: string
  rolledBackAt?: string
  createBy?: string
  createTime?: string
  updateTime?: string
}

export function createGrayRelease(params: {
  publishRecordId: number
  grayPercentage: number
  tenantWhitelist?: string
  createBy?: string
}) {
  return post<LowCodeGrayRelease>('/api/lowcode/gray-release', params)
}

export function updateGrayPercentage(id: number, newPercentage: number) {
  return post<LowCodeGrayRelease>(`/api/lowcode/gray-release/${id}/percentage`, null, {
    params: { newPercentage }
  })
}

export function releaseFull(id: number) {
  return post<LowCodeGrayRelease>(`/api/lowcode/gray-release/${id}/full`)
}

export function rollbackGray(id: number) {
  return post<LowCodeGrayRelease>(`/api/lowcode/gray-release/${id}/rollback`)
}

export function listGrayReleases(configType: string, configId: number) {
  return get<LowCodeGrayRelease[]>('/api/lowcode/gray-release', { configType, configId })
}

export function getActiveGrayRelease(configType: string, configId: number) {
  return get<LowCodeGrayRelease | null>('/api/lowcode/gray-release/active', { configType, configId })
}

export function checkGray(params: {
  configType: string
  configId: number
  userId?: number
  tenantId?: string
}) {
  return get<boolean>('/api/lowcode/gray-release/check', params)
}
