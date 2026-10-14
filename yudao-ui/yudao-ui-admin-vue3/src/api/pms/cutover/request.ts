import request from '@/config/axios'
import type { AxiosRequestConfig } from 'axios'

// 复用平台拦截器中的 Token、租户、错误处理；割接使用正式 Business API 物理路径。
type CutoverRequest = AxiosRequestConfig & { silentError?: boolean }
const options = (value: CutoverRequest) => ({ ...value, baseURL: import.meta.env.VITE_BASE_URL || '/' })

export const baseUrl = '/api/v1/pms/cutover-tasks'
export default {
  get: <T = any>(value: CutoverRequest) => request.get<T>(options(value)),
  post: <T = any>(value: CutoverRequest) => request.post<T>(options(value)),
  put: <T = any>(value: CutoverRequest) => request.put<T>(options(value)),
  delete: <T = any>(value: CutoverRequest) => request.delete<T>(options(value)),
  download: <T = any>(value: CutoverRequest) => request.download<T>(options(value))
}
