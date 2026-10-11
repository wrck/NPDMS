/** 安装地址快照仅展示可读地址，保留历史纯文本，不展示结构化 JSON。 */
export const locationSnapshotText = (snapshot?: string | null): string => {
  if (!snapshot) return ''
  try {
    const value: unknown = JSON.parse(snapshot)
    if (typeof value === 'string') return value
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      const location = (value as Record<string, unknown>).fallbackLocation
      return typeof location === 'string' ? location : ''
    }
    return ''
  } catch {
    return snapshot
  }
}
