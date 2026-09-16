import axios from 'axios'

const TOKEN_KEY = 'pms_token'

/** Excel 导入接口返回的统一错误项 */
export interface ExcelImportError {
  rowIndex: number
  rowData: string
  errorMessage: string
}

/** Excel 导入接口返回的统一结构 */
export interface ExcelImportResult<T = unknown> {
  successList: T[]
  errors: ExcelImportError[]
  successCount?: number
  errorCount?: number
}

/**
 * 通用的 Excel 文件下载函数：以 blob 形式请求并通过浏览器 a 标签触发下载。
 *
 * @param url     下载地址（相对路径，例如 /api/asset/template）
 * @param params  查询参数（可选）
 * @param fileName 下载到本地的文件名（含扩展名，例如 asset-template.xlsx）
 */
export async function downloadExcel(
  url: string,
  params?: Record<string, unknown>,
  fileName?: string
): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const response = await axios.get(url, {
    params,
    responseType: 'blob',
    headers: {
      Authorization: `Bearer ${token}`
    }
  })
  triggerBlobDownload(response.data, fileName ?? extractFileNameFromResponse(response.headers, url))
}

/**
 * 通用的 Excel 上传导入函数：以 FormData 形式上传文件并返回后端聚合的导入结果。
 *
 * @param url   上传地址（例如 /api/asset/import）
 * @param file  上传的 .xlsx 文件
 */
export async function uploadExcel<T = unknown>(
  url: string,
  file: File
): Promise<ExcelImportResult<T>> {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const formData = new FormData()
  formData.append('file', file)
  const response = await axios.post(url, formData, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'multipart/form-data'
    }
  })
  // 后端统一信封：{ code, message, data }
  const payload = response.data as { code?: number; data?: ExcelImportResult<T> } & Partial<ExcelImportResult<T>>
  if (payload && typeof payload === 'object' && 'data' in payload && payload.data) {
    return payload.data
  }
  return payload as unknown as ExcelImportResult<T>
}

/**
 * 触发浏览器下载一个 Blob。
 *
 * @param blob     待下载的 Blob 数据
 * @param fileName 文件名（含扩展名）
 */
export function triggerBlobDownload(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 释放 URL，避免内存泄漏
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

/**
 * 从响应头 Content-Disposition 中提取文件名，失败时回退到 URL 的最后一段。
 *
 * @param headers 响应头
 * @param url     请求地址（用于回退）
 */
function extractFileNameFromResponse(
  headers: Record<string, unknown> | undefined,
  url: string
): string {
  const raw = headers?.['content-disposition'] ?? headers?.['Content-Disposition']
  const disposition = typeof raw === 'string' ? raw : ''
  if (disposition) {
    const match = /filename\*?=([^;]+)/i.exec(disposition)
    if (match && match[1]) {
      let name = match[1].trim().replace(/^["']|["']$/g, '')
      // 处理 URL 编码（filename*=UTF-8''xxx.xlsx）
      if (name.startsWith("UTF-8''") || name.startsWith("utf-8''")) {
        name = decodeURIComponent(name.split("''")[1])
      }
      return name
    }
  }
  // 回退：以 URL 末段 + .xlsx
  const seg = url.split('/').filter(Boolean).pop() ?? 'download'
  return seg.endsWith('.xlsx') ? seg : `${seg}.xlsx`
}
