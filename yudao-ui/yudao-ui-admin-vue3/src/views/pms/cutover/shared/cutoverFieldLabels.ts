const currentPerformanceLabels: Record<string, string> = {
  concurrentConnections: '当前并发连接数',
  newConnections: '当前新建连接数',
  cpuUsage: '当前CPU使用率',
  memoryUsage: '当前内存使用率'
}

export const cutoverFieldLabel = (field: { code?: string; label?: string; name?: string }) =>
  currentPerformanceLabels[field.code || ''] || field.label || field.name || field.code || ''
