export const withRequestTimestamp = <T extends { applyTime: string | number }>(data: T) => {
  const applyTime = typeof data.applyTime === 'string'
    ? new Date(data.applyTime.replace(' ', 'T')).getTime() : data.applyTime
  if (!Number.isFinite(applyTime)) throw new Error('申请时间无效')
  return { ...data, applyTime }
}
