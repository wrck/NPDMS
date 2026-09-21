import type { ReleaseRequest } from '@/types/management'

export const MAX_INPUT_BYTES = 8 * 1024 * 1024
export const MAX_DRAFT_BYTES = 32 * 1024 * 1024
export function inputError(text: string, limit = MAX_INPUT_BYTES): string {
  if (!text.trim()) return '请输入非空日志输入。'
  return new TextEncoder().encode(text).byteLength > limit ? `输入超过 ${limit} 字节 UTF-8 上限。` : ''
}
export function parseDraft(text: string, logType: string): ReleaseRequest {
  if (new TextEncoder().encode(text).byteLength > MAX_DRAFT_BYTES) throw new Error('草稿超过 32 MiB 上限。')
  const value = JSON.parse(text) as ReleaseRequest
  if (!value || typeof value !== 'object' || !value.manifest || value.manifest.logType !== logType) throw new Error('manifest.logType 必须与选中日志类型一致。')
  for (const key of ['manifestVersion', 'logType', 'releaseVersion', 'inputAdapter', 'inputSchemaVersion', 'outputSchemaVersion', 'engineVersion', 'ruleVersion', 'projectionVersion'] as const) {
    if (typeof value.manifest[key] !== 'string' || !value.manifest[key].trim()) throw new Error(`manifest.${key} 为必填字符串。`)
  }
  if (typeof value.projectionsJson !== 'string' || !value.projectionsJson.trim() || !Array.isArray(value.verificationCases) || !value.verificationCases.length) throw new Error('projectionsJson 和非空 verificationCases 样例必填。')
  for (const sample of value.verificationCases) {
    if (!sample || ['caseId', 'inputContent', 'expectedResultJson'].some(key => typeof sample[key as keyof typeof sample] !== 'string' || !sample[key as keyof typeof sample].trim())) throw new Error('验证样例需要 caseId / inputContent / expectedResultJson 字符串。')
  }
  return value
}
