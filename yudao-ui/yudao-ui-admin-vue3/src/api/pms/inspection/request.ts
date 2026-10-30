import request from '@/config/axios'
import type { AxiosRequestConfig } from 'axios'

type InspectionRequest = AxiosRequestConfig & { silentError?: boolean }
const options = (value: InspectionRequest) => ({ ...value, baseURL: import.meta.env.VITE_BASE_URL || '/' })

// 共用平台身份、租户和错误拦截器，Business API 使用宿主根路径。
export default {
  get: <T = any>(value: InspectionRequest) => request.get<T>(options(value)),
  post: <T = any>(value: InspectionRequest) => request.post<T>(options(value)),
  put: <T = any>(value: InspectionRequest) => request.put<T>(options(value)),
  download: <T = any>(value: InspectionRequest) => request.download<T>(options(value))
}

export type WireLong = string | number
export const writeHeaders = (key: string, version?: WireLong) => ({
  'Idempotency-Key': key,
  ...(version == null ? {} : { 'If-Match': String(version) })
})
