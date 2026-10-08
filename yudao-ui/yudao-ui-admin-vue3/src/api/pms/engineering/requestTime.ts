export const withRequestTimestamp = <T extends { applyTime?: string | number | null }>(data: T) => {
  // Native historical rows may omit this optional server field; do not invent an application time.
  if (data.applyTime == null) return { ...data, applyTime: undefined }
  const applyTime = typeof data.applyTime === 'string'
    ? new Date(data.applyTime.replace(' ', 'T')).getTime() : data.applyTime
  if (!Number.isFinite(applyTime)) throw new Error('申请时间无效')
  return { ...data, applyTime }
}
