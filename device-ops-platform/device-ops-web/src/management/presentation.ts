const labels: Record<string, string> = {
  QUEUED: '排队中', CONNECTING: '连接中', EXECUTING: '执行中', PARSING: '解析中',
  RUNNING: '运行中', WAITING: '等待中', SUCCEEDED: '成功', PARTIAL_SUCCESS: '部分成功',
  FAILED: '失败', TIMED_OUT: '超时', CANCELLED: '已取消', DRAFT: '草稿',
  PUBLISHED: '已发布', DISABLED: '已停用', UNKNOWN: '未知',
  ENABLED: '已启用', SENT: '已发送', SKIPPED: '已跳过'
}

export function statusLabel(status?: string | null): string {
  return status ? labels[status] ?? status : labels.UNKNOWN!
}

export function statusTone(status?: string | null): 'danger' | 'success' | 'warning' | 'primary' | 'info' {
  if (['FAILED', 'TIMED_OUT'].includes(status || '')) return 'danger'
  if (['SUCCEEDED', 'PUBLISHED', 'SENT', 'ENABLED'].includes(status || '')) return 'success'
  if (['WAITING', 'PARTIAL_SUCCESS'].includes(status || '')) return 'warning'
  if (['CONNECTING', 'EXECUTING', 'PARSING', 'RUNNING'].includes(status || '')) return 'primary'
  return 'info'
}

/** Display only: never substitute the current clock for missing historical evidence. */
export function formatLocalTime(value?: string | null): string {
  if (!value) return '未记录'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return '无效时间'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
